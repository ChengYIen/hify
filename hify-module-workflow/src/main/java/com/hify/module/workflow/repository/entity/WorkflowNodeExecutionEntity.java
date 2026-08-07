package com.hify.module.workflow.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作流节点执行详情实体.
 * <p>
 * 对应表 {@code hify_workflow_node_execution}。记录工作流中每个节点的
 * 输入/输出、状态、耗时和重试次数。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_workflow_node_execution")
public class WorkflowNodeExecutionEntity extends BaseEntity {

    /** 工作流执行 ID */
    private Long executionId;

    /** 节点 ID（对应定义中的节点标识） */
    private String nodeId;

    /** 节点类型：LLM / CONDITION / TOOL / START / END */
    private String nodeType;

    /** 节点输入（JSON） */
    private String inputData;

    /** 节点输出（JSON） */
    private String outputData;

    /** 状态：PENDING / RUNNING / COMPLETED / FAILED / SKIPPED */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 结束执行时间 */
    private LocalDateTime finishedAt;

    /** 执行耗时（毫秒） */
    private Integer durationMs;

    /** 重试次数 */
    private Integer retryCount;
}
