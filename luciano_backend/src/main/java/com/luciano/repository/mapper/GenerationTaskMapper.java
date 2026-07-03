package com.luciano.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luciano.entity.GenerationTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GenerationTaskMapper extends BaseMapper<GenerationTask> {
}