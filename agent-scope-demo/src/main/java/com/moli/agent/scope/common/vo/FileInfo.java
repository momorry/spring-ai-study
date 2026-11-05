package com.moli.agent.scope.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件产物信息。
 * <p>用于在流式响应结束时携带生成的文件元数据（如对话记录导出、代码产物下载等）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {

    /** 文件名称（含扩展名），如 "conversation_1.txt" */
    private String fileName;

    /** 文件访问地址（相对路径或完整 URL） */
    private String fileUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型 / MIME，如 "text/plain"、"application/pdf" */
    private String fileType;
}
