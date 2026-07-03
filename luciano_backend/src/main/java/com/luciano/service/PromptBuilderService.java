package com.luciano.service;

import com.luciano.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 提示词构建服务
 * 所有生成任务的提示词必须通过此服务构建，禁止前端硬编码。
 * <p>
 * 铁律：提示词是后端构建的，前端只传 assetId/storyboardId。
 */
@Slf4j
@Service
public class PromptBuilderService {

    // ==================== 角色图片提示词 ====================

    /**
     * 构建角色图片生成提示词
     */
    public String buildCharacterImagePrompt(CharacterAsset character) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("【角色形象生成】");
        prompt.append("请生成一张角色参考图。\n\n");

        // 基本信息
        prompt.append("角色名：").append(character.getName()).append("\n");

        if (character.getRoleRef() != null) {
            prompt.append("角色标签：").append(character.getRoleRef()).append("\n");
        }

        // 描述
        if (character.getDescription() != null && !character.getDescription().isBlank()) {
            prompt.append("角色描述：").append(character.getDescription()).append("\n");
        }

        // 外观描述（最重要的部分）
        if (character.getAppearance() != null && !character.getAppearance().isBlank()) {
            prompt.append("外观特征：").append(character.getAppearance()).append("\n");
        }

        // 视觉属性（JSONB 扩展字段）
        if (character.getVisualAttributes() != null && !character.getVisualAttributes().isBlank()) {
            prompt.append("视觉属性：").append(character.getVisualAttributes()).append("\n");
        }

        // 性格
        if (character.getPersonality() != null && !character.getPersonality().isBlank()) {
            prompt.append("性格特征：").append(character.getPersonality()).append("\n");
        }

        // 一致性提示
        prompt.append("\n请保持角色形象的一致性，生成高质量的角色参考图。");

