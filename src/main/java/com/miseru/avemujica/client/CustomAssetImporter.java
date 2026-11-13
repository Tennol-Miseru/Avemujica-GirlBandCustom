package com.miseru.avemujica.client;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * A resource-pack based personal asset importer, deliberately independent of
 * Minecraft/Fabric APIs so it also runs as a Java 21 single-file program.
 * The client owns the generated pack; Minecraft's normal resource loader handles
 * skin textures, sound definitions and translations, including F3+T reloads.
 */
public final class CustomAssetImporter {
    private CustomAssetImporter() {}
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    private static final Map<String, Set<String>> PERMITTED = new LinkedHashMap<>();
    private static final String PACK_NAME = "AveMujica_Custom_Assets";
    private static final long MAX_PNG = 4L * 1024 * 1024;
    private static final long MAX_OGG = 20L * 1024 * 1024;
    static {
        NAMES.put("oblivionis", "丰川祥子");
        NAMES.put("doloris", "三角初华");
        NAMES.put("mortis", "若叶睦");
        NAMES.put("timoris", "八幡海铃");
        NAMES.put("amoris", "祐天寺若麦");
        PERMITTED.put("oblivionis", Set.of("v145", "v146", "v147", "v148", "v149", "v150", "v151", "v152", "v153", "v154", "v155", "v156", "v157", "v158", "v159", "v160", "d190", "d191", "d192", "d204", "d205", "d206", "d535", "d560", "d585", "d610"));
        PERMITTED.put("doloris", Set.of("v081", "v082", "v083", "v084", "v085", "v086", "v087", "v088", "v089", "v090", "v091", "v092", "v093", "v094", "v095", "v096", "d106", "d107", "d108", "d120", "d121", "d531", "d556", "d581", "d606"));
        PERMITTED.put("mortis", Set.of("v097", "v098", "v099", "v100", "v101", "v102", "v103", "v104", "v105", "v106", "v107", "v108", "v109", "v110", "v111", "v112", "d127", "d128", "d129", "d141", "d142", "d532", "d557", "d582", "d607"));
        PERMITTED.put("timoris", Set.of("v113", "v114", "v115", "v116", "v117", "v118", "v119", "v120", "v121", "v122", "v123", "v124", "v125", "v126", "v127", "v128", "d148", "d149", "d150", "d162", "d163", "d533", "d558", "d583", "d608"));
        PERMITTED.put("amoris", Set.of("v129", "v130", "v131", "v132", "v133", "v134", "v135", "v136", "v137", "v138", "v139", "v140", "v141", "v142", "v143", "v144", "d169", "d170", "d171", "d183", "d184", "d534", "d559", "d584", "d609"));
    }

    public record Result(Path input, Path pack, int skins, int voices,
                         int subtitles, int instrumentSamples, List<String> warnings) {
        public String summary() {
            return "Ave Mujica 自定义素材：皮肤 " + skins + "、对话语音 " + voices
                    + "、字幕 " + subtitles + " 条、演奏 OGG " + instrumentSamples
                    + " 个；跳过/警告 " + warnings.size() + " 条";
        }
    }

    public static Path inputDir(Path gameDir) {
        return gameDir.resolve("config/avemujica/custom_assets");
    }

    public static Path guitarSamplesDir(Path gameDir) {
        return gameDir.resolve("config/avemujica/guitar_samples");
    }

    /** Standalone local folder for custom MIDI singing vowel notes, not dialogue clips. */
    public static Path vocalSamplesDir(Path gameDir) {
        return gameDir.resolve("config/avemujica/vocal_samples");
    }

    /** Explicit local client-side import; no bytes are sent to the server. */
    public static void selectGuitarOgg(Path gameDir, Path source) throws IOException {
        selectGuitarOgg(gameDir, source, "mortis");
    }

