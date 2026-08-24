package com.smartlab.engine.connection;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时活跃接口连接索引。不落库；任务结束或步骤结束后边从索引移除。
 * 设备命令占用记在活边上对应的实例，不把 taskId 带进状态机。
 */
@Component
public class InterfaceConnectionIndex {
    private final ConcurrentHashMap<Long, List<LiveInterfaceConnection>> edgesByTask = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Occupancy> occupancyByInstance = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> closedCommands = new ConcurrentHashMap<>();

    public List<LiveInterfaceConnection> edges(Long taskId) {
        if (taskId == null) return List.of();
        return edgesByTask.getOrDefault(taskId, List.of());
    }

    public synchronized void replaceTaskEdges(Long taskId, List<LiveInterfaceConnection> edges) {
        if (taskId == null) return;
        if (edges == null || edges.isEmpty()) {
            edgesByTask.remove(taskId);
        } else {
            edgesByTask.put(taskId, List.copyOf(edges));
        }
        pruneOccupancyAndClosed();
    }

    public synchronized void upsert(LiveInterfaceConnection edge) {
        if (edge == null || edge.taskId() == null) return;
        List<LiveInterfaceConnection> current = new ArrayList<>(edges(edge.taskId()));
        current.removeIf(existing -> sameChannel(existing, edge));
        current.add(edge);
        edgesByTask.put(edge.taskId(), List.copyOf(current));
    }

    public LiveInterfaceConnection nodeToDevice(long taskId, long stepId) {
        for (LiveInterfaceConnection edge : edges(taskId)) {
            if ("NODE_TO_DEVICE".equals(edge.connectionType()) && Objects.equals(edge.sourceStepId(), stepId)) {
                return edge;
            }
        }
        return null;
    }

    public LiveInterfaceConnection deviceToNode(long taskId, long stepId) {
        for (LiveInterfaceConnection edge : edges(taskId)) {
            if ("DEVICE_TO_NODE".equals(edge.connectionType()) && Objects.equals(edge.targetStepId(), stepId)) {
                return edge;
            }
        }
        return null;
    }

    public List<LiveInterfaceConnection> deviceToNode(long instanceId) {
        List<LiveInterfaceConnection> result = new ArrayList<>();
        for (List<LiveInterfaceConnection> edges : edgesByTask.values()) {
            for (LiveInterfaceConnection edge : edges) {
                if ("DEVICE_TO_NODE".equals(edge.connectionType())
                        && Objects.equals(edge.deviceInstanceId(), instanceId)) {
                    result.add(edge);
                }
            }
        }
        return List.copyOf(result);
    }

    public void occupy(long instanceId, long taskId, long stepId, String messageId) {
        if (messageId == null || messageId.isBlank()) return;
        occupancyByInstance.put(instanceId, new Occupancy(taskId, stepId, messageId));
        closedCommands.remove(closedKey(instanceId, messageId));
    }

    public Occupancy occupancy(long instanceId) {
        return occupancyByInstance.get(instanceId);
    }

    public boolean isClosed(long instanceId, String messageId) {
        if (messageId == null || messageId.isBlank()) return false;
        return closedCommands.containsKey(closedKey(instanceId, messageId));
    }

    public void releaseIfRejected(long instanceId, String messageId) {
        Occupancy occupancy = occupancyByInstance.get(instanceId);
        if (occupancy != null && occupancy.messageId().equals(messageId)) {
            occupancyByInstance.remove(instanceId);
        }
    }

    public void releaseAndClose(long instanceId, String messageId) {
        Occupancy occupancy = occupancyByInstance.get(instanceId);
        if (occupancy != null && occupancy.messageId().equals(messageId)) {
            occupancyByInstance.remove(instanceId);
        }
        if (messageId != null && !messageId.isBlank()) {
            closedCommands.put(closedKey(instanceId, messageId), Boolean.TRUE);
        }
    }

    private void pruneOccupancyAndClosed() {
        occupancyByInstance.entrySet().removeIf(entry ->
                deviceToNode(entry.getValue().taskId(), entry.getValue().stepId()) == null);
        closedCommands.keySet().removeIf(key -> {
            int split = key.indexOf("::");
            if (split <= 0) return true;
            try {
                long instanceId = Long.parseLong(key.substring(0, split));
                return deviceToNode(instanceId).isEmpty();
            } catch (NumberFormatException ignored) {
                return true;
            }
        });
    }

    private static boolean sameChannel(LiveInterfaceConnection left, LiveInterfaceConnection right) {
        return left.connectionType().equals(right.connectionType())
                && Objects.equals(left.taskId(), right.taskId())
                && Objects.equals(left.sourceStepId(), right.sourceStepId())
                && Objects.equals(left.targetStepId(), right.targetStepId())
                && Objects.equals(left.sourceInterfaceName(), right.sourceInterfaceName())
                && Objects.equals(left.targetInterfaceName(), right.targetInterfaceName())
                && Objects.equals(left.deviceInstanceId(), right.deviceInstanceId());
    }

    private static String closedKey(long instanceId, String messageId) {
        return instanceId + "::" + messageId;
    }

    public record Occupancy(long taskId, long stepId, String messageId) {
    }
}
