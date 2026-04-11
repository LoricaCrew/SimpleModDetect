package top.mcbi.spigot.simplemoddetect.managers;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
    private final List<TranslationModConfig> translationMods = new ArrayList<>();

    public static class ChannelModConfig {
        public final String name;
        public final List<String> matches;
        public final List<String> commands;

        public ChannelModConfig(String name, List<String> matches, List<String> commands) {
            this.name = name;
            this.matches = matches;
            this.commands = commands;
        }
    }

    public static class TranslationModConfig {
        public final String name;
        public final String key;
        public final List<String> commands;

        public TranslationModConfig(String name, String key, List<String> commands) {
            this.name = name;
            this.key = key;
            this.commands = commands;
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

                List<String> commands = new ArrayList<>(modSection.getStringList("commands"));
                channelMods.add(new ChannelModConfig(modName, matches, commands));
            }
        }

        plugin.getLogger().info("已加载 " + channelMods.size() + " 个频道检测模组配置");
    }

    private void loadTranslationDetectConfig() {
        translationEnabled = config.getBoolean("translation-detect.enabled", true);
        notifyStaff = config.getBoolean("translation-detect.notify-staff", true);
        notificationPermission = config.getString("translation-detect.notification-permission", "simplemoddetect.notify");

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

            List<String> commands = new ArrayList<>(modSection.getStringList("commands"));
            translationMods.add(new TranslationModConfig(modName, key, commands));
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
}
