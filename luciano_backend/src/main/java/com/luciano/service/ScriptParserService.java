package com.luciano.service;

import com.luciano.entity.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 剧本解析服务
 * <p>
 * 从剧本文本（Agent 生成的 Markdown）中解析出：
 * - 角色资产 (CharacterAsset)
 * - 场景资产 (SceneAsset)
 * - 道具资产 (PropAsset)
 * - 分镜 (Storyboard)
 * <p>
 * 铁律：Agent 输出 Markdown 后端解析，比让 Agent 输出 JSON 更稳定容错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptParserService {

    private final CharacterAssetService characterAssetService;
    private final SceneAssetService sceneAssetService;
    private final PropAssetService propAssetService;
    private final StoryboardService storyboardService;
    private final EpisodeService episodeService;

    // ==================== 解析入口 ====================

    /**
     * 从剧本 Markdown 解析并创建所有资产和分镜
     *
     * @param projectId 项目ID
     * @param userId     创建者ID
     * @param episodeId  集ID（短剧必须有，短视频传null）
     * @param scriptText 剧本文本（Markdown格式）
     * @return 解析结果
     */
    @Transactional
    public ParseResult parseAndCreate(Long projectId, Long userId, Long episodeId, String scriptText) {
        ParseResult result = new ParseResult();

        // 1. 解析角色
        List<ParsedCharacter> characters = parseCharacters(scriptText);
        for (ParsedCharacter pc : characters) {
            CharacterAsset asset = new CharacterAsset();
            asset.setCreatorId(userId);
            asset.setProjectId(projectId);
            asset.setName(pc.name);
            asset.setRoleRef(pc.roleRef);
            asset.setDescription(pc.description);
            asset.setAppearance(pc.appearance);
            asset.setPersonality(pc.personality);
            asset.setIsPublic(false);
            characterAssetService.save(asset);
            result.characters.add(asset);
            log.info("[ScriptParser] Created character: id={}, name={}", asset.getId(), asset.getName());
        }

        // 2. 解析场景
        List<ParsedScene> scenes = parseScenes(scriptText);
        for (ParsedScene ps : scenes) {
            SceneAsset asset = new SceneAsset();
            asset.setCreatorId(userId);
            asset.setProjectId(projectId);
            asset.setName(ps.name);
            asset.setLocationRef(ps.locationRef);
            asset.setDescription(ps.description);
            asset.setAtmosphere(ps.atmosphere);
            asset.setLighting(ps.lighting);
            asset.setIsPublic(false);
            sceneAssetService.save(asset);
            result.scenes.add(asset);
            log.info("[ScriptParser] Created scene: id={}, name={}", asset.getId(), asset.getName());
        }

        // 3. 解析道具
        List<ParsedProp> props = parseProps(scriptText);
        for (ParsedProp pp : props) {
            PropAsset asset = new PropAsset();
            asset.setCreatorId(userId);
            asset.setProjectId(projectId);
            asset.setName(pp.name);
            asset.setPropRef(pp.propRef);
            asset.setDescription(pp.description);
            asset.setMaterial(pp.material);
            asset.setIsPublic(false);
            propAssetService.save(asset);
            result.props.add(asset);
            log.info("[ScriptParser] Created prop: id={}, name={}", asset.getId(), asset.getName());
        }

        // 4. 解析分镜
        List<ParsedStoryboard> storyboards = parseStoryboards(scriptText);
        int storyboardNumber = 1;
        for (ParsedStoryboard psb : storyboards) {
            Storyboard sb = new Storyboard();
            sb.setProjectId(projectId);
            sb.setEpisodeId(episodeId);
            sb.setStoryboardNumber(storyboardNumber++);
            sb.setShotId(psb.shotId != null ? psb.shotId : "S" + storyboardNumber);
            sb.setDescription(psb.description);
            sb.setRawDescription(psb.rawDescription);
            sb.setSceneDescription(psb.sceneDescription);
            sb.setAtmosphere(psb.atmosphere);
            sb.setTimeOfDay(psb.timeOfDay);
            sb.setDialogue(psb.dialogue);
            sb.setVoiceover(psb.voiceover);
            sb.setGenerationMode("agent");  // Agent 解析生成的
            sb.setExpressPrompt(psb.expressPrompt);
            sb.setStatus("draft");
            storyboardService.save(sb);
            result.storyboards.add(sb);
            log.info("[ScriptParser] Created storyboard: id={}, number={}", sb.getId(), sb.getStoryboardNumber());
        }

        log.info("[ScriptParser] Parse complete: projectId={}, characters={}, scenes={}, props={}, storyboards={}",
                projectId, characters.size(), scenes.size(), props.size(), storyboards.size());

        return result;
    }

    // ==================== 角色解析 ====================

    /**
     * 支持的 Markdown 格式：
     * ## 角色设定
     * ### 苏晚(R1)
     * 25岁，温柔善良的出版社编辑...
     * 外观：长发、素色长裙、温和的眼神
     * 性格：温柔但内心坚韧
     */
    private List<ParsedCharacter> parseCharacters(String text) {
        List<ParsedCharacter> result = new ArrayList<>();

        // 匹配角色标题行：### 角色名(R1) 或 ### 角色名（R1）
        Pattern charPattern = Pattern.compile(
                "###\\s+(.+?)[（(](\\w+)[)）]\\s*\\n((?:(?!###)[\\s\\S])*)(?=###|##\\s|$)",
                Pattern.MULTILINE);

        // 只在角色设定区域搜索
        String charSection = extractSection(text, "角色", "场景|分镜|道具");
        if (charSection == null) {
            // 尝试英文关键词
            charSection = extractSection(text, "character", "scene|storyboard|prop");
        }
        if (charSection == null) {
            log.info("[ScriptParser] No character section found");
            return result;
        }

        Matcher matcher = charPattern.matcher(charSection);
        while (matcher.find()) {
            ParsedCharacter pc = new ParsedCharacter();
            pc.name = matcher.group(1).trim();
            pc.roleRef = matcher.group(2).trim();
            String body = matcher.group(3).trim();

            // 解析角色详情
            parseCharacterBody(body, pc);
            result.add(pc);
        }

        return result;
    }

    private void parseCharacterBody(String body, ParsedCharacter pc) {
        String[] lines = body.split("\n");
        StringBuilder desc = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("外观") || trimmed.startsWith("外貌") || trimmed.startsWith("形象")) {
                pc.appearance = extractValue(trimmed);
            } else if (trimmed.startsWith("性格") || trimmed.startsWith("个性")) {
                pc.personality = extractValue(trimmed);
            } else {
                desc.append(trimmed).append(" ");
            }
        }

        pc.description = desc.toString().trim();
    }

    // ==================== 场景解析 ====================

    private List<ParsedScene> parseScenes(String text) {
        List<ParsedScene> result = new ArrayList<>();

        String sceneSection = extractSection(text, "场景", "分镜|道具|角色");
        if (sceneSection == null) {
            sceneSection = extractSection(text, "scene", "storyboard|prop|character");
        }
        if (sceneSection == null) {
            return result;
        }

        Pattern scenePattern = Pattern.compile(
                "###\\s+(.+?)[（(](\\w+)[)）]\\s*\\n((?:(?!###)[\\s\\S])*)(?=###|##\\s|$)",
                Pattern.MULTILINE);

        Matcher matcher = scenePattern.matcher(sceneSection);
        while (matcher.find()) {
            ParsedScene ps = new ParsedScene();
            ps.name = matcher.group(1).trim();
            ps.locationRef = matcher.group(2).trim();
            String body = matcher.group(3).trim();

            parseSceneBody(body, ps);
            result.add(ps);
        }

        return result;
    }

    private void parseSceneBody(String body, ParsedScene ps) {
        String[] lines = body.split("\n");
        StringBuilder desc = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("氛围") || trimmed.startsWith("气氛")) {
                ps.atmosphere = extractValue(trimmed);
            } else if (trimmed.startsWith("光线") || trimmed.startsWith("灯光") || trimmed.startsWith("照明")) {
                ps.lighting = extractValue(trimmed);
            } else {
                desc.append(trimmed).append(" ");
            }
        }

        ps.description = desc.toString().trim();
    }

    // ==================== 道具解析 ====================

    private List<ParsedProp> parseProps(String text) {
        List<ParsedProp> result = new ArrayList<>();

        String propSection = extractSection(text, "道具", "角色|场景|分镜");
        if (propSection == null) {
            propSection = extractSection(text, "prop", "character|scene|storyboard");
        }
        if (propSection == null) {
            return result;
        }

        Pattern propPattern = Pattern.compile(
                "###\\s+(.+?)[（(](\\w+)[)）]\\s*\\n((?:(?!###)[\\s\\S])*)(?=###|##\\s|$)",
                Pattern.MULTILINE);

        Matcher matcher = propPattern.matcher(propSection);
        while (matcher.find()) {
            ParsedProp pp = new ParsedProp();
            pp.name = matcher.group(1).trim();
            pp.propRef = matcher.group(2).trim();
            String body = matcher.group(3).trim();

            parsePropBody(body, pp);
            result.add(pp);
        }

        return result;
    }

    private void parsePropBody(String body, ParsedProp pp) {
        String[] lines = body.split("\n");
        StringBuilder desc = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("材质") || trimmed.startsWith("材料")) {
                pp.material = extractValue(trimmed);
            } else {
                desc.append(trimmed).append(" ");
            }
        }

        pp.description = desc.toString().trim();
    }

    // ==================== 分镜解析 ====================

    /**
     * 支持的分镜格式：
     * ### 镜头1 / S1 / 第1镜
     * 场景：城市天台
     * 描述：苏晚站在天台边缘...
     * 氛围：紧张
     * 时间：夜晚
     * 对话：苏晚：我必须找到真相
     * 旁白：城市的灯火在身后闪烁
     */
    private List<ParsedStoryboard> parseStoryboards(String text) {
        List<ParsedStoryboard> result = new ArrayList<>();

        String sbSection = extractSection(text, "分镜", "$");
        if (sbSection == null) {
            sbSection = extractSection(text, "镜头", "$");
        }
        if (sbSection == null) {
            sbSection = extractSection(text, "storyboard", "$");
        }
        if (sbSection == null) {
            return result;
        }

        // 匹配分镜标题：### 镜头N / ### S1 / ### 第N镜
        Pattern sbPattern = Pattern.compile(
                "###\\s+(?:镜头|镜|S|s|Shot)?\\s*(\\d+|[Ss]?\\d+).*\\n((?:(?!###)[\\s\\S])*)(?=###|$)",
                Pattern.MULTILINE);

        Matcher matcher = sbPattern.matcher(sbSection);
        while (matcher.find()) {
            ParsedStoryboard psb = new ParsedStoryboard();
            psb.shotId = "S" + matcher.group(1).replaceAll("[^0-9]", "");
            String body = matcher.group(2).trim();

            parseStoryboardBody(body, psb);
            result.add(psb);
        }

        return result;
    }

    private void parseStoryboardBody(String body, ParsedStoryboard psb) {
        String[] lines = body.split("\n");
        StringBuilder descBuilder = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("场景") || trimmed.startsWith("地点")) {
                psb.sceneDescription = extractValue(trimmed);
            } else if (trimmed.startsWith("氛围") || trimmed.startsWith("气氛")) {
                psb.atmosphere = extractValue(trimmed);
            } else if (trimmed.startsWith("时间") || trimmed.startsWith("时段")) {
                psb.timeOfDay = extractValue(trimmed);
            } else if (trimmed.startsWith("对话") || trimmed.startsWith("台词")) {
                psb.dialogue = extractValue(trimmed);
            } else if (trimmed.startsWith("旁白") || trimmed.startsWith("独白") || trimmed.startsWith("内心")) {
                psb.voiceover = extractValue(trimmed);
            } else {
                descBuilder.append(trimmed).append("\n");
            }
        }

        psb.description = descBuilder.toString().trim();
        psb.rawDescription = body;
        // 直通模式的提示词 = 整个分镜描述
        psb.expressPrompt = body;
    }

    // ==================== 辅助方法 ====================

    /**
     * 提取 Markdown 中的特定章节
     * 例: extractSection(text, "角色", "场景|道具") → 提取"角色"章节到"场景"章节之前的内容
     */
    private String extractSection(String text, String sectionTitle, String nextSectionPattern) {
        // 匹配 ## 角色设定 / ## 角色 / ## Character
        Pattern startPattern = Pattern.compile(
                "^##\\s+.*?" + sectionTitle + ".*\\n",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher startMatcher = startPattern.matcher(text);

        if (!startMatcher.find()) return null;

        int start = startMatcher.end();

        // 找下一个章节标题
        Pattern endPattern = Pattern.compile(
                "^##\\s+.*?(" + nextSectionPattern + ").*\\n",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher endMatcher = endPattern.matcher(text.substring(start));

        if (endMatcher.find()) {
            return text.substring(start, start + endMatcher.start());
        }

        // 没有下一个章节，取到末尾
        return text.substring(start);
    }

    private String extractValue(String line) {
        int colonIdx = line.indexOf("：");
        if (colonIdx == -1) {
            colonIdx = line.indexOf(":");
        }
        if (colonIdx == -1) return line;

        String value = line.substring(colonIdx + 1).trim();
        return value.isEmpty() ? line : value;
    }

    // ==================== 内部数据类 ====================

    @Data
    public static class ParseResult {
        private List<CharacterAsset> characters = new ArrayList<>();
        private List<SceneAsset> scenes = new ArrayList<>();
        private List<PropAsset> props = new ArrayList<>();
        private List<Storyboard> storyboards = new ArrayList<>();
    }

    @Data
    static class ParsedCharacter {
        String name;
        String roleRef;
        String description;
        String appearance;
        String personality;
    }

    @Data
    static class ParsedScene {
        String name;
        String locationRef;
        String description;
        String atmosphere;
        String lighting;
    }

    @Data
    static class ParsedProp {
        String name;
        String propRef;
        String description;
        String material;
    }

    @Data
    static class ParsedStoryboard {
        String shotId;
        String description;
        String rawDescription;
        String sceneDescription;
        String atmosphere;
        String timeOfDay;
        String dialogue;
        String voiceover;
        String expressPrompt;
    }
}