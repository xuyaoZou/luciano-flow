package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.entity.StylePreset;
import com.luciano.service.StylePresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets/styles")
@RequiredArgsConstructor
public class StylePresetController {

    private final StylePresetService stylePresetService;

    @PostMapping
    public Result<StylePreset> create(@RequestBody StylePreset preset) {
        stylePresetService.save(preset);
        return Result.ok(preset);
    }

    @GetMapping
    public Result<List<StylePreset>> list(@RequestParam Long creatorId) {
        return Result.ok(stylePresetService.lambdaQuery()
                .eq(StylePreset::getCreatorId, creatorId)
                .orderByDesc(StylePreset::getUpdatedAt)
                .list());
    }

    @GetMapping("/public")
    public Result<List<StylePreset>> listPublic() {
        return Result.ok(stylePresetService.lambdaQuery()
                .eq(StylePreset::getIsPublic, true)
                .orderByDesc(StylePreset::getUpdatedAt)
                .list());
    }

    @GetMapping("/{id}")
    public Result<StylePreset> get(@PathVariable Long id) {
        return Result.ok(stylePresetService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<StylePreset> update(@PathVariable Long id, @RequestBody StylePreset preset) {
        preset.setId(id);
        stylePresetService.updateById(preset);
        return Result.ok(stylePresetService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        stylePresetService.removeById(id);
        return Result.ok();
    }
}