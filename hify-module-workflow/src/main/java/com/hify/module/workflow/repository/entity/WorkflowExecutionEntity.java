package com.hify.module.workflow.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作流执行记录实体.
 * <p>
 * 对应表 {@code hify_workflow_execution}。记录每次工作流执行的输入/输出、
 * 状态和耗时，关联 WorkflowNodeExecutionEntity 记录各节点执行详情。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_workflow_execution")
public class WorkflowExecutionEntity extends BaseEntity {

    /** 工作流 ID */
    private Long workflowId;

    /** 执行时的工作流版本 */
    private Integer workflowVersion;

    /** 输入参数（JSON） */
    private String inputData;

    /** 输出结果（JSON） */
    private String outputData;

    /** 状态：PENDING / RUNNING / COMPLETED / FAILED / CANCELLED */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 结束执行时间 */
    private LocalDateTime finishedAt;

    /** 执行耗时（毫秒） */
    private Integer durationMs;

    /** 触发人用户 ID */
    private Long triggeredBy;
}
