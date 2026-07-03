package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.ApiOperationLog;
import com.luciano.repository.mapper.ApiOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API 操作日志服务
 * <p>
 * 写入方式：异步（不阻塞主流程）
 * 查询方式：同步（管理后台用）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApiOperationLogService extends ServiceImpl<ApiOperationLogMapper, ApiOperationLog> {

    /**
     * 异步写入操作日志
     */
    @Async("operationLogExecutor")
    public void saveAsync(ApiOperationLog operationLog) {
        try {
            save(operationLog);
        } catch (Exception e) {
            // 日志写入失败不能影响主流程，只记录错误
            log.error("[ApiOperationLog] Failed to save operation log: {}", e.getMessage(), e);
        }
    }

    /**
     * 分页查询（管理后台用）
     */
    public Page<ApiOperationLog> queryPage(int pageNum, int pageSize,
                                            Long userId, String adapterId,
                                            String capability, String operationType,
                                            Integer responseStatus,
                                            String platformStatus,
                                            OffsetDateTime startTime, OffsetDateTime endTime) {
        LambdaQueryWrapper<ApiOperationLog> wrapper = new LambdaQueryWrapper<ApiOperationLog>()
                .eq(userId != null, ApiOperationLog::getUserId, userId)
                .eq(adapterId != null, ApiOperationLog::getAdapterId, adapterId)
                .eq(capability != null, ApiOperationLog::getCapability, capability)
                .eq(operationType != null, ApiOperationLog::getOperationType, operationType)
                .eq(responseStatus != null, ApiOperationLog::getResponseStatus, responseStatus)
                .eq(platformStatus != null, ApiOperationLog::getPlatformStatus, platformStatus)
                .ge(startTime != null, ApiOperationLog::getCreatedAt, startTime)
                .le(endTime != null, ApiOperationLog::getCreatedAt, endTime)
                .orderByDesc(ApiOperationLog::getCreatedAt);

        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 按 task_id 查询完整调用链路
     */
    public List<ApiOperationLog> getByTaskId(String taskId) {
        return list(new LambdaQueryWrapper<ApiOperationLog>()
                .eq(ApiOperationLog::getTaskId, taskId)
                .orderByAsc(ApiOperationLog::getCreatedAt));
    }

    /**
     * 更新最近一条日志的 taskId 和 providerTaskId（提交成功后补写）
     */
    public void updateTaskIdByContext(String taskId, String providerTaskId,
                                       String adapterId, String capability,
                                       String operationType, Long userId) {
        try {
            LambdaQueryWrapper<ApiOperationLog> wrapper = new LambdaQueryWrapper<ApiOperationLog>()
                    .eq(ApiOperationLog::getAdapterId, adapterId)
                    .eq(ApiOperationLog::getCapability, capability)
                    .eq(ApiOperationLog::getOperationType, operationType)
                    .eq(userId != null, ApiOperationLog::getUserId, userId)
                    .isNull(ApiOperationLog::getTaskId)
                    .orderByDesc(ApiOperationLog::getId)
                    .last("LIMIT 1");
            ApiOperationLog latest = getOne(wrapper);
            if (latest != null) {
                latest.setTaskId(taskId);
                latest.setProviderTaskId(providerTaskId);
                updateById(latest);
            }
        } catch (Exception e) {
            log.warn("[ApiOperationLog] Failed to update taskId: {}", e.getMessage());
        }
    }

    /**
     * 按 provider_task_id 查询
     */
    public List<ApiOperationLog> getByProviderTaskId(String providerTaskId) {
        return list(new LambdaQueryWrapper<ApiOperationLog>()
                .eq(ApiOperationLog::getProviderTaskId, providerTaskId)
                .orderByAsc(ApiOperationLog::getCreatedAt));
    }

    /**
     * 统计概览（管理后台 Dashboard 用）
     */
    public Map<String, Object> getStats(OffsetDateTime startTime, OffsetDateTime endTime) {
        LambdaQueryWrapper<ApiOperationLog> wrapper = new LambdaQueryWrapper<ApiOperationLog>()
                .ge(startTime != null, ApiOperationLog::getCreatedAt, startTime)
                .le(endTime != null, ApiOperationLog::getCreatedAt, endTime);

        long totalCalls = count(wrapper);

        // 按适配器统计
        List<Map<String, Object>> byAdapter = listObjs(new LambdaQueryWrapper<ApiOperationLog>()
                .select(ApiOperationLog::getAdapterId)
                .ge(startTime != null, ApiOperationLog::getCreatedAt, startTime)
                .le(endTime != null, ApiOperationLog::getCreatedAt, endTime)
                .groupBy(ApiOperationLog::getAdapterId), obj -> {
                    // MyBatis Plus listObjs 返回的是单列值
                    return Map.of("adapterId", obj);
                });

        // 按操作类型统计
        // 简化：用 SQL 查询
        long submitCount = count(new LambdaQueryWrapper<ApiOperationLog>()
                .eq(ApiOperationLog::getOperationType, "submit")
                .ge(startTime != null, ApiOperationLog::getCreatedAt, startTime)
                .le(endTime != null, ApiOperationLog::getCreatedAt, endTime));

        long pollCount = count(new LambdaQueryWrapper<ApiOperationLog>()
                .eq(ApiOperationLog::getOperationType, "poll")
                .ge(startTime != null, ApiOperationLog::getCreatedAt, startTime)
                .le(endTime != null, ApiOperationLog::getCreatedAt, endTime));

        long completeCount = count(new LambdaQueryWrapper<ApiOperationLog>()
                .eq(ApiOperationLog::getOperationType, "complete")
                .ge(startTime != null, ApiOperationLog::getCreatedAt, startTime)
                .le(endTime != null, ApiOperationLog::getCreatedAt, endTime));

        long failedCount = count(new LambdaQueryWrapper<ApiOperationLog>()
                .eq(ApiOperationLog::getOperationType, "failed")
                .ge(startTime != null, ApiOperationLog::getCreatedAt, startTime)
                .le(endTime != null, ApiOperationLog::getCreatedAt, endTime));

        long errorCount = count(new LambdaQueryWrapper<ApiOperationLog>()
                .ne(ApiOperationLog::getResponseStatus, 200)
                .ge(startTime != null, ApiOperationLog::getCreatedAt, startTime)
                .le(endTime != null, ApiOperationLog::getCreatedAt, endTime));

        // 平均耗时
        // 简化版：最近100条的平均值
        List<ApiOperationLog> recentLogs = list(new LambdaQueryWrapper<ApiOperationLog>()
                .isNotNull(ApiOperationLog::getDurationMs)
                .orderByDesc(ApiOperationLog::getCreatedAt)
                .last("LIMIT 100"));
        double avgDuration = recentLogs.stream()
                .mapToInt(ApiOperationLog::getDurationMs)
                .average()
                .orElse(0);

        // 总扣费
        // 简化版：最近1000条的累计
        List<ApiOperationLog> costLogs = list(new LambdaQueryWrapper<ApiOperationLog>()
                .isNotNull(ApiOperationLog::getCreditsUsed)
                .ge(startTime != null, ApiOperationLog::getCreatedAt, startTime)
                .le(endTime != null, ApiOperationLog::getCreatedAt, endTime)
                .last("LIMIT 10000"));
        double totalCredits = costLogs.stream()
                .mapToDouble(log -> log.getCreditsUsed() != null ? log.getCreditsUsed().doubleValue() : 0)
                .sum();

        return Map.of(
                "totalCalls", totalCalls,
                "submitCount", submitCount,
                "pollCount", pollCount,
                "completeCount", completeCount,
                "failedCount", failedCount,
                "errorCount", errorCount,
                "avgDurationMs", Math.round(avgDuration),
                "totalCredits", totalCredits
        );
    }
}