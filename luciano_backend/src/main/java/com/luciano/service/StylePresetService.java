package com.luciano.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.StylePreset;
import com.luciano.repository.mapper.StylePresetMapper;
import org.springframework.stereotype.Service;

@Service
public class StylePresetService extends ServiceImpl<StylePresetMapper, StylePreset> {
}