package com.moli.spring.ai.agents;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import java.util.Optional;

public class TestAgent2 {

    public static void main(String[] args) {
        ReactAgent reactAgent = MyReactAgent.get();

        try {
            Optional<OverAllState> invoke = reactAgent.invoke("帮我写一首诗");
            OverAllState overAllState = invoke.get();
            System.out.println(JSONUtil.toJsonPrettyStr(overAllState.data()));

        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }


    }
}
