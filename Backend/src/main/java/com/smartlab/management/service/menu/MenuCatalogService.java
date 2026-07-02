package com.smartlab.management.service.menu;

import com.smartlab.management.dto.user.MenuDTO;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
/**
 * 系统菜单目录管理持久层服务。
 */
public class MenuCatalogService {

    private final List<MenuDTO> fullMenuTree = new ArrayList<>();

    @PostConstruct
    public void init() {
        fullMenuTree.clear();
        fullMenuTree.add(create("首页", "/home"));

        MenuDTO deviceCenter = createFolder("设备中心");
        deviceCenter.addChild(create("设备模型管理", "/device-model-management", "device_model"));
        deviceCenter.addChild(create("设备实例管理", "/device-instance-management", "device_instance"));
        deviceCenter.addChild(create("设备执行代理", "/adapter-management", "adapter"));
        fullMenuTree.add(deviceCenter);

        fullMenuTree.add(create("数据中心", "/data-management", "data_template", "data_dataset"));

        MenuDTO taskCenter = createFolder("任务中心");
        taskCenter.addChild(create("任务列表", "/task-management", "task"));
        taskCenter.addChild(create("流程设计", "/task-designer", "workflow"));
        fullMenuTree.add(taskCenter);

        fullMenuTree.add(create("约束管理", "/constraint-management", "constraint_rule", "violation_log"));
        fullMenuTree.add(create("用户管理", "/user-management", "user", "permission"));
    }

    public List<MenuDTO> getFullMenuTree() {
        return fullMenuTree;
    }

    private MenuDTO create(String name, String path, String... requires) {
        MenuDTO menu = new MenuDTO();
        menu.setName(name);
        menu.setPath(path);
        for (String require : requires) {
            menu.addRequire(require);
        }
        return menu;
    }

    private MenuDTO createFolder(String name, String... requires) {
        return create(name, null, requires);
    }
}
