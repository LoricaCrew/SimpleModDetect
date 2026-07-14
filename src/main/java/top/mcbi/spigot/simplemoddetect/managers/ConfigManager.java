package top.mcbi.spigot.simplemoddetect.managers;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class ConfigManager {
    private final JavaPlugin plugin;
    @Getter
    private File configFile;
    @Getter
    private FileConfiguration config;

    @Getter
    private boolean debugMode;

    @Getter
    private boolean channelDetectEnabled;
    @Getter
    private boolean channelNotifyStaff;
    @Getter
    private String channelNotificationPermission;
    @Getter
    private PunishmentAction channelAction;
    @Getter
    private final List<ChannelModConfig> channelMods = new ArrayList<>();

    @Getter
    private boolean disableMarlowCrystalOptimizer;

    @Getter
    private boolean translationEnabled;
    @Getter
    private boolean notifyStaff;
    @Getter
    private String notificationPermission;
    @Getter
    private long translationInitialCheckDelayTicks;
    @Getter
    private long translationOpenSignDelayTicks;
    @Getter
    private long translationNextBatchDelayTicks;
    @Getter
    private int translationStuckRestartSeconds;
    @Getter
    private int translationStuckRestartMaxAttempts;
    @Getter
    private int translationModsPerLine;
    @Getter
    private PunishmentAction translationAction;
    @Getter
    private final List<TranslationModConfig> translationMods = new ArrayList<>();

    public enum PunishmentType {
        SEND,
        COMMAND
    }

    public static class PunishmentAction {
        public final PunishmentType type;
        public final List<String> commands;
        public final String server;
        public final String message;

        public PunishmentAction(PunishmentType type, List<String> commands, String server, String message) {
            this.type = type;
            this.commands = commands;
            this.server = server;
            this.message = message;
        }
    }

    public static class ChannelModConfig {
        public final String name;
        public final List<String> matches;
        public final PunishmentAction action;

        public ChannelModConfig(String name, List<String> matches, PunishmentAction action) {
            this.name = name;
            this.matches = matches;
            this.action = action;
        }
    }

    public static class TranslationModConfig {
        public final String name;
        public final String key;
        public final PunishmentAction action;

        public TranslationModConfig(String name, String key, PunishmentAction action) {
            this.name = name;
            this.key = key;
            this.action = action;
        }
    }

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void createConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void loadConfig() {
        try {
            config.load(configFile);

            debugMode = config.getBoolean("debug-mode", false);
            disableMarlowCrystalOptimizer = config.getBoolean("disable-marlow-crystal-optimizer", true);

            loadChannelDetectConfig();
            loadTranslationDetectConfig();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "加载配置文件时出错", e);
        }
    }

    private void loadChannelDetectConfig() {
        channelDetectEnabled = config.getBoolean("channel-detect.enabled", true);
        channelNotifyStaff = config.getBoolean("channel-detect.notify-staff", true);
        channelNotificationPermission = config.getString("channel-detect.notification-permission", "simplemoddetect.notify");
        channelAction = parsePunishmentAction(config.getConfigurationSection("channel-detect"), "频道检测", true);

        channelMods.clear();
        ConfigurationSection modsSection = config.getConfigurationSection("channel-detect.mods");
        if (modsSection != null) {
            for (String modName : modsSection.getKeys(false)) {
                ConfigurationSection modSection = modsSection.getConfigurationSection(modName);
                if (modSection == null) {
                    continue;
                }

                List<String> matches = new ArrayList<>(modSection.getStringList("matches"));
                if (matches.isEmpty()) {
                    matches.addAll(modSection.getStringList("channels"));
                }

                String singleMatch = modSection.getString("match");
                if (matches.isEmpty() && singleMatch != null && !singleMatch.isBlank()) {
                    matches.add(singleMatch);
                }

                String singleChannel = modSection.getString("channel");
                if (matches.isEmpty() && singleChannel != null && !singleChannel.isBlank()) {
                    matches.add(singleChannel);
                }

                if (matches.isEmpty()) {
                    plugin.getLogger().warning("频道检测配置缺失 matches: " + modName);
                    continue;
                }

                PunishmentAction modAction = parsePunishmentAction(modSection, "频道检测模组 " + modName, false);
                if (modAction == null && channelAction == null) {
                    plugin.getLogger().warning("频道检测模组缺失可用 action: " + modName);
                }

                channelMods.add(new ChannelModConfig(modName, matches, modAction));
            }
        }

        plugin.getLogger().info("已加载 " + channelMods.size() + " 个频道检测模组配置");
    }

    private void loadTranslationDetectConfig() {
        translationEnabled = config.getBoolean("translation-detect.enabled", true);
        notifyStaff = config.getBoolean("translation-detect.notify-staff", true);
        notificationPermission = config.getString("translation-detect.notification-permission", "simplemoddetect.notify");
        translationInitialCheckDelayTicks = Math.max(0L, config.getLong("translation-detect.timing.initial-check-delay-ticks", 10L));
        translationOpenSignDelayTicks = Math.max(0L, config.getLong("translation-detect.timing.open-sign-delay-ticks", 1L));
        translationNextBatchDelayTicks = Math.max(0L, config.getLong("translation-detect.timing.next-batch-delay-ticks", 1L));
        translationStuckRestartSeconds = Math.max(0, config.getInt("translation-detect.timing.stuck-restart-seconds", 10));
        translationStuckRestartMaxAttempts = Math.max(0, config.getInt("translation-detect.timing.stuck-restart-max-attempts", 2));
        translationModsPerLine = Math.max(1, config.getInt("translation-detect.batch.mods-per-line", 1));
        translationAction = parsePunishmentAction(config.getConfigurationSection("translation-detect"), "翻译检测", true);

        translationMods.clear();
        ConfigurationSection modsSection = config.getConfigurationSection("translation-detect.mods");
        if (modsSection == null) {
            return;
        }

        for (String modName : modsSection.getKeys(false)) {
            ConfigurationSection modSection = modsSection.getConfigurationSection(modName);
            if (modSection == null) {
                continue;
            }

            String key = modSection.getString("key");
            if (key == null) {
                plugin.getLogger().warning("翻译检测配置缺失必要的 key: " + modName);
                continue;
            }

            PunishmentAction modAction = parsePunishmentAction(modSection, "翻译检测模组 " + modName, false);
            if (modAction == null && translationAction == null) {
                plugin.getLogger().warning("翻译检测模组缺失可用 action: " + modName);
            }

            translationMods.add(new TranslationModConfig(modName, key, modAction));
        }

        plugin.getLogger().info("已加载 " + translationMods.size() + " 个翻译检测模组配置");
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "保存配置文件时出错", e);
        }
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        config.set("debug-mode", debugMode);
        saveConfig();
    }

    private PunishmentAction parsePunishmentAction(ConfigurationSection detectSection, String detectType, boolean required) {
        if (detectSection == null) {
            if (required) {
                plugin.getLogger().warning(detectType + "配置缺失配置节点");
            }
            return null;
        }

        ConfigurationSection actionSection = detectSection.getConfigurationSection("action");
        if (actionSection == null) {
            List<String> legacyCommands = new ArrayList<>(detectSection.getStringList("commands"));
            if (!legacyCommands.isEmpty()) {
                return new PunishmentAction(PunishmentType.COMMAND, legacyCommands, null, null);
            }

            if (required) {
                plugin.getLogger().warning(detectType + "配置缺失 action");
            }
            return null;
        }

        String rawType = actionSection.getString("type", "COMMAND");
        PunishmentType type;
        try {
            type = PunishmentType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(detectType + "配置 action.type 无效: " + rawType);
            return null;
        }

        if (type == PunishmentType.COMMAND) {
            List<String> commands = new ArrayList<>(actionSection.getStringList("commands"));
            if (commands.isEmpty()) {
                plugin.getLogger().warning(detectType + "配置的 COMMAND 动作缺失 commands");
                return null;
            }

            return new PunishmentAction(type, commands, null, null);
        }

        String server = actionSection.getString("server");
        if (server == null || server.isBlank()) {
            plugin.getLogger().warning(detectType + "配置的 SEND 动作缺失 server");
            return null;
        }

        String message = actionSection.getString("message", "");
        return new PunishmentAction(type, List.of(), server, message);
    }
}
