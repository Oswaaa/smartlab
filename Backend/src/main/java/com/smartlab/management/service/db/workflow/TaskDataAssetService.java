package com.smartlab.management.service.db.workflow;

import com.smartlab.management.dto.workflow.TaskDataAssetsResponse;
import com.smartlab.management.dto.workflow.TaskDataAssetsResponse.TaskInstanceDataAsset;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskExecutionKind;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.service.db.resource.adapter.VirtualLeaseService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class TaskDataAssetService {
    private final TaskMapper taskMapper;
    private final WorkflowTaskResourceService resourceService;
    private final DeviceInstanceService deviceInstanceService;
    private final DataIndexService dataIndexService;
    private final VirtualLeaseService virtualLeaseService;

    public TaskDataAssetService(TaskMapper taskMapper,
                                 WorkflowTaskResourceService resourceService,
                                 DeviceInstanceService deviceInstanceService,
                                 DataIndexService dataIndexService,
                                 VirtualLeaseService virtualLeaseService) {
        this.taskMapper = taskMapper;
        this.resourceService = resourceService;
        this.deviceInstanceService = deviceInstanceService;
        this.dataIndexService = dataIndexService;
        this.virtualLeaseService = virtualLeaseService;
    }

    public TaskDataAssetsResponse listDataAssets(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        boolean simulation = TaskExecutionKind.isSimulation(task);
        List<TaskInstanceDataAsset> instances = new ArrayList<>();
        for (Long physicalId : uniquePhysicalIds(task)) {
            DeviceInstances physical = deviceInstanceService.getById(physicalId);
            String name = physical == null || physical.getInstanceName() == null || physical.getInstanceName().isBlank()
                    ? String.valueOf(physicalId)
                    : physical.getInstanceName();
            instances.add(new TaskInstanceDataAsset(physicalId, name, datasetsFor(task, physicalId, simulation)));
        }
        return new TaskDataAssetsResponse(task.getId(), task.getTaskName(), task.getExecutionKind(), List.copyOf(instances));
    }

    private Set<Long> uniquePhysicalIds(Task task) {
        try {
            return resourceService.boundDeviceInstanceIds(task.getResourceMap());
        } catch (RuntimeException ignored) {
            return Set.of();
        }
    }

    private List<DataIndex> datasetsFor(Task task, Long physicalId, boolean simulation) {
        if (!simulation) {
            List<DataIndex> production = dataIndexService.listByDeviceInstance(physicalId);
            return production == null ? List.of() : List.copyOf(production);
        }
        Long dataIndexId = virtualLeaseService.resolveArchiveDataIndexId(physicalId, task.getId());
        if (dataIndexId == null) {
            return List.of();
        }
        DataIndex index = dataIndexService.getById(dataIndexId);
        return index == null ? List.of() : List.of(index);
    }
}
