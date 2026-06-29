package com.smartlab.management.service.db.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.global.auth.AuthenticationTokenService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.user.MenuDTO;
import com.smartlab.management.dto.user.UserRequestDTO;
import com.smartlab.management.entity.user.PermissionInfo;
import com.smartlab.management.entity.user.UserInfo;
import com.smartlab.management.mapper.user.UserInfoMapper;
import com.smartlab.management.service.menu.MenuService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户表服务。
 * 对应 USER_INFO 表，负责登录、注册、用户维护和用户可见菜单计算。
 */
@Service
/**
 * User业务持久层核心操作服务。
 */
public class UserService {

    private final UserInfoMapper userInfoMapper;
    private final PasswordEncoder passwordEncoder;
    private final MenuService menuService;
    private final PermissionService permissionService;
    private final AuthenticationTokenService authenticationTokenService;

    public UserService(UserInfoMapper userInfoMapper,
                       PasswordEncoder passwordEncoder,
                       MenuService menuService,
                       PermissionService permissionService,
                       AuthenticationTokenService authenticationTokenService) {
        this.userInfoMapper = userInfoMapper;
        this.passwordEncoder = passwordEncoder;
        this.menuService = menuService;
        this.permissionService = permissionService;
        this.authenticationTokenService = authenticationTokenService;
    }

    public Map<String, Object> login(String username, String password) {
        UserInfo user = userInfoMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUserName, username));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = authenticationTokenService.generateToken(user.getUserName());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", buildProfile(user));
        return result;
    }

    public Map<String, Object> currentUserProfile(String username) {
        UserInfo user = userInfoMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUserName, username));
        if (user == null) {
            throw new IllegalArgumentException("用户不存在或登录已过期");
        }
        return buildProfile(user);
    }

    public void register(UserRequestDTO request) {
        UserInfo existing = userInfoMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUserName, request.getUserName()));
        if (existing != null) {
            throw new IllegalArgumentException("该用户名已被注册");
        }
        UserInfo user = new UserInfo();
        applyRequest(user, request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userInfoMapper.insert(user);
    }

    public void verifyAdmin(String username, String password) {
        UserInfo user = userInfoMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUserName, username));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("验证密码错误");
        }
        if (user.getUserLevel() == null || user.getUserLevel() < 3) {
            throw new IllegalArgumentException("无管理员权限");
        }
    }

    public List<UserInfo> listUsers() {
        List<UserInfo> users = userInfoMapper.selectList(Wrappers.<UserInfo>lambdaQuery().orderByAsc(UserInfo::getId));
        users.forEach(user -> user.setPasswordHash(null));
        return users;
    }

    public void updateUser(UserRequestDTO request) {
        if (request.getId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        UserInfo user = userInfoMapper.selectById(request.getId());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        applyRequest(user, request);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        userInfoMapper.updateById(user);
    }

    public void deleteUser(Long id) {
        userInfoMapper.deleteById(id);
    }

    private Map<String, Object> buildProfile(UserInfo user) {
        List<PermissionInfo> permissions = permissionService.getUserPermissions(user.getId(), user.getLab(), user.getUserLevel());
        Set<String> authObjects = permissions.stream()
                .map(PermissionInfo::getObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<MenuDTO> menus = menuService.getVisibleMenus(authObjects);

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUserName());
        profile.put("userName", user.getUserName());
        profile.put("role", user.getRole());
        profile.put("roleName", user.getRole());
        profile.put("userBasicInfo", user.getUserBasicinfo());
        profile.put("lab", user.getLab());
        profile.put("userLevel", user.getUserLevel());
        profile.put("permissions", permissions);
        profile.put("authObjects", authObjects);
        profile.put("menus", menus);
        return profile;
    }

    private void applyRequest(UserInfo user, UserRequestDTO request) {
        if (request.getUserName() != null) {
            user.setUserName(request.getUserName());
        }
        if (request.getRoleName() != null) {
            user.setRole(request.getRoleName());
            user.setUserLevel(roleLevel(request.getRoleName()));
        }
        if (request.getLab() != null) {
            user.setLab(request.getLab());
        }
        if (request.getUserLevel() != null) {
            user.setUserLevel(request.getUserLevel());
        }
        if (request.getUserBasicInfo() != null) {
            user.setUserBasicinfo(JsonNodeSupport.toNode(request.getUserBasicInfo()).toString());
        }
        if (user.getUserLevel() == null) {
            user.setUserLevel(roleLevel(user.getRole()));
        }
    }

    private int roleLevel(String role) {
        if ("system_admin".equals(role)) return 4;
        if ("lab_admin".equals(role)) return 3;
        if ("researcher".equals(role)) return 2;
        if ("observer".equals(role)) return 1;
        return 1;
    }
}
