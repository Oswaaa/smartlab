package com.smartlab.agent.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.management.entity.resource.device.DeviceModels;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用模型 41（反应釜）真实的 capabilities 结构做断言。
 */
class CapabilityBinderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static DeviceModels reactorModel() {
        String json = """
                [
                  {"capabilityName":"capability_1","displayName":"加热","adapterCommandName":"heat",
                   "isAbort":false,"abortCapabilityName":"capability_3",
                   "parameters":[
                     {"name":"parameter_1","displayName":"目标温度1","dataType":"DOUBLE","unit":"℃","required":true},
                     {"name":"parameter_2","displayName":"加热时间1","dataType":"INTEGER","unit":"min","required":true},
                     {"name":"parameter_3","displayName":"目标温度2","dataType":"DOUBLE","unit":"℃","required":false},
                     {"name":"parameter_4","displayName":"加热时间2","dataType":"INTEGER","unit":"min","required":false}
                   ]},
                  {"capabilityName":"capability_2","displayName":"加压","adapterCommandName":"pressurize",
                   "isAbort":false,"abortCapabilityName":"capability_5",
                   "parameters":[
                     {"name":"parameter_1","displayName":"目标压力","dataType":"DOUBLE","unit":null,"required":true}
                   ]},
                  {"capabilityName":"capability_3","displayName":"停止加热","adapterCommandName":"stopHeat",
                   "isAbort":true,"parameters":[]},
                  {"capabilityName":"capability_4","displayName":"设置报警温度","adapterCommandName":"setAlarmTemp",
                   "isAbort":false,
                   "parameters":[
                     {"name":"parameter_1","displayName":"报警温度","dataType":"DOUBLE","unit":"℃","required":true}
                   ]},
                  {"capabilityName":"capability_5","displayName":"停止加压","adapterCommandName":"stopPressurize",
                   "isAbort":true,"parameters":[]}
                ]
                """;
        DeviceModels model = new DeviceModels();
        try {
            model.setCapabilities(MAPPER.readTree(json));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        return model;
    }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    @Test
    void 能力按中文名英文名与编号都能定位() {
        DeviceModels model = reactorModel();
        assertEquals("capability_1", CapabilityBinder.findCapability(model, "加热").capabilityName());
        assertEquals("capability_1", CapabilityBinder.findCapability(model, "heat").capabilityName());
        assertEquals("capability_1", CapabilityBinder.findCapability(model, "capability_1").capabilityName());
        assertEquals("capability_2", CapabilityBinder.findCapability(model, "加压").capabilityName());
        assertEquals("capability_3", CapabilityBinder.findCapability(model, "停止加热").capabilityName());
    }

    @Test
    void 能力清单包含中止标记与中止能力绑定() {
        List<CapabilityBinder.CapabilityView> all = CapabilityBinder.capabilities(reactorModel());
        assertEquals(5, all.size());
        CapabilityBinder.CapabilityView heat = all.get(0);
        assertFalse(heat.abort());
        assertEquals("capability_3", heat.abortCapabilityName());
        assertTrue(all.get(2).abort());
    }

    @Test
    void 语义参数落到parameter编号() {
        CapabilityBinder.Binding binding = CapabilityBinder.bind(
                CapabilityBinder.findCapability(reactorModel(), "加热"),
                params("温度", 500, "时间", 30));
        assertTrue(binding.ok(), binding.error());
        assertEquals(2, binding.parameters().size());
        assertEquals("parameter_1", binding.parameters().get(0).name());
        assertEquals(500.0, binding.parameters().get(0).value());
        assertEquals("parameter_2", binding.parameters().get(1).name());
        assertEquals(30L, binding.parameters().get(1).value());
        assertEquals(CapabilityBinder.Source.USER, binding.parameters().get(0).source());
    }

    @Test
    void 未指定加热时长按持续保持补9999并给预警() {
        CapabilityBinder.Binding binding = CapabilityBinder.bind(
                CapabilityBinder.findCapability(reactorModel(), "加热"),
                params("温度", 500));
        assertTrue(binding.ok(), binding.error());
        CapabilityBinder.BoundParameter minutes = binding.parameters().stream()
                .filter(p -> "parameter_2".equals(p.name())).findFirst().orElseThrow();
        assertEquals(9999L, minutes.value());
        assertEquals(CapabilityBinder.Source.DEFAULT, minutes.source());
        assertEquals(1, binding.warnings().size());
    }

    @Test
    void 未指定温度直接报错不猜值() {
        CapabilityBinder.Binding binding = CapabilityBinder.bind(
                CapabilityBinder.findCapability(reactorModel(), "加热"),
                params("时间", 30));
        assertFalse(binding.ok());
        assertTrue(binding.error().contains("目标温度1"), binding.error());
    }

    @Test
    void 非必填参数未提供时绝不补零() {
        CapabilityBinder.Binding binding = CapabilityBinder.bind(
                CapabilityBinder.findCapability(reactorModel(), "加热"),
                params("温度", 500, "时间", 30));
        Map<String, Object> sent = CapabilityBinder.toParameterMap(binding);
        assertEquals(2, sent.size());
        assertFalse(sent.containsKey("parameter_3"), "未提供的非必填参数不能出现在下发报文里");
        assertFalse(sent.containsKey("parameter_4"));
    }

    @Test
    void 显式写零会被原样下发_因此调用方必须禁止() {
        // 本类不做业务拦截，但要把行为固定下来：0 会被当作真实值发出去
        CapabilityBinder.Binding binding = CapabilityBinder.bind(
                CapabilityBinder.findCapability(reactorModel(), "加热"),
                params("温度", 500, "时间", 0));
        assertEquals(0L, CapabilityBinder.toParameterMap(binding).get("parameter_2"));
    }

    @Test
    void 第二段参数按序号匹配() {
        CapabilityBinder.Binding binding = CapabilityBinder.bind(
                CapabilityBinder.findCapability(reactorModel(), "加热"),
                params("温度", 500, "时间", 30, "第2段温度", 250, "时间2", 60));
        Map<String, Object> sent = CapabilityBinder.toParameterMap(binding);
        assertEquals(500.0, sent.get("parameter_1"));
        assertEquals(30L, sent.get("parameter_2"));
        assertEquals(250.0, sent.get("parameter_3"));
        assertEquals(60L, sent.get("parameter_4"));
    }

    @Test
    void 一个说法只被消费一次不会串到别的段() {
        CapabilityBinder.Binding binding = CapabilityBinder.bind(
                CapabilityBinder.findCapability(reactorModel(), "加热"),
                params("温度", 500, "时间", 30));
        Map<String, Object> sent = CapabilityBinder.toParameterMap(binding);
        assertNull(sent.get("parameter_3"), "「温度」只应绑定第 1 段");
    }

    @Test
    void 零参数能力可以直接下发() {
        CapabilityBinder.Binding binding = CapabilityBinder.bind(
                CapabilityBinder.findCapability(reactorModel(), "停止加热"), Map.of());
        assertTrue(binding.ok(), binding.error());
        assertTrue(binding.parameters().isEmpty());
    }

    @Test
    void 加压必填压力缺失要报错() {
        CapabilityBinder.Binding binding = CapabilityBinder.bind(
                CapabilityBinder.findCapability(reactorModel(), "加压"), Map.of());
        assertFalse(binding.ok());
        assertTrue(binding.error().contains("目标压力"), binding.error());
    }

    @Test
    void 无法定位的能力返回空() {
        assertNull(CapabilityBinder.findCapability(reactorModel(), "离心"));
        assertNotNull(CapabilityBinder.findCapability(reactorModel(), "capability_4"));
    }
}
