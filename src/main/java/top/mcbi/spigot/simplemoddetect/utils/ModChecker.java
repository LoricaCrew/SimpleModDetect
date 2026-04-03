package top.mcbi.spigot.simplemoddetect.utils;

import top.mcbi.spigot.simplemoddetect.managers.ConfigManager;
import top.mcbi.spigot.simplemoddetect.managers.ConfigManager.ChannelModConfig;

import java.util.ArrayList;
import java.util.List;

public class ModChecker {
    private final ConfigManager configManager;

    public ModChecker(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<ChannelModConfig> checkMods(List<String> playerMods) {
        List<ChannelModConfig> matchedConfigs = new ArrayList<>();
        for (ChannelModConfig config : configManager.getChannelMods()) {
            if (matchesAnyPlayerMod(config, playerMods)) {
                matchedConfigs.add(config);
            }
        }
        return matchedConfigs;
    }

    public String findMatchedModId(ChannelModConfig config, List<String> playerMods) {
        for (String playerMod : playerMods) {
            for (String match : config.matches) {
                if (matches(playerMod, match)) {
                    return playerMod;
                }
            }
        }
        return config.name;
    }

    public boolean isValidModId(String modId) {
        // 有效的mod ID通常包含字母、数字、下划线、连字符
        if (!modId.matches("[a-zA-Z0-9_-]+")) {
            return false;
        }

        // 排除常见的关键词
        String[] excluded = {"the", "and", "for", "mod", "api", "lib", "core", "common"};
        for (String exclude : excluded) {
            if (modId.equalsIgnoreCase(exclude)) {
                return false;
            }
        }

        // 检查长度
        if (modId.length() < 2 || modId.length() > 64) {
            return false;
        }

        return true;
    }

    public boolean isBaseChannel(String modId) {
        return modId.equals("minecraft") ||
            modId.equals("fabric") ||
            modId.equals("forge") ||
            modId.equals("vanilla") ||
            modId.isEmpty();
    }

    private boolean matchesAnyPlayerMod(ChannelModConfig config, List<String> playerMods) {
        for (String playerMod : playerMods) {
            for (String match : config.matches) {
                if (matches(playerMod, match)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matches(String playerMod, String match) {
        String normalizedPlayerMod = playerMod.toLowerCase();
        String normalizedMatch = match.toLowerCase();
        return normalizedPlayerMod.equals(normalizedMatch)
            || normalizedPlayerMod.contains(normalizedMatch)
            || normalizedMatch.contains(normalizedPlayerMod);
    }
}

