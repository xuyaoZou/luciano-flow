package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.entity.Episode;
import com.luciano.entity.Project;
import com.luciano.entity.Storyboard;
import com.luciano.service.EpisodeService;
import com.luciano.service.ProjectService;
import com.luciano.service.StoryboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final EpisodeService episodeService;
    private final StoryboardService storyboardService;

    @PostMapping
    public Result<Project> create(@AuthenticationPrincipal Long userId, @RequestBody Project project) {
        project.setCreatorId(userId);
        return Result.ok(projectService.createProject(project));
    }

    @GetMapping
    public Result<List<Project>> list(
            @RequestParam Long creatorId,
            @RequestParam(required = false) String type) {
        if (type != null) {
            return Result.ok(projectService.listByCreatorIdAndType(creatorId, type));
        }
        return Result.ok(projectService.listByCreatorId(creatorId));
    }

    @GetMapping("/{id}")
    public Result<Project> get(@PathVariable Long id) {
        return Result.ok(projectService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project) {
        project.setId(id);
        projectService.updateById(project);
        return Result.ok(projectService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.removeById(id);
        return Result.ok();
    }

    // --- Episodes ---

    @GetMapping("/{projectId}/episodes")
    public Result<List<Episode>> listEpisodes(@PathVariable Long projectId) {
        return Result.ok(episodeService.listByProjectId(projectId));
    }

    @PostMapping("/{projectId}/episodes")
    public Result<Episode> createEpisode(@PathVariable Long projectId, @RequestBody Episode episode) {
        episode.setProjectId(projectId);
        episodeService.save(episode);
        return Result.ok(episode);
    }

    // --- Storyboards (direct under project, for non-drama types) ---

    @GetMapping("/{projectId}/storyboards")
    public Result<List<Storyboard>> listStoryboards(@PathVariable Long projectId) {
        return Result.ok(storyboardService.listByProjectId(projectId));
    }
}