package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.entity.Storyboard;
import com.luciano.service.StoryboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/episodes/{episodeId}/storyboards")
@RequiredArgsConstructor
public class StoryboardController {

    private final StoryboardService storyboardService;

    @GetMapping
    public Result<List<Storyboard>> list(@PathVariable Long episodeId) {
        return Result.ok(storyboardService.listByEpisodeId(episodeId));
    }

    @PostMapping
    public Result<Storyboard> create(@PathVariable Long episodeId, @RequestBody Storyboard storyboard) {
        storyboard.setEpisodeId(episodeId);
        storyboardService.save(storyboard);
        return Result.ok(storyboard);
    }

    @GetMapping("/{id}")
    public Result<Storyboard> get(@PathVariable Long id) {
        return Result.ok(storyboardService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<Storyboard> update(@PathVariable Long id, @RequestBody Storyboard storyboard) {
        storyboard.setId(id);
        storyboardService.updateById(storyboard);
        return Result.ok(storyboardService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        storyboardService.removeById(id);
        return Result.ok();
    }
}