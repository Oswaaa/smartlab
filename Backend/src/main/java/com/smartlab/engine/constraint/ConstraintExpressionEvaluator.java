package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class ConstraintExpressionEvaluator {

    public boolean evaluate(String expression, Map<String, JsonNode> variables,
                            Map<String, List<TimedValue>> histories, Instant now) {
        Object result = new Parser(expression, variables, histories, now).parse();
        if (!(result instanceof Boolean value)) {
            throw new IllegalArgumentException("约束expression必须返回boolean");
        }
        return value;
    }

    public record TimedValue(Instant occurredAt, JsonNode value) {
    }

    private static final class Parser {
        private final Lexer lexer;
        private final Map<String, JsonNode> variables;
        private final Map<String, List<TimedValue>> histories;
        private final Instant now;
        private Token current;

        private Parser(String expression, Map<String, JsonNode> variables,
                       Map<String, List<TimedValue>> histories, Instant now) {
            if (expression == null || expression.isBlank()) throw new IllegalArgumentException("约束expression不能为空");
            this.lexer = new Lexer(expression);
            this.variables = variables;
            this.histories = histories;
            this.now = now;
            this.current = lexer.next();
        }

        private Object parse() {
            Object result = or();
            if (current.type != TokenType.EOF) throw error("expression存在无法解析的内容");
            return result;
        }

        private Object or() {
            Object result = and();
            while (match("||")) result = asBoolean(result) || asBoolean(and());
            return result;
        }

        private Object and() {
            Object result = equality();
            while (match("&&")) result = asBoolean(result) && asBoolean(equality());
            return result;
        }

        private Object equality() {
            Object left = comparison();
            while (current.is("==") || current.is("!=")) {
                String operator = current.text;
                advance();
                Object right = comparison();
                left = "==".equals(operator) ? equal(left, right) : !equal(left, right);
            }
            return left;
        }

        private Object comparison() {
            Object left = addition();
            while (current.is(">") || current.is("<") || current.is(">=") || current.is("<=")) {
                String operator = current.text;
                advance();
                int comparison = compare(left, addition());
                left = switch (operator) {
                    case ">" -> comparison > 0;
                    case "<" -> comparison < 0;
                    case ">=" -> comparison >= 0;
                    case "<=" -> comparison <= 0;
                    default -> throw error("不支持的比较操作符: " + operator);
                };
            }
            return left;
        }

        private Object addition() {
            Object left = multiplication();
            while (current.is("+") || current.is("-")) {
                String operator = current.text;
                advance();
                Object right = multiplication();
                if ("+".equals(operator) && (left instanceof String || right instanceof String)) {
                    left = String.valueOf(left) + right;
                } else {
                    left = "+".equals(operator) ? decimal(left).add(decimal(right)) : decimal(left).subtract(decimal(right));
                }
            }
            return left;
        }

        private Object multiplication() {
            Object left = unary();
            while (current.is("*") || current.is("/")) {
                String operator = current.text;
                advance();
                BigDecimal right = decimal(unary());
                if ("/".equals(operator) && BigDecimal.ZERO.compareTo(right) == 0) throw error("expression除数不能为0");
                left = "*".equals(operator) ? decimal(left).multiply(right) : decimal(left).divide(right, 12, java.math.RoundingMode.HALF_UP);
            }
            return left;
        }

        private Object unary() {
            if (match("!")) return !asBoolean(unary());
            if (match("-")) return decimal(unary()).negate();
            return primary();
        }

        private Object primary() {
            if (current.type == TokenType.NUMBER) {
                BigDecimal result = new BigDecimal(current.text);
                advance();
                return result;
            }
            if (current.type == TokenType.STRING) {
                String result = current.text;
                advance();
                return result;
            }
            if (match("(")) {
                Object result = or();
                require(")");
                return result;
            }
            if (current.type != TokenType.IDENTIFIER) throw error("expression需要值");
            String identifier = current.text;
            advance();
            if (match("(")) return function(identifier);
            if ("true".equalsIgnoreCase(identifier)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(identifier)) return Boolean.FALSE;
            JsonNode value = variables.get(identifier);
            return value == null || value.isNull() ? identifier : unwrap(value);
        }

        private Object function(String name) {
            if (!List.of("delta", "avg", "rate").contains(name)) {
                throw error("不支持的约束内置函数: " + name);
            }
            if (current.type != TokenType.IDENTIFIER) throw error(name + "第一个参数必须是绑定变量名");
            String variableName = current.text;
            advance();
            require(",");
            BigDecimal seconds = decimal(or());
            require(")");
            if (seconds.compareTo(BigDecimal.ZERO) <= 0) throw error(name + "时间窗口必须大于0");
            List<TimedValue> samples = new ArrayList<>(histories.getOrDefault(variableName, List.of()));
            if (samples.isEmpty() && variables.containsKey(variableName)) {
                samples.add(new TimedValue(now, variables.get(variableName)));
            }
            samples.sort(Comparator.comparing(TimedValue::occurredAt));
            Instant threshold = now.minusMillis(seconds.multiply(BigDecimal.valueOf(1000)).longValue());
            List<TimedValue> window = samples.stream().filter(sample -> !sample.occurredAt().isBefore(threshold)).toList();
            if (window.isEmpty()) throw error(name + "缺少变量历史数据: " + variableName);
            return switch (name) {
                case "delta" -> decimal(unwrap(window.get(window.size() - 1).value())).subtract(decimal(unwrap(window.get(0).value())));
                case "avg" -> average(window);
                case "rate" -> rate(window);
                default -> throw error("不支持的约束内置函数: " + name);
            };
        }

        private BigDecimal average(List<TimedValue> samples) {
            BigDecimal total = BigDecimal.ZERO;
            for (TimedValue sample : samples) total = total.add(decimal(unwrap(sample.value())));
            return total.divide(BigDecimal.valueOf(samples.size()), 12, java.math.RoundingMode.HALF_UP);
        }

        private BigDecimal rate(List<TimedValue> samples) {
            if (samples.size() < 2) return BigDecimal.ZERO;
            TimedValue first = samples.get(0);
            TimedValue last = samples.get(samples.size() - 1);
            long elapsedMillis = Math.max(1L, last.occurredAt().toEpochMilli() - first.occurredAt().toEpochMilli());
            return decimal(unwrap(last.value())).subtract(decimal(unwrap(first.value())))
                    .divide(BigDecimal.valueOf(elapsedMillis), 12, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(1000));
        }

        private boolean match(String token) {
            if (!current.is(token)) return false;
            advance();
            return true;
        }

        private void require(String token) {
            if (!match(token)) throw error("expression缺少" + token);
        }

        private void advance() {
            current = lexer.next();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + "，位置" + current.position);
        }

        private static Object unwrap(JsonNode value) {
            if (value.isNumber()) return value.decimalValue();
            if (value.isBoolean()) return value.booleanValue();
            if (value.isTextual()) return value.textValue();
            throw new IllegalArgumentException("约束expression只支持number、string和boolean变量");
        }

        private static boolean asBoolean(Object value) {
            if (value instanceof Boolean bool) return bool;
            throw new IllegalArgumentException("逻辑操作符两侧必须是boolean");
        }

        private static BigDecimal decimal(Object value) {
            if (value instanceof BigDecimal decimal) return decimal;
            if (value instanceof Number number) return new BigDecimal(number.toString());
            throw new IllegalArgumentException("算术或比较操作符两侧必须是number");
        }

        private static int compare(Object left, Object right) {
            if (left instanceof BigDecimal || left instanceof Number || right instanceof BigDecimal || right instanceof Number) {
                return decimal(left).compareTo(decimal(right));
            }
            if (left instanceof Boolean leftBoolean && right instanceof Boolean rightBoolean) {
                return Boolean.compare(leftBoolean, rightBoolean);
            }
            return String.valueOf(left).compareTo(String.valueOf(right));
        }

        private static boolean equal(Object left, Object right) {
            if (left instanceof BigDecimal || left instanceof Number || right instanceof BigDecimal || right instanceof Number) {
                return decimal(left).compareTo(decimal(right)) == 0;
            }
            return java.util.Objects.equals(left, right);
        }
    }

    private enum TokenType { NUMBER, STRING, IDENTIFIER, OPERATOR, EOF }

    private record Token(TokenType type, String text, int position) {
        private boolean is(String value) {
            return text.equals(value);
        }
    }

    private static final class Lexer {
        private final String source;
        private int index;

        private Lexer(String source) {
            this.source = source;
        }

        private Token next() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
            if (index >= source.length()) return new Token(TokenType.EOF, "", index);
            int start = index;
            char character = source.charAt(index);
            if (Character.isDigit(character) || (character == '.' && index + 1 < source.length() && Character.isDigit(source.charAt(index + 1)))) {
                index++;
                while (index < source.length() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) index++;
                return new Token(TokenType.NUMBER, source.substring(start, index), start);
            }
            if (Character.isLetter(character) || character == '_') {
                index++;
                while (index < source.length() && (Character.isLetterOrDigit(source.charAt(index)) || source.charAt(index) == '_')) index++;
                return new Token(TokenType.IDENTIFIER, source.substring(start, index), start);
            }
            if (character == '\'' || character == '"') {
                char quote = character;
                index++;
                StringBuilder value = new StringBuilder();
                while (index < source.length() && source.charAt(index) != quote) {
                    if (source.charAt(index) == '\\' && index + 1 < source.length()) index++;
                    value.append(source.charAt(index++));
                }
                if (index >= source.length()) throw new IllegalArgumentException("字符串没有结束引号，位置" + start);
                index++;
                return new Token(TokenType.STRING, value.toString(), start);
            }
            if (index + 1 < source.length()) {
                String doubleOperator = source.substring(index, index + 2);
                if (List.of("&&", "||", ">=", "<=", "==", "!=").contains(doubleOperator)) {
                    index += 2;
                    return new Token(TokenType.OPERATOR, doubleOperator, start);
                }
            }
            if ("+-*/><!(),".indexOf(character) >= 0) {
                index++;
                return new Token(TokenType.OPERATOR, String.valueOf(character), start);
            }
            throw new IllegalArgumentException("无法识别的expression字符: " + character + "，位置" + start);
        }
    }
}
