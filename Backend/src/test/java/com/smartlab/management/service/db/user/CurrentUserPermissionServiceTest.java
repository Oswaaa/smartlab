package com.smartlab.management.service.db.user;

import com.smartlab.management.entity.user.PermissionInfo;
import com.smartlab.management.entity.user.UserInfo;
import com.smartlab.management.mapper.user.UserInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserPermissionServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enforcesTheRequestedBackendPermission() {
        UserInfoMapper users = mock(UserInfoMapper.class);
        PermissionService permissions = mock(PermissionService.class);
        UserInfo user = new UserInfo();
        user.setId(9L);
        user.setUserName("operator");
        user.setLab("lab1");
        user.setUserLevel(2);
        when(users.selectOne(any())).thenReturn(user);
        when(permissions.getUserPermissions(9L, "lab1", 2)).thenReturn(List.of(permission("constraint_rule", "view")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("operator", null, List.of())
        );

        CurrentUserPermissionService service = new CurrentUserPermissionService(users, permissions);

        assertDoesNotThrow(() -> service.require("constraint_rule", "view"));
        assertThrows(AccessDeniedException.class, () -> service.require("constraint_rule", "edit"));
    }

    private PermissionInfo permission(String object, String action) {
        PermissionInfo permission = new PermissionInfo();
        permission.setObject(object);
        permission.setAction(action);
        return permission;
    }
}
