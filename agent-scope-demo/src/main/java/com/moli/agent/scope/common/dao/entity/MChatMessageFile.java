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
 * 消息文件产物关联表
 * </p>
 * 记录每次对话产生的文件产物，关联到具体的回复（replyId）；
 * 加载历史消息时，根据 replyId 查询并附加文件信息到对应的 assistant 消息。
 *
 * @author moli
 * @since 2026-11-05
 */
@Getter
@Setter
@ToString
@TableName("m_chat_message_file")
public class MChatMessageFile implements Serializable {

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
     * AgentScope 会话ID
     */
    private String sessionId;

    /**
     * 回复ID（关联一次具体的 AI 回复）
     */
    private String replyId;

    /**
     * 文件名称（含扩展名）
     */
    private String fileName;

    /**
     * 文件访问地址（相对路径或完整 URL）
     */
    private String fileUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型 / MIME（如 text/plain）
     */
    private String fileType;

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
