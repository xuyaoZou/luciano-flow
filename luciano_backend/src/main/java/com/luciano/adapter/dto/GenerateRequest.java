package com.luciano.adapter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 提交生成任务请求
 */
@Data
public class GenerateRequest {

    /** 适配器 ID（如 "kling", "seedance"） */
    @NotBlank(message = "adapterId 不能为空")
    private String adapterId;

    /** 能力标识（如 "text_to_video", "first_last_frame"） */
    @NotBlank(message = "capability 不能为空")
    private String capability;

    /** 生成参数（根据 CapabilitySchema 填写） */
    private Map<String, Object> params;

    /** 所属项目 ID（用于关联 media_assets） */
    private Object projectId;

    /** 获取 projectId 为 Long */
    public Long getProjectIdAsLong() {
        if (projectId == null) return null;
        if (projectId instanceof Number) return ((Number) projectId).longValue();
        if (projectId instanceof String) {
            try { return Long.parseLong((String) projectId); } catch (NumberFormatException e) { return null; }
        }
        // 如果是 Map/对象，忽略
        return null;
    }
}