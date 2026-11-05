package com.moli.agent.scope.common.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moli.agent.scope.common.dao.entity.MChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 聊天会话表（历史会话列表） Mapper 接口
 * </p>
 *
 * @author moli
 * @since 2026-11-03
 */
@Mapper
public interface MChatSessionMapper extends BaseMapper<MChatSession> {

}
