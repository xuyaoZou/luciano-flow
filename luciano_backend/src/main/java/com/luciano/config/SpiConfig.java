package com.luciano.config;

import com.luciano.spi.*;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SPI 注册配置
 * 通过 Spring 自动注入所有 VideoGenerator/ImageGenerator/TtsGenerator/AgentGenerator 实现
 * 新增厂商只需添加实现类 + @Service 注解，无需修改此类
 */
@Configuration
public class SpiConfig {

    /**
     * 根据 SPI 接口列表构建 provider -> implementation 映射
     */
    public static <T> Map<String, T> buildProviderMap(List<T> implementations) {
        // 由 Spring 自动注入，此处为逻辑说明
        // 实际通过 @Autowired Map<String, VideoGenerator> 实现
        Map<String, T> map = new HashMap<>();
        for (T impl : implementations) {
            // 每个 SPI 实现类的 getProvider() 返回唯一标识
        }
        return map;
    }
}