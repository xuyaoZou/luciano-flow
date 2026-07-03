package com.luciano.spi.request;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ImageGenerateRequest {
    private String prompt;
    private String negativePrompt;
    private String aspectRatio;
    private List<String> referenceUrls;
    private Map<String, Object> extra;
}