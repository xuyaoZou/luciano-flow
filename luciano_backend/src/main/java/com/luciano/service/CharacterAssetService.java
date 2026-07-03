package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.CharacterAsset;
import com.luciano.repository.mapper.CharacterAssetMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CharacterAssetService extends ServiceImpl<CharacterAssetMapper, CharacterAsset> {

    public List<CharacterAsset> listByCreatorId(Long creatorId) {
        LambdaQueryWrapper<CharacterAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CharacterAsset::getCreatorId, creatorId)
               .orderByDesc(CharacterAsset::getUpdatedAt);
        return list(wrapper);
    }

    public List<CharacterAsset> listPublicAssets() {
        LambdaQueryWrapper<CharacterAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CharacterAsset::getIsPublic, true)
               .orderByDesc(CharacterAsset::getUpdatedAt);
        return list(wrapper);
    }
}