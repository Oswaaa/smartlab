package com.smartlab.management.service.menu;

import com.smartlab.management.dto.MenuDTO;
import com.smartlab.management.entity.PermissionInfo;
import com.smartlab.management.service.db.user.PermissionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单可见性服务。
 * 菜单定义保存在后端代码字典中，是否可见由 PERMISSION_INFO 计算出的权限对象决定。
 */
@Service
public class MenuService {

    private final MenuCatalogService menuCatalogService;
    private final PermissionService permissionService;

    public MenuService(MenuCatalogService menuCatalogService, PermissionService permissionService) {
        this.menuCatalogService = menuCatalogService;
        this.permissionService = permissionService;
    }

    public List<MenuDTO> getVisibleMenusForUser(Long userId, String labCode, Integer userLevel) {
        Set<String> hasObjects = permissionService.getUserPermissions(userId, labCode, userLevel).stream()
                .map(PermissionInfo::getObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<MenuDTO> fullTree = menuCatalogService.getFullMenuTree();
        return filter(fullTree, hasObjects);
    }

    public List<MenuDTO> getVisibleMenus(Set<String> hasObjects) {
        List<MenuDTO> fullTree = menuCatalogService.getFullMenuTree();
        return filter(fullTree, hasObjects);
    }

    private List<MenuDTO> filter(List<MenuDTO> nodes, Set<String> hasObjects) {
        List<MenuDTO> result = new ArrayList<>();

        for (MenuDTO node : nodes) {
            if (canSee(node, hasObjects)) {
                MenuDTO copy = copyNode(node);
                copy.setChildren(filter(node.getChildren(), hasObjects));
                if (copy.getPath() == null && copy.getChildren().isEmpty()) {
                    continue;
                }
                result.add(copy);
            }
        }
        return result;
    }

    private boolean canSee(MenuDTO node, Set<String> hasObjects) {
        if (node.getRequires() == null || node.getRequires().isEmpty()) {
            return true;
        }
        for (String req : node.getRequires()) {
            if (hasObjects.contains(req)) {
                return true;
            }
        }
        return false;
    }

    private MenuDTO copyNode(MenuDTO source) {
        MenuDTO target = new MenuDTO();
        target.setName(source.getName());
        target.setPath(source.getPath());
        target.setRequires(new ArrayList<>(source.getRequires()));
        return target;
    }
}
