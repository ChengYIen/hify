package com.hify.module.workflow.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.workflow.repository.entity.WorkflowNodeRunEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRunEntity> {
}
