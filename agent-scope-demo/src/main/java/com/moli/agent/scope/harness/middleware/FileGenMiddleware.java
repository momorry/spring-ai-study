package com.moli.agent.scope.harness.middleware;

import com.fasterxml.jackson.core.type.TypeReference;
import com.moli.agent.scope.common.dao.manager.MChatMessageFileManager;
import com.moli.agent.scope.common.util.JsonUtil;
import com.moli.agent.scope.common.vo.FileInfo;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.extern.slf4j.Slf4j;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 对话产物生成中间件：在每次 Agent 调用结束后，
 * 将用户输入和 AI 回复追加写入一个 TXT 文件作为产物输出，
 * 同时将产物信息持久化到数据库，以便历史消息查询时能够还原。
 *
 * <p>只对“真正答完了”的那一轮开产物：本轮被人在回路暂停时，上层会拿到一个带
 * {@link GenerateReason#PERMISSION_ASKING} 的结果消息（内容只是“让我调用工具”那段中间输出），
 * 若不拦住，产物会在工具调用阶段就生成，并把 messageId 钉在中间消息上。
 */
@Slf4j
public class FileGenMiddleware implements MiddlewareBase {

    /**
     * 产物输出目录，默认输出到项目根目录下的 output 文件夹
     */
    private static final Path OUTPUT_DIR = Paths.get("./output");

    private static final DateTimeFormatter FILE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * CustomEvent 的 name 标识：产物文件已生成
     */
    public static final String EVENT_FILE_GENERATED = "file_generated";

    /**
     * RuntimeContext 属性键：人在回路恢复那次调用补传的用户提问原文。
     * <p>恢复请求只带裁决 metadata、不带提问（见 {@code SimpleHarnessActAgent}），本轮输入里抽不到用户文本，
     * 不从这里取的话，产物会只剩 [AI] 那一段。
     */
    public static final String CTX_KEY_RESUME_USER_TEXT = "fileGenResumeUserText";

    /**
     * 本轮没有产出最终回答的原因：这些情况下 {@code AgentResultEvent} 里的是中间输出或被停下/中断的残体，
     * 不该据此生成产物。正常答完时该字段不下发，{@code Msg#getGenerateReason()} 缺省返回 MODEL_STOP，不在此列。
     */
    private static final Set<GenerateReason> TURN_NOT_FINISHED = EnumSet.of(
            GenerateReason.TOOL_CALLS,
            GenerateReason.TOOL_SUSPENDED,
            GenerateReason.REASONING_STOP_REQUESTED,
            GenerateReason.ACTING_STOP_REQUESTED,
            GenerateReason.MIDDLEWARE_STOP_REQUESTED,
            GenerateReason.PERMISSION_ASKING,
            GenerateReason.ALL_TOOLS_DENIED,
            GenerateReason.INTERRUPTED,
            GenerateReason.MAX_ITERATIONS);

    /**
     * 消息文件产物管理器（通过构造函数注入）
     */
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
        if (userTexts.isEmpty()) {
            // 确认恢复那一轮：本次输入只有裁决 metadata，提问原文由服务端从上下文取出后随 RuntimeContext 下发
            Object resumeText = ctx.get(CTX_KEY_RESUME_USER_TEXT);
            if (resumeText instanceof String text && !text.isBlank()) {
                userTexts.add(text);
            }
        }

        // 收集 AI 回复（从 AgentResultEvent 中提取）
        List<String> assistantTexts = new ArrayList<>();
        // 收集消息ID（用于关联文件产物）
        AtomicReference<String> aiMessageId = new AtomicReference<>();

        return next.apply(input)
                // 拦截事件流，提取 AgentResultEvent 中的回复消息和消息ID
                .doOnNext(event -> {
                    if (event instanceof AgentResultEvent resultEvent) {
                        Msg result = resultEvent.getResult();
                        if (result == null) {
                            return;
                        }
                        // 本轮没答完（最常见的是命中 ASK 规则被暂停）：这段只是“让我调用工具”的中间输出，
                        // 既不写进产物正文，也不拿它的 messageId 去落库，否则产物会在工具调用阶段就出现在前端
                        GenerateReason reason = result.getGenerateReason();
                        if (reason != null && TURN_NOT_FINISHED.contains(reason)) {
                            log.info("[FileGenMiddleware] 本轮未产出最终回答(generateReason={})，不生成产物: sessionId={}",
                                    reason, ctx.getSessionId());
                            return;
                        }
                        String text = result.getTextContent();
                        if (text != null && !text.isBlank()) {
                            assistantTexts.add(text);
                        }
                        // 提取消息ID（从 Msg 的 id 字段）
                        aiMessageId.set(extractMessageId(result));
                    }
                })
                // 流结束后写入文件，并将产物地址作为 CustomEvent 发射给下游
                .concatWith(Flux.defer(() -> {
                    String messageId = aiMessageId.get();
                    // 没有最终回答就没有产物：以前这里会照写不误，落到 conversation_null.md 或一份只有用户提问的空文件
                    if (messageId == null || assistantTexts.isEmpty()) {
                        return Flux.empty();
                    }
                    Path filePath = writeConversationToFile(ctx, messageId, userTexts, assistantTexts);
                    if (filePath != null) {
                        // 持久化文件产物记录到数据库
                        persistFileRecord(ctx, filePath, messageId);

                        // 构建 FileInfo 对象
                        String fileName = filePath.getFileName().toString();
                        String fileUrl = filePath.toAbsolutePath().toString();
                        Long fileSize = null;
                        try {
                            fileSize = Files.size(filePath);
                        } catch (IOException e) {
                            System.err.println("[FileGenMiddleware] 读取文件大小失败：" + e.getMessage());
                        }

                        FileInfo fileInfo = FileInfo.builder()
                                .fileName(fileName)
                                .fileUrl(fileUrl)
                                .fileSize(fileSize)
                                .fileType("text/plain")
                                .messageId(messageId)
                                .build();
                        Map<String, Object> file = JsonUtil.fromJson(JsonUtil.toJson(fileInfo), new TypeReference<Map<String, Object>>() {
                        });
                        CustomEvent fileEvent = new CustomEvent(
                                EVENT_FILE_GENERATED,
                                file
                        );
                        return Flux.just(fileEvent);
                    }
                    return Flux.empty();
                }));
    }

    /**
     * 从 Msg 中提取消息ID。
     * 优先尝试 getId()，如果不存在则从 metadata 中获取。
     */
    private String extractMessageId(Msg msg) {
        // 尝试从 Msg 的 id 字段获取
        try {
            String id = msg.getId();
            if (id != null && !id.isBlank()) {
                return id;
            }
        } catch (Exception e) {
            // getId() 方法不存在，忽略
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
                                         String messageId,
                                         List<String> userTexts,
                                         List<String> assistantTexts) {
        try {
            Files.createDirectories(OUTPUT_DIR);

            String sessionId = ctx.getSessionId() != null ? ctx.getSessionId() : "default";
            String fileName = "conversation_" + messageId + ".md";
            Path filePath = OUTPUT_DIR.resolve(fileName);

            StringBuilder sb = new StringBuilder();
            sb.append("========== 对话记录 ==========\n");
            sb.append("会话ID: ").append(sessionId).append("\n");
            sb.append("用户ID: ").append(ctx.getUserId() != null ? ctx.getUserId() : "unknown").append("\n");
            sb.append("消息ID: ").append(messageId).append("\n");
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
    private void persistFileRecord(RuntimeContext ctx, Path filePath, String messageId) {
        try {
            String sessionId = ctx.getSessionId() != null ? ctx.getSessionId() : "default";
            Long userId = ctx.getUserId() != null ? Long.parseLong(ctx.getUserId()) : null;
            if (userId == null) {
                System.err.println("[FileGenMiddleware] 无法解析 userId，跳过持久化文件记录");
                return;
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

            messageFileManager.saveFileRecord(userId, sessionId, messageId, fileName, fileUrl, fileSize, "text/plain");
            System.out.println("[FileGenMiddleware] 文件产物记录已持久化: sessionId=" + sessionId
                    + ", messageId=" + messageId + ", fileName=" + fileName);
        } catch (Exception e) {
            // 持久化失败不影响本次对话，仅打印告警日志
            System.err.println("[FileGenMiddleware] 持久化文件产物记录失败（不影响本次对话）: " + e.getMessage());
        }
    }
}

