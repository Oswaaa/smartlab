package com.smartlab.management.service.db.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.PermissionInfo;
import com.smartlab.management.entity.UserInfo;
import com.smartlab.management.mapper.PermissionInfoMapper;
import com.smartlab.management.mapper.UserInfoMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限表服务。
 * 对应 PERMISSION_INFO 表，并根据用户等级和用户特权 JSON 计算实际权限。
 */
@Service
public class PermissionService extends ManagementCrudService<PermissionInfo> {

    private final PermissionInfoMapper permissionMapper;
    private final UserInfoMapper userInfoMapper;

    public PermissionService(PermissionInfoMapper permissionMapper, UserInfoMapper userInfoMapper) {
        super(permissionMapper);
        this.permissionMapper = permissionMapper;
        this.userInfoMapper = userInfoMapper;
    }

    public List<PermissionInfo> listAll() {
        return permissionMapper.selectList(Wrappers.<PermissionInfo>lambdaQuery().orderByAsc(PermissionInfo::getId));
    }

    public List<PermissionInfo> getUserPermissions(Long userId, String labCode, Integer userLevel) {
        List<PermissionInfo> all = listAll();
        Set<Long> privilegedIds = getUserPermissionIds(userId).stream().map(Long::valueOf).collect(Collectors.toSet());
        List<PermissionInfo> result = new ArrayList<>();
        for (PermissionInfo permission : all) {
            boolean levelMatched = permission.getPermissionLevel() == null
                    || (userLevel != null && userLevel >= permission.getPermissionLevel());
            boolean explicitlyGranted = permission.getId() != null && privilegedIds.contains(permission.getId());
            if (levelMatched || explicitlyGranted) {
                result.add(permission);
            }
        }
        return result;
    }

    public List<Integer> getUserPermissionIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null || user.getPrivileged() == null || user.getPrivileged().isNull()) {
            return List.of();
        }
        Set<Integer> ids = new HashSet<>();
        JsonNode node = user.getPrivileged();
        if (node.isArray()) {
            node.forEach(item -> collectPermissionId(item, ids));
        } else {
            collectPermissionId(node, ids);
        }
        return ids.stream().sorted().toList();
    }

    public void assignPermissionsToUser(Long userId, List<Integer> permissionIds) {
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        List<Integer> ids = permissionIds == null ? List.of() : permissionIds;
        user.setPrivileged(JsonNodeSupport.toNode(ids));
        user.setIsPrivilegedUser(!ids.isEmpty());
        userInfoMapper.updateById(user);
    }

    private void collectPermissionId(JsonNode node, Set<Integer> ids) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.canConvertToInt()) {
            ids.add(node.asInt());
        } else if (node.isObject()) {
            JsonNode id = node.get("id");
            if (id != null && id.canConvertToInt()) {
                ids.add(id.asInt());
            }
        }
    }
}
