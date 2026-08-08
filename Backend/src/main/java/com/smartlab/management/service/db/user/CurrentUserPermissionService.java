package com.smartlab.management.service.db.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.user.PermissionInfo;
import com.smartlab.management.entity.user.UserInfo;
import com.smartlab.management.mapper.user.UserInfoMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 将现有权限字典应用到后端接口，避免只依赖前端按钮可见性。
 */
@Service
public class CurrentUserPermissionService {

    private final UserInfoMapper userInfoMapper;
    private final PermissionService permissionService;

    public CurrentUserPermissionService(UserInfoMapper userInfoMapper, PermissionService permissionService) {
        this.userInfoMapper = userInfoMapper;
        this.permissionService = permissionService;
    }

    public void require(String object, String action) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? null : authentication.getName();
        if (username == null || username.isBlank()) throw new AccessDeniedException("用户未登录");

        UserInfo user = userInfoMapper.selectOne(
                Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUserName, username)
        );
        if (user == null) throw new AccessDeniedException("用户不存在或登录已过期");

        boolean allowed = permissionService.getUserPermissions(user.getId(), user.getLab(), user.getUserLevel()).stream()
                .anyMatch(permission -> grants(permission, object, action));
        if (!allowed) throw new AccessDeniedException("无权限执行" + object + ":" + action);
    }

    private boolean grants(PermissionInfo permission, String object, String action) {
        if (permission == null) return false;
        if ("all".equals(permission.getObject()) && "all".equals(permission.getAction())) return true;
        return object.equals(permission.getObject()) && action.equals(permission.getAction());
    }
}
