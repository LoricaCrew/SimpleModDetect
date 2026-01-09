package top.mcbi.spigot.simplemoddetect.utils;

import top.mcbi.spigot.simplemoddetect.managers.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ModChecker {
    private final ConfigManager configManager;

    public ModChecker(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<String> checkMods(List<String> playerMods) {
        List<String> violatingMods = new ArrayList<>();

        if (configManager.isEnableWhitelistMode()) {
            // 白名单模式
            for (String mod : playerMods) {
                if (!isWhitelisted(mod)) {
                    violatingMods.add(mod);
                }
            }
        } else {
            // 黑名单模式
            for (String mod : playerMods) {
                if (isBlocked(mod)) {
                    violatingMods.add(mod);
                }
            }
        }

        return violatingMods;
    }

    private boolean isBlocked(String modId) {
        Set<String> blockedMods = configManager.getBlockedMods();
        
        // 精确匹配
        if (blockedMods.contains(modId)) {
            return true;
        }

        // 部分匹配
        for (String blockedMod : blockedMods) {
            if (modId.toLowerCase().contains(blockedMod.toLowerCase()) ||
                blockedMod.toLowerCase().contains(modId.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private boolean isWhitelisted(String modId) {
        Set<String> whitelistedMods = configManager.getWhitelistedMods();
        
        // 精确匹配
        if (whitelistedMods.contains(modId)) {
            return true;
        }

        // 部分匹配
        for (String whitelistedMod : whitelistedMods) {
            if (modId.toLowerCase().contains(whitelistedMod.toLowerCase()) ||
                whitelistedMod.toLowerCase().contains(modId.toLowerCase())) {
                return true;
            }
        }

        // 允许Fabric基础组件
        if (modId.contains("fabric-api") || modId.contains("fabricloader") ||
            modId.contains("minecraft") || modId.contains("java")) {
            return true;
        }

        return false;
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
}

