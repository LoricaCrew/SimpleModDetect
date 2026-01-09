package top.mcbi.spigot.simplemoddetect.managers;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.utils.ModChecker;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;

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

        List<String> violatingMods = modChecker.checkMods(mods);

        if (!violatingMods.isEmpty()) {
            ConfigManager configManager = plugin.getConfigManager();
            String fallbackServer = configManager.getFallbackServer();
            
            if (fallbackServer != null && !fallbackServer.isEmpty()) {
                sendToFallbackServer(player, violatingMods);
            } else {
                kickPlayer(player, violatingMods);
            }
        } else {
            plugin.getLogger().info("玩家 " + player.getName() + " 模组检查通过，检测到 " + mods.size() + " 个模组");
            if (plugin.getConfigManager().isDebugMode()) {
                for (String mod : mods) {
                    plugin.getLogger().info("  - " + mod);
                }
            }
        }
    }

    private void kickPlayer(Player player, List<String> violatingMods) {
        String kickMessage = plugin.getConfigManager().getKickMessage();
        String finalMessage = kickMessage.replace("%mods%", String.join(", ", violatingMods));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.kickPlayer(finalMessage);
                plugin.getLogger().warning("玩家 " + player.getName() + " 因使用违规模组被踢出: " + violatingMods);
            }
        });
    }

    private void sendToFallbackServer(Player player, List<String> violatingMods) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            ConfigManager configManager = plugin.getConfigManager();
            String fallbackMessage = configManager.getFallbackMessage();
            String message = fallbackMessage.replace("%mods%", String.join(", ", violatingMods));

            player.sendMessage(message);

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(configManager.getFallbackServer());

            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        });
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

