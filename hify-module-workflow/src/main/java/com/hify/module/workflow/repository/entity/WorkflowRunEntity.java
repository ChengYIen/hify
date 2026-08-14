package com.hify.module.workflow.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow engine run record backed by {@code workflow_run}.
 */
@Data
@TableName("workflow_run")
public class WorkflowRunEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    /** RUNNING / SUCCESS / FAILED */
    private String status;

    private String input;

    private String output;

    private String error;

    private Integer elapsedMs;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
