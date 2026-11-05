package com.moli.agent.scope.common.dao.manager.impl;

import com.moli.agent.scope.common.dao.entity.MUser;
import com.moli.agent.scope.common.dao.mapper.MUserMapper;
import com.moli.agent.scope.common.dao.manager.MUserManager;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author moli
 * @since 2026-11-03
 */
@Service
public class MUserManagerImpl extends ServiceImpl<MUserMapper, MUser> implements MUserManager {

}
