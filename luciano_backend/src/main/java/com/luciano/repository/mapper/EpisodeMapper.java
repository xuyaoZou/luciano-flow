package com.luciano.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luciano.entity.Episode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EpisodeMapper extends BaseMapper<Episode> {
}