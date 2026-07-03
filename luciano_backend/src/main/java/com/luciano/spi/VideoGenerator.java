package com.luciano.spi;

import com.luciano.spi.request.VideoGenerateRequest;
import com.luciano.spi.response.VideoGenerateResult;
import com.luciano.spi.response.VideoPollResult;

/**
 * 视频生成 SPI
 * 所有视频模型厂商实现此接口
 */
public interface VideoGenerator {

    String getProvider();

    VideoGenerateResult generate(VideoGenerateRequest request);

    VideoPollResult poll(String taskId);

    void cancel(String taskId);
}