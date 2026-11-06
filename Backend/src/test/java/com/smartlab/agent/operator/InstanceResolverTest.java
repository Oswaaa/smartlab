package com.smartlab.agent.operator;

import com.smartlab.management.entity.resource.device.DeviceInstances;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用库里真实的 65 个实例中的关键子集做断言。
 *
 * <p>最有价值的一条是 {@code 电磁阀1 → Valve00}：中文序号与点位序号差 1，
 * 任何"从点位名推导序号"的实现都会在这里翻车。
 */
class InstanceResolverTest {

    private static DeviceInstances instance(long id, String name, String point, long modelId) {
        DeviceInstances entity = new DeviceInstances();
        entity.setId(id);
        entity.setInstanceName(name);
        entity.setBoundDevicePoint(point);
        entity.setBoundAdapterName("GetPLCDataAdapter");
        entity.setDeviceModelId(modelId);
        entity.setLifecycleStatus("IN_USE");
        entity.setInstanceKind("PHYSICAL");
        return entity;
    }

    private static InstanceResolver resolver() {
        List<DeviceInstances> all = new ArrayList<>();
        long[] coFlowIds = {152, 154, 156, 158, 160, 163, 165, 167};
        long[] h2FlowIds = {153, 155, 157, 159, 162, 164, 166, 168};
        for (int i = 1; i <= 8; i++) {
            all.add(instance(219 + i, "反应釜#" + i, "Reactor" + i, 41));
            all.add(instance(203 + i, "热阱#" + i, "Heatsink" + i, 37));
            all.add(instance(coFlowIds[i - 1], "CO流量计#" + i, "CoFlow" + i, 38));
            all.add(instance(h2FlowIds[i - 1], "H2流量计#" + i, "H2Flow" + i, 38));
            all.add(instance(192 + i, "气密连接器#" + i, String.format("valve_%02d", i), 45));
        }
        for (int i = 1; i <= 16; i++) {
            all.add(instance(117 + i, "电磁阀#" + i, String.format("Valve%02d", i - 1), 35));
        }
        all.add(instance(169, "保温箱#1", "Incubator1", 39));
        all.add(instance(170, "保温箱#2", "Incubator2", 39));
        all.add(instance(171, "色谱伴热", "Chromatography", 40));
        all.add(instance(181, "H2总阀", "H2Valve", 42));
        all.add(instance(182, "CO总阀", "CoValve", 42));
        all.add(instance(183, "PLC-上位机控制系统", "Plant", 43));
        all.add(instance(203, "三轴机械臂", "arm_001", 44));
        all.add(instance(201, "气动矫正器", "corrector_001", 46));
        return new InstanceResolver(all);
    }

    private static void assertId(long expected, InstanceResolver.Resolution resolution) {
        assertEquals(InstanceResolver.Status.FOUND, resolution.status(), "期望唯一命中: " + resolution);
        assertEquals(expected, resolution.resolved().id());
    }

    @Test
    void 归一化去除井号空格与全角() {
        assertEquals("反应釜1", InstanceResolver.normalize("反应釜#1"));
        assertEquals("反应釜1", InstanceResolver.normalize(" 反应釜 # 1 "));
        assertEquals("反应釜1", InstanceResolver.normalize("反应釜＃１"));
        assertEquals("co流量计1", InstanceResolver.normalize("CO流量计#1"));
    }

    @Test
    void 中文数字转阿拉伯() {
        assertEquals("反应釜1", InstanceResolver.normalize("反应釜一"));
        assertEquals("反应釜16", InstanceResolver.normalize("反应釜十六"));
        assertEquals("反应釜20", InstanceResolver.normalize("反应釜二十"));
        // 「1号反应釜」保留下量词，靠别名表命中，不做改写
        assertEquals("1号反应釜", InstanceResolver.normalize("1号反应釜"));
    }

    @Test
    void 反应釜序号必须精确匹配() {
        InstanceResolver resolver = resolver();
        assertId(220, resolver.resolve("反应釜1"));
        assertId(221, resolver.resolve("反应釜2"));
        assertId(227, resolver.resolve("反应釜8"));
        assertId(220, resolver.resolve("1号反应釜"));
        assertId(220, resolver.resolve("反应釜一"));
        assertId(220, resolver.resolve("反应釜#1"));
        assertId(220, resolver.resolve("Reactor1"));
    }

    @Test
    void 反应釜序号越界不能顺延到别的设备() {
        InstanceResolver.Resolution resolution = resolver().resolve("反应釜9");
        assertEquals(InstanceResolver.Status.NOT_FOUND, resolution.status(), "序号 9 不存在，不能返回 8 号");
        assertNull(resolution.resolved());
    }

    @Test
    void 电磁阀中文序号与点位序号差一() {
        InstanceResolver resolver = resolver();
        // 电磁阀#1 → Valve00
        assertId(118, resolver.resolve("电磁阀1"));
        assertEquals("Valve00", resolver.resolve("电磁阀1").resolved().devicePoint());
        // 电磁阀#16 → Valve15
        assertId(133, resolver.resolve("电磁阀16"));
        assertEquals("Valve15", resolver.resolve("电磁阀16").resolved().devicePoint());
    }

    @Test
    void 气密连接器走小写点位命名() {
        InstanceResolver resolver = resolver();
        assertId(193, resolver.resolve("气密连接器1"));
        assertEquals("valve_01", resolver.resolve("气密连接器1").resolved().devicePoint());
        assertId(200, resolver.resolve("气密连接器8"));
    }

    @Test
    void 两组valve点位名归一化后冲突必须报歧义() {
        // 电磁阀#2 的 Valve01 与 气密连接器#1 的 valve_01 归一化后同为 valve01
        InstanceResolver.Resolution resolution = resolver().resolve("valve01");
        assertTrue(resolution.status() != InstanceResolver.Status.FOUND,
                "点位名在两套命名间冲突，不能静默选中一个");
    }

    @Test
    void 只说流量计必须报歧义() {
        InstanceResolver.Resolution resolution = resolver().resolve("流量计1");
        assertEquals(InstanceResolver.Status.AMBIGUOUS, resolution.status());
        assertTrue(resolution.candidates().size() >= 2);
        assertTrue(resolution.candidates().stream().anyMatch(c -> "CO流量计#1".equals(c.instanceName())));
        assertTrue(resolution.candidates().stream().anyMatch(c -> "H2流量计#1".equals(c.instanceName())));
    }

    @Test
    void 无序号单例可以唯一命中() {
        InstanceResolver resolver = resolver();
        assertId(203, resolver.resolve("三轴机械臂"));
        assertId(171, resolver.resolve("色谱伴热"));
        assertId(183, resolver.resolve("PLC-上位机控制系统"));
        assertId(181, resolver.resolve("H2总阀"));
    }

    @Test
    void 完全无关的名字返回未找到() {
        assertEquals(InstanceResolver.Status.NOT_FOUND, resolver().resolve("离心机").status());
        assertEquals(InstanceResolver.Status.NOT_FOUND, resolver().resolve("").status());
    }

    @Test
    void 关键字搜索能列出同类设备() {
        List<InstanceResolver.Candidate> hits = resolver().search("反应釜");
        assertEquals(8, hits.size());
    }

    @Test
    void 索引规模与去重正确() {
        assertEquals(8 + 8 + 8 + 8 + 8 + 16 + 2 + 1 + 1 + 1 + 1 + 1 + 1, resolver().size());
    }
}
