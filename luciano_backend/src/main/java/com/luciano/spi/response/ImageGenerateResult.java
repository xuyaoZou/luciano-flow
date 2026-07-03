package com.luciano.spi.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImageGenerateResult {
    private String taskId;
    private String provider;
}