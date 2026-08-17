package com.smartlab.management.sse;

import com.smartlab.global.event.TaskExecutionLogEvent;
import com.smartlab.global.event.TaskLifecycleObservationEvent;
import com.smartlab.global.event.WorkflowNodeObservationEvent;
import com.smartlab.management.entity.workflow.ExecutionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务执行监控实时事件流 SSE 集中调度管理器。
 * 订阅工作流节点跃迁、日志生成及生命周期事件，直通推送到前端任务监控抽屉。
 */
@Component
public class TaskExecutionSseHub {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionSseHub.class);

    private final Map<Long, List<SseEmitter>> emittersByTaskId = new ConcurrentHashMap<>();

    /**
     * 为指定任务实例注册新的 SSE 终端连接（抽屉打开时建立）。
     */
    public SseEmitter register(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        // 30 分钟保活超时
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        List<SseEmitter> list = emittersByTaskId.computeIfAbsent(taskId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable cleanup = () -> {
            list.remove(emitter);
            if (list.isEmpty()) {
                emittersByTaskId.remove(taskId, list);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("taskId", taskId, "status", "CONNECTED", "timestamp", System.currentTimeMillis())));
        } catch (IOException e) {
            cleanup.run();
        }

        return emitter;
    }

    /**
     * 监听日志产生事件，向前端日志控制台实时推送。
     */
    @EventListener
    public void handleExecutionLog(TaskExecutionLogEvent event) {
        if (event == null || event.log() == null || event.log().getTaskId() == null) return;
        ExecutionLog execLog = event.log();
        Long taskId = execLog.getTaskId();
        List<SseEmitter> list = emittersByTaskId.get(taskId);
        if (list == null || list.isEmpty()) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", execLog.getId());
        data.put("taskId", execLog.getTaskId());
        data.put("taskStepId", execLog.getTaskStepId());
        data.put("deviceInstanceId", execLog.getDeviceInstanceId());
        data.put("sourceType", execLog.getSourceType());
        data.put("logLevel", execLog.getLogLevel());
        data.put("logInfo", execLog.getLogInfo());
        data.put("logTime", execLog.getLogTime() != null ? execLog.getLogTime().toString() : null);

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("log").data(data));
            } catch (Exception e) {
                list.remove(emitter);
            }
        }
    }

    /**
     * 监听工作流节点跃迁事件，向前端拓扑图实时推送。
     */
    @EventListener
    public void handleWorkflowNode(WorkflowNodeObservationEvent event) {
        if (event == null || event.taskId() == null) return;
        Long taskId = event.taskId();
        List<SseEmitter> list = emittersByTaskId.get(taskId);
        if (list == null || list.isEmpty()) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", event.taskId());
        data.put("workflowTemplateId", event.workflowTemplateId());
        data.put("nodeIdRef", event.nodeIdRef());
        data.put("taskStepId", event.taskStepId());
        data.put("nodeLifecycleState", event.nodeLifecycleState());
        data.put("variableSpace", event.variableSpace());
        data.put("occurredAt", event.occurredAt() != null ? event.occurredAt().toEpochMilli() : System.currentTimeMillis());

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("step").data(data));
            } catch (Exception e) {
                list.remove(emitter);
            }
        }
    }

    /**
     * 监听任务生命周期事件（启动、暂停、完成、失败）。
     */
    @EventListener
    public void handleTaskLifecycle(TaskLifecycleObservationEvent event) {
        if (event == null || event.taskId() == null) return;
        Long taskId = event.taskId();
        List<SseEmitter> list = emittersByTaskId.get(taskId);
        if (list == null || list.isEmpty()) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", event.taskId());
        data.put("taskStatus", event.taskLifecycleState());
        data.put("occurredAt", event.occurredAt() != null ? event.occurredAt().toEpochMilli() : System.currentTimeMillis());

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("task").data(data));
            } catch (Exception e) {
                list.remove(emitter);
            }
        }
    }
}
