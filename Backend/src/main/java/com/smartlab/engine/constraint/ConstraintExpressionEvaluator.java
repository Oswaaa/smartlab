package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConstraintExpressionEvaluator {

    private static final Pattern FUNCTION_CALL = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Set<String> CONSTRAINT_FUNCTIONS = Set.of("delta", "avg", "rate");
    private static final Set<String> WORKFLOW_FUNCTIONS = Set.of("delta", "avg", "rate", "max", "min", "abs");

    public JsonNode evaluateValue(String expression, Map<String, JsonNode> variables,
                                  Map<String, List<TimedValue>> histories, Instant now) {
        Object result = new Parser(expression, variables, histories, now, CONSTRAINT_FUNCTIONS).parse();
        return JsonNodeSupport.MAPPER.valueToTree(result);
    }

    public JsonNode evaluateWorkflowValue(String expression, Map<String, JsonNode> variables,
                                          Map<String, List<TimedValue>> histories, Instant now) {
        Object result = new Parser(expression, variables, histories, now, WORKFLOW_FUNCTIONS).parse();
        return JsonNodeSupport.MAPPER.valueToTree(result);
    }

    public boolean evaluate(String expression, Map<String, JsonNode> variables,
                            Map<String, List<TimedValue>> histories, Instant now) {
        JsonNode result = evaluateValue(expression, variables, histories, now);
        if (!result.isBoolean()) throw new IllegalArgumentException("约束expression必须返回boolean");
        return result.booleanValue();
    }

    public void validate(String expression, Map<String, JsonNode> variables) {
        Set<String> referenced = referencedVariables(expression);
        Set<String> unknown = new LinkedHashSet<>(referenced);
        unknown.removeAll(variables.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("expression引用了未绑定变量: " + unknown + "；字符串常量必须使用引号");
        }
        Set<String> unused = new LinkedHashSet<>(variables.keySet());
        unused.removeAll(referenced);
        if (!unused.isEmpty()) throw new IllegalArgumentException("bindings存在未被expression引用的变量: " + unused);
        Instant now = Instant.now();
        Map<String, List<TimedValue>> histories = variables.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, entry -> List.of(new TimedValue(now.minusSeconds(1), entry.getValue()), new TimedValue(now, entry.getValue()))));
        evaluate(expression, variables, histories, now);
    }

    public void validateCalculation(String expression, Map<String, JsonNode> variables) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("工作流计算expression不能为空");
        }
        Matcher function = FUNCTION_CALL.matcher(expression);
        if (function.find()) {
            throw new IllegalArgumentException("工作流计算expression暂不支持函数: " + function.group(1));
        }
        Set<String> referenced = referencedVariables(expression);
        Set<String> unknown = new LinkedHashSet<>(referenced);
        unknown.removeAll(variables.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("工作流计算expression引用了未声明的数值变量: " + unknown);
        }
        for (String name : referenced) {
            JsonNode value = variables.get(name);
            if (value == null || !value.isNumber()) {
                throw new IllegalArgumentException("工作流计算expression只能引用数值变量: " + name);
            }
        }
        JsonNode result = evaluateValue(expression, variables, Map.of(), Instant.now());
        if (!result.isNumber()) {
            throw new IllegalArgumentException("工作流计算expression必须返回数值");
        }
    }

    public void validateTemporalCalculation(String expression, Map<String, JsonNode> variables) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("工作流计算expression不能为空");
        }
        Matcher functions = FUNCTION_CALL.matcher(expression);
        while (functions.find()) {
            String name = functions.group(1).toLowerCase();
            if (!WORKFLOW_FUNCTIONS.contains(name)) {
                throw new IllegalArgumentException("工作流计算expression不支持函数: " + name);
            }
        }
        Set<String> referenced = referencedVariables(expression);
        Set<String> unknown = new LinkedHashSet<>(referenced);
        unknown.removeAll(variables.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("工作流计算expression引用了未声明的数值变量: " + unknown);
        }
        Instant now = Instant.now();
        Map<String, List<TimedValue>> histories = new java.util.LinkedHashMap<>();
        for (String name : referenced) {
            JsonNode value = variables.get(name);
            if (value == null || !value.isNumber()) {
                throw new IllegalArgumentException("工作流计算expression只能引用数值变量: " + name);
            }
            histories.put(name, List.of(new TimedValue(now.minusSeconds(1), value), new TimedValue(now, value)));
        }
        JsonNode result = evaluateWorkflowValue(expression, variables, histories, now);
        if (!result.isNumber()) throw new IllegalArgumentException("工作流计算expression必须返回数值");
    }

    public Set<String> referencedVariables(String expression) {
        if (expression == null || expression.isBlank()) throw new IllegalArgumentException("约束expression不能为空");
        Set<String> result = new LinkedHashSet<>();
        Lexer lexer = new Lexer(expression);
        Token token;
        do {
            token = lexer.next();
            if (token.type == TokenType.IDENTIFIER
                    && !Set.of("true", "false", "delta", "avg", "rate", "max", "min", "abs")
                    .contains(token.text.toLowerCase())) {
                result.add(token.text);
            }
        } while (token.type != TokenType.EOF);
        return result;
    }

    public record TimedValue(Instant occurredAt, JsonNode value) {
    }

    public static final class TemporalDataUnavailableException extends IllegalArgumentException {
        public TemporalDataUnavailableException(String message) { super(message); }
    }

    private static final class Parser {
        private final Lexer lexer;
        private final Map<String, JsonNode> variables;
        private final Map<String, List<TimedValue>> histories;
        private final Instant now;
        private final Set<String> allowedFunctions;
        private Token current;

        private Parser(String expression, Map<String, JsonNode> variables,
                       Map<String, List<TimedValue>> histories, Instant now, Set<String> allowedFunctions) {
            if (expression == null || expression.isBlank()) throw new IllegalArgumentException("约束expression不能为空");
            this.lexer = new Lexer(expression);
            this.variables = variables;
            this.histories = histories;
            this.now = now;
            this.allowedFunctions = allowedFunctions;
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
            JsonNode value = resolvePath(variables, identifier);
            if (value == null || value.isNull()) throw error("expression引用了未绑定变量: " + identifier);
            return unwrap(value);
        }

        private JsonNode resolvePath(Map<String, JsonNode> roots, String path) {
            String[] parts = path.split("\\.");
            JsonNode current = roots.get(parts[0]);
            for (int index = 1; current != null && index < parts.length; index++) {
                current = current.isObject() ? current.get(parts[index]) : null;
            }
            return current;
        }

        private Object function(String name) {
            name = name.toLowerCase();
            if (!allowedFunctions.contains(name)) {
                throw error("不支持的约束内置函数: " + name);
            }
            if ("abs".equals(name)) {
                Object value = or();
                require(")");
                return decimal(value).abs();
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
            if (window.isEmpty()) throw new TemporalDataUnavailableException(name + "缺少变量历史数据: " + variableName);
            if (List.of("rate", "delta").contains(name) && window.size() < 2) {
                throw new TemporalDataUnavailableException(name + "至少需要两个历史样本: " + variableName);
            }
            return switch (name) {
                case "delta" -> decimal(unwrap(window.get(window.size() - 1).value())).subtract(decimal(unwrap(window.get(0).value())));
                case "avg" -> average(window);
                case "rate" -> rate(window);
                case "max" -> window.stream().map(sample -> decimal(unwrap(sample.value()))).max(BigDecimal::compareTo).orElseThrow();
                case "min" -> window.stream().map(sample -> decimal(unwrap(sample.value()))).min(BigDecimal::compareTo).orElseThrow();
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
            if (value.isArray() || value.isObject()) return value.deepCopy();
            throw new IllegalArgumentException("约束expression不支持null变量");
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
            if (left instanceof JsonNode leftNode && leftNode.isArray() && !(right instanceof JsonNode rightNode && rightNode.isArray())) {
                for (JsonNode item : leftNode) if (equal(unwrap(item), right)) return true;
                return false;
            }
            if (right instanceof JsonNode rightNode && rightNode.isArray() && !(left instanceof JsonNode leftNode && leftNode.isArray())) {
                for (JsonNode item : rightNode) if (equal(left, unwrap(item))) return true;
                return false;
            }
            if (left instanceof JsonNode leftNode || right instanceof JsonNode) {
                JsonNode leftNodeValue = left instanceof JsonNode node ? node : JsonNodeSupport.MAPPER.valueToTree(left);
                JsonNode rightNodeValue = right instanceof JsonNode node ? node : JsonNodeSupport.MAPPER.valueToTree(right);
                return leftNodeValue.equals(rightNodeValue);
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
                while (index < source.length() && (Character.isLetterOrDigit(source.charAt(index)) || source.charAt(index) == '_' || source.charAt(index) == '.')) index++;
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
