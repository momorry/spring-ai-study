package com.moli.agent.scope.middleware;

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
 * 将用户输入和 AI 回复追加写入一个 TXT 文件作为产物输出。
 */
public class FileGenMiddleware implements MiddlewareBase {

    /** 产物输出目录，默认输出到项目根目录下的 output 文件夹 */
    private static final Path OUTPUT_DIR = Paths.get("./output");

    private static final DateTimeFormatter FILE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** CustomEvent 的 name 标识：产物文件已生成 */
    public static final String EVENT_FILE_GENERATED = "file_generated";

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

        return next.apply(input)
                // 拦截事件流，提取 AgentResultEvent 中的回复消息
                .doOnNext(event -> {
                    if (event instanceof AgentResultEvent resultEvent) {
                        Msg result = resultEvent.getResult();
                        if (result != null) {
                            String text = result.getTextContent();
                            if (text != null && !text.isBlank()) {
                                assistantTexts.add(text);
                            }
                        }
                    }
                })
                // 流结束后写入文件，并将产物地址作为 CustomEvent 发射给下游
                .concatWith(Flux.defer(() -> {
                    Path filePath = writeConversationToFile(ctx, userTexts, assistantTexts);
                    if (filePath != null) {
                        CustomEvent fileEvent = new CustomEvent(
                                EVENT_FILE_GENERATED,
                                Map.of(
                                        "filePath", filePath.toAbsolutePath().toString(),
                                        "sessionId", ctx.getSessionId() != null ? ctx.getSessionId() : "default",
                                        "userId", ctx.getUserId() != null ? ctx.getUserId() : "unknown"
                                )
                        );
                        return Flux.just(fileEvent);
                    }
                    return Flux.empty();
                }));
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
}

