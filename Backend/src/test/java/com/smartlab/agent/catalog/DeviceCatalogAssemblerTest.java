package com.smartlab.agent.catalog;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.service.db.resource.device.DeviceCategoryService;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class DeviceCatalogAssemblerTest {
    @Test
    void summarizeDropsAdapterAndStateMachineBodies() throws Exception {
        DeviceModels model = new DeviceModels();
        model.setId(3L);
        model.setModelName("加热套");
        model.setCapabilities(JsonNodeSupport.MAPPER.readTree("""
                [{"capabilityName":"heat","displayName":"加热","isAbort":false,"adapterCommandName":"HEAT_CMD",
                  "parameterMapping":[{"commandParamName":"t","isFixedValue":false}],
                  "parameters":[{"name":"target","displayName":"目标温度","dataType":"DOUBLE","required":true}]}]
                """));
        model.setAttributes(JsonNodeSupport.MAPPER.readTree("""
                [{"attributeName":"temperature","displayName":"温度","dataType":"DOUBLE","unit":"℃"}]
                """));
        model.setPorts(JsonNodeSupport.MAPPER.readTree("""
                [{"portName":"tempOut","direction":"OUT","bindingAttrName":"temperature"}]
                """));
        model.setAdapterContract(JsonNodeSupport.MAPPER.readTree("{\"config\":{\"adapterName\":\"secret\"}}"));
        ObjectNode cmd = JsonNodeSupport.objectNode();
        cmd.put("initialStateName", "IDLE");
        model.setCmdState(cmd);
        model.setStateMachineInterfaces(JsonNodeSupport.MAPPER.readTree("""
                [{"name":"Interface_workflow_in","direction":"IN","interfaceType":"WORKFLOW"},
                 {"name":"Interface_adapter_out","direction":"OUT","interfaceType":"ADAPTER"}]
                """));

        DeviceCatalogAssembler assembler = new DeviceCatalogAssembler(mock(DeviceModelService.class), mock(DeviceCategoryService.class));
        DeviceCatalogItem listed = assembler.summarize(model, "热工", false);
        String listedJson = JsonNodeSupport.MAPPER.writeValueAsString(listed);
        assertFalse(listedJson.contains("adapterContract"));
        assertFalse(listedJson.contains("cmdState"));
        assertFalse(listedJson.contains("adapterCommandName"));
        assertFalse(listedJson.contains("parameterMapping"));
        assertEquals("heat", listed.capabilities().get(0).capabilityName());
        assertEquals(1, listed.capabilities().get(0).parameters().size());
        assertEquals(Boolean.TRUE, listed.capabilities().get(0).parameters().get(0).required());
        assertNull(listed.error());
        assertNull(listed.ports());
        assertNull(listed.stateMachineInterfaces());
        assertFalse(listedJson.contains("isAbort"));
        assertFalse(listedJson.contains("tempOut"));

        DeviceCatalogItem detail = assembler.summarize(model, "热工", true);
        assertEquals("Interface_workflow_in", detail.stateMachineInterfaces().get(0).name());
        assertEquals(1, detail.stateMachineInterfaces().size());
        assertEquals("tempOut", detail.ports().get(0).name());
        String detailJson = JsonNodeSupport.MAPPER.writeValueAsString(detail);
        assertFalse(detailJson.contains("adapterContract"));
        assertFalse(detailJson.contains("cmdState"));
        assertFalse(detailJson.contains("Interface_adapter_out"));
    }

    @Test
    void detailExposesStateRegionsForStateMapping() throws Exception {
        DeviceModels model = new DeviceModels();
        model.setId(4L);
        model.setModelName("热阱");
        model.setOpState(JsonNodeSupport.MAPPER.readTree("""
                {"regions":[
                  {"regionName":"Main","regionType":"OPERATIONAL","initialStateName":"停止",
                   "states":[{"stateName":"停止"},{"stateName":"调节中"},{"stateName":"保温"}],
                   "transitions":[{"fromState":"停止","toState":"调节中"}]},
                  {"regionName":"Exception","regionType":"EXCEPTION","initialStateName":"",
                   "states":[{"stateName":"超温"}]}]}
                """));

        DeviceCatalogAssembler assembler = new DeviceCatalogAssembler(mock(DeviceModelService.class), mock(DeviceCategoryService.class));
        assertNull(assembler.summarize(model, null, false).stateRegions());

        DeviceCatalogItem detail = assembler.summarize(model, null, true);
        assertEquals(2, detail.stateRegions().size());
        assertEquals("Main", detail.stateRegions().get(0).regionName());
        assertEquals("OPERATIONAL", detail.stateRegions().get(0).regionType());
        assertEquals(java.util.List.of("停止", "调节中", "保温"), detail.stateRegions().get(0).states());
        assertEquals("EXCEPTION", detail.stateRegions().get(1).regionType());
        assertFalse(JsonNodeSupport.MAPPER.writeValueAsString(detail).contains("transitions"));
    }

    @Test
    void missingRequiredIsReportedInsteadOfTreatedAsMandatory() throws Exception {
        DeviceModels model = new DeviceModels();
        model.setId(5L);
        model.setModelName("反应釜");
        model.setCapabilities(JsonNodeSupport.MAPPER.readTree("""
                [{"capabilityName":"heat","displayName":"加热",
                  "parameters":[
                    {"name":"target","displayName":"目标温度","dataType":"DOUBLE","required":true},
                    {"name":"temperature2","displayName":"目标温度2","dataType":"DOUBLE"}]}]
                """));

        DeviceCatalogAssembler assembler = new DeviceCatalogAssembler(mock(DeviceModelService.class), mock(DeviceCategoryService.class));
        DeviceCatalogItem item = assembler.summarize(model, null, false);

        assertEquals(Boolean.TRUE, item.capabilities().get(0).parameters().get(0).required());
        assertNull(item.capabilities().get(0).parameters().get(1).required());
        assertEquals("能力参数缺少required: heat.temperature2", item.error());
    }
}
