package com.smartlab.engine.connection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortConnectionIndexTest {

    @Test
    void replacesAndClearsTaskEdges() {
        PortConnectionIndex index = new PortConnectionIndex();
        PortConnectionEdge edge = new PortConnectionEdge(9L, 1L, 1L, 2L, "out", "in", 32L, null, 0);

        index.replaceTaskEdges(9L, List.of(edge));
        assertThat(index.edges(9L)).containsExactly(edge);

        index.replaceTaskEdges(9L, List.of());
        assertThat(index.edges(9L)).isEmpty();
    }
}
