package com.moli.agent.scope.common.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 聊天会话表（历史会话列表）
 * </p>
 * 仅存业务目录/索引信息（标题、最后消息预览、轮数、时间），供前端历史会话侧边栏展示；
 * 真正的对话上下文（记忆）由 AgentScope 的 agentscope_sessions 表按 (userId, sessionId) 持久化，
 * 两表通过 sessionId 关联。
 *
 * @author moli
 * @since 2026-11-03
 */
@Getter
@Setter
@ToString
@TableName("m_chat_session")
public class MChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID（关联 m_user.id）
     */
    private Long userId;

    /**
     * AgentScope 会话ID（与 agentscope_sessions 的 sessionId 一致）
     */
    private String sessionId;

    /**
     * 会话标题（默认取首条用户消息截断）
     */
    private String title;

    /**
     * 最后一条消息预览
     */
    private String lastMessage;

    /**
     * 对话轮数（用户提问次数）
     */
    private Integer roundCount;

    /**
     * 是否置顶 1-是 0-否
     */
    private Boolean pinned;

    /**
     * 逻辑删除 1-已删除 0-未删除
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

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
