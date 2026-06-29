package com.smartlab.management.mapper.user;

import com.smartlab.management.entity.user.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * UserInfo持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}