    public static void selectGuitarOgg(Path gameDir, Path source, String roleId) throws IOException {
        if (!roleId.equals("mortis") && !roleId.equals("doloris_guitar"))
            throw new IOException("只能为睦或初华的吉他导入 OGG");
        prepare(gameDir);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("找不到 OGG 文件，或目标是快捷方式");
        if (!source.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg"))
            throw new IOException("必须选择 .ogg 文件");
        List<String> warnings = new ArrayList<>();
        if (!validOgg(source, warnings)) throw new IOException(String.join("；", warnings));
        String fileName = roleId.equals("doloris_guitar") ? "uika_guitar.ogg" : "mortis_guitar.ogg";
        Path target = inputDir(gameDir).resolve("instruments/" + fileName);
        if (!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize()))
            copy(source, target);
    }

    /** Import one pitched human-vocal note (A3 / MIDI 57) without touching guitar slots. */
    public static void selectVocalOgg(Path gameDir, Path source) throws IOException {
        prepare(gameDir);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("找不到 OGG 文件，或目标是快捷方式");
        if (!source.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg"))
            throw new IOException("必须选择 .ogg 文件");
        List<String> warnings = new ArrayList<>();
        if (!validOgg(source, warnings)) throw new IOException(String.join("；", warnings));
        Path target = inputDir(gameDir).resolve("instruments/uika_vocal.ogg");
        if (!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize()))
            copy(source, target);
    }

    /** Registered dialogue slots by role, for the in-game character editor. */
    public static List<String> voiceSlots(String roleId) {
        Set<String> slots = PERMITTED.get(roleId);
        return slots == null ? List.of() : slots.stream().sorted().toList();
    }

