package com.hify.module.provider.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.provider.repository.entity.ModelConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型配置 Mapper.
 */
@Mapper
public interface ProviderModelMapper extends BaseMapper<ModelConfig> {
}
