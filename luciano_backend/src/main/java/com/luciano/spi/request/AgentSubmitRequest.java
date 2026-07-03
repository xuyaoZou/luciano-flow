package com.luciano.spi.request;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AgentSubmitRequest {
    private String prompt;
    private String threadId;
    private List<String> assetUrls;
    private Map<String, Object> extra;
}