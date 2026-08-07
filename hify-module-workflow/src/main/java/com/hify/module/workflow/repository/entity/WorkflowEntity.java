package com.hify.module.workflow.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流定义实体.
 * <p>
 * 对应表 {@code hify_workflow}。存储工作流的 DAG 定义（节点、边），
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

    /** 工作流定义（JSON：节点、边、输入输出） */
    private String definition;

    /** 状态：DRAFT / PUBLISHED / DISABLED */
    private String status;

    /** 版本号 */
    private Integer version;

    /** 创建人用户 ID */
    private Long createdBy;
}
