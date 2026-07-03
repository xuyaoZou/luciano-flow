package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.ModelProvider;
import com.luciano.repository.mapper.ModelProviderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelProviderService extends ServiceImpl<ModelProviderMapper, ModelProvider> {

    public List<ModelProvider> listActive() {
        LambdaQueryWrapper<ModelProvider> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelProvider::getIsActive, true);
        return list(wrapper);
    }

    public ModelProvider getByName(String name) {
        return getOne(new LambdaQueryWrapper<ModelProvider>().eq(ModelProvider::getName, name));
    }
}