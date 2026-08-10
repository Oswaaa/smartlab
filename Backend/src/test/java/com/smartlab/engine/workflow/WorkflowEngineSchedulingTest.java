package com.smartlab.engine.workflow;

import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.engine.workflow.action.WorkflowActionRegistry;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowEngineSchedulingTest {
    private final WorkflowRuntimeService runtime = mock(WorkflowRuntimeService.class);
    private final WorkflowService workflows = mock(WorkflowService.class);
    private final FlowNodeService flowNodes = mock(FlowNodeService.class);
    private final WorkflowExecutionOperations operations = mock(WorkflowExecutionOperations.class);

    @Test
    void dispatchesDifferentTasksConcurrently() throws Exception {
        Task first = task(1L);
        Task second = task(2L);
        when(runtime.runningTasks()).thenReturn(List.of(first, second));
        when(runtime.task(1L)).thenReturn(first);
        when(runtime.task(2L)).thenReturn(second);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        WorkflowEngine engine = engine(workers, task -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            started.countDown();
            await(release);
            active.decrementAndGet();
        });

        try {
            engine.driveWorkflows();

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(maximum.get()).isEqualTo(2);
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void doesNotSubmitSameTaskAgainWhileItsPollIsInFlight() {
        Task task = task(1L);
        when(runtime.runningTasks()).thenReturn(List.of(task));
        when(runtime.task(1L)).thenReturn(task);
        QueueingExecutor workers = new QueueingExecutor();
        WorkflowEngine engine = engine(workers, ignored -> { });

        engine.driveWorkflows();
        engine.driveWorkflows();

        assertThat(workers.size()).isEqualTo(1);
        workers.runNext();
        engine.driveWorkflows();
        assertThat(workers.size()).isEqualTo(1);
    }

    @Test
    void rejectedSubmissionReleasesTaskForNextPoll() {
        Task task = task(1L);
        when(runtime.runningTasks()).thenReturn(List.of(task));
        when(runtime.task(1L)).thenReturn(task);
        QueueingExecutor accepted = new QueueingExecutor();
        AtomicBoolean rejectFirst = new AtomicBoolean(true);
        Executor workers = command -> {
            if (rejectFirst.getAndSet(false)) throw new RejectedExecutionException("full");
            accepted.execute(command);
        };
        WorkflowEngine engine = engine(workers, ignored -> { });

        engine.driveWorkflows();
        engine.driveWorkflows();

        assertThat(accepted.size()).isEqualTo(1);
    }

    @Test
    void oneTaskFailureDoesNotPreventAnotherTaskPoll() {
        Task first = task(1L);
        Task second = task(2L);
        when(runtime.runningTasks()).thenReturn(List.of(first, second));
        when(runtime.task(1L)).thenReturn(first);
        when(runtime.task(2L)).thenReturn(second);
        AtomicInteger completed = new AtomicInteger();
        WorkflowEngine engine = engine(Runnable::run, task -> {
            if (task.getId() == 1L) throw new IllegalStateException("boom");
            completed.incrementAndGet();
        });

        engine.driveWorkflows();

        verify(runtime).failTask(first, "boom");
        assertThat(completed.get()).isEqualTo(1);
    }

    private WorkflowEngine engine(Executor executor, TaskPoll taskPoll) {
        return new WorkflowEngine(runtime, workflows, flowNodes,
                new WorkflowConditionEvaluator(), new ConstraintExpressionEvaluator(),
                mock(WorkflowActionRegistry.class), operations, executor) {
            @Override
            void processTask(Task task) {
                taskPoll.run(task);
            }
        };
    }

    private Task task(long id) {
        Task task = new Task();
        task.setId(id);
        task.setTaskStatus("RUNNING");
        return task;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface TaskPoll {
        void run(Task task);
    }

    private static final class QueueingExecutor implements Executor {
        private final ArrayDeque<Runnable> commands = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            commands.add(command);
        }

        int size() {
            return commands.size();
        }

        void runNext() {
            commands.remove().run();
        }
    }
}
