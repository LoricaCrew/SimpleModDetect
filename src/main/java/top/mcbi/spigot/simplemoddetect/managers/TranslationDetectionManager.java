package top.mcbi.spigot.simplemoddetect.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;
import top.mcbi.spigot.simplemoddetect.managers.ConfigManager.TranslationModConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class TranslationDetectionManager implements Listener {
    private final SimpleModDetect plugin;
    private final Map<UUID, BlockData> originalBlockData = new HashMap<>();

    public TranslationDetectionManager(SimpleModDetect plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void checkPlayer(Player player) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isTranslationEnabled() || config.getTranslationMods().isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> openSignEditor(player), 10L);
    }

    private void openSignEditor(Player player) {
        try {
            Block block = player.getLocation().clone().add(0.0, -5.0, 0.0).getBlock();
            BlockData originalBlockType = block.getBlockData();
            UUID uuid = player.getUniqueId();
            originalBlockData.put(uuid, originalBlockType);

            block.setType(Material.OAK_SIGN, false);
            if (block.getState() instanceof Sign sign) {
                try {
                    Component content = Component.empty();
                    for (TranslationModConfig config : plugin.getConfigManager().getTranslationMods()) {
                        content = content.append(Component.text(" TTT: ").append(Component.translatable(config.key)));
                    }

                    SignSide backSide = sign.getSide(Side.BACK);
                    backSide.line(0, content);

                    sign.update(false, false);
                    sign.setAllowedEditorUniqueId(uuid);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.openSign(sign, Side.BACK);
                            player.closeInventory();
                        }
                    }, 5L);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to use adventure API for sign", e);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open sign editor: " + e.getMessage(), e);
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (originalBlockData.containsKey(uuid)) {
            // Restore original block
            BlockData blockData = originalBlockData.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                event.getBlock().setBlockData(blockData, false);
            });

            // Parse result
            try {
                Component line0 = event.line(0);
                if (line0 != null) {
                    String signContent = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line0);

                    for (TranslationModConfig config : plugin.getConfigManager().getTranslationMods()) {
                        if (!signContent.contains("TTT: " + config.key)) {
                            handleModDetection(player, config, signContent);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse sign content", e);
            }
        }
    }

    private void handleModDetection(Player player, TranslationModConfig config, String detectedValue) {
        plugin.getLogger().info("玩家 " + player.getName() + " 被检测到使用 " + config.name);
        notifyStaff("玩家 " + player.getName() + " 正在使用 " + config.name);
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (config.commands != null && !config.commands.isEmpty()) {
                for (String cmd : config.commands) {
                    String finalCmd = cmd
                        .replace("%player%", player.getName())
                        .replace("%mod_name%", config.name)
                        .replace("%detected_value%", detectedValue);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                }
            }
        });
    }

    private void notifyStaff(String message) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isNotifyStaff()) return;
        
        Component staffNotification = Component.text("[SimpleModDetect] " + message, NamedTextColor.RED);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission(config.getNotificationPermission())) {
                onlinePlayer.sendMessage(staffNotification);
            }
        }
    }

    public void removePlayer(UUID uuid) {
        if (originalBlockData.containsKey(uuid)) {
            BlockData blockData = originalBlockData.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Block block = player.getLocation().clone().add(0.0, -5.0, 0.0).getBlock();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    block.setBlockData(blockData, false);
                });
            }
        }
    }
}
