package com.smartlab.management.dto;

import lombok.Data;
import java.util.Map;

@Data
public class UserRequestDTO {

    private Integer id;        // 编辑时必传
    private String userName;
    private String password;
    private String roleName;
    private String lab;
    private Map<String, Object> userBasicInfo;
    private Integer userLevel;
}