    /** Original Chinese voice line for an existing slot, as a picker hint only. */
    public static String voiceDescription(String roleId, String slot) {
        if (!PERMITTED.containsKey(roleId) || !PERMITTED.get(roleId).contains(slot)) return "";
        try (InputStream in = CustomAssetImporter.class.getClassLoader()
                .getResourceAsStream("assets/avemujica/custom_voice_index.csv")) {
            if (in == null) return "";
            try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.replace("\uFEFF", "").split(",", 6);
                    if (fields.length >= 5 && fields[0].trim().equals(roleId)
                            && fields[2].trim().equals(slot + ".ogg")) return fields[4].trim();
                }
            }
        } catch (IOException ignored) { }
        return "";
    }

    /** Accept any original PNG filename; the editor chooses its target role. */
    public static void selectCharacterSkin(Path gameDir, String roleId, Path source) throws IOException {
        if (!PERMITTED.containsKey(roleId)) throw new IOException("无效的角色");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("找不到普通 PNG 文件");
        if (!source.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
            throw new IOException("请选择 PNG 文件");
        List<String> warnings = new ArrayList<>();
        if (!validPng(source, true, warnings)) throw new IOException(String.join("；", warnings));
        prepare(gameDir);
        Path target = inputDir(gameDir).resolve("skins").resolve(roleId + ".png");
        if (!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) copy(source, target);
    }

    /** Accept any original OGG filename, but never arbitrary sound-event IDs. */
    public static void selectCharacterVoice(Path gameDir, String roleId, String slot, Path source) throws IOException {
        if (!PERMITTED.containsKey(roleId) || !PERMITTED.get(roleId).contains(slot))
            throw new IOException("该角色不存在此语音槽位");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("找不到普通 OGG 文件");
        if (!source.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg"))
            throw new IOException("请选择 OGG 文件");
        List<String> warnings = new ArrayList<>();
        if (!validOgg(source, warnings)) throw new IOException(String.join("；", warnings));
        prepare(gameDir);
        Path target = inputDir(gameDir).resolve("voices").resolve(roleId).resolve(slot + ".ogg");
        if (!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) copy(source, target);
    }

    /** Removing the source override and rebuilding reverts to the original mod asset. */
    public static void resetCharacterSkin(Path gameDir, String roleId) throws IOException {
        if (!PERMITTED.containsKey(roleId)) throw new IOException("无效的角色");
        Files.deleteIfExists(inputDir(gameDir).resolve("skins").resolve(roleId + ".png"));
    }

    public static void resetCharacterVoice(Path gameDir, String roleId, String slot) throws IOException {
        if (!PERMITTED.containsKey(roleId) || !PERMITTED.get(roleId).contains(slot))
            throw new IOException("该角色不存在此语音槽位");
        Files.deleteIfExists(inputDir(gameDir).resolve("voices").resolve(roleId).resolve(slot + ".ogg"));
    }

    /** A discoverable inbox for arbitrary-name candidate skins and voice clips. */
    public static Path candidateDir(Path gameDir) {
        return inputDir(gameDir).resolve("candidates");
    }

    public static Path packDir(Path gameDir) {
        return gameDir.resolve("resourcepacks").resolve(PACK_NAME);
    }

    public static void prepare(Path gameDir) throws IOException {
        Path input = inputDir(gameDir);
        Files.createDirectories(input.resolve("skins"));
        for (String role : PERMITTED.keySet())
            Files.createDirectories(input.resolve("voices").resolve(role));
        Files.createDirectories(input.resolve("subtitles"));
        Files.createDirectories(guitarSamplesDir(gameDir));
        Files.createDirectories(vocalSamplesDir(gameDir));
        Files.createDirectories(input.resolve("instruments"));
        Files.createDirectories(candidateDir(gameDir));
        Path guide = input.resolve("README_custom_assets.txt");
        if (!Files.exists(guide)) {
            Files.writeString(guide, """
                    Ave Mujica 自定义角色素材（客户端本地资源）

                    皮肤：skins/oblivionis.png 等，必须为 64x64 Minecraft PNG；推荐 Alex 细手臂皮肤。
                    语音：voices/oblivionis/v145.ogg 等，只能替换现有语音编号；须为 Ogg Vorbis。
                    字幕：subtitles/zh_cn.csv（可选）或 subtitles/en_us.csv；每行：角色目录,语音编号,字幕内容。
                    示例：oblivionis,v145,大家好，今天也请多关照
                    语音编号请看 custom_voice_index.csv。允许只替换其中几个文件，其余自动用模组原文件。

                    自定义电吉他：将 Ogg Vorbis 采样放入 config/avemujica/guitar_samples/
                    游戏的“成员乐器设置”分别提供初华吉他、睦吉他的自选 OGG 音色管理。两人使用独立文件，互不覆盖；不提供内置 OGG 乐器采样。
                    采样建议使用 A2 (110 Hz / MIDI 45) 单音、0.3～2 秒、单声道，不要提供整段歌曲。
                    游戏中输入 /avemujicaassets reload 可重新导入并刷新资源。
                    第一次导入后，请在 设置 -> 资源包 中启用 AveMujica_Custom_Assets。
                    资源包请放在其他覆盖同名素材的资源包之上。
                    该功能仅改变此客户端的外观、声音和字幕；不会改变 NPC 的 AI、战斗、存档或服务器内容。
                    音频时长尽量接近原台词，否则原有台词队列的排队时间可能不匹配。
                    自定义资源包请仅使用你拥有或获准使用的素材。
                    """, StandardCharsets.UTF_8);
        }
        // Separate README for existing installs whose original guide already exists.
        Path vocalGuide = input.resolve("README_演唱人声OGG.txt");
        if (!Files.exists(vocalGuide)) Files.writeString(vocalGuide, """
                Ave Mujica · 初华演奏人声 OGG
                1. 在成员乐器设置点击「初华人声 OGG」，导入自有 Ogg Vorbis 单音素材。
                2. 推荐 A3（220Hz/MIDI 57）单个清晰元音，时长约 0.3～2 秒；不是歌词或整首歌。
                3. 可复制素材至 config/avemujica/vocal_samples，选择导入。
                4. 实际生效的文件为 custom_assets/instruments/uika_vocal.ogg。
                5. 界面可在「原版竖琴」与「自选 OGG 人声」之间切换。
                6. 此接口仅替换初华 MIDI Vocal 音轨；与吉他采样和 126 条对话 OGG 无关。
                7. 需要让其他联机玩家听到同样的 OGG，他们也须启用包含此采样的资源包。
                """, StandardCharsets.UTF_8);
        Path index = input.resolve("custom_voice_index.csv");
        if (!Files.exists(index)) {
            try (InputStream in = CustomAssetImporter.class.getClassLoader()
                    .getResourceAsStream("assets/avemujica/custom_voice_index.csv")) {
                if (in != null) Files.copy(in, index);
                else {
                    // Standalone source-file tool: its classpath does not contain the mod resources.
                    Path projectCatalog = Path.of("src/main/resources/assets/avemujica/custom_voice_index.csv");
                    if (Files.isRegularFile(projectCatalog)) Files.copy(projectCatalog, index);
                }
            }
        }
    }

    /**
     * Rebuilds only this mod's dedicated generated resource pack.
     * A bad individual asset is skipped; failure never modifies source mods/worlds.
     */
    public static Result install(Path gameDir) throws IOException {
        prepare(gameDir);
        Path input = inputDir(gameDir);
        Path pack = packDir(gameDir);
        List<String> warnings = new ArrayList<>();
        // Generated pack is disposable. Delete stale assets to make removal = fallback.
        if (Files.exists(pack)) {
            if (Files.isSymbolicLink(pack)) throw new IOException("资源包目录不能是符号链接: " + pack);
            try (Stream<Path> paths = Files.walk(pack)) {
                for (Path p : paths.sorted((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount())).toList())
                    Files.delete(p);
            }
        }
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), """
                {"pack":{"pack_format":34,"description":"Ave Mujica 自定义皮肤 / 配音 / 字幕 (Fabric 1.21.1)"}}
                """, StandardCharsets.UTF_8);
        Path icon = gameDir.resolve("config/avemujica/custom_assets/pack.png");
        if (Files.isRegularFile(icon, LinkOption.NOFOLLOW_LINKS)) {
            if (validPng(icon, false, warnings)) Files.copy(icon, pack.resolve("pack.png"), StandardCopyOption.REPLACE_EXISTING);
        } else {
            // Pack menu icon reuses the mod's octopus icon. No new art dependency.
            try (InputStream in = CustomAssetImporter.class.getClassLoader()
                    .getResourceAsStream("assets/avemujica/textures/item/blue_octopus.png")) {
                if (in != null) Files.copy(in, pack.resolve("pack.png"));
            }
        }
        int skins = 0, voices = 0, subtitles = 0;
        List<String> replacedVoiceKeys = new ArrayList<>();
        for (String role : PERMITTED.keySet()) {
            Path src = input.resolve("skins").resolve(role + ".png");
            if (Files.isRegularFile(src, LinkOption.NOFOLLOW_LINKS)) {
                if (validPng(src, true, warnings)) {
                    copy(src, pack.resolve("assets/avemujica/textures/entity/skins/" + role + ".png"));
                    skins++;
                }
            }
            Path voiceDir = input.resolve("voices").resolve(role);
            if (Files.isDirectory(voiceDir, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> files = Files.list(voiceDir)) {
                    for (Path v : files.sorted().toList()) {
                        if (!Files.isRegularFile(v, LinkOption.NOFOLLOW_LINKS)) continue;
                        String name = v.getFileName().toString();
                        if (!name.endsWith(".ogg")) { warnings.add("非 OGG 文件已跳过: " + v); continue; }
                        String code = name.substring(0, name.length() - 4);
                        if (!PERMITTED.get(role).contains(code)) {
                            warnings.add("未注册的语音编号已跳过: " + v); continue;
                        }
                        if (!validOgg(v, warnings)) continue;
                        copy(v, pack.resolve("assets/avemujica/sounds/voice/" + role + "/" + name));
                        voices++;
                        replacedVoiceKeys.add(role + "." + code);
                    }
                }
            }
        }
        for (String locale : List.of("zh_cn", "en_us")) {
            Path csv = input.resolve("subtitles").resolve(locale + ".csv");
            Map<String, String> translations = new LinkedHashMap<>();
            // A different voice with the old subtitle is misleading. Use a neutral
            // placeholder unless the player explicitly supplies an updated caption.
            for (String key : replacedVoiceKeys) {
                String role = key.substring(0, key.indexOf('.'));
                String label = locale.equals("zh_cn")
                        ? NAMES.get(role) + "：（自定义语音）"
                        : NAMES.get(role) + ": [custom voice]";
                translations.put("subtitles.avemujica.voice." + key, label);
            }
            if (Files.isRegularFile(csv, LinkOption.NOFOLLOW_LINKS)) {
                try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
                    String line;
                    int row = 0;
                    while ((line = reader.readLine()) != null) {
                        row++;
                        line = line.replace("\uFEFF", "").trim();
                        if (line.isEmpty() || line.startsWith("#") || line.startsWith("角色目录,")) continue;
                        String[] fields = line.split(",", 3);
                        if (fields.length != 3 || !PERMITTED.containsKey(fields[0].trim())
                                || !PERMITTED.get(fields[0].trim()).contains(fields[1].trim())
                                || fields[2].isBlank() || fields[2].length() > 256) {
                            warnings.add("字幕格式或编号错误: " + csv.getFileName() + " 第 " + row + " 行");
                            continue;
                        }
                        translations.put("subtitles.avemujica.voice." + fields[0].trim() + "." + fields[1].trim(), fields[2].trim());
                        subtitles++;
                    }
                }
            }
            if (!translations.isEmpty()) {
                Path output = pack.resolve("assets/avemujica/lang/" + locale + ".json");
                Files.createDirectories(output.getParent());
                StringBuilder json = new StringBuilder("{\n");
                int i = 0;
                for (var e : translations.entrySet()) {
                    if (i++ > 0) json.append(",\n");
                    json.append("  \"").append(escape(e.getKey())).append("\": \"")
                            .append(escape(e.getValue())).append("\"");
                }
                Files.writeString(output, json.append("\n}\n").toString(), StandardCharsets.UTF_8);
            }
        }
        // No custom OGG is bundled with the mod. A user-uploaded single-note
        // OGG is copied to their resource pack and its sound event is replaced
        // (not mixed with the vanilla fallback event in the base sounds.json).
        Path guitar = input.resolve("instruments/mortis_guitar.ogg");
        boolean customMortis = Files.isRegularFile(guitar, LinkOption.NOFOLLOW_LINKS)
                && validOgg(guitar, warnings);
        if (customMortis) {
            copy(guitar, pack.resolve("assets/avemujica/sounds/instruments/mortis_guitar_custom.ogg"));
        }
        Path uikaGuitar = input.resolve("instruments/uika_guitar.ogg");
        boolean customUika = Files.isRegularFile(uikaGuitar, LinkOption.NOFOLLOW_LINKS)
                && validOgg(uikaGuitar, warnings);
        if (customUika) {
            copy(uikaGuitar, pack.resolve("assets/avemujica/sounds/instruments/uika_guitar_custom.ogg"));
        }
        Path uikaVocal = input.resolve("instruments/uika_vocal.ogg");
        boolean customVocal = Files.isRegularFile(uikaVocal, LinkOption.NOFOLLOW_LINKS)
                && validOgg(uikaVocal, warnings);
        if (customVocal) {
            copy(uikaVocal, pack.resolve("assets/avemujica/sounds/instruments/uika_vocal_custom.ogg"));
        }
        if (customMortis || customUika || customVocal) {
            StringBuilder guitarEvents = new StringBuilder("{\n");
            if (customMortis) {
                guitarEvents.append("  \"mortis_guitar_custom\": {\"replace\":true,\"sounds\":[{\"name\":\"avemujica:instruments/mortis_guitar_custom\",\"stream\":false}]}");
            }
            if (customUika) {
                if (customMortis) guitarEvents.append(",\n");
                guitarEvents.append("  \"uika_guitar_custom\": {\"replace\":true,\"sounds\":[{\"name\":\"avemujica:instruments/uika_guitar_custom\",\"stream\":false}]}");
            }
            if (customVocal) {
                if (customMortis || customUika) guitarEvents.append(",\n");
                guitarEvents.append("  \"uika_vocal_custom\": {\"replace\":true,\"sounds\":[{\"name\":\"avemujica:instruments/uika_vocal_custom\",\"stream\":false}]},\n");
                // The second-level stage must use a fixed-range packet AND a direct
                // 112-block client sample; referencing a short-range sound event
                // would still fade prematurely under some resource loaders.
                guitarEvents.append("  \"concert_uika_vocal_custom\": {\"replace\":true,\"sounds\":[{\"name\":\"avemujica:instruments/uika_vocal_custom\",\"stream\":false,\"attenuation_distance\":112}]}");
            }
            guitarEvents.append("\n}\n");
            Path soundsJson = pack.resolve("assets/avemujica/sounds.json");
            Files.createDirectories(soundsJson.getParent());
            Files.writeString(soundsJson, guitarEvents.toString(), StandardCharsets.UTF_8);
        }
        return new Result(input, pack, skins, voices, subtitles,
                (customMortis ? 1 : 0) + (customUika ? 1 : 0) + (customVocal ? 1 : 0),
                List.copyOf(warnings));
    }

    private static String escape(String text) {
        StringBuilder builder = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> { if (c < 32) builder.append(String.format("\\u%04x", (int)c)); else builder.append(c); }
            }
        }
        return builder.toString();
    }

    private static void copy(Path source, Path dest) throws IOException {
        Files.createDirectories(dest.getParent());
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean validPng(Path path, boolean skin, List<String> warnings) {
        try {
            if (Files.size(path) > MAX_PNG) throw new IOException("PNG 超过 4 MB");
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) throw new IOException("无法解码 PNG");
            if (skin && (image.getWidth() != 64 || image.getHeight() != 64))
                throw new IOException("皮肤必须是 64x64，实际为 " + image.getWidth() + "x" + image.getHeight());
            if (!skin && (image.getWidth() != image.getHeight() || image.getWidth() > 512))
                throw new IOException("资源包图标必须为不大于 512 的正方形 PNG");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] header = in.readNBytes(8);
                if (header.length != 8 || header[0] != (byte)137 || header[1] != 80 || header[2] != 78 || header[3] != 71)
                    throw new IOException("不是 PNG 格式");
            }
            return true;
        } catch (IOException e) {
            warnings.add("图片已跳过: " + path + "（" + e.getMessage() + "）");
            return false;
        }
    }

    private static boolean validOgg(Path path, List<String> warnings) {
        try {
            long size = Files.size(path);
            if (size == 0 || size > MAX_OGG) throw new IOException("OGG 必须在 0～20 MB 范围内");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] header = in.readNBytes(8192);
                if (header.length < 32 || !new String(header, 0, 4, StandardCharsets.ISO_8859_1).equals("OggS"))
                    throw new IOException("不是 Ogg 容器");
                if (!new String(header, StandardCharsets.ISO_8859_1).contains("vorbis"))
                    throw new IOException("必须是 Ogg Vorbis，Opus 格式不受支持");
            }
            return true;
        } catch (IOException e) {
            warnings.add("语音已跳过: " + path + "（" + e.getMessage() + "）");
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path game = os.contains("win")
                ? Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), ".minecraft")
                : os.contains("mac")
                    ? Path.of(System.getProperty("user.home"), "Library/Application Support/minecraft")
                    : Path.of(System.getProperty("user.home"), ".minecraft");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--game-dir") && i + 1 < args.length) game = Path.of(args[++i]);
        }
        Result result = install(game);
        System.out.println(result.summary());
        System.out.println("素材目录：" + result.input());
        System.out.println("资源包：" + result.pack());
        for (String warning : result.warnings()) System.out.println("警告：" + warning);
        System.out.println("在 设置 > 资源包 启用 AveMujica_Custom_Assets；需要重新导入可在游戏中运行 /avemujicaassets reload");
    }
}
