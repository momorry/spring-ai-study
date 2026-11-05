package com.moli.agent.scope.common.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户表
 * </p>
 *
 * @author moli
 * @since 2026-11-03
 */
@Getter
@Setter
@ToString
@TableName("m_user")
public class MUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录号
     */
    private String loginAcct;

    /**
     * 登录号类型：mobile-手机号,email-邮箱,custom-自定义
     */
    private String loginAcctType;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 姓名
     */
    private String userName;

    /**
     * 密码（BCrypt 加密）
     */
    private String password;

    /**
     * 状态 1-生效 0-注销
     */
    private Boolean status;

    /**
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
