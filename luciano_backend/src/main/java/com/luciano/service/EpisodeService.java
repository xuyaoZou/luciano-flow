package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.Episode;
import com.luciano.entity.Storyboard;
import com.luciano.repository.mapper.EpisodeMapper;
import com.luciano.repository.mapper.StoryboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EpisodeService extends ServiceImpl<EpisodeMapper, Episode> {

    private final StoryboardMapper storyboardMapper;

    public List<Episode> listByProjectId(Long projectId) {
        LambdaQueryWrapper<Episode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Episode::getProjectId, projectId)
               .orderByAsc(Episode::getEpisodeNumber);
        return list(wrapper);
    }

    public List<Storyboard> listStoryboards(Long episodeId) {
        LambdaQueryWrapper<Storyboard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Storyboard::getEpisodeId, episodeId)
               .orderByAsc(Storyboard::getStoryboardNumber);
        return storyboardMapper.selectList(wrapper);
    }
}