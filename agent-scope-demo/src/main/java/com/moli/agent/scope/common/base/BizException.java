package com.moli.agent.scope.common.base;

import lombok.Getter;

/**
 * 业务异常：{@code message} 本身就是给最终用户看的原因说明。
 * <p>抛出后由 {@link GlobalExceptionHandler} 捕获，装进 {@link ApiResult} 回传，
 * 前端直接展示即可。HTTP 状态码不在本异常里携带，由 handler 按实际场景决定（当前统一 500）。
 * <p>使用约定：
 * <ul>
 *   <li>只有「原因用户能理解、且需要看到」的失败才抛本异常，message 用第二人称写清下一步动作
 *       （如「请刷新页面后重试」），不要塞 SQL、堆栈、内部 ID 之外的技术细节；</li>
 *   <li>系统内部错误（NPE、DB 挂了、第三方 SDK 异常）不要包装成本异常往外漏细节，
 *       直接抛出让兜底 handler 记日志、给用户通用文案；</li>
 *   <li>排查用的细节（sessionId、消息 ID、上下文条数等）留在抛出点的日志里。</li>
 * </ul>
 *
 * @author moli
 */
@Getter
public class BizException extends RuntimeException {

    /**
     * 业务响应码，回传在 {@link ApiResult#getCode()} 中（前端按它区分成功/失败）。
     * 默认 500，与 {@link EnumErrorCode#FAIL} 一致。
     * <p>只是给前端看的业务码，与 HTTP 状态码无关（后者由 {@link GlobalExceptionHandler} 写定）。
     */
    private final int code;

    public BizException(String message) {
        this(EnumErrorCode.FAIL.getCode(), message);
    }

    public BizException(String message, Throwable cause) {
        this(EnumErrorCode.FAIL.getCode(), message, cause);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

}
