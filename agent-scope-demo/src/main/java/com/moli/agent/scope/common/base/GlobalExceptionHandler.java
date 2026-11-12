package com.moli.agent.scope.common.base;

import com.moli.agent.scope.common.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 全局异常处理器：把各类异常统一收敛成 {@link ApiResult} JSON 响应，让前端能拿到可读的失败原因。
 *
 * <h3>为什么一律「直接写 HttpServletResponse」而不 return ApiResult</h3>
 * 这是为了兼容 SSE 接口（{@code POST /agent/conversation} 声明了
 * {@code produces = MediaType.TEXT_EVENT_STREAM_VALUE}）。若 {@code @ExceptionHandler} 返回对象，
 * Spring 仍会为返回值走一遍消息转换器的内容协商：
 * <ul>
 *   <li>请求头 {@code Accept: text/event-stream}；</li>
 *   <li>原 mapping 的 {@code produces} 还会留在 {@code PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE} 里。</li>
 * </ul>
 * 两者都容不下 {@code application/json}，于是 Jackson 选不上，轻则 406、重则响应体为空 ——
 * 前端只看到一个没有原因的 5xx。
 * <p>这里显式 {@code setContentType(application/json)} 后直接写 writer，完全绕开协商机制；
 * 与 {@code LoginInterceptor#writeUnauthorized} 用的是同一套写法（该路径线上已验证可行）。
 *
 * <h3>与流式异常分工</h3>
 * 本处理器只能覆盖「SSE 尚未建连」的失败（参数校验、装配阶段抛错等）。
 * 一旦流已建立（模型 403 额度用完、超时等发生在订阅之后），响应头早已发出，改不了状态码，
 * 那类异常由 {@code SimpleHarnessActAgent#resumeFromError} 转成 {@code ERROR} 事件下发。
 * 因此每个 handler 都会先判断 {@code response.isCommitted()}，已提交就只记日志。
 *
 * @author moli
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：message 本就是写给用户看的，原样回传，不动文案、不泄露堆栈。
     * <p>HTTP 状态码在下游各 handler 里按场景写定（不在 BizException 上搭字段）：
     * 业务失败对应 500，保持 HTTP 语义诚实（不用 200 承载错误），前端 fetch / axios 才能走非 2xx 分支。
     */
    @ExceptionHandler(BizException.class)
    public void handleBizException(HttpServletRequest request, HttpServletResponse response,
                                   BizException e) throws IOException {
        log.warn("业务异常: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        writeApiResult(request, response, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ApiResult.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 请求本身不合法：请求体不是合法 JSON、缺参数、参数类型不匹配。
     */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public void handleBadRequest(HttpServletRequest request, HttpServletResponse response,
                                 Exception e) throws IOException {
        log.warn("请求参数异常: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        writeApiResult(request, response, HttpStatus.BAD_REQUEST.value(),
                ApiResult.fail(HttpStatus.BAD_REQUEST.value(), "请求参数不合法，请检查输入内容后重试"));
    }

    /**
     * 地址不存在 / 方法不支持：避免被兜底 handler 误报成 500（前端静态资源 404 不该变成服务器错误）。
     */
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public void handleNotFoundOrMethod(HttpServletRequest request, HttpServletResponse response,
                                       Exception e) throws IOException {
        log.warn("请求无法处理: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        writeApiResult(request, response, HttpStatus.NOT_FOUND.value(),
                ApiResult.fail(HttpStatus.NOT_FOUND.value(), "请求的接口不存在或已下线"));
    }

    /**
     * 兜底：所有未被上面捕获的异常。
     * <p>日志留完整堆栈，响应只给异常类名 —— 便于用户截图反馈时定位，又不把 SQL / 内部路径漏到浏览器。
     * 若是对外服务，把类名也去掉即可。
     */
    @ExceptionHandler(Exception.class)
    public void handleException(HttpServletRequest request, HttpServletResponse response,
                                Exception e) throws IOException {
        log.error("未预期的异常: {} {}", request.getMethod(), request.getRequestURI(), e);
        String detail = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
        writeApiResult(request, response, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ApiResult.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "服务器内部错误（" + e.getClass().getSimpleName() + "）："
                                + StringUtils.abbreviate(detail, 200)));
    }

    /**
     * 手动写出统一响应，绕开内容协商（原因见类注释）。
     */
    private void writeApiResult(HttpServletRequest request, HttpServletResponse response,
                                int httpStatus, ApiResult<Void> body) throws IOException {
        if (response.isCommitted()) {
            // 多见于 SSE 已建连后才抛异常：此时状态码和响应头都改不了，只能留日志
            log.warn("响应已提交，无法写出统一错误信息（流式接口请在流内以 ERROR 事件下发）: {} -> {}",
                    request.getRequestURI(), body.getMessage());
            return;
        }
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 复用全局唯一的 ObjectMapper（由 JsonUtil 统一管理），保证与 Spring MVC 序列化配置一致
        response.getWriter().write(JsonUtil.toJson(body));
        response.getWriter().flush();
    }

}
