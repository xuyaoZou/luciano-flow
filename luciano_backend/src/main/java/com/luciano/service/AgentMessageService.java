package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luciano.entity.AgentMessage;
import com.luciano.repository.mapper.AgentMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 消息服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMessageService {

    private final AgentMessageMapper messageMapper;

    /**
     * 保存消息
     */
    public AgentMessage save(AgentMessage message) {
        messageMapper.insert(message);
        return message;
    }

    /**
     * 更新消息（轮询结果更新）
     */
    public boolean updateById(AgentMessage message) {
        return messageMapper.updateById(message) > 0;
    }

    /**
     * 获取会话的所有消息（按时间排序）
     */
    public List<AgentMessage> listByConversationId(Long conversationId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<AgentMessage>()
                        .eq(AgentMessage::getConversationId, conversationId)
                        .orderByAsc(AgentMessage::getId)
        );
    }

    /**
     * 获取会话的最后一条消息
     */
    public AgentMessage getLastMessage(Long conversationId) {
        return messageMapper.selectOne(
                new LambdaQueryWrapper<AgentMessage>()
                        .eq(AgentMessage::getConversationId, conversationId)
                        .orderByDesc(AgentMessage::getId)
                        .last("LIMIT 1")
        );
    }

    /**
     * 按 runId 精确查找 assistant 消息
     */
    public AgentMessage getByRunId(Long conversationId, String runId) {
        return messageMapper.selectOne(
                new LambdaQueryWrapper<AgentMessage>()
                        .eq(AgentMessage::getConversationId, conversationId)
                        .eq(AgentMessage::getRunId, runId)
                        .eq(AgentMessage::getRole, "assistant")
                        .last("LIMIT 1")
        );
    }
}