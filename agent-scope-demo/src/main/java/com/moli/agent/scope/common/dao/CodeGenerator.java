package com.moli.agent.scope.common.dao;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.generator.IFill;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;
import com.baomidou.mybatisplus.generator.fill.Column;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MyBatis-Plus 代码生成器
 * <p>
 * 直接运行 main 方法，即可为指定表生成 Entity / Mapper / MapperXML / Service / Controller 代码。
 * DAO 层（entity、mapper）统一生成到 com.moli.agent.scope.common.dao 包下。
 *
 * @author moli
 * @since 2026-11-03
 */
public class CodeGenerator {

    /** 数据库连接信息（与 application-local.yml 保持一致） */
    private static final String DB_URL = "jdbc:mysql://10.129.67.77:4000/iresearch_aid?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&allowMultiQueries=true&useCursorFetch=true&rewriteBatchedStatements=true";
    private static final String DB_USERNAME = "iresearch_aid";
    private static final String DB_PASSWORD = "iresearch_aid@2024";

    /** 需要生成代码的表名，支持多张表 */
    private static final String[] TABLES = {"m_user"};

    /** 作者 */
    private static final String AUTHOR = "moli";

    public static void main(String[] args) {
        String moduleDir = resolveModuleDir();

        FastAutoGenerator.create(DB_URL, DB_USERNAME, DB_PASSWORD)
                // 全局配置
                .globalConfig(builder -> builder
                        .author(AUTHOR)
                        .outputDir(moduleDir + "/src/main/java")
                        .disableOpenDir())
                // 包配置：DAO 层统一放在 common.dao 下
                .packageConfig(builder -> builder
                        .parent("com.moli.agent.scope")
                        .entity("common.dao.entity")
                        .mapper("common.dao.mapper")
                        .service("common.dao.manager")
                        .serviceImpl("common.dao.manager.impl")
                        .controller("controller")
                        .pathInfo(Collections.singletonMap(OutputFile.xml,
                                moduleDir + "/src/main/resources/mapper")))
                // 策略配置
                .strategyConfig(builder -> {
                    // 需要生成的表（如需过滤表前缀可追加 addTablePrefix("t_")）
                    builder.addInclude(TABLES);
                    // 字段自动填充策略：逻辑删除位、创建/更新人与时间
                    // （3.5.17 中 TableFill 已由 fill.Column 替代）
                    List<IFill> tableFills = new ArrayList<>();
                    Column tableFill1 = new Column("deleted", FieldFill.INSERT);
                    Column tableFill2 = new Column("create_by", FieldFill.INSERT);
                    Column tableFill3 = new Column("create_time", FieldFill.INSERT);
                    Column tableFill4 = new Column("update_by", FieldFill.INSERT_UPDATE);
                    Column tableFill5 = new Column("update_time", FieldFill.INSERT_UPDATE);
                    tableFills.add(tableFill1);
                    tableFills.add(tableFill2);
                    tableFills.add(tableFill3);
                    tableFills.add(tableFill4);
                    tableFills.add(tableFill5);
                    // 实体：启用 Lombok、允许覆盖旧文件、自增主键、自动填充
                    builder.entityBuilder()
                            .enableLombok()
                            .enableFileOverride()
                            .idType(IdType.AUTO)
                            .addTableFills(tableFills);
                    // Mapper：@Mapper 注解 + 基础 ResultMap / ColumnList
                    builder.mapperBuilder()
                            .enableMapperAnnotation()
                            .enableBaseResultMap()
                            .enableBaseColumnList();
                    // Service：接口 XxxService + 实现 XxxServiceImpl
                    builder.serviceBuilder()
                            .formatServiceFileName("%sManager")
                            .formatServiceImplFileName("%sManagerImpl");
                    // Controller：@RestController 风格
                    builder.controllerBuilder()
                            .enableRestStyle()
                            .formatFileName("%sController");
                })
                // 模板配置：Mapper 接口使用自定义模板（带 @Repository 注解）
                .templateConfig(builder -> builder.mapper("/template/mapper.java.vm"))
                // 模板引擎：Velocity（.vm 模板）
                .templateEngine(new VelocityTemplateEngine())
                .execute();
    }

    /**
     * 解析 agent-scope-demo 模块目录，
     * 兼容「项目根目录」与「模块目录」两种运行工作目录
     */
    private static String resolveModuleDir() {
        String userDir = System.getProperty("user.dir");
        if (userDir.endsWith("agent-scope-demo")) {
            return userDir;
        }
        return userDir + File.separator + "agent-scope-demo";
    }
}
