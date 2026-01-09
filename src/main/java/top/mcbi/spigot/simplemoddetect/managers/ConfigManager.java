package top.mcbi.spigot.simplemoddetect.managers;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class ConfigManager {
    private final JavaPlugin plugin;
    @Getter
    private File configFile;
    @Getter
    private FileConfiguration config;

    // Getters and Setters
    @Getter
    private Set<String> blockedMods;
    @Getter
    private Set<String> whitelistedMods;
    @Getter
    private boolean enableWhitelistMode;
    @Getter
    private String kickMessage;
    @Getter
    private boolean debugMode;
    @Getter
    private String fallbackServer;
    @Getter
    private String fallbackMessage;

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

            blockedMods = new HashSet<>(config.getStringList("blocked-mods"));
            whitelistedMods = new HashSet<>(config.getStringList("whitelisted-mods"));
            enableWhitelistMode = config.getBoolean("whitelist-mode", false);
            kickMessage = config.getString("kick-message",
                "§c您使用的模组不被允许进入此服务器\n§6被检测到的模组: %mods%\n§e请移除这些模组后重新加入");

            debugMode = config.getBoolean("debug-mode", false);
            fallbackServer = config.getString("fallback-server", "");
            fallbackMessage = config.getString("fallback-message",
                "§c您使用的模组不被允许进入此服务器\n§6被检测到的模组: %mods%\n§e正在将您传送到兼容服务器...");

            plugin.getLogger().info("已加载 " + blockedMods.size() + " 个被阻止的模组");
            plugin.getLogger().info("模式: " + (enableWhitelistMode ? "白名单模式" : "黑名单模式"));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "加载配置文件时出错", e);
        }
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

    public void setFallbackServer(String fallbackServer) {
        this.fallbackServer = fallbackServer;
        config.set("fallback-server", fallbackServer);
        saveConfig();
    }
}

