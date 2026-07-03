package com.luciano.spi;

import com.luciano.spi.request.TtsGenerateRequest;
import com.luciano.spi.response.TtsGenerateResult;

/**
 * TTS 生成 SPI
 * 所有语音合成厂商实现此接口
 */
public interface TtsGenerator {

    String getProvider();

    TtsGenerateResult generate(TtsGenerateRequest request);
}