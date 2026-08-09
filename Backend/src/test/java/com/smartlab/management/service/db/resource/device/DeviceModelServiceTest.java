package com.smartlab.management.service.db.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.resource.device.DeviceModelSaveDTO;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.db.resource.data.DataTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeviceModelServiceTest {

        private AdapterManifestService adapterManifestService;
        private DeviceModelsMapper modelMapper;
        private DeviceInstancesMapper deviceInstancesMapper;
        private DeviceModelService service;
        private AdapterIndexService adapterIndexService;
        private DataTemplateService dataTemplateService;

        @BeforeEach
        void setUp() {
                modelMapper = mock(DeviceModelsMapper.class);
                deviceInstancesMapper = mock(DeviceInstancesMapper.class);
                adapterIndexService = mock(AdapterIndexService.class);
                ProtocolDictionaryService protocol = new ProtocolDictionaryService();
                adapterManifestService = mock(AdapterManifestService.class);
                dataTemplateService = mock(DataTemplateService.class);
                service = new DeviceModelService(
                                modelMapper,
                                deviceInstancesMapper,
                                mock(DeviceCategoryService.class),
                                adapterManifestService,
                                protocol,
                                dataTemplateService,
                                adapterIndexService);
                when(adapterManifestService.normalizeDataType(any()))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(modelMapper.insert(any(DeviceModels.class))).thenAnswer(invocation -> {
                        DeviceModels model = invocation.getArgument(0);
                        if (model.getId() == null) model.setId(7L);
                        return 1;
                });
        }

        @Test
        void sealsGenericAndConstraintMutationBypasses() {
                assertThrows(UnsupportedOperationException.class, () -> service.save(completeModel()));


                verifyNoInteractions(modelMapper, deviceInstancesMapper);
        }

        @Test
        void genericDeleteSignatureOverridesTheBaseMethodAndKeepsReferenceFreeze() throws Exception {
                assertEquals(DeviceModelService.class,
                                DeviceModelService.class.getMethod("delete", Serializable.class).getDeclaringClass());
                DeviceModels lockedModel = completeModel();
                when(modelMapper.selectByIdForUpdate(7L)).thenReturn(lockedModel);
                when(deviceInstancesMapper.selectCount(any())).thenReturn(1L);

                assertThrows(IllegalStateException.class, () -> service.delete((Serializable) "7"));

                InOrder order = inOrder(modelMapper, deviceInstancesMapper);
                order.verify(modelMapper).selectByIdForUpdate(7L);
                order.verify(deviceInstancesMapper).selectCount(any());
                verify(modelMapper, never()).deleteById(any(Serializable.class));
        }

        @Test
        void deleteRemovesTemplatesBeforeDeletingUninstantiatedModel() {
                DeviceModels lockedModel = completeModel();
                when(modelMapper.selectByIdForUpdate(7L)).thenReturn(lockedModel);
                when(deviceInstancesMapper.selectCount(any())).thenReturn(0L);

                service.delete((Serializable) "7");

                InOrder order = inOrder(modelMapper, deviceInstancesMapper, dataTemplateService);
                order.verify(modelMapper).selectByIdForUpdate(7L);
                order.verify(deviceInstancesMapper).selectCount(any());
                order.verify(dataTemplateService).deleteByModelIdForModelRemoval(7L);
                order.verify(modelMapper).deleteById(7L);
        }
        @Test
        void updateLocksModelRowBeforeReferenceCheckAndWrite() {
                DeviceModelSaveDTO payload = completePayload();
                payload.setModelId(7L);
                DeviceModels lockedModel = completeModel();
                when(modelMapper.selectByIdForUpdate(7L)).thenReturn(lockedModel);
                when(deviceInstancesMapper.selectCount(any())).thenReturn(0L);

                service.savePayload(payload);

                InOrder order = inOrder(modelMapper, deviceInstancesMapper);
                order.verify(modelMapper).selectByIdForUpdate(7L);
                order.verify(deviceInstancesMapper).selectCount(any());
                order.verify(modelMapper).updateById(any(DeviceModels.class));
        }

        @Test
        void rejectsMalformedRequiredJsonShapes() {
                List<ShapeCase> cases = List.of(
                                new ShapeCase("attributes",
                                                payload -> payload.setAttributes(JsonNodeSupport.objectNode())),
                                new ShapeCase("capabilities",
                                                payload -> payload.setCapabilities(JsonNodeSupport.objectNode())),
                                new ShapeCase("ports", payload -> payload.setPorts(JsonNodeSupport.objectNode())),
                                new ShapeCase("intrinsicConstraints",
                                                payload -> payload
                                                                .setIntrinsicConstraints(JsonNodeSupport.objectNode())),
                                new ShapeCase("capabilities[].parameters",
                                                payload -> ((ObjectNode) payload.getCapabilities().get(0))
                                                                .set("parameters", JsonNodeSupport.objectNode())),
                                new ShapeCase("capabilities[].parameterMapping",
                                                payload -> ((ObjectNode) payload.getCapabilities().get(0))
                                                                .remove("parameterMapping")),
                                new ShapeCase("adapterContract.commands",
                                                payload -> ((ObjectNode) payload.getAdapterContract())
                                                                .set("commands", JsonNodeSupport.objectNode())),
                                new ShapeCase("adapterContract.commands[].commandParameters",
                                                payload -> ((ObjectNode) payload.getAdapterContract().path("commands")
                                                                .get(0))
                                                                .set("commandParameters",
                                                                                JsonNodeSupport.objectNode())),
                                new ShapeCase("adapterContract.telemetry",
                                                payload -> ((ObjectNode) payload.getAdapterContract())
                                                                .set("telemetry", JsonNodeSupport.arrayNode())),
                                new ShapeCase("adapterContract.telemetry.adapterAttributes",
                                                payload -> ((ObjectNode) payload.getAdapterContract().path("telemetry"))
                                                                .set("adapterAttributes",
                                                                                JsonNodeSupport.objectNode())),
                                new ShapeCase("adapterContract.telemetry.attributesMapping",
                                                payload -> ((ObjectNode) payload.getAdapterContract().path("telemetry"))
                                                                .remove("attributesMapping")),
                                new ShapeCase("adapterContract.events",
                                                payload -> ((ObjectNode) payload.getAdapterContract())
                                                                .set("events", JsonNodeSupport.arrayNode())),
                                new ShapeCase("adapterContract.events.cmdEvents",
                                                payload -> ((ObjectNode) payload.getAdapterContract().path("events"))
                                                                .set("cmdEvents", JsonNodeSupport.objectNode())),
                                new ShapeCase("adapterContract.events.opEvents",
                                                payload -> ((ObjectNode) payload.getAdapterContract().path("events"))
                                                                .remove("opEvents")));

                assertAll(cases.stream().map(shapeCase -> () -> {
                        DeviceModelSaveDTO payload = completePayload();
                        shapeCase.mutation().accept(payload);
                        IllegalArgumentException error = assertThrows(
                                        IllegalArgumentException.class, () -> service.previewModel(payload));
                        assertTrue(error.getMessage().contains(shapeCase.expectedPath()),
                                        () -> shapeCase.expectedPath() + " should identify the malformed shape, got: "
                                                        + error.getMessage());
                }));
        }

        @Test
        void saveRejectsPortWithoutRequiredFieldsBeforeInsert() {
                DeviceModelSaveDTO payload = completePayload();
                ((ArrayNode) payload.getPorts()).addObject();

                assertThrows(IllegalArgumentException.class, () -> service.savePayload(payload));
                verify(modelMapper, never()).insert(any(DeviceModels.class));
        }

        @Test
        void saveRejectsIntrinsicConstraintWithoutRequiredFieldsBeforeInsert() {
                DeviceModelSaveDTO payload = completePayload();
                ((ArrayNode) payload.getIntrinsicConstraints()).addObject();

                assertThrows(IllegalArgumentException.class, () -> service.savePayload(payload));
                verify(modelMapper, never()).insert(any(DeviceModels.class));
        }

        @Test
        void saveRejectsParameterMappingWithoutIsFixedValueBeforeInsert() {
                DeviceModelSaveDTO payload = completePayload();
                ((ObjectNode) payload.getCapabilities().get(0).path("parameterMapping").get(0))
                                .remove("isFixedValue");

                assertThrows(IllegalArgumentException.class, () -> service.savePayload(payload));
                verify(modelMapper, never()).insert(any(DeviceModels.class));
        }

        @Test
        void rejectsCollectionElementFieldsWithTypesOutsideFrozenCapabilitySchema() {
                List<ShapeCase> cases = List.of(
                                new ShapeCase("portName", payload -> ((ArrayNode) payload.getPorts()).addObject()
                                                .put("portName", 1)
                                                .put("direction", "IN")
                                                .put("bindingAttrName", "temperature")),
                                new ShapeCase("direction", payload -> ((ArrayNode) payload.getPorts()).addObject()
                                                .put("portName", "input")
                                                .put("direction", "SIDEWAYS")
                                                .put("bindingAttrName", "temperature")),
                                new ShapeCase("bindingAttrName", payload -> ((ArrayNode) payload.getPorts()).addObject()
                                                .put("portName", "input")
                                                .put("direction", "IN")
                                                .put("bindingAttrName", true)),
                                new ShapeCase("objectAttributeName",
                                                payload -> addIntrinsicConstraint(payload).put("objectAttributeName",
                                                                1)),
                                new ShapeCase("operator",
                                                payload -> addIntrinsicConstraint(payload).put("operator", "BETWEEN")),
                                new ShapeCase("boundaryValue",
                                                payload -> addIntrinsicConstraint(payload).put("boundaryValue", "100")),
                                new ShapeCase("violationStateName",
                                                payload -> addIntrinsicConstraint(payload).put("violationStateName",
                                                                1)),
                                new ShapeCase("commandParamName",
                                                payload -> ((ObjectNode) payload.getCapabilities().get(0)
                                                                .path("parameterMapping").get(0))
                                                                .put("commandParamName", 1)),
                                new ShapeCase("isFixedValue", payload -> ((ObjectNode) payload.getCapabilities().get(0)
                                                .path("parameterMapping").get(0))
                                                .put("isFixedValue", "false")),
                                new ShapeCase("capabilityParamName",
                                                payload -> ((ObjectNode) payload.getCapabilities().get(0)
                                                                .path("parameterMapping").get(0))
                                                                .put("capabilityParamName", 1)));

                assertAll(cases.stream().map(shapeCase -> () -> {
                        DeviceModelSaveDTO payload = completePayload();
                        shapeCase.mutation().accept(payload);
                        IllegalArgumentException error = assertThrows(
                                        IllegalArgumentException.class, () -> service.previewModel(payload));
                        assertTrue(error.getMessage().contains(shapeCase.expectedPath()),
                                        () -> shapeCase.expectedPath() + " should identify the malformed field, got: "
                                                        + error.getMessage());
                }));
        }

        @Test
        void previewUsesCategoryAsTheOnlyAdapterContractSelector() {
                DeviceModelSaveDTO payload = minimalPayload();
                ObjectNode config = (ObjectNode) payload.getAdapterContract().path("config");
                config.put("categoryName", "Reactor");
                config.put("adapterName", "adapter-1");
                when(adapterIndexService.buildAdapterContract("adapter-1", "Reactor"))
                                .thenReturn(registeredContract("adapter-1", "Reactor", "heat"));
                config.put("templateName", "ReactorTemplate");

                ObjectNode bundle = service.previewModel(payload);
                JsonNode previewConfig = bundle.path("capabilityModel").path("adapterContract").path("config");

                assertEquals("Reactor", previewConfig.path("categoryName").asText());
                assertFalse(previewConfig.has("templateName"));
        }

        @Test
        void saveUsesTheSameCanonicalAdapterContractAsPreview() {
                DeviceModelSaveDTO payload = minimalPayload();
                payload.setCategoryId(7L);
                ObjectNode defaultTemplate = JsonNodeSupport.objectNode();
                defaultTemplate.putArray("details").addObject()
                                .put("columnName", "constant_value")
                                .put("propertyTypeId", 4L)
                                .put("defaultValue", "0");
                payload.setDefaultDataTemplate(defaultTemplate);
                ObjectNode config = (ObjectNode) payload.getAdapterContract().path("config");
                config.put("categoryName", "Reactor");
                config.put("adapterName", "adapter-1");
                when(adapterIndexService.buildAdapterContract("adapter-1", "Reactor"))
                                .thenReturn(registeredContract("adapter-1", "Reactor", "heat"));
                config.put("templateName", "ReactorTemplate");

                service.savePayload(payload);

                ArgumentCaptor<DeviceModels> captor = ArgumentCaptor.forClass(DeviceModels.class);
                verify(modelMapper).insert(captor.capture());
                JsonNode savedConfig = captor.getValue().getAdapterContract().path("config");
                assertEquals("Reactor", savedConfig.path("categoryName").asText());
                assertFalse(savedConfig.has("templateName"));
        }

        @Test
        void previewKeepsUserConfiguredTelemetryMappingsWhenCanonicalizingAdapterContract() {
                DeviceModelSaveDTO payload = completePayload();
                ObjectNode canonical = registeredContract("adapter-1", "Reactor", "start");
                ((ObjectNode) canonical.path("commands").get(0)).withArray("commandParameters").addObject()
                                .put("paramName", "duration")
                                .put("dataType", "INTEGER");
                ((ArrayNode) canonical.path("telemetry").path("adapterAttributes")).addObject()
                                .put("telemetryName", "temperature")
                                .put("dataType", "DOUBLE");
                when(adapterIndexService.buildAdapterContract("adapter-1", "Reactor")).thenReturn(canonical);

                ObjectNode preview = service.previewModel(payload);
                JsonNode mapping = preview.path("capabilityModel").path("adapterContract")
                                .path("telemetry").path("attributesMapping").get(0);

                assertEquals("temperature", mapping.path("adapterAttrName").asText());
                assertEquals("temperature", mapping.path("modelAttributeName").asText());
        }

        @Test
        void saveRejectsCapabilityWithoutRequiredShapeBeforeInsert() {
                DeviceModelSaveDTO payload = completePayload();
                ((ArrayNode) payload.getCapabilities()).addObject()
                                .put("capabilityName", "broken");

                assertThrows(IllegalArgumentException.class, () -> service.savePayload(payload));
                verify(modelMapper, never()).insert(any(DeviceModels.class));
        }
        @Test
        void acceptsAbortCapabilityRelationAndScope() {
                DeviceModelSaveDTO payload = completePayload();
                addAbortCapability(payload, "start");

                ObjectNode bundle = service.previewModel(payload);

                JsonNode capabilities = bundle.path("capabilityModel").path("capabilities");
                assertEquals("stop", capabilities.get(0).path("abortCapabilityName").asText());
                assertEquals("start", capabilities.get(1).path("scope").get(0).asText());
        }

        @Test
        void rejectsAbortScopeReferencingUnknownCapability() {
                DeviceModelSaveDTO payload = completePayload();
                addAbortCapability(payload, "missing");

                assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
        }


        @Test
        void saveRejectsStateTransitionReferencingUnknownAdapterEvent() {
                DeviceModelSaveDTO payload = completePayload();
                payload.setStateTransitions(arrayWithTransition(
                                "CMD", "SENT", "RUNNING", "UNKNOWN_EVENT"));

                assertThrows(IllegalArgumentException.class, () -> service.savePayload(payload));
                verify(modelMapper, never()).insert(any(DeviceModels.class));
        }

        @Test
        void allowsModelWithoutOptionalExplicitAbortTerminalEvent() {
                DeviceModelSaveDTO payload = completePayload();
                ArrayNode transitions = (ArrayNode) payload.getStateTransitions();
                for (int index = transitions.size() - 1; index >= 0; index--) {
                        JsonNode transition = transitions.get(index);
                        if ("CMD".equals(transition.path("stateSpace").asText())
                                        && "ABORTING".equals(transition.path("fromStateName").asText())
                                        && "ABORTED".equals(transition.path("toStateName").asText())) {
                                transitions.remove(index);
                        }
                }

                assertDoesNotThrow(() -> service.previewModel(payload));
        }

        @Test
        void runtimeReadBackfillsMissingTerminationDefaultsWithoutPersisting() {
                DeviceModels model = completeModel();
                ObjectNode capability = (ObjectNode) model.getCapabilities().get(0);
                capability.remove("isAbort");
                capability.remove("abortCapabilityName");
                when(modelMapper.selectById(7L)).thenReturn(model);

                DeviceModels normalized = service.requireRuntimeReady(7L);

                assertFalse(normalized.getCapabilities().get(0).path("isAbort").asBoolean());
                assertTrue(normalized.getCapabilities().get(0).path("abortCapabilityName").isNull());
                verify(modelMapper, never()).updateById(any(DeviceModels.class));
        }

        @Test
        void runtimeReadInfersSingleLegacyAbortCapabilityWithoutChangingDatabase() {
                DeviceModels model = completeModel();
                ObjectNode legacyAbort = ((ArrayNode) model.getCapabilities()).addObject();
                legacyAbort.put("capabilityName", "stop");
                legacyAbort.put("adapterCommandName", "start");
                legacyAbort.put("displayName", "Stop");
                legacyAbort.put("isAbort", true);
                legacyAbort.putArray("parameters");
                legacyAbort.putArray("parameterMapping").addObject()
                                .put("commandParamName", "duration")
                                .put("isFixedValue", true)
                                .put("fixedValue", 0);
                when(modelMapper.selectById(7L)).thenReturn(model);

                DeviceModels normalized = service.requireRuntimeReady(7L);

                assertEquals("stop", normalized.getCapabilities().get(0).path("abortCapabilityName").asText());
                assertEquals("start", normalized.getCapabilities().get(1).path("scope").get(0).asText());
                verify(modelMapper, never()).updateById(any(DeviceModels.class));
        }

        @Test
        void runtimeReadyReturnsOnlyFullyValidatedModel() {
                DeviceModels model = completeModel();
                when(modelMapper.selectById(7L)).thenReturn(model);

                assertSame(model, service.requireRuntimeReady(7L));
        }

        @Test
        void previewRejectsCapabilityModelThatViolatesItsSchema() {
                DeviceModelSaveDTO payload = minimalPayload();
                ((ArrayNode) payload.getAttributes()).addObject()
                                .put("attributeName", "temperature")
                                .put("valueKind", "CONTINUOUS")
                                .put("dataType", "UNSUPPORTED");

                assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
        }

        @Test
        void previewBuildsUniqueCurrentStateMachineContract() {
                DeviceModelSaveDTO payload = minimalPayload();
                ObjectNode config = (ObjectNode) payload.getAdapterContract().path("config");
                config.put("adapterName", "adapter-1");
                config.put("categoryName", "Reactor");
                when(adapterIndexService.buildAdapterContract("adapter-1", "Reactor"))
                                .thenReturn(registeredContract("adapter-1", "Reactor", "heat"));
                ObjectNode bundle = service.previewModel(payload);
                JsonNode stateMachine = bundle.path("stateMachineModel");
                JsonNode interfaces = stateMachine.path("interfaces");

                assertEquals(6, interfaces.size());
                Set<String> names = new HashSet<>();
                interfaces.forEach(item -> names.add(item.path("name").asText()));
                assertEquals(6, names.size());
                assertEquals(Set.of("WF_EXECUTE_START", "WF_EXECUTE_ABORT"),
                                interfaceSignals(interfaces, "Interface_workflow_in"));
                assertEquals(Set.of("SENT_EVENT", "RUNNING_EVENT", "COMPLETED_EVENT", "FAILED_EVENT", "ABORTED_EVENT"),
                                interfaceSignals(interfaces, "Interface_adapter_in"));
                assertEquals(Set.of("CMD_STATE", "OP_STATE"), interfaceSignals(interfaces, "Interface_state_out"));
                assertTrue(hasTransition(stateMachine.path("transitions"), "SENT", "RUNNING", "SENT_EVENT"));
                assertTrue(hasTransition(stateMachine.path("transitions"), "RUNNING", "COMPLETED", "COMPLETED_EVENT"));
                assertTrue(hasTransition(stateMachine.path("transitions"), "RUNNING", "FAILED", "FAILED_EVENT"));
                assertTrue(hasTransition(stateMachine.path("transitions"), "ABORTING", "ABORTED", "ABORTED_EVENT"));
                assertEquals(Set.of("IDLE", "SENT", "RUNNING", "COMPLETED", "FAILED", "ABORTING", "ABORTED"),
                                stateNames(stateMachine.path("cmdLifecycleSpace").path("states")));

        }

        @Test
        void rejectsMalformedCustomTransitionInsteadOfSilentlyDroppingIt() {
                DeviceModelSaveDTO payload = minimalPayload();
                ObjectNode transition = ((ArrayNode) payload.getStateTransitions()).addObject();
                transition.put("stateSpace", "OP");
                transition.put("fromStateName", "IDLE");
                transition.put("toStateName", "IDLE");

                assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
        }

        @Test
        void rejectsCommandTransitionTriggeredByOperationEvent() {
                DeviceModelSaveDTO payload = minimalPayload();
                ObjectNode events = (ObjectNode) payload.getAdapterContract().path("events");
                events.withArray("cmdEvents").addObject().put("eventName", "DONE");
                events.withArray("opEvents").addObject().put("eventName", "HEAT_STARTED");
                addTransition(payload, "CMD", "RUNNING", "COMPLETED", "HEAT_STARTED");

                assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
        }

        @Test
        void rejectsTransitionWhoseStatesDoNotBelongToDeclaredStateSpace() {
                DeviceModelSaveDTO payload = minimalPayload();
                ObjectNode events = (ObjectNode) payload.getAdapterContract().path("events");
                events.withArray("opEvents").addObject().put("eventName", "HEAT_STARTED");
                addTransition(payload, "OP", "IDLE", "MISSING", "HEAT_STARTED");

                assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
        }

        @Test
        void rejectsAutomaticExecutionLifecycleTransition() {
                DeviceModelSaveDTO payload = minimalPayload();
                ObjectNode transition = ((ArrayNode) payload.getStateTransitions()).addObject();
                transition.put("stateSpace", "CMD");
                transition.put("fromStateName", "SENT");
                transition.put("toStateName", "RECEIVED");
                transition.putNull("trigger");
                transition.putArray("actions");

                assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
        }

        @Test
        void capabilityModelRejectsAdapterInternalParameters() {
                DeviceModelSaveDTO payload = minimalPayload();
                ObjectNode command = ((ArrayNode) payload.getAdapterContract().path("commands")).addObject();
                command.put("commandName", "heat");
                ObjectNode parameter = command.putArray("commandParameters").addObject();
                parameter.put("paramName", "index");
                parameter.put("dataType", "INTEGER");
                parameter.put("internal", true);

                assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
        }

        @Test
        void submittedAdapterCommandsCannotOverrideRegisteredContract() {
                DeviceModelSaveDTO payload = minimalPayload();
                ObjectNode config = (ObjectNode) payload.getAdapterContract().path("config");
                config.put("adapterName", "adapter-1");
                config.put("categoryName", "Reactor");
                ((ArrayNode) payload.getAdapterContract().path("commands")).addObject()
                                .put("commandName", "injected-command")
                                .putArray("commandParameters");
                when(adapterIndexService.buildAdapterContract("adapter-1", "Reactor"))
                                .thenReturn(registeredContract("adapter-1", "Reactor", "heat"));

                ObjectNode preview = service.previewModel(payload);

                JsonNode commands = preview.path("capabilityModel").path("adapterContract").path("commands");
                assertEquals(1, commands.size());
                assertEquals("heat", commands.get(0).path("commandName").asText());
        }

        private ObjectNode addAbortCapability(DeviceModelSaveDTO payload, String scopeName) {
                ObjectNode normal = (ObjectNode) payload.getCapabilities().get(0);
                normal.put("abortCapabilityName", "stop");
                ObjectNode abort = ((ArrayNode) payload.getCapabilities()).addObject();
                abort.put("capabilityName", "stop");
                abort.put("adapterCommandName", "start");
                abort.put("displayName", "Stop");
                abort.put("isAbort", true);
                abort.putNull("abortCapabilityName");
                abort.putArray("scope").add(scopeName);
                abort.putArray("parameters");
                abort.putArray("parameterMapping").addObject()
                                .put("commandParamName", "duration")
                                .put("isFixedValue", true)
                                .put("fixedValue", 0);
                return abort;
        }

        private void addTransition(DeviceModelSaveDTO payload, String stateSpace, String from, String to,
                        String signal) {
                ObjectNode transition = ((ArrayNode) payload.getStateTransitions()).addObject();
                transition.put("stateSpace", stateSpace);
                transition.put("fromStateName", from);
                transition.put("toStateName", to);
                transition.putObject("trigger")
                                .put("interfaceName", "Interface_adapter_in")
                                .put("signalName", signal);
                transition.putArray("actions");
        }

        private Set<String> interfaceSignals(JsonNode interfaces, String interfaceName) {
                Set<String> result = new HashSet<>();
                interfaces.forEach(item -> {
                        if (interfaceName.equals(item.path("name").asText())) {
                                item.path("allowedSignals").forEach(signal -> result.add(signal.asText()));
                        }
                });
                return result;
        }

        private boolean hasTransition(JsonNode transitions, String from, String to, String signal) {
                for (JsonNode transition : transitions) {
                        if (from.equals(transition.path("fromStateName").asText())
                                        && to.equals(transition.path("toStateName").asText())
                                        && signal.equals(transition.path("trigger").path("signalName").asText())) {
                                return true;
                        }
                }
                return false;
        }

        private ObjectNode registeredContract(String adapterName, String categoryName, String commandName) {
                ObjectNode contract = JsonNodeSupport.objectNode();
                contract.putObject("config")
                                .put("protocol", "MQTT")
                                .put("adapterName", adapterName)
                                .put("categoryName", categoryName);
                contract.putArray("commands").addObject()
                                .put("commandName", commandName)
                                .putArray("commandParameters");
                ObjectNode telemetry = contract.putObject("telemetry");
                telemetry.putArray("adapterAttributes");
                telemetry.putArray("attributesMapping");
                ObjectNode events = contract.putObject("events");
                addEvent(events.putArray("cmdEvents"), "SENT_EVENT");
                addEvent(events.withArray("cmdEvents"), "RUNNING_EVENT");
                addEvent(events.withArray("cmdEvents"), "COMPLETED_EVENT");
                addEvent(events.withArray("cmdEvents"), "FAILED_EVENT");
                addEvent(events.withArray("cmdEvents"), "ABORTED_EVENT");
                events.putArray("opEvents");
                return contract;
        }

        private DeviceModelSaveDTO completePayload() {
                DeviceModelSaveDTO payload = minimalPayload();
                ((ArrayNode) payload.getAttributes()).addObject()
                                .put("attributeName", "temperature")
                                .put("valueKind", "CONTINUOUS")
                                .put("dataType", "DOUBLE");
                ObjectNode capability = ((ArrayNode) payload.getCapabilities()).addObject();
                capability.put("capabilityName", "start")
                                .put("adapterCommandName", "start")
                                .put("displayName", "启动");
                capability.put("isAbort", false);
                capability.putNull("abortCapabilityName");
                capability.putArray("parameters").addObject()
                                .put("name", "duration")
                                .put("displayName", "时长")
                                .put("dataType", "INTEGER");
                capability.putArray("parameterMapping").addObject()
                                .put("commandParamName", "duration")
                                .put("capabilityParamName", "duration")
                                .put("isFixedValue", false);

                ObjectNode contract = (ObjectNode) payload.getAdapterContract();
                ObjectNode config = (ObjectNode) contract.path("config");
                config.put("adapterName", "adapter-1");
                config.put("categoryName", "Reactor");
                contract.putArray("commands").addObject()
                                .put("commandName", "start")
                                .putArray("commandParameters").addObject()
                                .put("paramName", "duration")
                                .put("dataType", "INTEGER");
                ObjectNode telemetry = (ObjectNode) contract.path("telemetry");
                telemetry.putArray("adapterAttributes").addObject()
                                .put("telemetryName", "temperature")
                                .put("dataType", "DOUBLE");
                telemetry.putArray("attributesMapping").addObject()
                                .put("adapterAttrName", "temperature")
                                .put("modelAttributeName", "temperature");
                ObjectNode events = (ObjectNode) contract.path("events");
                addEvent(events.withArray("cmdEvents"), "SENT_EVENT");
                addEvent(events.withArray("cmdEvents"), "RUNNING_EVENT");
                addEvent(events.withArray("cmdEvents"), "COMPLETED_EVENT");
                addEvent(events.withArray("cmdEvents"), "FAILED_EVENT");
                addEvent(events.withArray("cmdEvents"), "ABORTED_EVENT");
                payload.setStateMachineInterfaces(standardInterfaces());
                payload.setCmdState(completeCmdState());
                payload.setStateTransitions(completeCmdTransitions());
                ObjectNode defaultTemplate = JsonNodeSupport.objectNode();
                defaultTemplate.put("enabled", true);
                defaultTemplate.put("templateName", "model 默认数据模板");
                defaultTemplate.putArray("details").addObject()
                                .put("columnName", "temperature")
                                .put("columnDesc", "温度")
                                .put("propertyTypeId", 4L)
                                .put("columnLength", 255)
                                .put("deviceAttrKey", "temperature");
                payload.setDefaultDataTemplate(defaultTemplate);                when(adapterIndexService.buildAdapterContract("adapter-1", "Reactor")).thenReturn(contract);
                return payload;
        }

        private DeviceModels completeModel() {
                DeviceModelSaveDTO payload = completePayload();
                DeviceModels model = new DeviceModels();
                model.setModelName(payload.getModelName());
                model.setCategoryId(payload.getCategoryId());
                model.setAttributes(payload.getAttributes());
                model.setCapabilities(payload.getCapabilities());
                model.setAdapterContract(payload.getAdapterContract());
                model.setPorts(payload.getPorts());
                model.setIntrinsicConstraints(payload.getIntrinsicConstraints());
                model.setStateMachineInterfaces(payload.getStateMachineInterfaces());
                model.setCmdState(payload.getCmdState());
                model.setOpState(payload.getOpState());
                model.setStateTransitions(payload.getStateTransitions());
                return model;
        }

        private ArrayNode completeCmdTransitions() {
                ArrayNode transitions = JsonNodeSupport.arrayNode();
                addTransition(transitions, "CMD", "SENT", "RUNNING", "SENT_EVENT");
                addTransition(transitions, "CMD", "RUNNING", "RUNNING", "RUNNING_EVENT");
                addTransition(transitions, "CMD", "RUNNING", "COMPLETED", "COMPLETED_EVENT");
                addTransition(transitions, "CMD", "RUNNING", "FAILED", "FAILED_EVENT");
                addTransition(transitions, "CMD", "ABORTING", "ABORTED", "ABORTED_EVENT");
                return transitions;
        }

        private ArrayNode arrayWithTransition(String stateSpace, String from, String to, String signal) {
                ArrayNode transitions = JsonNodeSupport.arrayNode();
                addTransition(transitions, stateSpace, from, to, signal);
                return transitions;
        }

        private void addTransition(ArrayNode transitions, String stateSpace, String from, String to, String signal) {
                ObjectNode transition = transitions.addObject();
                transition.put("stateSpace", stateSpace);
                transition.put("fromStateName", from);
                transition.put("toStateName", to);
                transition.putObject("trigger")
                                .put("interfaceName", "Interface_adapter_in")
                                .put("signalName", signal);
                transition.putArray("actions");
        }

        private ObjectNode completeCmdState() {
                ObjectNode cmdState = JsonNodeSupport.objectNode();
                cmdState.put("initialStateName", "IDLE");
                for (String stateName : Set.of("IDLE", "SENT", "RUNNING", "COMPLETED", "FAILED", "ABORTING",
                                "ABORTED")) {
                        ObjectNode state = cmdState.withArray("states").addObject();
                        state.put("stateName", stateName);
                        state.putArray("onEntry").addObject()
                                        .put("actionName", "SEND")
                                        .putObject("payload")
                                        .put("interfaceName", "Interface_state_out")
                                        .put("signalName", "CMD_STATE");
                }
                return cmdState;
        }

        private ArrayNode standardInterfaces() {
                ArrayNode interfaces = JsonNodeSupport.arrayNode();
                addInterface(interfaces, "Interface_workflow_in", "IN", "WORKFLOW", "WF_EXECUTE_START",
                                "WF_EXECUTE_ABORT");
                addInterface(interfaces, "Interface_control_in", "IN", "CONTROL", "MANUAL_EXECUTE_START",
                                "MANUAL_EXECUTE_ABORT");
                addInterface(interfaces, "Interface_constraint_in", "IN", "CONSTRAINT", "CONSTRAINT_EXECUTE",
                                "CONSTRAINT_ABORT");
                addInterface(interfaces, "Interface_adapter_in", "IN", "ADAPTER", "SENT_EVENT", "RUNNING_EVENT",
                                "COMPLETED_EVENT", "FAILED_EVENT", "ABORTED_EVENT");
                addInterface(interfaces, "Interface_adapter_out", "OUT", "ADAPTER", "CMD_START", "CMD_ABORT");
                addInterface(interfaces, "Interface_state_out", "OUT", "STATE", "CMD_STATE", "OP_STATE");
                return interfaces;
        }

        private void addInterface(ArrayNode interfaces, String name, String direction, String type, String... signals) {
                ObjectNode item = interfaces.addObject();
                item.put("name", name);
                item.put("direction", direction);
                item.put("interfaceType", type);
                ArrayNode allowedSignals = item.putArray("allowedSignals");
                for (String signal : signals) {
                        allowedSignals.add(signal);
                }
        }

        private void addEvent(ArrayNode events, String eventName) {
                events.addObject()
                                .put("eventName", eventName)
                                .put("description", eventName);
        }

        private ObjectNode addIntrinsicConstraint(DeviceModelSaveDTO payload) {
                return ((ArrayNode) payload.getIntrinsicConstraints()).addObject()
                                .put("objectAttributeName", "temperature")
                                .put("operator", ">")
                                .put("boundaryValue", 100)
                                .put("violationStateName", "FAILED");
        }

        private DeviceModelSaveDTO minimalPayload() {
                DeviceModelSaveDTO payload = new DeviceModelSaveDTO();
                payload.setModelName("ReactorModel");
                payload.setCategoryId(7L);
                payload.setAttributes(JsonNodeSupport.arrayNode());
                payload.setCapabilities(JsonNodeSupport.arrayNode());
                ObjectNode contract = JsonNodeSupport.objectNode();
                contract.putObject("config")
                                .put("protocol", "MQTT")
                                .put("adapterName", "")
                                .put("categoryName", "");
                contract.putArray("commands");
                ObjectNode telemetry = contract.putObject("telemetry");
                telemetry.putArray("adapterAttributes");
                telemetry.putArray("attributesMapping");
                ObjectNode events = contract.putObject("events");
                addEvent(events.putArray("cmdEvents"), "SENT_EVENT");
                addEvent(events.withArray("cmdEvents"), "RUNNING_EVENT");
                addEvent(events.withArray("cmdEvents"), "COMPLETED_EVENT");
                addEvent(events.withArray("cmdEvents"), "FAILED_EVENT");
                addEvent(events.withArray("cmdEvents"), "ABORTED_EVENT");
                events.putArray("opEvents");
                payload.setAdapterContract(contract);
                payload.setPorts(JsonNodeSupport.arrayNode());
                payload.setIntrinsicConstraints(JsonNodeSupport.arrayNode());
                ObjectNode opState = JsonNodeSupport.objectNode();
                ArrayNode regions = opState.putArray("regions");
                ObjectNode region = regions.addObject();
                region.put("regionName", "operatingMode");
                region.put("regionType", "OPERATIONAL");
                region.put("initialStateName", "IDLE");
                ArrayNode states = region.putArray("states");
                ObjectNode state = states.addObject();
                state.put("stateName", "IDLE");
                state.putArray("onEntry").addObject()
                                .put("actionName", "SEND")
                                .putObject("payload")
                                .put("interfaceName", "Interface_state_out")
                                .put("signalName", "OP_STATE");
                payload.setOpState(opState);
                payload.setStateMachineInterfaces(standardInterfaces());
                payload.setCmdState(completeCmdState());
                payload.setStateTransitions(completeCmdTransitions());
                return payload;
        }

        private Set<String> stateNames(JsonNode states) {
                Set<String> names = new HashSet<>();
                states.forEach(state -> names.add(state.path("stateName").asText()));
                return names;
        }

        private record ShapeCase(String expectedPath, Consumer<DeviceModelSaveDTO> mutation) {
        }
}
