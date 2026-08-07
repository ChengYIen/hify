package com.hify.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体.
 * <p>
 * 对应表 {@code hify_user}。一期 MVP 从简，不做 RBAC。
 * 密码使用 bcrypt 加密存储。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_user")
public class UserEntity extends BaseEntity {

    /** 用户名 */
    private String username;

    /** 密码（bcrypt） */
    private String password;

    /** 显示名 */
    private String displayName;

    /** 角色：ADMIN / USER */
    private String role;
}
