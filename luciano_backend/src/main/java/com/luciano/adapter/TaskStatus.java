package com.luciano.adapter;

import lombok.Data;

/**
 * 任务状态
 * 统一所有适配器的任务状态表示。
 */
public enum TaskStatus {

    /** 已提交，等待处理 */
    PENDING,

    /** 处理中 */
    PROCESSING,

    /** 已完成 */
    COMPLETED,

    /** 失败 */
    FAILED,

    /** 已取消 */
    CANCELLED
}