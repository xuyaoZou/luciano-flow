package com.luciano.adapter;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 参数校验结果
 */
@Data
@Builder
public class ValidationResult {

    /** 是否通过校验 */
    private boolean valid;

    /** 错误信息列表 */
    private List<String> errors;

    /** 警告信息列表（不阻断提交，但提示用户） */
    private List<String> warnings;

    public static ValidationResult ok() {
        return ValidationResult.builder().valid(true).errors(List.of()).warnings(List.of()).build();
    }

    public static ValidationResult errors(List<String> errors) {
        return ValidationResult.builder().valid(false).errors(errors).warnings(List.of()).build();
    }

    public static ValidationResult errors(String... errors) {
        return ValidationResult.builder().valid(false).errors(List.of(errors)).warnings(List.of()).build();
    }
}