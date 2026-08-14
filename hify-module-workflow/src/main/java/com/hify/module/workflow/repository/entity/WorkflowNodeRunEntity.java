package com.hify.module.workflow.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow engine node run record backed by {@code workflow_node_run}.
 */
@Data
@TableName("workflow_node_run")
public class WorkflowNodeRunEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowRunId;

    private String nodeKey;

    private String nodeType;

    /** RUNNING / SUCCESS / FAILED */
    private String status;

    /** Serialized ExecutionContext.snapshot(). */
    private String outputs;

    private String error;

    private Integer elapsedMs;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
