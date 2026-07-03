package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.entity.PropAsset;
import com.luciano.service.PropAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets/props")
@RequiredArgsConstructor
public class PropAssetController {

    private final PropAssetService propAssetService;

    @PostMapping
    public Result<PropAsset> create(@RequestBody PropAsset asset) {
        propAssetService.save(asset);
        return Result.ok(asset);
    }

    @GetMapping
    public Result<List<PropAsset>> list(@RequestParam Long creatorId) {
        return Result.ok(propAssetService.lambdaQuery()
                .eq(PropAsset::getCreatorId, creatorId)
                .orderByDesc(PropAsset::getUpdatedAt)
                .list());
    }

    @GetMapping("/{id}")
    public Result<PropAsset> get(@PathVariable Long id) {
        return Result.ok(propAssetService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<PropAsset> update(@PathVariable Long id, @RequestBody PropAsset asset) {
        asset.setId(id);
        propAssetService.updateById(asset);
        return Result.ok(propAssetService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        propAssetService.removeById(id);
        return Result.ok();
    }
}