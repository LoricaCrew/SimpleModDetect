package top.mcbi.spigot.simplemoddetect.managers;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;
import top.mcbi.spigot.simplemoddetect.managers.ConfigManager.ChannelModConfig;
import top.mcbi.spigot.simplemoddetect.utils.ModChecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModDetectionManager {
    private final SimpleModDetect plugin;
    private final ModChecker modChecker;
    private final Map<String, List<String>> playerMods = new HashMap<>();
    @Getter
    private final Map<String, List<String>> playerChannels = new HashMap<>();

    public ModDetectionManager(SimpleModDetect plugin, ModChecker modChecker) {
        this.plugin = plugin;
        this.modChecker = modChecker;
    }

    public void handleDetectedChannel(Player player, String channel) {
        if(!playerChannels.containsKey(player.getName())) {
            playerChannels.put(player.getName(), new ArrayList<>());
        }
        List<String> channels = playerChannels.get(player.getName());
        channels.add(channel);
    }

    public void checkPlayerChannels(Player player) {
        if (!playerChannels.containsKey(player.getName())) {
            playerChannels.put(player.getName(), new ArrayList<>());
        }
        List<String> channels = playerChannels.get(player.getName());
        List<ChannelModConfig> matchedConfigs = modChecker.checkMods(channels);
        if (matchedConfigs.isEmpty()) {
            plugin.getLogger().warning("玩家 %s 频道名检查通过，共有 %d 个频道 %c"
                    .formatted(player.getName(), channels.size(), channels.isEmpty() ? '。' : ':'));
            if (plugin.getConfigManager().isDebugMode()) {
                for (String channel : channels) {
                    plugin.getLogger().info("  - %s"
                            .formatted(channel));
                }
            }
            return;
        }

        List<String> sharedModNames = new ArrayList<>();
        List<String> sharedDetectedMods = new ArrayList<>();
        for (ChannelModConfig config : matchedConfigs) {
            String detectedMod = modChecker.findMatchedModId(config, channels);
            plugin.getLogger().warning("玩家 " + player.getName() + " 被检测到频道 " + config.name + "，实际标识: " + detectedMod);
            notifyStaff("玩家 " + player.getName() + " 被检测到使用 " + config.name + " (" + detectedMod + ")");

            if (config.action != null) {
                executePunishment(player, config.action, player.getName(), config.name, detectedMod);
                continue;
            }

            sharedModNames.add(config.name);
            sharedDetectedMods.add(detectedMod);
        }

        executeSharedPunishment(player, sharedModNames, sharedDetectedMods);
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

        List<String> sharedModNames = new ArrayList<>();
        List<String> sharedDetectedMods = new ArrayList<>();
        for (ChannelModConfig config : matchedConfigs) {
            String detectedMod = modChecker.findMatchedModId(config, mods);
            plugin.getLogger().warning("玩家 " + player.getName() + " 被检测到使用频道模组 " + config.name + "，实际标识: " + detectedMod);
            notifyStaff("玩家 " + player.getName() + " 被检测到使用 " + config.name + " (" + detectedMod + ")");

            if (config.action != null) {
                executePunishment(player, config.action, player.getName(), config.name, detectedMod);
                continue;
            }

            sharedModNames.add(config.name);
            sharedDetectedMods.add(detectedMod);
        }

        executeSharedPunishment(player, sharedModNames, sharedDetectedMods);
    }

    private void executeSharedPunishment(Player player, List<String> modNames, List<String> detectedMods) {
        if (modNames.isEmpty()) {
            return;
        }

        executePunishment(
            player,
            plugin.getConfigManager().getChannelAction(),
            player.getName(),
            String.join(", ", modNames),
            String.join(", ", detectedMods)
        );
    }

    private void executePunishment(Player player, ConfigManager.PunishmentAction action, String playerName, String modName, String detectedMod) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", playerName);
        placeholders.put("%mod_name%", modName);
        placeholders.put("%detected_mod%", detectedMod);
        plugin.getPunishmentExecutor().execute(player, action, placeholders);
    }

    public void notifyStaff(String message) {
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

