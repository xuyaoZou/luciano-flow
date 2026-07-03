package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.Storyboard;
import com.luciano.repository.mapper.StoryboardMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StoryboardService extends ServiceImpl<StoryboardMapper, Storyboard> {

    public List<Storyboard> listByEpisodeId(Long episodeId) {
        LambdaQueryWrapper<Storyboard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Storyboard::getEpisodeId, episodeId)
               .orderByAsc(Storyboard::getStoryboardNumber);
        return list(wrapper);
    }

    public List<Storyboard> listByProjectId(Long projectId) {
        LambdaQueryWrapper<Storyboard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Storyboard::getProjectId, projectId)
               .orderByAsc(Storyboard::getStoryboardNumber);
        return list(wrapper);
    }
}