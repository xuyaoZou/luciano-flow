package com.luciano.service;

import com.luciano.spi.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model Gateway - 模型调用的统一入口
 * 根据步骤级模型配置，路由到对应的 SPI 实现
 * 
 * SPI 实现通过 getProvider() 方法声明自己的 provider 名，
 * Gateway 在初始化时自动注册。
 */
@Service
public class ModelGateway {

    private final Map<String, VideoGenerator> videoGenerators = new HashMap<>();
    private final Map<String, ImageGenerator> imageGenerators = new HashMap<>();
    private final Map<String, TtsGenerator> ttsGenerators = new HashMap<>();
    private final Map<String, AgentGenerator> agentGenerators = new HashMap<>();

    private final List<VideoGenerator> videoGeneratorList;
    private final List<ImageGenerator> imageGeneratorList;
    private final List<TtsGenerator> ttsGeneratorList;
    private final List<AgentGenerator> agentGeneratorList;

    public ModelGateway(List<VideoGenerator> videoGeneratorList,
                        List<ImageGenerator> imageGeneratorList,
                        List<TtsGenerator> ttsGeneratorList,
                        List<AgentGenerator> agentGeneratorList) {
        this.videoGeneratorList = videoGeneratorList;
        this.imageGeneratorList = imageGeneratorList;
        this.ttsGeneratorList = ttsGeneratorList;
        this.agentGeneratorList = agentGeneratorList;
    }

    @PostConstruct
    public void init() {
        videoGeneratorList.forEach(g -> videoGenerators.put(g.getProvider(), g));
        imageGeneratorList.forEach(g -> imageGenerators.put(g.getProvider(), g));
        ttsGeneratorList.forEach(g -> ttsGenerators.put(g.getProvider(), g));
        agentGeneratorList.forEach(g -> agentGenerators.put(g.getProvider(), g));

        // 日志
        System.out.println("[ModelGateway] Registered VideoGenerators: " + videoGenerators.keySet());
        System.out.println("[ModelGateway] Registered ImageGenerators: " + imageGenerators.keySet());
        System.out.println("[ModelGateway] Registered TtsGenerators: " + ttsGenerators.keySet());
        System.out.println("[ModelGateway] Registered AgentGenerators: " + agentGenerators.keySet());
    }

    public VideoGenerator getVideoGenerator(String provider) {
        VideoGenerator generator = videoGenerators.get(provider);
        if (generator == null) {
            throw new IllegalArgumentException("Unknown video provider: " + provider);
        }
        return generator;
    }

    public ImageGenerator getImageGenerator(String provider) {
        ImageGenerator generator = imageGenerators.get(provider);
        if (generator == null) {
            throw new IllegalArgumentException("Unknown image provider: " + provider);
        }
        return generator;
    }

    public TtsGenerator getTtsGenerator(String provider) {
        TtsGenerator generator = ttsGenerators.get(provider);
        if (generator == null) {
            throw new IllegalArgumentException("Unknown tts provider: " + provider);
        }
        return generator;
    }

    public AgentGenerator getAgentGenerator(String provider) {
        AgentGenerator generator = agentGenerators.get(provider);
        if (generator == null) {
            throw new IllegalArgumentException("Unknown agent provider: " + provider);
        }
        return generator;
    }
}