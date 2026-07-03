package com.luciano.spi.request;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class TtsGenerateRequest {
    private String text;
    private String voiceId;
    private String language;
    private Map<String, Object> extra;
}