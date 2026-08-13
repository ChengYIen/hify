package com.hify.module.workflow.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流节点定义实体.
 * <p>
 * 对应表 {@code hify_workflow_node}。节点配置以 JSON 存储，读取时由
 * {@code NodeConfigParser} 按 {@code nodeType} 解析成对应的 record。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_workflow_node")
public class WorkflowNodeEntity extends BaseEntity {

    /** 所属工作流 ID */
    private Long workflowId;

    /** 节点 key（工作流内唯一） */
    private String nodeKey;

    /** 节点显示名 */
    private String nodeName;

    /** 节点类型: START / END / LLM / CONDITION / API_CALL / KNOWLEDGE */
    private String nodeType;

    /** 节点配置 JSON，由 NodeConfigParser 按 nodeType 解析 */
    private String config;

    /** 画布 X 坐标 */
    private Double positionX;

    /** 画布 Y 坐标 */
    private Double positionY;
}
