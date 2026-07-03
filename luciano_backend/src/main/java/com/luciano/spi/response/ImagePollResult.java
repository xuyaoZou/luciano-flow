package com.luciano.spi.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImagePollResult {
    private TaskStatus status;
    private String outputUrl;
    private String localPath;
    private String errorMsg;
}