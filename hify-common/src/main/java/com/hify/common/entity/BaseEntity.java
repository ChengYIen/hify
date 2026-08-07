package com.hify.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 所有业务实体的基类.
 * <p>
 * 提供通用字段：主键 id（自增）、创建时间 createdAt（插入自动填充）、
 * 更新时间 updatedAt（插入+更新自动填充）、逻辑删除 deleted（默认 0）。
 * 配合 {@link com.hify.common.config.MyMetaObjectHandler} 实现时间字段自动填充。
 * </p>
 */
@Data
public abstract class BaseEntity {

    /**
     * 主键 ID，数据库自增.
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间，插入时由 {@link com.hify.common.config.MyMetaObjectHandler} 自动填充.
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间，插入和更新时由 {@link com.hify.common.config.MyMetaObjectHandler} 自动填充.
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记：0=未删除，1=已删除.
     * MyBatis-Plus 自动在查询/删除时追加 deleted 条件.
     */
    @TableLogic
    private Integer deleted;
}
