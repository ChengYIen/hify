package com.hify.module.knowledge.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.knowledge.repository.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper.
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {
}
