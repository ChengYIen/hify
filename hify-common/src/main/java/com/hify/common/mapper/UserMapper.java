package com.hify.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.common.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper.
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
