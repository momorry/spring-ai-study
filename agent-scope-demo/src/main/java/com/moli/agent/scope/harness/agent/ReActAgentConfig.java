package com.moli.agent.scope.harness.agent;

import com.moli.agent.scope.harness.middleware.FileGenMiddleware;
import com.moli.agent.scope.harness.model.ModelCreator;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.middleware.MiddlewareBase;
import org.apache.commons.compress.utils.Lists;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ReActAgentConfig {


    @Bean
    public ReActAgent reActAgent(ModelCreator modelCreator) {
        List<MiddlewareBase> middlewareBases = Lists.newArrayList();
        middlewareBases.add(new FileGenMiddleware());
        return ReActAgent.builder()
                .model(modelCreator.streamModel())
                .sysPrompt("你是一个聪明可爱的智能机器人，可以回答用户的各种问题！你的名字叫青梧。")
                .name("qing wu")
                .middlewares(middlewareBases)
                .build();
    }
}
