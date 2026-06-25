package com.smartlab.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.PostgresJsonbTypeHandler;
import lombok.Data;

@Data
@TableName(value = "\"USER_INFO\"", autoResultMap = true)
public class UserInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_name")
    private String userName;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("user_basicinfo")
    private String userBasicinfo;

    @TableField("role")
    private String role;

    public String getRoleName() {
        return role;
    }

    @JsonSetter("roleName")
    public void setRoleName(String roleName) {
        this.role = roleName;
    }

    @TableField("lab")
    private String lab;

    @TableField("user_level")
    private Integer userLevel;

    @TableField("is_privileged_user")
    private Boolean isPrivilegedUser;

    @TableField(value = "privileged", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode privileged;

}
