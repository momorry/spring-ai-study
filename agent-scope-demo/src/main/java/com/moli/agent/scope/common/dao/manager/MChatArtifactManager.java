package com.moli.agent.scope.common.dao.manager;

import com.baomidou.mybatisplus.spring.service.IService;
import com.moli.agent.scope.common.dao.entity.MChatArtifact;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 对话产物台账 服务类
 * </p>
 *
 * @author moli
 * @since 2026-11-13
 */
public interface MChatArtifactManager extends IService<MChatArtifact> {

    /**
     * 新增一条产物台账。
     *
     * @param artifact 产物记录（objectKey 应已指向落好的对象）
     * @return 落库后的产物ID（自增主键，即对外暴露的下载入参）
     */
    Long saveArtifact(MChatArtifact artifact);

    /**
     * 同一会话下是否已存在同名且仍可用的产物。
     * <p>对应 {@code deliver_artifact} 的 force=false 冲突语义：命中则本轮交付被拒，模型应换个文件名重试。
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param fileName  产物文件名
     * @return 存在返回 true
     */
    boolean hasActiveArtifact(Long userId, String sessionId, String fileName);

    /**
     * 把同一会话下同名的旧产物置为 SUPERSEDED（force=true 覆盖时调用）。
     * <p>只改状态、不立刻删对象：用户手上可能还拿着旧 artifactId，物理删除交给回收任务，避免正在用的下载突然 404。
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param fileName  产物文件名
     * @return 被置为 SUPERSEDED 的行数
     */
    int supersedeSameName(Long userId, String sessionId, String fileName);

    /**
     * 把本轮尚未绑定消息的产物回填到这条最终回答上。
     * <p>{@code deliver_artifact} 交付时拿不到本轮最终回答的 Msg.getId()（那条消息还没生成完），
     * 只能在轮末回填。用「会话 + 本轮起始时间」划窗口，避免顶到上一轮遗留的未绑定产物。
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param messageId 本轮最终回答的消息ID（Msg.getId()）
     * @param since     本轮起始时间，只回填这个时间点之后创建的
     * @return 被回填的行数
     */
    int bindMessageId(Long userId, String sessionId, String messageId, LocalDateTime since);

    /**
     * 查本轮已交付的产物，按创建时间升序。
     * <p>交付工具的结果事件里只有文本摘要、没有 artifactId，所以由事件流反查台账；
     * 调用方按次去重，同一个产物不会重复下发卡片。
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param since     本轮起始时间
     * @return 产物列表，无则空列表
     */
    List<MChatArtifact> listSince(Long userId, String sessionId, LocalDateTime since);

    /**
     * 按消息ID批量查产物，供历史加载时把产物挂到对应回答上。
     *
     * @param messageIds 消息ID集合
     * @return 产物列表（按创建时间升序），入参为空时返回空列表
     */
    List<MChatArtifact> listByMessageIds(List<String> messageIds);
}
