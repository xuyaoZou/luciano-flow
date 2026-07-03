package com.luciano.spi.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TtsGenerateResult {
    private String taskId;
    private String provider;
    private String audioUrl;
}