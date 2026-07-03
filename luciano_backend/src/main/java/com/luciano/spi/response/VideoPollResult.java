package com.luciano.spi.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VideoPollResult {
    private TaskStatus status;
    private String outputUrl;
    private String localPath;
    private Integer durationMs;
    private String errorMsg;
}