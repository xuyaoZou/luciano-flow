package com.luciano.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 节点间数据传递引用
 * <p>
 * 节点执行完成后，输出端口的值封装为 OutputRef。
 * 下游节点通过连线接收到的是 OutputRef，从中取 URL 传给厂商 API。
 * 参考设计文档 §5.2 传递机制。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutputRef {

    /** 原始公网 URL（厂商 CDN 返回） — 主结果 */
    private String url;

    /** 本地代理 URL（/api/v1/media/{id}/file） */
    private String localUrl;

    /** 关联的 MediaAsset ID */
    private Long assetId;

    /** 数据类型 */
    private PortType dataType;

    /** 所有结果 URL（多图/组图模式时包含全部，[0] 为主结果） */
    @Builder.Default
    private List<String> allUrls = java.util.Collections.emptyList();

    /** 所有结果对应的 assetId 列表 */
    @Builder.Default
    private List<Long> allAssetIds = java.util.Collections.emptyList();
}