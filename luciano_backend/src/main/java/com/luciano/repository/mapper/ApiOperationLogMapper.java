package com.luciano.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luciano.entity.ApiOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiOperationLogMapper extends BaseMapper<ApiOperationLog> {
}