package com.smartlab.management.controller;

import com.smartlab.management.dto.ApiResponse;
import com.smartlab.management.dto.UserRequestDTO;
import com.smartlab.management.entity.PermissionInfo;
import com.smartlab.management.entity.Task;
import com.smartlab.management.entity.UserInfo;
import com.smartlab.management.service.db.user.PermissionService;
import com.smartlab.management.service.db.user.UserService;
import com.smartlab.management.service.db.workflow.TaskService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final PermissionService permissionService;
    private final TaskService taskService;

    public UserController(UserService userService, PermissionService permissionService, TaskService taskService) {
        this.userService = userService;
        this.permissionService = permissionService;
        this.taskService = taskService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody UserRequestDTO request) {
        try {
            return ApiResponse.ok(userService.login(request.getUserName(), request.getPassword()));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ApiResponse.fail("未登录或登录已过期");
            }
            return ApiResponse.ok(userService.currentUserProfile(authentication.getName()));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/menus")
    public ApiResponse<Object> menus(Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ApiResponse.fail("未登录或登录已过期");
            }
            return ApiResponse.ok(userService.currentUserProfile(authentication.getName()).get("menus"));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/register")
    public ApiResponse<String> register(@RequestBody UserRequestDTO request) {
        try {
            userService.register(request);
            return ApiResponse.ok("注册成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/verify")
    public ApiResponse<String> verify(@RequestBody UserRequestDTO request) {
        try {
            userService.verifyAdmin(request.getUserName(), request.getPassword());
            return ApiResponse.ok("验证通过");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<UserInfo>> list() {
        return ApiResponse.ok(userService.listUsers());
    }

    @PostMapping("/update")
    public ApiResponse<String> update(@RequestBody UserRequestDTO request) {
        try {
            userService.updateUser(request);
            return ApiResponse.ok("更新成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.ok("删除成功");
    }

    @GetMapping("/permissions/list")
    public ApiResponse<List<PermissionInfo>> listPermissions() {
        return ApiResponse.ok(permissionService.listAll());
    }

    @GetMapping("/permissions/by-user/{id}")
    public ApiResponse<List<Integer>> permissionsByUser(@PathVariable Long id) {
        return ApiResponse.ok(permissionService.getUserPermissionIds(id));
    }

    @PostMapping("/permissions/assign")
    public ApiResponse<String> assignPermissions(@RequestBody Map<String, Object> payload) {
        Number userIdRaw = (Number) payload.get("userId");
        if (userIdRaw == null) {
            return ApiResponse.fail("userId 不能为空");
        }
        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) payload.getOrDefault("permissionIds", Collections.emptyList());
        List<Integer> ids = rawIds.stream().map(Number::intValue).toList();
        permissionService.assignPermissionsToUser(userIdRaw.longValue(), ids);
        return ApiResponse.ok("特权分配成功");
    }

    @GetMapping("/task-history/{id}")
    public ApiResponse<List<Task>> taskHistory(@PathVariable Long id) {
        return ApiResponse.ok(taskService.listByCreator(id));
    }
}

