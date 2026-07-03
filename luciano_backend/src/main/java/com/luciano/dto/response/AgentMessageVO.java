package com.luciano.dto.response;

import com.luciano.entity.MediaAsset;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class AgentMessageVO {
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String text;
    private String runId;
    private String status;
    private String errorMsg;
    private Integer mediaCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** 该消息关联的媒体资产 */
    private List<MediaAsset> mediaAssets;
}