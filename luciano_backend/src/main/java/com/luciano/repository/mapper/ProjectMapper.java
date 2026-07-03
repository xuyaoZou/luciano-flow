package com.luciano.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luciano.entity.Project;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}