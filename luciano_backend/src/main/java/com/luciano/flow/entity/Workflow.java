package com.luciano.flow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 工作流实体
 * <p>
 * 参考设计文档 §4.2 Workflow 定义。
 * nodes/edges/variables 用 JSONB 存储，整体序列化/反序列化。
 */
@Data
@TableName(value = "workflows", autoResultMap = true)
public class Workflow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String thumbnailUrl;

    /** 模板分类: "video_generation", "character_consistency", ... */
    private String category;

    /** 是否为系统模板 */
    private Boolean isTemplate;

    /** 创建者 ID，模板则为 null */
    private Long userId;

    private Long projectId;

    /** 节点列表 JSON */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String nodes;

    /** 连线列表 JSON */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String edges;

    /** 全局变量 JSON */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String variables;

    /** 状态: draft / running / completed / failed */
    private String status;

    /** 版本号，模板更新时递增 */
    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 最近一次执行的节点结果（非持久化，由 Service 层填充） */
    @TableField(exist = false)
    private String lastExecutionResults;
}