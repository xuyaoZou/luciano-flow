package com.luciano.flow.template;

import com.luciano.flow.CapabilityPorts;
import com.luciano.flow.NodeType;
import com.luciano.flow.PortDef;
import com.luciano.flow.WorkflowNode;
import com.luciano.flow.WorkflowEdge;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流模板定义
 * <p>
 * 预置常用场景模板，用户从模板创建工作流后可自由修改。
 * nodes/edges 使用索引引用（0, 1, 2...），创建时由 Service 转为实际数据。
 */
@Data
@Builder
public class WorkflowTemplate {

    /** 模板 ID（用于 API 路由，非数据库 ID） */
    private String id;

    /** 显示名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 分类: video_generation / image_generation / ... */
    private String category;

    /** 图标 emoji */
    private String icon;

    /** 节点定义 */
    private List<TemplateNode> nodes;

    /** 连线定义 */
    private List<TemplateEdge> edges;

    // ==================== 节点/连线 内部类 ====================

    @Data
    @Builder
    public static class TemplateNode {
        /** 节点类型（Capability 名称或特殊节点类型） */
        private String type;
        /** 显示标签 */
        private String label;
        /** X 坐标 */
        private double x;
        /** Y 坐标 */
        private double y;
        /** 是否为特殊节点 */
        private boolean special;
        /** Capability 名称（capability 节点用） */
        private String capabilityName;
    }

    @Data
    @Builder
    public static class TemplateEdge {
        /** 源节点索引 */
        private int sourceIndex;
        /** 源端口名 */
        private String sourceSlot;
        /** 目标节点索引 */
        private int targetIndex;
        /** 目标端口名 */
        private String targetSlot;
    }

    // ==================== 预置模板 ====================

    /** 图生视频：上传图片 → 生成视频 */
    public static final WorkflowTemplate IMAGE_TO_VIDEO = WorkflowTemplate.builder()
            .id("image-to-video")
            .name("图生视频")
            .description("上传图片，AI 生成视频")
            .category("video_generation")
            .icon("🎬")
            .nodes(List.of(
                    TemplateNode.builder().type("ImageInput").label("参考图片").x(80).y(200).special(true).build(),
                    TemplateNode.builder().type("IMAGE_TO_VIDEO").label("图生视频").x(350).y(200).special(false).capabilityName("IMAGE_TO_VIDEO").build(),
                    TemplateNode.builder().type("VideoPreview").label("视频结果").x(620).y(200).special(true).build()
            ))
            .edges(List.of(
                    TemplateEdge.builder().sourceIndex(0).sourceSlot("image").targetIndex(1).targetSlot("image").build(),
                    TemplateEdge.builder().sourceIndex(1).sourceSlot("video").targetIndex(2).targetSlot("video").build()
            ))
            .build();

    /** 文生视频：输入提示词 → 生成视频 */
    public static final WorkflowTemplate TEXT_TO_VIDEO = WorkflowTemplate.builder()
            .id("text-to-video")
            .name("文生视频")
            .description("输入提示词，AI 生成视频")
            .category("video_generation")
            .icon("✍️")
            .nodes(List.of(
                    TemplateNode.builder().type("TextInput").label("提示词").x(80).y(200).special(true).build(),
                    TemplateNode.builder().type("TEXT_TO_VIDEO").label("文生视频").x(350).y(200).special(false).capabilityName("TEXT_TO_VIDEO").build(),
                    TemplateNode.builder().type("VideoPreview").label("视频结果").x(620).y(200).special(true).build()
            ))
            .edges(List.of(
                    TemplateEdge.builder().sourceIndex(0).sourceSlot("prompt").targetIndex(1).targetSlot("prompt").build(),
                    TemplateEdge.builder().sourceIndex(1).sourceSlot("video").targetIndex(2).targetSlot("video").build()
            ))
            .build();

    /** 文生图：输入提示词 → 生成图片 */
    public static final WorkflowTemplate TEXT_TO_IMAGE = WorkflowTemplate.builder()
            .id("text-to-image")
            .name("文生图")
            .description("输入提示词，AI 生成图片")
            .category("image_generation")
            .icon("🖼️")
            .nodes(List.of(
                    TemplateNode.builder().type("TextInput").label("提示词").x(80).y(200).special(true).build(),
                    TemplateNode.builder().type("TEXT_TO_IMAGE").label("文生图").x(350).y(200).special(false).capabilityName("TEXT_TO_IMAGE").build(),
                    TemplateNode.builder().type("ImagePreview").label("图片结果").x(620).y(200).special(true).build()
            ))
            .edges(List.of(
                    TemplateEdge.builder().sourceIndex(0).sourceSlot("prompt").targetIndex(1).targetSlot("prompt").build(),
                    TemplateEdge.builder().sourceIndex(1).sourceSlot("image").targetIndex(2).targetSlot("image").build()
            ))
            .build();

    /** 首尾帧：控制起止画面 → 生成视频 */
    public static final WorkflowTemplate FIRST_LAST_FRAME = WorkflowTemplate.builder()
            .id("first-last-frame")
            .name("首尾帧")
            .description("指定首帧和尾帧图片，控制视频起止画面")
            .category("video_generation")
            .icon("🎞️")
            .nodes(List.of(
                    TemplateNode.builder().type("ImageInput").label("首帧图片").x(80).y(120).special(true).build(),
                    TemplateNode.builder().type("ImageInput").label("尾帧图片").x(80).y(280).special(true).build(),
                    TemplateNode.builder().type("FIRST_LAST_FRAME").label("首尾帧").x(350).y(200).special(false).capabilityName("FIRST_LAST_FRAME").build(),
                    TemplateNode.builder().type("VideoPreview").label("视频结果").x(620).y(200).special(true).build()
            ))
            .edges(List.of(
                    TemplateEdge.builder().sourceIndex(0).sourceSlot("image").targetIndex(2).targetSlot("first_frame").build(),
                    TemplateEdge.builder().sourceIndex(1).sourceSlot("image").targetIndex(2).targetSlot("last_frame").build(),
                    TemplateEdge.builder().sourceIndex(2).sourceSlot("video").targetIndex(3).targetSlot("video").build()
            ))
            .build();

    /** 运镜控制：上传图片 → 运镜效果视频 */
    public static final WorkflowTemplate CAMERA_CONTROL = WorkflowTemplate.builder()
            .id("camera-control")
            .name("运镜效果")
            .description("上传图片，添加运镜效果生成视频")
            .category("video_generation")
            .icon("📷")
            .nodes(List.of(
                    TemplateNode.builder().type("ImageInput").label("参考图片").x(80).y(200).special(true).build(),
                    TemplateNode.builder().type("CAMERA_CONTROL").label("运镜").x(350).y(200).special(false).capabilityName("CAMERA_CONTROL").build(),
                    TemplateNode.builder().type("VideoPreview").label("视频结果").x(620).y(200).special(true).build()
            ))
            .edges(List.of(
                    TemplateEdge.builder().sourceIndex(0).sourceSlot("image").targetIndex(1).targetSlot("image").build(),
                    TemplateEdge.builder().sourceIndex(1).sourceSlot("video").targetIndex(2).targetSlot("video").build()
            ))
            .build();

    /** 全部预置模板 */
    public static final List<WorkflowTemplate> ALL = List.of(
            IMAGE_TO_VIDEO,
            TEXT_TO_VIDEO,
            TEXT_TO_IMAGE,
            FIRST_LAST_FRAME,
            CAMERA_CONTROL
    );

    /**
     * 根据 ID 查找模板
     */
    public static WorkflowTemplate findById(String id) {
        return ALL.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}