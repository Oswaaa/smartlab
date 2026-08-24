package com.smartlab.engine.connection;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时活跃端口连接索引。不落库；任务结束或下游步骤结束后边从索引移除。
 * 模板图仍以 FLOW_MODELS.port_connection 为准。
 */
@Component
public class PortConnectionIndex {
    private final ConcurrentHashMap<Long, List<PortConnectionEdge>> edgesByTask = new ConcurrentHashMap<>();

    public void replaceTaskEdges(Long taskId, List<PortConnectionEdge> edges) {
        if (taskId == null) return;
        if (edges == null || edges.isEmpty()) {
            edgesByTask.remove(taskId);
            return;
        }
        edgesByTask.put(taskId, List.copyOf(edges));
    }

    public List<PortConnectionEdge> edges(Long taskId) {
        if (taskId == null) return List.of();
        return edgesByTask.getOrDefault(taskId, List.of());
    }
}
