package com.luciano.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luciano.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}