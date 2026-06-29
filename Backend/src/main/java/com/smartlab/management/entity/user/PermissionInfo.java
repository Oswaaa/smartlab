package com.smartlab.management.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName(value = "\"PERMISSION_INFO\"", autoResultMap = false)
/**
 * 系统权限项实体。对应 PERMISSION_INFO 表，划定细粒度接口控制的RBAC权限码。
 */
public class PermissionInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scope")
    private String scope;

    @TableField("object")
    private String object;

    @TableField("action")
    private String action;

    @TableField("permission_level")
    private Integer permissionLevel;

    @TableField("permis_desc")
    private String permisDesc;

}
