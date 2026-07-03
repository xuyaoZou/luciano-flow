package com.luciano.spi.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentSubmitResult {
    private String threadId;
    private String runId;
    private String provider;
}