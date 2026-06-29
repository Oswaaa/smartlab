package com.smartlab.management.dto.user;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu业务数据传输载体对象（DTO）。
 */
public class MenuDTO {

    private String name;
    private String path;
    private List<String> requires = new ArrayList<>();
    private List<MenuDTO> children = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getRequires() {
        return requires != null ? requires : new ArrayList<>();
    }

    public void setRequires(List<String> requires) {
        this.requires = requires != null ? requires : new ArrayList<>();
    }

    public List<MenuDTO> getChildren() {
        return children != null ? children : new ArrayList<>();
    }

    public void setChildren(List<MenuDTO> children) {
        this.children = children != null ? children : new ArrayList<>();
    }

    public void addRequire(String object) {
        if (this.requires == null) {
            this.requires = new ArrayList<>();
        }
        this.requires.add(object);
    }

    public void addChild(MenuDTO child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}

