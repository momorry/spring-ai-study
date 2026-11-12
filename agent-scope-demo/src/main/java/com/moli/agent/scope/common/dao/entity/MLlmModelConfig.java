package com.moli.agent.scope.common.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模型厂商配置表（可动态切换的大模型清单）
 * </p>
 * 一行 = 一个「前端可选、服务端可动态构建」的模型；同一厂商（vendor_code）可提供多行模型，
 * 厂商级信息（base_url / api_key / formatter）按行冗余存储。
 * <p>
 * Agent 为单例且其 model 字段不可变，运行期由 DynamicModelMiddleware 按请求携带的 modelName
 * 换取本表配置，路由到对应的 OpenAI 兼容模型实例；查不到或为空则回退 application-local.yml 的 llm.*。
 *
 * @author moli
 * @since 2026-11-10
 */
@Getter
@Setter
@ToString(exclude = "apiKey")
@TableName("m_llm_model_config")
public class MLlmModelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 供应商编码（internal-gateway/deepseek/dashscope/kimi/zhipu 等）
     */
    private String vendorCode;

    /**
     * 供应商展示名称（前端下拉分组标题）
     */
    private String vendorName;

    /**
     * 模型标识：调用接口时传入的 model 值，全局唯一，也是前端传给服务端的切换依据
     */
    private String modelName;

    /**
     * 模型展示名称（前端下拉显示文案）
     */
    private String displayName;

    /**
     * OpenAI 兼容接口地址（不含 /chat/completions）
     */
    private String baseUrl;

    /**
     * 访问密钥（敏感信息，不允许下发给前端）
     */
    private String apiKey;

    /**
     * 自定义接口路径（可选，默认 /chat/completions）
     */
    private String endpointPath;

    /**
     * 消息格式化器：openai/deepseek/kimi/glm/minimax
     */
    private String formatter;

    /**
     * 是否默认模型 1-是 0-否（全局至多一行为 1）
     */
    @TableField("is_default")
    private Boolean isDefault;

    /**
     * 是否启用 1-启用 0-停用（停用不出现在下拉列表）
     */
    private Boolean enabled;

    /**
     * 排序号（大者靠前）
     */
    private Integer sortNo;

    /**
     * 备注
     */
    private String remark;

    /**
     * 逻辑删除 1-已删除 0-未删除
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

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
