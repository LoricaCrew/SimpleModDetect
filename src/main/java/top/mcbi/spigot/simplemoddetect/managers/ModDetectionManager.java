package top.mcbi.spigot.simplemoddetect.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;
import top.mcbi.spigot.simplemoddetect.managers.ConfigManager.ChannelModConfig;
import top.mcbi.spigot.simplemoddetect.utils.ModChecker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModDetectionManager {
    private final SimpleModDetect plugin;
    private final ModChecker modChecker;
    private final Map<String, List<String>> playerMods = new HashMap<>();

    public ModDetectionManager(SimpleModDetect plugin, ModChecker modChecker) {
        this.plugin = plugin;
        this.modChecker = modChecker;
    }

    public void handleDetectedMods(Player player, List<String> mods) {
        playerMods.put(player.getName(), mods);

        List<ChannelModConfig> matchedConfigs = modChecker.checkMods(mods);
        if (matchedConfigs.isEmpty()) {
            plugin.getLogger().info("玩家 " + player.getName() + " 模组检查通过，检测到 " + mods.size() + " 个模组");
            if (plugin.getConfigManager().isDebugMode()) {
                for (String mod : mods) {
                    plugin.getLogger().info("  - " + mod);
                }
            }
            return;
        }

        for (ChannelModConfig config : matchedConfigs) {
            String detectedMod = modChecker.findMatchedModId(config, mods);
            plugin.getLogger().warning("玩家 " + player.getName() + " 被检测到使用频道模组 " + config.name + "，实际标识: " + detectedMod);
            notifyStaff("玩家 " + player.getName() + " 被检测到使用 " + config.name + " (" + detectedMod + ")");
            executeCommands(player, config, detectedMod);
        }
    }

    private void executeCommands(Player player, ChannelModConfig config, String detectedMod) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (config.commands == null || config.commands.isEmpty()) {
                return;
            }

            for (String command : config.commands) {
                String finalCommand = command
                    .replace("%player%", player.getName())
                    .replace("%mod_name%", config.name)
                    .replace("%detected_mod%", detectedMod);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            }
        });
    }

    private void notifyStaff(String message) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isChannelNotifyStaff()) {
            return;
        }

        Component staffNotification = Component.text("[SimpleModDetect] " + message, NamedTextColor.RED);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission(config.getChannelNotificationPermission())) {
                onlinePlayer.sendMessage(staffNotification);
            }
        }
    }

    public List<String> getPlayerMods(String playerName) {
        return playerMods.get(playerName);
    }

    public Map<String, List<String>> getAllPlayerMods() {
        return new HashMap<>(playerMods);
    }

    public void removePlayer(String playerName) {
        playerMods.remove(playerName);
    }
}

