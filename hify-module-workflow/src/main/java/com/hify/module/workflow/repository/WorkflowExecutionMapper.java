package com.hify.module.workflow.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.workflow.repository.entity.WorkflowExecutionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流执行记录 Mapper.
 */
@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecutionEntity> {
}
