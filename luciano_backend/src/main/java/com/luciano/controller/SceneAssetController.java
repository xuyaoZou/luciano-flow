package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.entity.SceneAsset;
import com.luciano.service.SceneAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets/scenes")
@RequiredArgsConstructor
public class SceneAssetController {

    private final SceneAssetService sceneAssetService;

    @PostMapping
    public Result<SceneAsset> create(@RequestBody SceneAsset asset) {
        sceneAssetService.save(asset);
        return Result.ok(asset);
    }

    @GetMapping
    public Result<List<SceneAsset>> list(@RequestParam Long creatorId) {
        return Result.ok(sceneAssetService.lambdaQuery()
                .eq(SceneAsset::getCreatorId, creatorId)
                .orderByDesc(SceneAsset::getUpdatedAt)
                .list());
    }

    @GetMapping("/{id}")
    public Result<SceneAsset> get(@PathVariable Long id) {
        return Result.ok(sceneAssetService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<SceneAsset> update(@PathVariable Long id, @RequestBody SceneAsset asset) {
        asset.setId(id);
        sceneAssetService.updateById(asset);
        return Result.ok(sceneAssetService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sceneAssetService.removeById(id);
        return Result.ok();
    }
}