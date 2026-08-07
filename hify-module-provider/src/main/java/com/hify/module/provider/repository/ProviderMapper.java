package com.hify.module.provider.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.provider.repository.entity.Provider;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型提供商 Mapper.
 */
@Mapper
public interface ProviderMapper extends BaseMapper<Provider> {
}
