package com.smartlab.management.entity.resource.adapter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"VIRTUAL_LEASE\"", autoResultMap = false)
/**
 * 虚拟实例租约。对应 VIRTUAL_LEASE 表，记录物理实例某次模拟租约，不代替 DEVICE_INSTANCES。
 */
public class VirtualLease {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("physical_instance_id")
    private Long physicalInstanceId;

    @TableField("adapter_name")
    private String adapterName;

    @TableField("virtual_device_point")
    private String virtualDevicePoint;

    @TableField("data_index_id")
    private Long dataIndexId;

    @TableField("status")
    private String status;

    @TableField("task_id")
    private Long taskId;

    @TableField("cancelled_time")
    private OffsetDateTime cancelledTime;

    @TableField("create_time")
    private OffsetDateTime createTime;

    @TableField("last_used_time")
    private OffsetDateTime lastUsedTime;

    public String getStatus() {
        return VirtualLeaseStatus.normalize(status);
    }

    public void setStatus(String status) {
        this.status = status == null || status.isBlank()
                ? VirtualLeaseStatus.LEASING
                : VirtualLeaseStatus.normalize(status);
    }
}
