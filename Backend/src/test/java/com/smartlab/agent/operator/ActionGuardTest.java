package com.smartlab.agent.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.management.entity.resource.device.DeviceModels;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionGuardTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static DeviceModels model() {
        DeviceModels model = new DeviceModels();
        try {
            model.setAttributes(MAPPER.readTree("""
                    [{"attributeName":"attribute_1","displayName":"当前温度","unit":"℃","dataType":"DOUBLE"},
                     {"attributeName":"attribute_2","displayName":"当前压力","unit":"MPa","dataType":"DOUBLE"}]
                    """));
            model.setIntrinsicConstraint(MAPPER.readTree("""
                    [{"operator":">","boundaryValue":600,"violationStateName":"温度异常","objectAttributeName":"attribute_1"},
                     {"operator":">","boundaryValue":6,"violationStateName":"压力异常","objectAttributeName":"attribute_2"}]
                    """));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        return model;
    }

    private static ActionGuard.Snapshot online(String cmdState) {
        return new ActionGuard.Snapshot("反应釜#1", "IN_USE", "PHYSICAL", "ENABLED",
                "ONLINE", OffsetDateTime.now(), cmdState);
    }

    private static CapabilityBinder.CapabilityView heat() {
        return new CapabilityBinder.CapabilityView("capability_1", "加热", "heat", false,
                "capability_3", List.of());
    }

    private static CapabilityBinder.CapabilityView stopHeat() {
        return new CapabilityBinder.CapabilityView("capability_3", "停止加热", "stopHeat", true,
                null, List.of());
    }

    private static List<CapabilityBinder.BoundParameter> params(double value, String unit) {
        return List.of(new CapabilityBinder.BoundParameter("parameter_1", "目标温度1", value, unit,
                CapabilityBinder.Source.USER));
    }

    @Test
    void 在线空闲设备可以通过() {
        ActionGuard.Result result = ActionGuard.check(online("IDLE"), heat(), List.of(), model());
        assertTrue(result.pass());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void 离线直接阻断() {
        ActionGuard.Snapshot offline = new ActionGuard.Snapshot("反应釜#1", "IN_USE", "PHYSICAL", "ENABLED",
                "OFFLINE", OffsetDateTime.now().minusMinutes(5), "IDLE");
        ActionGuard.Result result = ActionGuard.check(offline, heat(), List.of(), model());
        assertFalse(result.pass());
        assertTrue(result.blocking().stream().anyMatch(i -> i.code().contains("OFFLINE")));
    }

    @Test
    void 在线但心跳超时同样阻断() {
        ActionGuard.Snapshot stale = new ActionGuard.Snapshot("反应釜#1", "IN_USE", "PHYSICAL", "ENABLED",
                "ONLINE", OffsetDateTime.now().minusSeconds(45), "IDLE");
        ActionGuard.Result result = ActionGuard.check(stale, heat(), List.of(), model());
        assertFalse(result.pass());
        assertTrue(result.blocking().stream().anyMatch(i -> "DEVICE_HEARTBEAT_LOST".equals(i.code())));
    }

    @Test
    void 正在执行时普通指令被阻断() {
        for (String state : List.of("SENT", "RUNNING", "ABORTING")) {
            ActionGuard.Result result = ActionGuard.check(online(state), heat(), List.of(), model());
            assertFalse(result.pass(), state + " 状态下不应放行普通指令");
        }
    }

    @Test
    void 正在执行时中止指令仍然放行() {
        for (String state : List.of("SENT", "RUNNING", "ABORTING")) {
            ActionGuard.Result result = ActionGuard.check(online(state), stopHeat(), List.of(), model());
            assertTrue(result.pass(), state + " 状态下必须允许停止");
        }
    }

    @Test
    void 已注销设备阻断() {
        ActionGuard.Snapshot retired = new ActionGuard.Snapshot("反应釜#1", "RETIRED", "PHYSICAL", "ENABLED",
                "ONLINE", OffsetDateTime.now(), "IDLE");
        ActionGuard.Result result = ActionGuard.check(retired, heat(), List.of(), model());
        assertFalse(result.pass());
        assertTrue(result.blocking().stream().anyMatch(i -> "DEVICE_RETIRED".equals(i.code())));
    }

    @Test
    void 网关停用阻断() {
        ActionGuard.Snapshot disabled = new ActionGuard.Snapshot("反应釜#1", "IN_USE", "PHYSICAL", "DISABLED",
                "ONLINE", OffsetDateTime.now(), "IDLE");
        ActionGuard.Result result = ActionGuard.check(disabled, heat(), List.of(), model());
        assertFalse(result.pass());
        assertTrue(result.blocking().stream().anyMatch(i -> "ADAPTER_DISABLED".equals(i.code())));
    }

    @Test
    void 超过温度上限只预警不阻断() {
        ActionGuard.Result result = ActionGuard.check(online("IDLE"), heat(), params(700, "℃"), model());
        assertTrue(result.pass(), "超限是危险但不是非法，系统本身允许设定");
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).text().contains("温度异常"));
    }

    @Test
    void 恰好等于上限不预警() {
        ActionGuard.Result result = ActionGuard.check(online("IDLE"), heat(), params(600, "℃"), model());
        assertTrue(result.pass());
        assertTrue(result.warnings().isEmpty(), "约束是大于 600，等于 600 不触发");
    }

    @Test
    void 压力超限能按单位匹配到约束() {
        List<CapabilityBinder.BoundParameter> pressure = List.of(
                new CapabilityBinder.BoundParameter("parameter_1", "目标压力", 7.5, "MPa",
                        CapabilityBinder.Source.USER));
        ActionGuard.Result result = ActionGuard.check(online("IDLE"), heat(), pressure, model());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).text().contains("压力异常"));
    }

    @Test
    void 没有单位的参数靠名字兜底匹配() {
        List<CapabilityBinder.BoundParameter> noUnit = List.of(
                new CapabilityBinder.BoundParameter("parameter_1", "目标压力", 9.0, null,
                        CapabilityBinder.Source.USER));
        ActionGuard.Result result = ActionGuard.check(online("IDLE"), heat(), noUnit, model());
        assertEquals(1, result.warnings().size(), "单位缺失时应退回名称匹配");
    }

    @Test
    void 操作符比较正确() {
        assertTrue(ActionGuard.violates(1, ">", 0));
        assertFalse(ActionGuard.violates(0, ">", 0));
        assertTrue(ActionGuard.violates(0, ">=", 0));
        assertTrue(ActionGuard.violates(-1, "<", 0));
        assertFalse(ActionGuard.violates(5, "==", 6));
        assertTrue(ActionGuard.violates(5, "!=", 6));
    }
}
