package com.luciano.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luciano.entity.UserApiKey;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserApiKeyMapper extends BaseMapper<UserApiKey> {
}