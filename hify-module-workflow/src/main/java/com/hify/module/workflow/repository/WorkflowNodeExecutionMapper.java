package com.hify.module.workflow.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.workflow.repository.entity.WorkflowNodeExecutionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流节点执行详情 Mapper.
 */
@Mapper
public interface WorkflowNodeExecutionMapper extends BaseMapper<WorkflowNodeExecutionEntity> {
}
