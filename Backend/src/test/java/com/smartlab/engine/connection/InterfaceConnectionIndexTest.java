package com.smartlab.engine.connection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceConnectionIndexTest {

    @Test
    void replacesAndClearsTaskEdges() {
        InterfaceConnectionIndex index = new InterfaceConnectionIndex();
        LiveInterfaceConnection edge = new LiveInterfaceConnection(
                "NODE_TO_NODE", 9L, 1L, 1L, 2L, "out", "in", null, null, 11L, 32L);

        index.replaceTaskEdges(9L, List.of(edge));
        assertThat(index.edges(9L)).containsExactly(edge);

        index.replaceTaskEdges(9L, List.of());
        assertThat(index.edges(9L)).isEmpty();
    }

    @Test
    void occupancyFollowsLiveDeviceEdgeAndClosesAfterRelease() {
        InterfaceConnectionIndex index = new InterfaceConnectionIndex();
        LiveInterfaceConnection inbound = new LiveInterfaceConnection(
                "DEVICE_TO_NODE", 9L, 1L, null, 2L, "Interface_state_out", "state-in",
                21L, 99L, null, 12L);
        index.replaceTaskEdges(9L, List.of(inbound));
        index.occupy(99L, 9L, 12L, "m-1");

        assertThat(index.occupancy(99L)).isEqualTo(new InterfaceConnectionIndex.Occupancy(9L, 12L, "m-1"));
        assertThat(index.deviceToNode(99L)).containsExactly(inbound);

        index.releaseAndClose(99L, "m-1");
        assertThat(index.occupancy(99L)).isNull();
        assertThat(index.isClosed(99L, "m-1")).isTrue();

        index.replaceTaskEdges(9L, List.of());
        assertThat(index.isClosed(99L, "m-1")).isFalse();
    }
}
