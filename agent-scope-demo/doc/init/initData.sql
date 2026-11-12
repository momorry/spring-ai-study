create table m_user
(

    id              bigint primary key auto_increment comment '主键',
    login_acct      varchar(32)          not null comment '登录号',
    login_acct_type varchar(32)          not null comment '登录号类型：mobile-手机号,email-邮箱,custom-自定义',
    nick_name       varchar(32)          not null comment '用户昵称',
    user_name       varchar(32)          not null comment '姓名',
    password        varchar(128)         not null comment '密码（BCrypt 加密）',
    status          tinyint(1) default 0 not null comment '状态 1-生效 0-注销',
    create_by       varchar(32)          null comment '创建人',
    create_time     datetime             null comment '创建时间',
    update_by       varchar(32)          null comment '更新人',
    update_time     datetime             null comment '更新时间'
) comment '用户表';

create unique index uq_la on m_user(login_acct);

-- 初始演示账号：admin / 123456（密码为 BCrypt 加密存储）
insert into m_user (login_acct, login_acct_type, nick_name, user_name, password, status, create_by, create_time)
values ('admin', 'custom', '管理员', '管理员', '$2a$10$sjTiUOv5/pKNTYvu4uWESuceSS0yH3Vn8XQoJbACL0cQcuhpgwzsK', 1, 'system', now());


-- ============================================================
-- 增量迁移：若 m_user 表已存在（无 password 列），执行以下语句
-- ============================================================
-- ALTER TABLE m_user ADD COLUMN password varchar(128) NULL COMMENT '密码（BCrypt 加密）' AFTER user_name;
-- 为已有账号补默认密码 123456（BCrypt）后再将列改为 NOT NULL：
-- UPDATE m_user SET password = '$2a$10$sjTiUOv5/pKNTYvu4uWESuceSS0yH3Vn8XQoJbACL0cQcuhpgwzsK' WHERE password IS NULL;
-- ALTER TABLE m_user MODIFY COLUMN password varchar(128) NOT NULL COMMENT '密码（BCrypt 加密）';
-- 插入演示账号（若不存在）：
-- INSERT INTO m_user (login_acct, login_acct_type, nick_name, user_name, password, status, create_by, create_time)
-- SELECT 'admin', 'custom', '管理员', '管理员', '$2a$10$sjTiUOv5/pKNTYvu4uWESuceSS0yH3Vn8XQoJbACL0cQcuhpgwzsK', 1, 'system', now()
-- WHERE NOT EXISTS (SELECT 1 FROM m_user WHERE login_acct = 'admin');


-- ============================================================
-- 聊天会话表（历史会话列表）
-- 说明：本表只存「业务目录/索引」信息（标题、最后消息预览、轮数、时间），供前端历史会话侧边栏展示；
--      真正的对话上下文（记忆）由 AgentScope 的 agentscope_sessions 表按 (userId, sessionId) 持久化。
--      两表通过 session_id 关联：本表负责「看得见、能管理」，agentscope_sessions 负责「记得住、能续聊」。
-- ============================================================
create table m_chat_session
(
    id           bigint primary key auto_increment comment '主键',
    user_id      bigint                   not null comment '所属用户ID（关联 m_user.id）',
    session_id   varchar(64)              not null comment 'AgentScope 会话ID（与 agentscope_sessions 的 sessionId 一致）',
    title        varchar(128)             null comment '会话标题（默认取首条用户消息截断）',
    last_message varchar(512)             null comment '最后一条消息预览',
    round_count  int          default 0   not null comment '对话轮数（用户提问次数）',
    pinned       tinyint(1)   default 0   not null comment '是否置顶 1-是 0-否',
    deleted      tinyint(1)   default 0   not null comment '逻辑删除 1-已删除 0-未删除',
    create_by    varchar(32)              null comment '创建人',
    create_time  datetime                 null comment '创建时间',
    update_by    varchar(32)              null comment '更新人',
    update_time  datetime                 null comment '更新时间'
) comment '聊天会话表（历史会话列表）';

-- 同一用户下 session_id 唯一（upsert 依据）
create unique index uq_cs_user_session on m_chat_session (user_id, session_id);
-- 按用户查列表
create index idx_cs_user on m_chat_session (user_id);


-- ============================================================
-- 消息文件产物关联表
-- 说明：记录每次对话产生的文件产物，关联到具体的消息（messageId）；
--      加载历史消息时，根据 messageId 查询并附加文件信息到对应的 assistant 消息。
-- ============================================================
create table m_chat_message_file
(
    id           bigint primary key auto_increment comment '主键',
    user_id      bigint                   not null comment '所属用户ID（关联 m_user.id）',
    session_id   varchar(64)              not null comment 'AgentScope 会话ID',
    message_id   varchar(64)              not null comment '消息ID（关联消息的 id 字段）',
    file_name    varchar(256)             not null comment '文件名称（含扩展名）',
    file_url     varchar(512)             not null comment '文件访问地址（相对路径或完整 URL）',
    file_size    bigint                   null comment '文件大小（字节）',
    file_type    varchar(64)              null comment '文件类型 / MIME（如 text/plain）',
    create_by    varchar(32)              null comment '创建人',
    create_time  datetime                 null comment '创建时间',
    update_by    varchar(32)              null comment '更新人',
    update_time  datetime                 null comment '更新时间'
) comment '消息文件产物关联表';

