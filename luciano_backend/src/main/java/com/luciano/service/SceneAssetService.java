package com.luciano.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.SceneAsset;
import com.luciano.repository.mapper.SceneAssetMapper;
import org.springframework.stereotype.Service;

@Service
public class SceneAssetService extends ServiceImpl<SceneAssetMapper, SceneAsset> {
}