package com.moli.agent.scope.harness.middleware;

import com.moli.agent.scope.common.dao.manager.MChatMessageFileManager;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.middleware.*;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 对话产物生成中间件：在每次 Agent 调用结束后，
 * 将用户输入和 AI 回复追加写入一个 TXT 文件作为产物输出，
 * 同时将产物信息持久化到数据库，以便历史消息查询时能够还原。
 */
public class FileGenMiddleware implements MiddlewareBase {

    /** 产物输出目录，默认输出到项目根目录下的 output 文件夹 */
    private static final Path OUTPUT_DIR = Paths.get("./output");

    private static final DateTimeFormatter FILE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** CustomEvent 的 name 标识：产物文件已生成 */
    public static final String EVENT_FILE_GENERATED = "file_generated";

    /** 消息文件产物管理器（通过构造函数注入） */
    private final MChatMessageFileManager messageFileManager;

    /**
     * 构造函数。
     *
     * @param messageFileManager 消息文件产物管理器，用于持久化文件产物记录
     */
    public FileGenMiddleware(MChatMessageFileManager messageFileManager) {
        this.messageFileManager = messageFileManager;
    }

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext ctx, AgentInput input,
                                    Function<AgentInput, Flux<AgentEvent>> next) {

        // 收集本轮用户输入
        List<String> userTexts = new ArrayList<>();
        for (Msg msg : input.msgs()) {
            String text = msg.getTextContent();
            if (text != null && !text.isBlank()) {
                userTexts.add(text);
            }
        }

        // 收集 AI 回复（从 AgentResultEvent 中提取）
        List<String> assistantTexts = new ArrayList<>();
        // 收集回复ID（用于关联文件产物）
        List<String> replyIds = new ArrayList<>();

        return next.apply(input)
                // 拦截事件流，提取 AgentResultEvent 中的回复消息和 replyId
                .doOnNext(event -> {
                    if (event instanceof AgentResultEvent resultEvent) {
                        Msg result = resultEvent.getResult();
                        if (result != null) {
                            String text = result.getTextContent();
                            if (text != null && !text.isBlank()) {
                                assistantTexts.add(text);
                            }
                            // 提取 replyId（从 Msg 的 id 字段或 metadata 中获取）
                            String replyId = extractReplyId(result);
                            if (replyId != null && !replyId.isBlank()) {
                                replyIds.add(replyId);
                            }
                        }
                    }
                })
                // 流结束后写入文件，并将产物地址作为 CustomEvent 发射给下游
                .concatWith(Flux.defer(() -> {
                    Path filePath = writeConversationToFile(ctx, userTexts, assistantTexts);
                    if (filePath != null) {
                        // 使用最后一个 replyId（如果有）
                        String replyId = replyIds.isEmpty() ? null : replyIds.get(replyIds.size() - 1);
                        // 持久化文件产物记录到数据库
                        persistFileRecord(ctx, filePath, replyId);

                        CustomEvent fileEvent = new CustomEvent(
                                EVENT_FILE_GENERATED,
                                Map.of(
                                        "filePath", filePath.toAbsolutePath().toString(),
                                        "sessionId", ctx.getSessionId() != null ? ctx.getSessionId() : "default",
                                        "userId", ctx.getUserId() != null ? ctx.getUserId() : "unknown",
                                        "replyId", replyId != null ? replyId : ""
                                )
                        );
                        return Flux.just(fileEvent);
                    }
                    return Flux.empty();
                }));
    }

    /**
     * 从 Msg 中提取 replyId。
     * 优先尝试 getId()，如果不存在则从 metadata 中获取。
     */
    private String extractReplyId(Msg msg) {
        // 尝试从 Msg 的 id 字段获取
        try {
            String id = msg.getId();
            if (id != null && !id.isBlank()) {
                return id;
            }
        } catch (Exception e) {
            // getId() 方法不存在，忽略
        }
        // 尝试从 metadata 中获取
        if (msg.getMetadata() != null) {
            Object replyIdObj = msg.getMetadata().get("replyId");
            if (replyIdObj instanceof String replyId) {
                return replyId;
            }
            // 尝试其他可能的键名
            replyIdObj = msg.getMetadata().get("id");
            if (replyIdObj instanceof String replyId) {
                return replyId;
            }
        }
        return null;
    }

    /**
     * 将对话内容写入 TXT 文件。
     * 文件按 sessionId 命名，同一 session 的多次对话会追加到同一文件。
     *
     * @return 生成的文件路径，写入失败时返回 null
     */
    private Path writeConversationToFile(RuntimeContext ctx,
                                         List<String> userTexts,
                                         List<String> assistantTexts) {
        try {
            Files.createDirectories(OUTPUT_DIR);

            String sessionId = ctx.getSessionId() != null ? ctx.getSessionId() : "default";
            String fileName = "conversation_" + sessionId + ".txt";
            Path filePath = OUTPUT_DIR.resolve(fileName);

            StringBuilder sb = new StringBuilder();
            sb.append("========== 对话记录 ==========\n");
            sb.append("会话ID: ").append(sessionId).append("\n");
            sb.append("用户ID: ").append(ctx.getUserId() != null ? ctx.getUserId() : "unknown").append("\n");
            sb.append("时间: ").append(LocalDateTime.now().format(FILE_TIME_FMT)).append("\n");
            sb.append("------------------------------\n");

            // 写入用户输入
            for (String text : userTexts) {
                sb.append("[用户]: ").append(text).append("\n");
            }

            // 写入 AI 回复
            for (String text : assistantTexts) {
                sb.append("[AI]: ").append(text).append("\n");
            }

            sb.append("==============================\n\n");

            // 追加写入文件
            Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            System.out.println("[FileGenMiddleware] 对话产物已生成: " + filePath.toAbsolutePath());
            return filePath;
        } catch (IOException e) {
            System.err.println("[FileGenMiddleware] 写入对话产物失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 将文件产物记录持久化到数据库。
     * 失败时仅打印日志，不影响主流程。
     */
    private void persistFileRecord(RuntimeContext ctx, Path filePath, String replyId) {
        try {
            String sessionId = ctx.getSessionId() != null ? ctx.getSessionId() : "default";
            Long userId = ctx.getUserId() != null ? Long.parseLong(ctx.getUserId()) : null;
            if (userId == null) {
                System.err.println("[FileGenMiddleware] 无法解析 userId，跳过持久化文件记录");
                return;
            }

            // 生成一个默认的 replyId（如果没有从 Msg 中提取到）
            if (replyId == null || replyId.isBlank()) {
                replyId = "reply_" + sessionId + "_" + System.currentTimeMillis();
                System.out.println("[FileGenMiddleware] 未找到 replyId，使用生成的默认值: " + replyId);
            }

            // 从完整路径提取文件名
            String fileName = filePath.getFileName().toString();
            String fileUrl = filePath.toAbsolutePath().toString();

            // 读取文件大小
            Long fileSize = null;
            try {
                fileSize = Files.size(filePath);
            } catch (IOException e) {
                System.err.println("[FileGenMiddleware] 读取文件大小失败: " + e.getMessage());
            }

            messageFileManager.saveFileRecord(userId, sessionId, replyId, fileName, fileUrl, fileSize, "text/plain");
            System.out.println("[FileGenMiddleware] 文件产物记录已持久化: sessionId=" + sessionId
                    + ", replyId=" + replyId + ", fileName=" + fileName);
        } catch (Exception e) {
            // 持久化失败不影响本次对话，仅打印告警日志
            System.err.println("[FileGenMiddleware] 持久化文件产物记录失败（不影响本次对话）: " + e.getMessage());
        }
    }
}

