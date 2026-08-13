package com.hify.module.workflow.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流边定义实体.
 * <p>
 * 对应表 {@code hify_workflow_edge}。一条边表示 source 节点的某个输出口
 * 连接到 target 节点。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_workflow_edge")
public class WorkflowEdgeEntity extends BaseEntity {

    /** 所属工作流 ID */
    private Long workflowId;

    /** 边 ID（画布编辑用，可为空） */
    private String edgeId;

    /** 起始节点 key */
    private String sourceNodeKey;

    /** 路由条件标签，null 表示无条件边 */
    @TableField("edge_condition")
    private String edgeCondition;

    /** 目标节点 key */
    private String targetNodeKey;
}
