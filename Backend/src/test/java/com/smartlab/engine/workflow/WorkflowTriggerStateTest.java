package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTriggerStateTest {

    @Test
    void fingerprintIsStableIdentityKeyForEngine() throws Exception {
        JsonNode trigger = JsonNodeSupport.MAPPER.readTree("""
                {"condition":{"object":"nodeLifecycleState","operator":"=","threshold":"RUNNING"},\
                "action":{"actionName":"EMIT","payload":{"signalName":"WF_EXECUTE_START"}}}
                """);
        assertThat(WorkflowTriggerState.fingerprint(trigger))
                .isEqualTo("2dee86e05e960f4b71688b6f280e8519e636826dc675b5a9efb95ecbe36f0f07");
        assertThat(WorkflowTriggerState.key("Interface_workflow_out", WorkflowTriggerState.fingerprint(trigger), 0))
                .isEqualTo("Interface_workflow_out::2dee86e05e960f4b71688b6f280e8519e636826dc675b5a9efb95ecbe36f0f07::0");
    }

    @Test
    void indexKeyAlignsWithBindingTriggerOrder() {
        assertThat(WorkflowTriggerState.indexKey("Interface_workflow_out", 2))
                .isEqualTo("Interface_workflow_out::__i::2");
        ObjectNode states = JsonNodeSupport.objectNode()
                .put(WorkflowTriggerState.indexKey("in", 0), false)
                .put(WorkflowTriggerState.indexKey("in", 3), true);
        assertThat(WorkflowTriggerState.pruneIndexKeys(states, "in", 1)).isTrue();
        assertThat(states.has(WorkflowTriggerState.indexKey("in", 0))).isTrue();
        assertThat(states.has(WorkflowTriggerState.indexKey("in", 3))).isFalse();
    }

    @Test
    void rememberIndexFiredStaysTrueAfterConditionFalls() {
        ObjectNode states = JsonNodeSupport.objectNode();
        assertThat(WorkflowTriggerState.rememberIndexFired(states, "out", 1, false)).isFalse();
        assertThat(states.path(WorkflowTriggerState.indexKey("out", 1)).asBoolean()).isFalse();
        assertThat(WorkflowTriggerState.rememberIndexFired(states, "out", 1, true)).isTrue();
        assertThat(states.path(WorkflowTriggerState.indexKey("out", 1)).asBoolean()).isTrue();
        assertThat(WorkflowTriggerState.rememberIndexFired(states, "out", 1, false)).isFalse();
        assertThat(states.path(WorkflowTriggerState.indexKey("out", 1)).asBoolean()).isTrue();
    }
}
