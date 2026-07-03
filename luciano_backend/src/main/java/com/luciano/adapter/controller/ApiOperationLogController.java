package com.luciano.adapter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luciano.entity.ApiOperationLog;
import com.luciano.service.ApiOperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * API 操作日志管理端点（管理后台用）
 */
@RestController
@RequestMapping("/api/v1/admin/operation-logs")
@RequiredArgsConstructor
@Slf4j
public class ApiOperationLogController {

    private final ApiOperationLogService operationLogService;

    /**
     * 分页查询操作日志
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String adapterId,
            @RequestParam(required = false) String capability,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Integer responseStatus,
            @RequestParam(required = false) String platformStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        OffsetDateTime start = startTime != null ? OffsetDateTime.parse(startTime) : null;
        OffsetDateTime end = endTime != null ? OffsetDateTime.parse(endTime) : null;

        Page<ApiOperationLog> result = operationLogService.queryPage(
                page, size, userId, adapterId, capability, operationType, responseStatus, platformStatus, start, end);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "success",
                "data", Map.of(
                        "records", result.getRecords(),
                        "total", result.getTotal(),
                        "page", result.getCurrent(),
                        "size", result.getSize()
                )
        ));
    }

    /**
     * 按 taskId 查询完整调用链路
     */
    @GetMapping("/by-task/{taskId}")
    public ResponseEntity<Map<String, Object>> getByTaskId(@PathVariable String taskId) {
        List<ApiOperationLog> logs = operationLogService.getByTaskId(taskId);
        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "success",
                "data", logs
        ));
    }

    /**
     * 按 providerTaskId 查询
     */
    @GetMapping("/by-provider-task/{providerTaskId}")
    public ResponseEntity<Map<String, Object>> getByProviderTaskId(@PathVariable String providerTaskId) {
        List<ApiOperationLog> logs = operationLogService.getByProviderTaskId(providerTaskId);
        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "success",
                "data", logs
        ));
    }

    /**
     * 统计概览
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        OffsetDateTime start = startTime != null ? OffsetDateTime.parse(startTime) : null;
        OffsetDateTime end = endTime != null ? OffsetDateTime.parse(endTime) : null;

        Map<String, Object> stats = operationLogService.getStats(start, end);
        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "success",
                "data", stats
        ));
    }
}