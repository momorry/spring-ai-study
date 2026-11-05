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
-- 说明：记录每次对话产生的文件产物，关联到具体的回复（replyId）；
--      加载历史消息时，根据 replyId 查询并附加文件信息到对应的 assistant 消息。
-- ============================================================
create table m_chat_message_file
(
    id           bigint primary key auto_increment comment '主键',
    user_id      bigint                   not null comment '所属用户ID（关联 m_user.id）',
    session_id   varchar(64)              not null comment 'AgentScope 会话ID',
    reply_id     varchar(64)              not null comment '回复ID（关联一次具体的 AI 回复）',
    file_name    varchar(256)             not null comment '文件名称（含扩展名）',
    file_url     varchar(512)             not null comment '文件访问地址（相对路径或完整 URL）',
    file_size    bigint                   null comment '文件大小（字节）',
    file_type    varchar(64)              null comment '文件类型 / MIME（如 text/plain）',
    create_by    varchar(32)              null comment '创建人',
    create_time  datetime                 null comment '创建时间',
    update_by    varchar(32)              null comment '更新人',
    update_time  datetime                 null comment '更新时间'
) comment '消息文件产物关联表';

-- 按回复ID查文件（精确匹配）
create unique index idx_mf_reply on m_chat_message_file (reply_id);
-- 按会话查文件
create index idx_mf_session on m_chat_message_file (session_id);
-- 按用户查文件
create index idx_mf_user on m_chat_message_file (user_id);


