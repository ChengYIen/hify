package com.hify.module.provider.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.provider.repository.entity.ProviderHealth;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供商健康检查记录 Mapper.
 */
@Mapper
public interface ProviderHealthMapper extends BaseMapper<ProviderHealth> {
}
