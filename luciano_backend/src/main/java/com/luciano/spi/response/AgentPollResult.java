package com.luciano.spi.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AgentPollResult {
    private TaskStatus status;
    private Map<String, Object> artifacts;
    private String errorMsg;
}