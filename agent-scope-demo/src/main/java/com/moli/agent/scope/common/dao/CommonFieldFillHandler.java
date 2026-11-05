package com.moli.agent.scope.common.dao;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 通用字段填充处理器：配合实体上的 @TableField(fill = ...) 注解（由 CodeGenerator 自动填充策略生成），
 * 在 INSERT / UPDATE 时自动写入以下字段：
 * <ul>
 *     <li>deleted    - 逻辑删除位，INSERT 时填 0</li>
 *     <li>create_by  - 创建人，INSERT 时填当前操作人</li>
 *     <li>create_time- 创建时间，INSERT 时填当前时间</li>
 *     <li>update_by  - 更新人，INSERT / UPDATE 时填当前操作人</li>
 *     <li>update_time- 更新时间，INSERT / UPDATE 时填当前时间</li>
 * </ul>
 * 字段类型约定：create_by / update_by 为 String，create_time / update_time 为 LocalDateTime，deleted 为 Integer。
 * 采用 strict 填充：实体中不存在对应字段或类型不匹配时自动跳过，不会报错。
 *
 * @author moli
 * @since 2026-11-03
 */
@Component
public class CommonFieldFillHandler implements MetaObjectHandler {

    /** 默认操作人：未接入登录用户上下文时使用 */
    private static final String DEFAULT_OPERATOR = "system";

    /** 逻辑删除默认值：未删除 */
    private static final Integer NOT_DELETED = 0;

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "deleted", Integer.class, NOT_DELETED);
        this.strictInsertFill(metaObject, "createBy", String.class, currentOperator());
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateBy", String.class, currentOperator());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateBy", String.class, currentOperator());
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 获取当前操作人：后续接入登录用户上下文（如 Session / UserContext）后在此替换，
     * 当前返回默认操作人
     */
    private String currentOperator() {
        return DEFAULT_OPERATOR;
    }
}
