package com.smartlab.management.dto.user;

import lombok.Data;
import java.util.Map;

@Data
/**
 * UserRequest业务数据传输载体对象（DTO）。
 */
public class UserRequestDTO {

    private Integer id;        // 编辑时必传
    private String userName;
    private String password;
    private String roleName;
    private String lab;
    private Map<String, Object> userBasicInfo;
    private Integer userLevel;
}

