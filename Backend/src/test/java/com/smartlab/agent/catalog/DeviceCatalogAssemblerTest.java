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
                  "parameters":[{"name":"target","displayName":"目标温度","dataType":"DOUBLE"}]}]
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
}
