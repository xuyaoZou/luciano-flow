package com.luciano.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({"com.luciano.repository.mapper", "com.luciano.mapper", "com.luciano.flow.repository"})
public class MyBatisPlusConfig {
}