-- 按消息ID查文件（精确匹配）
create unique index idx_mf_message on m_chat_message_file (message_id);
-- 按会话查文件
create index idx_mf_session on m_chat_message_file (session_id);
-- 按用户查文件
create index idx_mf_user on m_chat_message_file (user_id);


-- ============================================================
-- 模型厂商配置表（可动态切换的大模型清单）
-- 说明：一行 = 一个「前端可选、服务端可动态构建」的模型；同一厂商（vendor_code）可提供多行模型，
--      厂商级信息（base_url / api_key / formatter）按行冗余存储，同一厂商改配置时按 vendor_code 批量 UPDATE 即可。
--      Agent 本身仍是单例（模型是 final 字段不可换），运行期由 DynamicModelMiddleware 依据请求携带的
--      modelName 换取本表配置，路由到对应的 OpenAI 兼容模型实例；查不到或字段为空则回退 application-local.yml 的 llm.*。
--      api_key 为敏感信息：对外接口只返回展示字段，不下发该列。
-- ============================================================
create table m_llm_model_config
(
    id            bigint primary key auto_increment comment '主键',
    vendor_code   varchar(64)            not null comment '供应商编码：internal-gateway/deepseek/dashscope/kimi/zhipu 等',
    vendor_name   varchar(64)            not null comment '供应商展示名称：如 内部网关、阿里云百炼',
    model_name    varchar(128)           not null comment '模型标识：调用接口时传入的 model 值',
    display_name  varchar(128)           not null comment '模型展示名称：前端下拉框显示文案',
    base_url      varchar(256)           not null comment 'OpenAI 兼容接口地址（不含 /chat/completions）',
    api_key       varchar(256)           not null comment '访问密钥',
    endpoint_path varchar(128)           null comment '自定义接口路径（可选，默认 /chat/completions）',
    formatter     varchar(32) default 'openai' not null comment '消息格式化器：openai/deepseek/kimi/glm/minimax',
    is_default    tinyint(1)  default 0  not null comment '是否默认模型 1-是 0-否（全局至多一行为 1）',
    enabled       tinyint(1)  default 1  not null comment '是否启用 1-启用 0-停用（停用不出现在下拉列表）',
    sort_no       int         default 0  not null comment '排序号（大者靠前）',
    remark        varchar(256)           null comment '备注',
    deleted       tinyint(1)  default 0  not null comment '逻辑删除 1-已删除 0-未删除',
    create_by     varchar(32)            null comment '创建人',
    create_time   datetime               null comment '创建时间',
    update_by     varchar(32)            null comment '更新人',
    update_time   datetime               null comment '更新时间'
) comment '模型厂商配置表（可动态切换的大模型清单）';

-- 模型标识全局唯一（动态路由依据）
create unique index uq_lmc_model_name on m_llm_model_config (model_name);
-- 按厂商查模型
create index idx_lmc_vendor on m_llm_model_config (vendor_code);

-- 初始数据：与 application-local.yml 中 llm.* 保持一致的默认模型（formatter 沿用 openai，行为与改造前完全相同）
insert into m_llm_model_config (vendor_code, vendor_name, model_name, display_name, base_url, api_key,
                                formatter, is_default, enabled, sort_no, remark, create_by, create_time)
values ('internal-gateway', '内部网关', 'external-deepseek-v4-flash', 'DeepSeek V4 Flash',
        '1', '1',
        'openai', 1, 1, 100, '默认模型', 'system', now());

-- 公共厂商示例（需先补齐 api_key 再把 enabled 改为 1，否则下拉列表会出现不可用模型）
-- insert into m_llm_model_config (vendor_code, vendor_name, model_name, display_name, base_url, api_key, formatter, is_default, enabled, sort_no, create_by, create_time)
-- values ('dashscope', '阿里云百炼', 'qwen-plus', '通义千问 Plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-请替换', 'openai', 0, 0, 90, 'system', now());
-- insert into m_llm_model_config (vendor_code, vendor_name, model_name, display_name, base_url, api_key, formatter, is_default, enabled, sort_no, create_by, create_time)
-- values ('deepseek', 'DeepSeek 官方', 'deepseek-chat', 'DeepSeek Chat', 'https://api.deepseek.com/v1', 'sk-请替换', 'deepseek', 0, 0, 80, 'system', now());
-- insert into m_llm_model_config (vendor_code, vendor_name, model_name, display_name, base_url, api_key, formatter, is_default, enabled, sort_no, create_by, create_time)
-- values ('kimi', '月之暗面', 'moonshot-v1-128k', 'Kimi 128K', 'https://api.moonshot.cn/v1', 'sk-请替换', 'kimi', 0, 0, 70, 'system', now());
-- insert into m_llm_model_config (vendor_code, vendor_name, model_name, display_name, base_url, api_key, formatter, is_default, enabled, sort_no, create_by, create_time)
-- values ('zhipu', '智谱清言', 'glm-4-plus', 'GLM-4 Plus', 'https://open.bigmodel.cn/api/paas/v4', '请替换', 'glm', 0, 0, 60, 'system', now());


