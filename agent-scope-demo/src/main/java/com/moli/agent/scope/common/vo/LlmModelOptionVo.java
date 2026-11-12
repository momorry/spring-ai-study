package com.moli.agent.scope.common.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 可选模型（前端聊天框模型下拉框的一项）。
 * <p>只暴露展示字段，baseUrl / apiKey 等接入配置绝不下发前端。
 */
@Data
@Builder
public class LlmModelOptionVo {

    /** 模型标识：发送对话请求时回传给服务端，作为动态切换模型的依据 */
    private String modelName;

    /** 展示名称（下拉框主文案） */
    private String displayName;

    /** 供应商编码 */
    private String vendorCode;

    /** 供应商展示名称（下拉框按厂商分组 + 副标题） */
    private String vendorName;

    /** 是否为默认模型 */
    private Boolean isDefault;
}
