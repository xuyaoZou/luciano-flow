package com.luciano.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luciano.entity.StorageProviderConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StorageProviderMapper extends BaseMapper<StorageProviderConfig> {
}