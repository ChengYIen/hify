package com.hify.module.knowledge.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.knowledge.repository.entity.KnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库 Mapper.
 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<KnowledgeEntity> {
}
