package com.smartlab.engine.statemachine;

import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.observation.SnapshotOrigin;
import com.smartlab.engine.observation.device.DeviceTwinSnapshot;
import com.smartlab.global.event.DeviceInstanceRetiredEvent;
import com.smartlab.global.event.DeviceInstanceSavedEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntrinsicConstraintPlanRegistryTest {

    @Test
    void compilesPlanTracksRevisionAndFollowsInstanceLifecycle() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        DeviceInstances instance = instance();
        DeviceModels model = model();
        when(instances.selectById(7L)).thenReturn(instance);
        when(models.selectById(9L)).thenReturn(model);
        IntrinsicConstraintPlanRegistry registry = new IntrinsicConstraintPlanRegistry(instances, models);

        registry.handleInstanceSaved(new DeviceInstanceSavedEvent(7L, 9L, true));

        var plan = registry.require(7L);
        assertEquals("Exception", plan.exceptionRegion("OVER_TEMPERATURE"));
        assertEquals(1, plan.constraints().size());
        DeviceTwinSnapshot snapshot = new DeviceTwinSnapshot(7L, 9L,
                JsonNodeSupport.objectNode().put("temperature", 205.0), "ONLINE",
                Instant.now(), Instant.now(), 4, ObservationStatus.VALID, SnapshotOrigin.LIVE);
        assertTrue(registry.needsEvaluation(snapshot));
        registry.markEvaluated(7L, 4);
        assertFalse(registry.needsEvaluation(snapshot));

        registry.handleInstanceSaved(new DeviceInstanceSavedEvent(7L, 9L, false));
        assertTrue(registry.needsEvaluation(snapshot));
        registry.handleInstanceRetired(new DeviceInstanceRetiredEvent(7L));
        assertEquals(0, registry.size());
        assertThrows(IllegalStateException.class, () -> registry.require(7L));

        registry.handleInstanceSaved(new DeviceInstanceSavedEvent(7L, 9L, true));
        registry.handleInstanceDeleted(new com.smartlab.global.event.DeviceInstanceDeletedEvent(7L));
        assertEquals(0, registry.size());
    }

    private DeviceInstances instance() {
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        instance.setLifecycleStatus(DeviceInstanceLifecycle.IN_USE);
        return instance;
    }

    private DeviceModels model() {
        DeviceModels model = new DeviceModels();
        model.setId(9L);
        var constraints = JsonNodeSupport.arrayNode();
        constraints.addObject()
                .put("objectAttributeName", "temperature")
                .put("operator", ">")
                .put("boundaryValue", 200)
                .put("violationStateName", "OVER_TEMPERATURE");
        model.setIntrinsicConstraint(constraints);
        var exception = JsonNodeSupport.objectNode();
        var regions = exception.putArray("regions");
        var region = regions.addObject();
        region.put("regionName", "Exception");
        region.put("regionType", "EXCEPTION");
        region.put("initialStateName", "");
        region.putArray("states").addObject().put("stateName", "OVER_TEMPERATURE");
        model.setOpState(exception);
        return model;
    }
}
