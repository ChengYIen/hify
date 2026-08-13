package com.hify.module.workflow.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.workflow.repository.entity.WorkflowEdgeEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作流边定义 Mapper.
 */
public interface WorkflowEdgeMapper extends BaseMapper<WorkflowEdgeEntity> {

    /**
     * 批量插入边，避免逐条 insert.
     */
    @Insert("""
            <script>
            INSERT INTO hify_workflow_edge
                (workflow_id, edge_id, source_node_key, edge_condition, target_node_key)
            VALUES
            <foreach collection='list' item='edge' separator=','>
                (#{edge.workflowId}, #{edge.edgeId}, #{edge.sourceNodeKey}, #{edge.edgeCondition},
                 #{edge.targetNodeKey})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<WorkflowEdgeEntity> edges);
}
