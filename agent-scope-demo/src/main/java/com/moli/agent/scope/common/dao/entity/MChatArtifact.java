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
 * 对话产物台账（权威索引）
 * </p>
 * 产物字节落在对象存储（OSS）或本地目录里，本表只存元数据与 object_key：可索引、可分页、可鉴权、可回收。
 * 加载历史消息时按 message_id 查出产物、拼成前端展示的卡片；产物卡片信息不写进消息本身
 * （消息只存在于 agentscope_sessions 的会话大 JSON 里，天生不可索引，且会被压缩/重新生成改写）。
 *
 * <p>取代旧的 {@code m_chat_message_file}，三点差别：
 * 一、{@code message_id} 可为空——deliver_artifact 在本轮答完之前执行，那时最终 assistant 消息还不存在，
 * 先按 (user_id, session_id) 落库，本轮结束时再回填；
 * 二、message_id 不再唯一——一轮可以交付多个文件；
 * 三、{@code object_key} 取代 {@code file_url}，绝不存服务器绝对路径。
 *
 * @author moli
 * @since 2026-11-13
 */
@Getter
@Setter
@ToString
@TableName("m_chat_artifact")
public class MChatArtifact implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：可下载、可在产物列表展示 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 状态：被同名新产物覆盖（force=true 时旧行转此状态），对象暂留以便回收与追溯 */
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    /** 状态：已删除（会话删除或人工清理），对象由回收任务按 object_key 删除 */
    public static final String STATUS_DELETED = "DELETED";

    /** 存储后端：对象存储 */
    public static final String STORAGE_OSS = "OSS";

    /** 存储后端：本地目录（开发环境兜底） */
    public static final String STORAGE_LOCAL = "LOCAL";

    /**
     * 主键，同时是对外暴露的产物ID（下载接口以此入参，不下发 object_key）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID（关联 m_user.id，归属与鉴权依据）
     */
    private Long userId;

    /**
     * AgentScope 会话ID
     */
    private String sessionId;

    /**
     * 关联消息ID（Msg.getId()）；交付时机拿不到，由本轮结束时回填，回填不上时为空
     */
    private String messageId;

    /**
     * 产物文件名（含扩展名，下载时的展示名）
     */
    private String fileName;

    /**
     * 存储后端：OSS / LOCAL，见 {@link #STORAGE_OSS}、{@link #STORAGE_LOCAL}
     */
    private String storageType;

    /**
     * 存储 key（相对 bucket 根 / 相对本地根目录），绝不写绝对路径
     */
    private String objectKey;

    /**
     * 产物在 Agent 工作区中的原始路径（deliver_artifact 传入的 filePath，排障用）
     */
    private String sourcePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MIME 类型（如 text/html）
     */
    private String contentType;

    /**
     * 模型填写的产物说明
     */
    private String description;

    /**
     * 状态：ACTIVE / SUPERSEDED / DELETED
     */
    private String status;

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
