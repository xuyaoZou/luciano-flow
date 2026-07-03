package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.entity.CharacterAsset;
import com.luciano.service.CharacterAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets/characters")
@RequiredArgsConstructor
public class CharacterAssetController {

    private final CharacterAssetService characterAssetService;

    @PostMapping
    public Result<CharacterAsset> create(@RequestBody CharacterAsset asset) {
        characterAssetService.save(asset);
        return Result.ok(asset);
    }

    @GetMapping
    public Result<List<CharacterAsset>> list(@RequestParam Long creatorId) {
        return Result.ok(characterAssetService.listByCreatorId(creatorId));
    }

    @GetMapping("/public")
    public Result<List<CharacterAsset>> listPublic() {
        return Result.ok(characterAssetService.listPublicAssets());
    }

    @GetMapping("/{id}")
    public Result<CharacterAsset> get(@PathVariable Long id) {
        return Result.ok(characterAssetService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<CharacterAsset> update(@PathVariable Long id, @RequestBody CharacterAsset asset) {
        asset.setId(id);
        characterAssetService.updateById(asset);
        return Result.ok(characterAssetService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        characterAssetService.removeById(id);
        return Result.ok();
    }
}