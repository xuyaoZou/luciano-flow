package com.luciano.flow.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luciano.flow.entity.WorkflowExecution;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecution> {
}