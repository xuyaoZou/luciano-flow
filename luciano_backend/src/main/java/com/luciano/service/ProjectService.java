package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.common.ProjectType;
import com.luciano.entity.Episode;
import com.luciano.entity.Project;
import com.luciano.repository.mapper.EpisodeMapper;
import com.luciano.repository.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService extends ServiceImpl<ProjectMapper, Project> {

    private final EpisodeMapper episodeMapper;

    @Transactional
    public Project createProject(Project project) {
        // 设置默认值
        if (project.getType() == null) {
            project.setType(ProjectType.SHORT_DRAMA.getCode());
        }
        if (project.getStatus() == null) {
            project.setStatus("draft");
        }
        if (project.getRatio() == null) {
            project.setRatio("9:16");
        }
        save(project);

        // 短剧类型自动创建第一集
        if (ProjectType.SHORT_DRAMA.getCode().equals(project.getType())) {
            Episode ep = new Episode();
            ep.setProjectId(project.getId());
            ep.setEpisodeNumber(1);
            ep.setTitle("第1集");
            ep.setStatus("draft");
            episodeMapper.insert(ep);
        }

        return project;
    }

    public List<Project> listByCreatorId(Long creatorId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getCreatorId, creatorId)
               .orderByDesc(Project::getUpdatedAt);
        return list(wrapper);
    }

    public List<Project> listByCreatorIdAndType(Long creatorId, String type) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getCreatorId, creatorId)
               .eq(Project::getType, type)
               .orderByDesc(Project::getUpdatedAt);
        return list(wrapper);
    }
}