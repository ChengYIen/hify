package com.hify.module.workflow.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.workflow.repository.entity.WorkflowEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流定义 Mapper.
 */
@Mapper
public interface WorkflowMapper extends BaseMapper<WorkflowEntity> {
}
