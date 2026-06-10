package com.meowantixray.antixray;

import com.meowantixray.MeowAntiXrayMod;
import com.meowantixray.platform.PlatformHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FakeOreConfig {
    public boolean enabled = true;
    public int engineMode = 2;
    public boolean enforceEngineMode2 = true;
    public int maxBlockHeight = 64;
    public boolean onlyObfuscateHiddenBlocks = false;
    public boolean mode2ObfuscateReplacementBlocks = true;
    public boolean guaranteeHideAllHiddenBlocks = true;
    public boolean lavaObscures = false;
    public boolean usePermission = false;
    public String bypassPermission = "paper.antixray.bypass";
    public boolean asyncChunkRewrite = true;
    public int asyncWorkerThreads = 1;
    public int asyncQueueSize = 16;
    public int updateRadius = 2;
    public int updateIntervalTicks = 10;
    public int maxBlocksPerChunk = 32768;
    public List<String> hiddenBlocks = defaultHiddenBlocks();
    public List<String> replacementBlocks = defaultReplacementBlocks("overworld");
    public final Map<String, DimensionSettings> dimensionSettings = new LinkedHashMap<>();
    private String loadSummary = "defaults in memory";

    public static final class DimensionSettings {
        public boolean enabled = true;
        public int engineMode = 2;
        public int maxBlockHeight = 64;
        public boolean onlyObfuscateHiddenBlocks = false;
        public boolean mode2ObfuscateReplacementBlocks = true;
        public boolean guaranteeHideAllHiddenBlocks = true;
        public boolean lavaObscures = false;
        public boolean usePermission = false;
        public String bypassPermission = "paper.antixray.bypass";
        public List<String> hiddenBlocks = defaultHiddenBlocks();
        public List<String> replacementBlocks = defaultReplacementBlocks("overworld");
    }

    public static FakeOreConfig loadOrCreate() {
        Path path = PlatformHelper.configDir().resolve("meowantixray.yml");
        return loadOrCreate(path);
    }

    static FakeOreConfig loadOrCreate(Path path) {
        FakeOreConfig config = new FakeOreConfig();
        config.setDimensionDefaults();
        if (!Files.exists(path)) {
            config.writeConfig(path);
            config.loadSummary = "generated default config at " + path;
            return config;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            config.read(lines);
            boolean supplemented = false;
            try {
                supplemented = config.supplementMissingKeys(path, lines);
            } catch (Exception supplementException) {
                MeowAntiXrayMod.LOGGER.warn("Meow Anti-Xray config supplement failed, keeping existing file: {}", path, supplementException);
            }
            config.loadSummary = supplemented
                ? "loaded config from " + path + " and supplemented missing keys"
                : "loaded config from " + path;
        } catch (Exception exception) {
            config.loadSummary = "failed to read config, using defaults: " + path;
            MeowAntiXrayMod.LOGGER.warn("Meow Anti-Xray config load failed, using defaults: {}", path, exception);
            config.writeConfig(path);
        }
        return config;
    }

    public String loadSummary() {
        return loadSummary;
    }

    private boolean supplementMissingKeys(Path path, List<String> lines) throws IOException {
        List<InsertFragment> inserts = new ArrayList<>();
        int antiXrayIndex = findTopLevelSection(lines, "anti-xray:");
        if (antiXrayIndex < 0) {
            List<String> updated = new ArrayList<>(lines);
            if (!updated.isEmpty() && !updated.get(updated.size() - 1).isBlank()) {
                updated.add("");
            }
            updated.addAll(renderConfigLines());
            writeLines(path, updated);
            return true;
        }

        int antiXrayEnd = findBlockEnd(lines, antiXrayIndex, 0);
        collectGlobalSupplements(lines, antiXrayIndex, antiXrayEnd, inserts);
        if (inserts.isEmpty()) {
            return false;
        }

        List<String> updated = new ArrayList<>(lines);
        for (int i = inserts.size() - 1; i >= 0; i--) {
            InsertFragment insert = inserts.get(i);
            updated.addAll(insert.index, insert.lines);
        }
        writeLines(path, updated);
        return true;
    }

    private void collectGlobalSupplements(List<String> lines, int antiXrayIndex, int antiXrayEnd, List<InsertFragment> inserts) {
        Set<String> presentGlobalScalars = new HashSet<>();
        int hiddenBlocksIndex = -1;
        int replacementBlocksIndex = -1;
        int dimensionSettingsIndex = -1;

        for (int i = antiXrayIndex + 1; i < antiXrayEnd; i++) {
            String content = contentOf(lines.get(i));
            if (content.isBlank()) {
                continue;
            }
            int indent = countIndent(stripComment(lines.get(i)));
            if (indent != 2) {
                continue;
            }
            String key = keyOf(content);
            if (isGlobalScalarKey(key)) {
                presentGlobalScalars.add(key);
            } else if ("hidden-blocks".equals(key)) {
                hiddenBlocksIndex = i;
            } else if ("replacement-blocks".equals(key)) {
                replacementBlocksIndex = i;
            } else if ("dimension-settings".equals(key)) {
                dimensionSettingsIndex = i;
            }
        }

        List<String> missingScalars = renderMissingGlobalScalarLines(presentGlobalScalars);
        if (!missingScalars.isEmpty()) {
            inserts.add(new InsertFragment(firstExistingIndex(hiddenBlocksIndex, replacementBlocksIndex, dimensionSettingsIndex, antiXrayEnd), missingScalars));
        }
        if (hiddenBlocksIndex < 0) {
            inserts.add(new InsertFragment(firstExistingIndex(replacementBlocksIndex, dimensionSettingsIndex, antiXrayEnd), renderGlobalListSection("hidden-blocks", hiddenBlocks)));
        }
        if (replacementBlocksIndex < 0) {
            inserts.add(new InsertFragment(firstExistingIndex(dimensionSettingsIndex, antiXrayEnd), renderGlobalListSection("replacement-blocks", replacementBlocks)));
        }
        if (dimensionSettingsIndex < 0) {
            inserts.add(new InsertFragment(antiXrayEnd, renderDimensionSettingsSection()));
            return;
        }

        int dimensionSettingsEnd = findBlockEnd(lines, dimensionSettingsIndex, 2);
        for (DimensionBlockInfo block : parseDimensionBlocks(lines, dimensionSettingsIndex + 1, dimensionSettingsEnd)) {
            collectDimensionSupplements(block, inserts);
        }
    }

    private void collectDimensionSupplements(DimensionBlockInfo block, List<InsertFragment> inserts) {
        DimensionSettings ds = dimensionSettings.get(block.key);
        if (ds == null) {
            return;
        }

        List<String> missingScalars = renderMissingDimensionScalarLines(ds, block.presentScalars);
        if (!missingScalars.isEmpty()) {
            inserts.add(new InsertFragment(firstExistingIndex(block.hiddenBlocksIndex, block.replacementBlocksIndex, block.endIndex), missingScalars));
        }
        if (block.hiddenBlocksIndex < 0) {
            inserts.add(new InsertFragment(firstExistingIndex(block.replacementBlocksIndex, block.endIndex), renderDimensionListSection("hidden-blocks", ds.hiddenBlocks)));
        }
        if (block.replacementBlocksIndex < 0) {
            inserts.add(new InsertFragment(block.endIndex, renderDimensionListSection("replacement-blocks", ds.replacementBlocks)));
        }
    }

    private List<DimensionBlockInfo> parseDimensionBlocks(List<String> lines, int start, int end) {
        List<DimensionBlockInfo> blocks = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<String> keys = new ArrayList<>();

        for (int i = start; i < end; i++) {
            String content = contentOf(lines.get(i));
            if (content.isBlank()) {
                continue;
            }
            int indent = countIndent(stripComment(lines.get(i)));
            if (indent == 4 && content.endsWith(":")) {
                starts.add(i);
                keys.add(normalizeDimensionKey(sectionNameOf(content)));
            }
        }

        for (int i = 0; i < starts.size(); i++) {
            int blockStart = starts.get(i);
            int blockEnd = i + 1 < starts.size() ? starts.get(i + 1) : end;
            Set<String> presentScalars = new HashSet<>();
            int hiddenBlocksIndex = -1;
            int replacementBlocksIndex = -1;

            for (int j = blockStart + 1; j < blockEnd; j++) {
                String content = contentOf(lines.get(j));
                if (content.isBlank()) {
                    continue;
                }
                int indent = countIndent(stripComment(lines.get(j)));
                if (indent != 6) {
                    continue;
                }
                String key = keyOf(content);
                if (isDimensionScalarKey(key)) {
                    presentScalars.add(key);
                } else if ("hidden-blocks".equals(key)) {
                    hiddenBlocksIndex = j;
                } else if ("replacement-blocks".equals(key)) {
                    replacementBlocksIndex = j;
                }
            }

            blocks.add(new DimensionBlockInfo(keys.get(i), blockEnd, hiddenBlocksIndex, replacementBlocksIndex, presentScalars));
        }

        return blocks;
    }

    private void setDimensionDefaults() {
        dimensionSettings.clear();

        DimensionSettings overworld = new DimensionSettings();
        overworld.replacementBlocks = defaultReplacementBlocks("overworld");
        dimensionSettings.put("minecraft:overworld", overworld);

        DimensionSettings nether = new DimensionSettings();
        nether.hiddenBlocks = new ArrayList<>(List.of(
            "minecraft:ancient_debris",
            "minecraft:nether_quartz_ore",
            "minecraft:nether_gold_ore"
        ));
        nether.replacementBlocks = defaultReplacementBlocks("nether");
        dimensionSettings.put("minecraft:the_nether", nether);

        DimensionSettings end = new DimensionSettings();
        end.hiddenBlocks = new ArrayList<>(List.of(
            "minecraft:diamond_ore",
            "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore",
            "minecraft:deepslate_emerald_ore"
        ));
        end.replacementBlocks = defaultReplacementBlocks("end");
        dimensionSettings.put("minecraft:the_end", end);
    }

    private void read(List<String> lines) {
        String section = "";
        String currentDim = null;
        String currentList = "";
        boolean globalHiddenBlocksSpecified = false;
        boolean globalReplacementBlocksSpecified = false;

        for (String rawLine : lines) {
            String lineNoComment = stripComment(rawLine);
            if (lineNoComment.isBlank()) {
                continue;
            }
            int indent = countIndent(lineNoComment);
            String line = lineNoComment.trim();

            if ("anti-xray:".equals(line)) {
                continue;
            }

            if (indent == 2) {
                currentDim = null;
                currentList = "";
                if ("hidden-blocks:".equals(line)) {
                    section = "global-hidden";
                    globalHiddenBlocksSpecified = true;
                    hiddenBlocks = new ArrayList<>();
                    continue;
                }
                if ("replacement-blocks:".equals(line)) {
                    section = "global-replacement";
                    globalReplacementBlocksSpecified = true;
                    replacementBlocks = new ArrayList<>();
                    continue;
                }
                if ("dimension-settings:".equals(line)) {
                    section = "dimensions";
                    continue;
                }

                section = "global";
                String scalarKey = keyOf(line);
                if ("hidden-blocks".equals(scalarKey)) {
                    globalHiddenBlocksSpecified = true;
                } else if ("replacement-blocks".equals(scalarKey)) {
                    globalReplacementBlocksSpecified = true;
                }
                applyGlobalScalar(line);
                continue;
            }

            if (indent == 4) {
                if ("global-hidden".equals(section) && line.startsWith("- ")) {
                    hiddenBlocks.add(parseString(line.substring(2), ""));
                    continue;
                }
                if ("global-replacement".equals(section) && line.startsWith("- ")) {
                    replacementBlocks.add(parseString(line.substring(2), ""));
                    continue;
                }

                if ("dimensions".equals(section)) {
                    if (line.endsWith(":")) {
                        currentDim = normalizeDimensionKey(line.substring(0, line.length() - 1));
                        dimensionSettings.putIfAbsent(currentDim, new DimensionSettings());
                        currentList = "";
                    }
                }
                continue;
            }

            if (indent == 6 && currentDim != null) {
                DimensionSettings ds = dimensionSettings.get(currentDim);
                if ("hidden-blocks:".equals(line)) {
                    currentList = "dim-hidden";
                    ds.hiddenBlocks = new ArrayList<>();
                    continue;
                }
                if ("replacement-blocks:".equals(line)) {
                    currentList = "dim-replacement";
                    ds.replacementBlocks = new ArrayList<>();
                    continue;
                }
                currentList = "";
                applyDimensionScalar(ds, line);
                continue;
            }

            if (indent >= 8 && currentDim != null && line.startsWith("- ")) {
                DimensionSettings ds = dimensionSettings.get(currentDim);
                String item = parseString(line.substring(2), "");
                if ("dim-hidden".equals(currentList)) {
                    ds.hiddenBlocks.add(item);
                } else if ("dim-replacement".equals(currentList)) {
                    ds.replacementBlocks.add(item);
                }
            }
        }

        if (!globalHiddenBlocksSpecified && hiddenBlocks.isEmpty()) {
            hiddenBlocks = defaultHiddenBlocks();
        }
        if (!globalReplacementBlocksSpecified && replacementBlocks.isEmpty()) {
            replacementBlocks = defaultReplacementBlocks("overworld");
        }
        if (dimensionSettings.isEmpty()) {
            setDimensionDefaults();
        }
    }

    private void applyGlobalScalar(String line) {
        int idx = line.indexOf(':');
        if (idx <= 0) {
            return;
        }
        String key = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
        String value = line.substring(idx + 1).trim();
        switch (key) {
            case "enabled" -> enabled = parseBoolean(value, enabled);
            case "engine-mode" -> engineMode = parseInt(value, 1, 3, engineMode);
            case "enforce-engine-mode-2" -> enforceEngineMode2 = parseBoolean(value, enforceEngineMode2);
            case "max-block-height" -> maxBlockHeight = normalizeMaxBlockHeight(parseInt(value, -64, 320, maxBlockHeight));
            case "only-obfuscate-hidden-blocks" -> onlyObfuscateHiddenBlocks = parseBoolean(value, onlyObfuscateHiddenBlocks);
            case "mode2-obfuscate-replacement-blocks" -> mode2ObfuscateReplacementBlocks = parseBoolean(value, mode2ObfuscateReplacementBlocks);
            case "guarantee-hide-all-hidden-blocks" -> guaranteeHideAllHiddenBlocks = parseBoolean(value, guaranteeHideAllHiddenBlocks);
            case "lava-obscures" -> lavaObscures = parseBoolean(value, lavaObscures);
            case "use-permission" -> usePermission = parseBoolean(value, usePermission);
            case "bypass-permission" -> bypassPermission = parseString(value, bypassPermission);
            case "async-chunk-rewrite" -> asyncChunkRewrite = parseBoolean(value, asyncChunkRewrite);
            case "async-worker-threads" -> asyncWorkerThreads = parseInt(value, 1, 4, asyncWorkerThreads);
            case "async-queue-size" -> asyncQueueSize = parseInt(value, 0, 1024, asyncQueueSize);
            case "update-radius" -> updateRadius = normalizeUpdateRadius(parseInt(value, 0, 2, updateRadius));
            case "update-interval-ticks" -> updateIntervalTicks = parseInt(value, 1, 100, updateIntervalTicks);
            case "max-blocks-per-chunk" -> maxBlocksPerChunk = parseInt(value, 64, 262144, maxBlocksPerChunk);
            case "hidden-blocks" -> {
                List<String> list = parseInlineList(value);
                if (list != null) {
                    hiddenBlocks = list;
                }
            }
            case "replacement-blocks" -> {
                List<String> list = parseInlineList(value);
                if (list != null) {
                    replacementBlocks = list;
                }
            }
            default -> {
            }
        }
    }

    private void applyDimensionScalar(DimensionSettings ds, String line) {
        int idx = line.indexOf(':');
        if (idx <= 0) {
            return;
        }
        String key = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
        String value = line.substring(idx + 1).trim();
        switch (key) {
            case "enabled" -> ds.enabled = parseBoolean(value, ds.enabled);
            case "engine-mode" -> ds.engineMode = parseInt(value, 1, 3, ds.engineMode);
            case "max-block-height" -> ds.maxBlockHeight = normalizeMaxBlockHeight(parseInt(value, -64, 320, ds.maxBlockHeight));
            case "only-obfuscate-hidden-blocks" -> ds.onlyObfuscateHiddenBlocks = parseBoolean(value, ds.onlyObfuscateHiddenBlocks);
            case "mode2-obfuscate-replacement-blocks" -> ds.mode2ObfuscateReplacementBlocks = parseBoolean(value, ds.mode2ObfuscateReplacementBlocks);
            case "guarantee-hide-all-hidden-blocks" -> ds.guaranteeHideAllHiddenBlocks = parseBoolean(value, ds.guaranteeHideAllHiddenBlocks);
            case "lava-obscures" -> ds.lavaObscures = parseBoolean(value, ds.lavaObscures);
            case "use-permission" -> ds.usePermission = parseBoolean(value, ds.usePermission);
            case "bypass-permission" -> ds.bypassPermission = parseString(value, ds.bypassPermission);
            case "hidden-blocks" -> {
                List<String> list = parseInlineList(value);
                if (list != null) {
                    ds.hiddenBlocks = list;
                }
            }
            case "replacement-blocks" -> {
                List<String> list = parseInlineList(value);
                if (list != null) {
                    ds.replacementBlocks = list;
                }
            }
            default -> {
            }
        }
    }

    private List<String> parseInlineList(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return null;
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : splitInlineList(body)) {
            String s = parseString(part, "");
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private boolean parseBoolean(String value, boolean fallback) {
        String parsed = stripYamlQuotes(value.trim());
        if ("true".equalsIgnoreCase(parsed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(parsed)) {
            return false;
        }
        return fallback;
    }

    private int parseInt(String value, int min, int max, int fallback) {
        try {
            int parsed = Integer.parseInt(stripYamlQuotes(value.trim()));
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String parseString(String value, String fallback) {
        String parsed = value == null ? "" : stripYamlQuotes(value.trim());
        return parsed.isEmpty() ? fallback : parsed;
    }

    static int normalizeMaxBlockHeight(int value) {
        return Math.floorDiv(value, 16) * 16;
    }

    static int normalizeUpdateRadius(int value) {
        return Math.max(0, Math.min(2, value));
    }

    private String stripComment(String line) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (quote == 0) {
                    quote = c;
                } else if (quote == c) {
                    quote = 0;
                }
            } else if (c == '#' && quote == 0) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private int countIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private String normalizeDimensionKey(String raw) {
        String key = stripYamlQuotes(raw.trim()).toLowerCase(Locale.ROOT);
        return switch (key) {
            case "overworld", "minecraft:overworld" -> "minecraft:overworld";
            case "nether", "the_nether", "minecraft:the_nether" -> "minecraft:the_nether";
            case "end", "the_end", "minecraft:the_end" -> "minecraft:the_end";
            default -> key;
        };
    }

    void writeConfig(Path path) {
        try {
            writeLines(path, renderConfigLines());
        } catch (IOException ignored) {
        }
    }

    private List<String> renderConfigLines() {
        List<String> lines = new ArrayList<>();
        lines.add("# Paper-like anti-xray config for Meow Anti-Xray");
        lines.add("# Existing config files are supplemented with new missing keys without overwriting your values.");
        lines.add("# Quoted YAML scalars, quoted list items, and inline lists are supported.");
        lines.add("anti-xray:");
        lines.addAll(renderMissingGlobalScalarLines(Set.of()));
        lines.addAll(renderGlobalListSection("hidden-blocks", hiddenBlocks));
        lines.addAll(renderGlobalListSection("replacement-blocks", replacementBlocks));
        lines.addAll(renderDimensionSettingsSection());
        return lines;
    }

    private List<String> renderMissingGlobalScalarLines(Set<String> presentKeys) {
        List<String> lines = new ArrayList<>();
        appendGlobalScalarIfMissing(lines, presentKeys, "enabled", enabled);
        appendGlobalScalarIfMissing(lines, presentKeys, "engine-mode", engineMode);
        appendGlobalScalarIfMissing(lines, presentKeys, "enforce-engine-mode-2", enforceEngineMode2);
        appendGlobalScalarIfMissing(lines, presentKeys, "max-block-height", maxBlockHeight);
        appendGlobalScalarIfMissing(lines, presentKeys, "only-obfuscate-hidden-blocks", onlyObfuscateHiddenBlocks);
        appendGlobalScalarIfMissing(lines, presentKeys, "mode2-obfuscate-replacement-blocks", mode2ObfuscateReplacementBlocks);
        appendGlobalScalarIfMissing(lines, presentKeys, "guarantee-hide-all-hidden-blocks", guaranteeHideAllHiddenBlocks);
        appendGlobalScalarIfMissing(lines, presentKeys, "lava-obscures", lavaObscures);
        appendGlobalScalarIfMissing(lines, presentKeys, "use-permission", usePermission);
        appendGlobalScalarIfMissing(lines, presentKeys, "bypass-permission", bypassPermission);
        appendGlobalScalarIfMissing(lines, presentKeys, "async-chunk-rewrite", asyncChunkRewrite);
        appendGlobalScalarIfMissing(lines, presentKeys, "async-worker-threads", asyncWorkerThreads);
        appendGlobalScalarIfMissing(lines, presentKeys, "async-queue-size", asyncQueueSize);
        appendGlobalScalarIfMissing(lines, presentKeys, "update-radius", updateRadius);
        appendGlobalScalarIfMissing(lines, presentKeys, "update-interval-ticks", updateIntervalTicks);
        appendGlobalScalarIfMissing(lines, presentKeys, "max-blocks-per-chunk", maxBlocksPerChunk);
        return lines;
    }

    private List<String> renderGlobalListSection(String key, List<String> values) {
        List<String> lines = new ArrayList<>();
        lines.add("  " + key + ":");
        for (String value : values) {
            lines.add("    - " + value);
        }
        return lines;
    }

    private List<String> renderDimensionSettingsSection() {
        List<String> lines = new ArrayList<>();
        lines.add("  dimension-settings:");
        for (Map.Entry<String, DimensionSettings> entry : dimensionSettings.entrySet()) {
            lines.addAll(renderDimensionBlock(entry.getKey(), entry.getValue()));
        }
        return lines;
    }

    private List<String> renderDimensionBlock(String dimensionKey, DimensionSettings ds) {
        List<String> lines = new ArrayList<>();
        lines.add("    " + dimensionKey + ":");
        lines.addAll(renderMissingDimensionScalarLines(ds, Set.of()));
        lines.addAll(renderDimensionListSection("hidden-blocks", ds.hiddenBlocks));
        lines.addAll(renderDimensionListSection("replacement-blocks", ds.replacementBlocks));
        return lines;
    }

    private List<String> renderMissingDimensionScalarLines(DimensionSettings ds, Set<String> presentKeys) {
        List<String> lines = new ArrayList<>();
        appendDimensionScalarIfMissing(lines, presentKeys, "enabled", ds.enabled);
        appendDimensionScalarIfMissing(lines, presentKeys, "engine-mode", ds.engineMode);
        appendDimensionScalarIfMissing(lines, presentKeys, "max-block-height", ds.maxBlockHeight);
        appendDimensionScalarIfMissing(lines, presentKeys, "only-obfuscate-hidden-blocks", ds.onlyObfuscateHiddenBlocks);
        appendDimensionScalarIfMissing(lines, presentKeys, "mode2-obfuscate-replacement-blocks", ds.mode2ObfuscateReplacementBlocks);
        appendDimensionScalarIfMissing(lines, presentKeys, "guarantee-hide-all-hidden-blocks", ds.guaranteeHideAllHiddenBlocks);
        appendDimensionScalarIfMissing(lines, presentKeys, "lava-obscures", ds.lavaObscures);
        appendDimensionScalarIfMissing(lines, presentKeys, "use-permission", ds.usePermission);
        appendDimensionScalarIfMissing(lines, presentKeys, "bypass-permission", ds.bypassPermission);
        return lines;
    }

    private List<String> renderDimensionListSection(String key, List<String> values) {
        List<String> lines = new ArrayList<>();
        lines.add("      " + key + ":");
        for (String value : values) {
            lines.add("        - " + value);
        }
        return lines;
    }

    private void appendGlobalScalarIfMissing(List<String> lines, Set<String> presentKeys, String key, Object value) {
        if (!presentKeys.contains(key)) {
            lines.add("  " + key + ": " + value);
        }
    }

    private void appendDimensionScalarIfMissing(List<String> lines, Set<String> presentKeys, String key, Object value) {
        if (!presentKeys.contains(key)) {
            lines.add("      " + key + ": " + value);
        }
    }

    private boolean isGlobalScalarKey(String key) {
        return switch (key) {
            case "enabled",
                 "engine-mode",
                 "enforce-engine-mode-2",
                 "max-block-height",
                 "only-obfuscate-hidden-blocks",
                 "mode2-obfuscate-replacement-blocks",
                 "guarantee-hide-all-hidden-blocks",
                 "lava-obscures",
                 "use-permission",
                  "bypass-permission",
                  "async-chunk-rewrite",
                  "async-worker-threads",
                  "async-queue-size",
                  "update-radius",
                 "update-interval-ticks",
                 "max-blocks-per-chunk" -> true;
            default -> false;
        };
    }

    private boolean isDimensionScalarKey(String key) {
        return switch (key) {
            case "enabled",
                 "engine-mode",
                 "max-block-height",
                 "only-obfuscate-hidden-blocks",
                 "mode2-obfuscate-replacement-blocks",
                 "guarantee-hide-all-hidden-blocks",
                 "lava-obscures",
                 "use-permission",
                 "bypass-permission" -> true;
            default -> false;
        };
    }

    private int findTopLevelSection(List<String> lines, String section) {
        for (int i = 0; i < lines.size(); i++) {
            String content = contentOf(lines.get(i));
            if (!content.isBlank() && countIndent(stripComment(lines.get(i))) == 0 && section.equals(content)) {
                return i;
            }
        }
        return -1;
    }

    private int findBlockEnd(List<String> lines, int startIndex, int parentIndent) {
        for (int i = startIndex + 1; i < lines.size(); i++) {
            String content = contentOf(lines.get(i));
            if (content.isBlank()) {
                continue;
            }
            if (countIndent(stripComment(lines.get(i))) <= parentIndent) {
                return i;
            }
        }
        return lines.size();
    }

    private int firstExistingIndex(int... indexes) {
        int result = Integer.MAX_VALUE;
        for (int index : indexes) {
            if (index >= 0 && index < result) {
                result = index;
            }
        }
        return result == Integer.MAX_VALUE ? 0 : result;
    }

    private String contentOf(String line) {
        return stripComment(line).trim();
    }

    private String keyOf(String line) {
        int idx = line.indexOf(':');
        if (idx < 0) {
            return "";
        }
        return line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
    }

    private String sectionNameOf(String line) {
        if (line.endsWith(":")) {
            return stripYamlQuotes(line.substring(0, line.length() - 1).trim());
        }
        return keyOf(line);
    }

    private List<String> splitInlineList(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || body.charAt(i - 1) != '\\')) {
                if (quote == 0) {
                    quote = c;
                } else if (quote == c) {
                    quote = 0;
                }
                current.append(c);
            } else if (c == ',' && quote == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private String stripYamlQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1).replace("\\" + first, String.valueOf(first));
        }
        return value;
    }

    private void writeLines(Path path, List<String> lines) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    private record InsertFragment(int index, List<String> lines) {
    }

    private static final class DimensionBlockInfo {
        private final String key;
        private final int endIndex;
        private final int hiddenBlocksIndex;
        private final int replacementBlocksIndex;
        private final Set<String> presentScalars;

        private DimensionBlockInfo(String key, int endIndex, int hiddenBlocksIndex, int replacementBlocksIndex, Set<String> presentScalars) {
            this.key = key;
            this.endIndex = endIndex;
            this.hiddenBlocksIndex = hiddenBlocksIndex;
            this.replacementBlocksIndex = replacementBlocksIndex;
            this.presentScalars = presentScalars;
        }
    }

    private static ArrayList<String> defaultHiddenBlocks() {
        return new ArrayList<>(List.of(
            "minecraft:diamond_ore",
            "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore",
            "minecraft:deepslate_emerald_ore",
            "minecraft:gold_ore",
            "minecraft:deepslate_gold_ore",
            "minecraft:iron_ore",
            "minecraft:deepslate_iron_ore",
            "minecraft:redstone_ore",
            "minecraft:deepslate_redstone_ore",
            "minecraft:lapis_ore",
            "minecraft:deepslate_lapis_ore",
            "minecraft:mossy_cobblestone",
            "minecraft:obsidian",
            "minecraft:chest",
            "minecraft:coal_ore",
            "minecraft:deepslate_coal_ore",
            "minecraft:copper_ore",
            "minecraft:deepslate_copper_ore",
            "minecraft:raw_copper_block",
            "minecraft:raw_iron_block",
            "minecraft:clay",
            "minecraft:ender_chest",
            "minecraft:ancient_debris",
            "minecraft:nether_quartz_ore",
            "minecraft:nether_gold_ore"
        ));
    }

    private static ArrayList<String> defaultReplacementBlocks(String dim) {
        return switch (dim) {
            case "nether" -> new ArrayList<>(List.of("minecraft:netherrack", "minecraft:basalt", "minecraft:blackstone"));
            case "end" -> new ArrayList<>(List.of("minecraft:end_stone"));
            default -> new ArrayList<>(List.of("minecraft:stone", "minecraft:oak_planks", "minecraft:deepslate"));
        };
    }
}
