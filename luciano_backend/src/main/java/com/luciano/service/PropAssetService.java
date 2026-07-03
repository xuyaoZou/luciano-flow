package com.luciano.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.PropAsset;
import com.luciano.repository.mapper.PropAssetMapper;
import org.springframework.stereotype.Service;

@Service
public class PropAssetService extends ServiceImpl<PropAssetMapper, PropAsset> {
}