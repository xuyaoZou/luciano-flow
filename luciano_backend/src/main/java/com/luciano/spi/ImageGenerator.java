package com.luciano.spi;

import com.luciano.spi.request.ImageGenerateRequest;
import com.luciano.spi.response.ImageGenerateResult;
import com.luciano.spi.response.ImagePollResult;

/**
 * 图片生成 SPI
 * 所有图片模型厂商实现此接口
 */
public interface ImageGenerator {

    String getProvider();

    ImageGenerateResult generate(ImageGenerateRequest request);

    ImagePollResult poll(String taskId);
}