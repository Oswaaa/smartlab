package com.smartlab.agent.operator;

import com.smartlab.management.entity.resource.device.DeviceInstances;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设备实例名解析器。
 *
 * <p>职责边界：只做"字符串 → 设备实例"的确定性映射，不做任何语义推断。
 * 因此结果是可断言的，可以直接写单元测试，不需要大模型参与。
 *
 * <p>三条硬规则（对着真实数据踩出来的）：
 * <ol>
 *   <li>序号只认中文实例名里的 {@code #N}，<b>绝不从点位名推导</b>。
 *       电磁阀#1 绑的是 Valve00，序号差 1；若按点位名推序号，会静默打开 2 号阀。</li>
 *   <li>数字不一致的候选直接淘汰，宁可返回"没找到"也不返回"像的那个"。</li>
 *   <li>命中多个一律返回歧义，由调用方反问用户，不自动挑第一个。</li>
 * </ol>
 */
public final class InstanceResolver {

    /** 解析状态。 */
    public enum Status {
        /** 唯一命中。 */
        FOUND,
        /** 命中多个，需要用户澄清。 */
        AMBIGUOUS,
        /** 无命中。 */
        NOT_FOUND
    }

    /** 一个设备实例的精简视图，只保留解析与展示需要的字段。 */
    public record Candidate(
            long id,
            String instanceName,
            String devicePoint,
            String adapterName,
            long modelId,
            String lifecycleStatus,
            String instanceKind
    ) {
        public boolean controllable() {
            return "IN_USE".equalsIgnoreCase(lifecycleStatus == null ? "" : lifecycleStatus);
        }
    }

    /** 解析结果。 */
    public record Resolution(Status status, String normalizedQuery, Candidate resolved,
                             List<Candidate> candidates, String hint) {
        public boolean found() {
            return status == Status.FOUND && resolved != null;
        }
    }

    private final Map<String, List<Candidate>> aliasIndex = new ConcurrentHashMap<>();
    private final Map<String, List<Candidate>> baseIndex = new ConcurrentHashMap<>();
    private final Map<Long, Candidate> byId = new ConcurrentHashMap<>();

    public InstanceResolver(Collection<DeviceInstances> instances) {
        rebuild(instances);
    }

    /** 全量重建索引。设备增删后重新调用即可。 */
    public synchronized void rebuild(Collection<DeviceInstances> instances) {
        aliasIndex.clear();
        baseIndex.clear();
        byId.clear();
        if (instances == null) return;
        for (DeviceInstances instance : instances) {
            if (instance == null || instance.getId() == null) continue;
            Candidate candidate = toCandidate(instance);
            byId.put(candidate.id(), candidate);
            register(candidate);
        }
    }

    public int size() {
        return byId.size();
    }

    public Candidate byInstanceId(long id) {
        return byId.get(id);
    }

    public List<Candidate> all() {
        return List.copyOf(new LinkedHashSet<>(byId.values()));
    }

    /** 按关键字模糊列出（给 list_instances 工具用），关键字为空则返回全部。 */
    public List<Candidate> search(String keyword) {
        String key = normalize(keyword);
        if (key.isEmpty()) return all();
        List<Candidate> hits = new ArrayList<>();
        for (Map.Entry<String, List<Candidate>> entry : aliasIndex.entrySet()) {
            if (entry.getKey().contains(key)) {
                for (Candidate candidate : entry.getValue()) {
                    if (!hits.contains(candidate)) hits.add(candidate);
                }
            }
        }
        if (hits.isEmpty()) {
            for (Map.Entry<String, List<Candidate>> entry : baseIndex.entrySet()) {
                if (entry.getKey().contains(key) || key.contains(entry.getKey())) {
                    for (Candidate candidate : entry.getValue()) {
                        if (!hits.contains(candidate)) hits.add(candidate);
                    }
                }
            }
        }
        hits.sort((a, b) -> Long.compare(a.id(), b.id()));
        return hits;
    }

    /**
     * 解析用户口述的设备名。
     */
    public Resolution resolve(String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return new Resolution(Status.NOT_FOUND, normalized, null, List.of(), "设备名不能为空");
        }
        List<Candidate> direct = aliasIndex.get(normalized);
        if (direct != null && !direct.isEmpty()) {
            return narrow(normalized, new ArrayList<>(new LinkedHashSet<>(direct)));
        }
        // 「第1个气密连接器」「气密连接器第1台」这类：抽数字 + 去量词后按基名匹配
        return narrow(normalized, fallbackPool(normalized));
    }

    private Resolution narrow(String normalized, List<Candidate> pool) {
        if (pool.isEmpty()) {
            return new Resolution(Status.NOT_FOUND, normalized, null, List.of(),
                    "没有匹配的设备，可先列出全部设备再选");
        }
        if (pool.size() == 1) {
            return new Resolution(Status.FOUND, normalized, pool.get(0), List.of(pool.get(0)), null);
        }
        return new Resolution(Status.AMBIGUOUS, normalized, null, List.copyOf(pool),
                "命中多个设备，需要用户确认具体是哪一个");
    }

    /**
     * 兜底匹配：从查询里分离出序号与基名，基名命中后强制校验序号一致。
     */
    private List<Candidate> fallbackPool(String normalized) {
        Integer number = firstNumber(normalized);
        String base = stripMeterWords(normalized.replaceAll("\\d+", ""));
        List<Candidate> pool = new ArrayList<>();
        if (!base.isEmpty()) {
            for (Map.Entry<String, List<Candidate>> entry : baseIndex.entrySet()) {
                String key = entry.getKey();
                if (key.equals(base) || key.contains(base) || base.contains(key)) {
                    for (Candidate candidate : entry.getValue()) {
                        if (!pool.contains(candidate)) pool.add(candidate);
                    }
                }
            }
        }
        if (pool.isEmpty()) {
            // 再退一步：别名包含匹配
            for (Map.Entry<String, List<Candidate>> entry : aliasIndex.entrySet()) {
                if (entry.getKey().contains(base) && !base.isEmpty()) {
                    for (Candidate candidate : entry.getValue()) {
                        if (!pool.contains(candidate)) pool.add(candidate);
                    }
                }
            }
        }
        if (number == null || pool.isEmpty()) return pool;
        // 序号强校验：名字里带序号的候选，序号必须与用户说的一致
        List<Candidate> matched = new ArrayList<>();
        for (Candidate candidate : pool) {
            Integer candidateNumber = nameNumber(candidate.instanceName());
            if (candidateNumber == null || candidateNumber.equals(number)) matched.add(candidate);
        }
        return matched;
    }

    private void register(Candidate candidate) {
        String name = candidate.instanceName() == null ? "" : candidate.instanceName();
        String normalizedName = normalize(name);
        put(aliasIndex, normalizedName, candidate);
        String point = normalize(candidate.devicePoint());
        if (!point.isEmpty()) put(aliasIndex, point, candidate);

        NameParts parts = splitName(normalizedName);
        String base = parts.base();
        if (parts.number() != null) {
            put(aliasIndex, base + parts.number(), candidate);
            put(aliasIndex, parts.number() + "号" + base, candidate);
            put(aliasIndex, base + "#" + parts.number(), candidate);
            put(aliasIndex, "第" + parts.number() + "个" + base, candidate);
        }
        if (!base.isEmpty()) {
            List<Candidate> bucket = baseIndex.computeIfAbsent(base, key -> new ArrayList<>());
            if (!bucket.contains(candidate)) bucket.add(candidate);
        }
    }

    private static void put(Map<String, List<Candidate>> index, String key, Candidate candidate) {
        if (key == null || key.isEmpty()) return;
        List<Candidate> bucket = index.computeIfAbsent(key, k -> new ArrayList<>());
        if (!bucket.contains(candidate)) bucket.add(candidate);
    }

    private record NameParts(String base, Integer number) {
    }

    private static NameParts splitName(String normalizedName) {
        Matcher matcher = Pattern.compile("^(.*?)(\\d+)$").matcher(normalizedName);
        if (matcher.matches()) {
            String digits = matcher.group(2);
            if (digits.length() <= 3) {
                return new NameParts(matcher.group(1), Integer.valueOf(digits));
            }
        }
        return new NameParts(normalizedName, null);
    }

    private static Integer nameNumber(String instanceName) {
        return splitName(normalize(instanceName)).number();
    }

    private static Integer firstNumber(String text) {
        Matcher matcher = Pattern.compile("(\\d+)").matcher(text);
        if (matcher.find()) {
            try {
                return Integer.valueOf(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String stripMeterWords(String text) {
        return text.replace("第", "").replace("个", "").replace("台", "")
                .replace("号", "").replace("的", "").replace("设备", "");
    }

    /**
     * 归一化：全角转半角 → 中文数字转阿拉伯 → 去分隔符与空白 → 小写。
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder halfWidth = new StringBuilder(raw.length());
        for (char ch : raw.toCharArray()) {
            char current = ch;
            if (current >= '\uFF01' && current <= '\uFF5E') {
                current = (char) (current - 0xFEE0);
            } else if (current == '\u3000') {
                current = ' ';
            }
            halfWidth.append(current);
        }
        String converted = convertChineseDigits(halfWidth.toString());
        StringBuilder cleaned = new StringBuilder(converted.length());
        String dropped = "#-—_/\\()（）【】[]{}<>、，。·.,:：;；\"'“”‘’!！?？@$%^&*+=|~`";
        for (char ch : converted.toCharArray()) {
            if (Character.isWhitespace(ch)) continue;
            if (dropped.indexOf(ch) >= 0) continue;
            cleaned.append(ch);
        }
        return cleaned.toString().toLowerCase(Locale.ROOT);
    }

    private static final Pattern TENS = Pattern.compile("([一二三四五六七八九])?十([一二三四五六七八九])?");
    private static final String CN_DIGITS = "零一二三四五六七八九";

    private static String convertChineseDigits(String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher matcher = TENS.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            int tens = matcher.group(1) == null ? 1 : CN_DIGITS.indexOf(matcher.group(1));
            int ones = matcher.group(2) == null ? 0 : CN_DIGITS.indexOf(matcher.group(2));
            matcher.appendReplacement(buffer, String.valueOf(tens * 10 + ones));
        }
        matcher.appendTail(buffer);
        String afterTens = buffer.toString();
        StringBuilder result = new StringBuilder(afterTens.length());
        for (char ch : afterTens.toCharArray()) {
            int index = CN_DIGITS.indexOf(ch);
            if (index > 0) {
                result.append(index);
            } else if (index == 0) {
                result.append('0');
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static Candidate toCandidate(DeviceInstances instance) {
        return new Candidate(
                instance.getId(),
                instance.getInstanceName(),
                instance.getBoundDevicePoint(),
                instance.getBoundAdapterName(),
                instance.getDeviceModelId() == null ? 0L : instance.getDeviceModelId(),
                instance.getLifecycleStatus(),
                instance.getInstanceKind()
        );
    }

    /** 便于调试：导出当前索引。 */
    Map<String, List<Candidate>> debugAliasIndex() {
        Map<String, List<Candidate>> snapshot = new LinkedHashMap<>();
        aliasIndex.forEach((key, value) -> snapshot.put(key, List.copyOf(value)));
        return snapshot;
    }
}
