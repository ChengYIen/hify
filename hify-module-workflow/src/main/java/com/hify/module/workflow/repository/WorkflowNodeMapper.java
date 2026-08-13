package com.hify.module.workflow.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.workflow.repository.entity.WorkflowNodeEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作流节点定义 Mapper.
 */
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNodeEntity> {

    /**
     * 批量插入节点，避免逐条 insert.
     */
    @Insert("""
            <script>
            INSERT INTO hify_workflow_node
                (workflow_id, node_key, node_name, node_type, config, position_x, position_y)
            VALUES
            <foreach collection='list' item='node' separator=','>
                (#{node.workflowId}, #{node.nodeKey}, #{node.nodeName}, #{node.nodeType},
                 #{node.config}, #{node.positionX}, #{node.positionY})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<WorkflowNodeEntity> nodes);
}
