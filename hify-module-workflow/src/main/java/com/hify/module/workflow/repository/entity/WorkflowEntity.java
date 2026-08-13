package com.hify.module.workflow.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流定义实体.
 * <p>
 * 对应表 {@code hify_workflow}。节点和边分别存在
 * {@code hify_workflow_node} / {@code hify_workflow_edge}，本表只存基本信息，
 * 支持版本管理和状态流转（DRAFT → PUBLISHED → DISABLED）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_workflow")
public class WorkflowEntity extends BaseEntity {

    /** 工作流名称 */
    private String name;

    /** 工作流描述 */
    private String description;

    /** 状态：DRAFT / PUBLISHED / DISABLED */
    private String status;

    /** 版本号 */
    private Integer version;

    /** 创建人用户 ID */
    private Long createdBy;
}