        return prompt.toString();
    }

    /**
     * 构建角色图片生成增强提示词（带项目上下文）
     */
    public String buildCharacterImagePrompt(CharacterAsset character, Project project) {
        String basePrompt = buildCharacterImagePrompt(character);

        if (project != null) {
            StringBuilder sb = new StringBuilder(basePrompt);
            sb.insert(0, "项目「").append(project.getTitle()).append("」 ");
            if (project.getVisualStyle() != null) {
                sb.append("\n项目整体风格：").append(project.getVisualStyle());
            }
            return sb.toString();
        }
        return basePrompt;
    }

    // ==================== 场景图片提示词 ====================

    /**
     * 构建场景图片生成提示词
     */
    public String buildSceneImagePrompt(SceneAsset scene) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("【场景图生成】");
        prompt.append("请生成一张场景参考图。\n\n");

        prompt.append("场景名：").append(scene.getName()).append("\n");

        if (scene.getLocationRef() != null) {
            prompt.append("场景标签：").append(scene.getLocationRef()).append("\n");
        }

        if (scene.getDescription() != null && !scene.getDescription().isBlank()) {
            prompt.append("场景描述：").append(scene.getDescription()).append("\n");
        }

        if (scene.getAtmosphere() != null && !scene.getAtmosphere().isBlank()) {
            prompt.append("氛围：").append(scene.getAtmosphere()).append("\n");
        }

        if (scene.getLighting() != null && !scene.getLighting().isBlank()) {
            prompt.append("光线：").append(scene.getLighting()).append("\n");
        }

        if (scene.getVisualAttributes() != null && !scene.getVisualAttributes().isBlank()) {
            prompt.append("视觉属性：").append(scene.getVisualAttributes()).append("\n");
        }

        prompt.append("\n请保持场景氛围的一致性，生成高质量的场景参考图。");

        return prompt.toString();
    }

    public String buildSceneImagePrompt(SceneAsset scene, Project project) {
        String basePrompt = buildSceneImagePrompt(scene);
        if (project != null && project.getVisualStyle() != null) {
            return basePrompt + "\n项目整体风格：" + project.getVisualStyle();
        }
        return basePrompt;
    }

    // ==================== 道具图片提示词 ====================

    /**
     * 构建道具图片生成提示词
     */
    public String buildPropImagePrompt(PropAsset prop) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("【道具形象生成】");
        prompt.append("请生成一张道具参考图。\n\n");

        prompt.append("道具名：").append(prop.getName()).append("\n");

        if (prop.getPropRef() != null) {
            prompt.append("道具标签：").append(prop.getPropRef()).append("\n");
        }

        if (prop.getDescription() != null && !prop.getDescription().isBlank()) {
            prompt.append("道具描述：").append(prop.getDescription()).append("\n");
        }

        if (prop.getMaterial() != null && !prop.getMaterial().isBlank()) {
            prompt.append("材质：").append(prop.getMaterial()).append("\n");
        }

        if (prop.getVisualAttributes() != null && !prop.getVisualAttributes().isBlank()) {
            prompt.append("视觉属性：").append(prop.getVisualAttributes()).append("\n");
        }

        prompt.append("\n请保持道具外观的一致性，生成高质量的道具参考图。");

        return prompt.toString();
    }

    public String buildPropImagePrompt(PropAsset prop, Project project) {
        String basePrompt = buildPropImagePrompt(prop);
        if (project != null && project.getVisualStyle() != null) {
            return basePrompt + "\n项目整体风格：" + project.getVisualStyle();
        }
        return basePrompt;
    }

    // ==================== 分镜相关提示词 ====================

    /**
     * 构建首帧图片提示词
     */
    public String buildFirstFramePrompt(Storyboard storyboard) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("【首帧图片生成】\n");
        prompt.append("请生成一张图片，作为视频的起始画面。\n\n");

        appendStoryboardContext(prompt, storyboard);

        if (storyboard.getFirstFrameImageUrl() != null) {
            prompt.append("\n参考已有首帧图进行重新生成。");
        }

        return prompt.toString();
    }

    /**
     * 构建尾帧图片提示词
     */
    public String buildLastFramePrompt(Storyboard storyboard) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("【尾帧图片生成】\n");
        prompt.append("请生成一张图片，作为视频的结束画面。\n\n");

        appendStoryboardContext(prompt, storyboard);

        if (storyboard.getLastFrameImageUrl() != null) {
            prompt.append("\n参考已有尾帧图进行重新生成。");
        }

        return prompt.toString();
    }

    /**
     * 构建视频生成提示词（直通模式）
     */
    public String buildExpressVideoPrompt(Storyboard storyboard) {
        // 直通模式：使用 express_prompt 直接作为视频提示词
        if (storyboard.getExpressPrompt() != null && !storyboard.getExpressPrompt().isBlank()) {
            return storyboard.getExpressPrompt();
        }

        // 没有直通提示词，从分镜描述构建
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下分镜描述生成视频：\n\n");
        appendStoryboardContext(prompt, storyboard);
        return prompt.toString();
    }

    /**
     * 构建视频生成提示词（专业模式 — 逐字段构建）
     */
    public String buildManualVideoPrompt(Storyboard storyboard) {
        StringBuilder prompt = new StringBuilder();

        // 场景描述
        if (storyboard.getSceneDescription() != null && !storyboard.getSceneDescription().isBlank()) {
            prompt.append("场景：").append(storyboard.getSceneDescription()).append("\n");
        }

        // 氛围
        if (storyboard.getAtmosphere() != null && !storyboard.getAtmosphere().isBlank()) {
            prompt.append("氛围：").append(storyboard.getAtmosphere()).append("\n");
        }

        // 时间
        if (storyboard.getTimeOfDay() != null && !storyboard.getTimeOfDay().isBlank()) {
            prompt.append("时间：").append(storyboard.getTimeOfDay()).append("\n");
        }

        // 对话
        if (storyboard.getDialogue() != null && !storyboard.getDialogue().isBlank()) {
            prompt.append("对话：").append(storyboard.getDialogue()).append("\n");
        }

        // 旁白
        if (storyboard.getVoiceover() != null && !storyboard.getVoiceover().isBlank()) {
            prompt.append("旁白：").append(storyboard.getVoiceover()).append("\n");
        }

        // 原始描述兜底
        if (prompt.isEmpty() && storyboard.getDescription() != null) {
            prompt.append(storyboard.getDescription());
        }

        return prompt.toString();
    }

    // ==================== 剧本生成提示词 ====================

    /**
     * 构建剧本生成提示词
     */
    public String buildScriptPrompt(String originalIdea, Project project) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("根据以下创意生成短剧剧本大纲，包含角色设定、场景描述、剧情走向和分集概要：\n\n");
        prompt.append(originalIdea);

        if (project != null) {
            if (project.getGenre() != null) {
                prompt.append("\n\n类型：").append(project.getGenre());
            }
            if (project.getVisualStyle() != null) {
                prompt.append("\n风格：").append(project.getVisualStyle());
            }
            if (project.getRatio() != null) {
                prompt.append("\n画面比例：").append(project.getRatio());
            }
        }

        return prompt.toString();
    }

    // ==================== 批量提示词构建 ====================

    /**
     * 批量构建资产生成提示词
     * @param assetType  asset 类型: character / scene / prop
     * @param assets     资产列表
     * @param project    项目（可为null）
     * @return 提示词列表，与资产列表一一对应
     */
    public List<String> buildAssetPrompts(String assetType, List<?> assets, Project project) {
        List<String> prompts = new ArrayList<>(assets.size());
        for (Object asset : assets) {
            switch (assetType) {
                case "character" -> prompts.add(buildCharacterImagePrompt((CharacterAsset) asset, project));
                case "scene" -> prompts.add(buildSceneImagePrompt((SceneAsset) asset, project));
                case "prop" -> prompts.add(buildPropImagePrompt((PropAsset) asset, project));
                default -> throw new IllegalArgumentException("Unknown asset type: " + assetType);
            }
        }
        return prompts;
    }

    // ==================== 私有方法 ====================

    private void appendStoryboardContext(StringBuilder prompt, Storyboard sb) {
        if (sb.getShotId() != null) {
            prompt.append("镜头编号：").append(sb.getShotId()).append("\n");
        }
        if (sb.getSceneDescription() != null && !sb.getSceneDescription().isBlank()) {
            prompt.append("场景：").append(sb.getSceneDescription()).append("\n");
        }
        if (sb.getDescription() != null && !sb.getDescription().isBlank()) {
            prompt.append("描述：").append(sb.getDescription()).append("\n");
        }
        if (sb.getAtmosphere() != null && !sb.getAtmosphere().isBlank()) {
            prompt.append("氛围：").append(sb.getAtmosphere()).append("\n");
        }
        if (sb.getTimeOfDay() != null && !sb.getTimeOfDay().isBlank()) {
            prompt.append("时间：").append(sb.getTimeOfDay()).append("\n");
        }
        if (sb.getDialogue() != null && !sb.getDialogue().isBlank()) {
            prompt.append("对话：").append(sb.getDialogue()).append("\n");
        }
        if (sb.getVoiceover() != null && !sb.getVoiceover().isBlank()) {
            prompt.append("旁白：").append(sb.getVoiceover()).append("\n");
        }
    }
}