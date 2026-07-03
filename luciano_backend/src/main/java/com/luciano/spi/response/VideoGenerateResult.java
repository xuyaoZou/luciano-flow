package com.luciano.spi.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VideoGenerateResult {
    private String taskId;
    private String provider;
    private String threadId;
    private String runId;
}