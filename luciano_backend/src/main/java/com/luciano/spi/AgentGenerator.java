package com.luciano.spi;

import com.luciano.spi.request.AgentSubmitRequest;
import com.luciano.spi.response.AgentSubmitResult;
import com.luciano.spi.response.AgentPollResult;

/**
 * Agent 生成 SPI（直出模式）
 * 对接各厂商的 Agent API
 */
public interface AgentGenerator {

    String getProvider();

    AgentSubmitResult submit(AgentSubmitRequest request);

    AgentPollResult poll(String threadId, String runId);
}