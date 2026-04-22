package top.mcbi.spigot.simplemoddetect.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;
import top.mcbi.spigot.simplemoddetect.managers.ConfigManager.TranslationModConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.logging.Level;

public class TranslationDetectionManager {
    private static final int BATCH_SIZE = 4;
    private static final long INITIAL_CHECK_DELAY_TICKS = 5L;
    private static final long OPEN_SIGN_DELAY_TICKS = 1L;
    private static final long NEXT_BATCH_DELAY_TICKS = 1L;

    private final SimpleModDetect plugin;
    private final Map<UUID, TranslationCheckSession> sessions = new HashMap<>();

    private static class TranslationCheckSession {
        private final Location signLocation;
        private final BlockData originalBlockData;
        private final List<TranslationModConfig> pendingMods;
        private final List<String> sharedDetectedModNames = new ArrayList<>();
        private final List<String> sharedDetectedValues = new ArrayList<>();
        private final String markerToken;
        private List<TranslationModConfig> currentBatch = List.of();
        private int currentBatchId;
        private boolean finished;

        private TranslationCheckSession(Location signLocation, BlockData originalBlockData, List<TranslationModConfig> pendingMods) {
            this.signLocation = signLocation;
            this.originalBlockData = originalBlockData;
            this.pendingMods = pendingMods;
            this.markerToken = generateMarkerToken();
        }

        private boolean hasPendingMods() {
            return !pendingMods.isEmpty();
        }

        private List<TranslationModConfig> nextBatch() {
            if (pendingMods.isEmpty()) {
                currentBatch = List.of();
                return currentBatch;
            }

            int size = Math.min(BATCH_SIZE, pendingMods.size());
            List<TranslationModConfig> batch = new ArrayList<>(pendingMods.subList(0, size));
            pendingMods.subList(0, size).clear();
            currentBatchId++;
            currentBatch = batch;
            return batch;
        }
    }

    public TranslationDetectionManager(SimpleModDetect plugin) {
        this.plugin = plugin;
    }

    public void checkPlayer(Player player) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isTranslationEnabled() || config.getTranslationMods().isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> startCheck(player), INITIAL_CHECK_DELAY_TICKS);
    }

    private void startCheck(Player player) {
        if (!player.isOnline()) {
            return;
        }

        Block block = player.getLocation().clone().add(0.0, -5.0, 0.0).getBlock();
        TranslationCheckSession previousSession = sessions.remove(player.getUniqueId());
        if (previousSession != null) {
            previousSession.signLocation.getBlock().setBlockData(previousSession.originalBlockData, false);
        }

        TranslationCheckSession session = new TranslationCheckSession(
            block.getLocation(),
            block.getBlockData(),
            new ArrayList<>(plugin.getConfigManager().getTranslationMods())
        );
        sessions.put(player.getUniqueId(), session);
        openNextBatch(player, session);
    }

    private void openNextBatch(Player player, TranslationCheckSession session) {
        if (!player.isOnline() || session.finished || !session.hasPendingMods()) {
            completeSession(player.getUniqueId(), player);
            return;
        }

        try {
            Block block = session.signLocation.getBlock();
            UUID uuid = player.getUniqueId();
            block.setType(Material.OAK_SIGN, false);
            if (block.getState() instanceof Sign sign) {
                try {
                    List<TranslationModConfig> currentBatch = session.nextBatch();
                    if (currentBatch.isEmpty()) {
                        completeSession(uuid, player);
                        return;
                    }

                    SignSide backSide = sign.getSide(Side.BACK);
                    for (int i = 0; i < BATCH_SIZE; i++) {
                        if (i < currentBatch.size()) {
                            TranslationModConfig config = currentBatch.get(i);
                            backSide.line(i, Component.text(getLinePrefix(session, i)).append(Component.translatable(config.key)));
                        } else {
                            backSide.line(i, Component.empty());
                        }
                    }

                    sign.update(false, false);
                    sign.setAllowedEditorUniqueId(uuid);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.openSign(sign, Side.BACK);
                            player.closeInventory();
                        }
                    }, OPEN_SIGN_DELAY_TICKS);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to use adventure API for sign", e);
                    cleanupSession(uuid, player);
                }
            } else {
                cleanupSession(uuid, player);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open sign editor: " + e.getMessage(), e);
            cleanupSession(player.getUniqueId(), player);
        }
    }

    public boolean handleSignUpdatePacket(Player player, ServerboundSignUpdatePacket packet) {
        TranslationCheckSession session = sessions.get(player.getUniqueId());
        if (session == null || session.currentBatch.isEmpty()) {
            return false;
        }

        if (packet.isFrontText()) {
            return false;
        }

        Location signLocation = session.signLocation;
        if (packet.getPos().getX() != signLocation.getBlockX()
            || packet.getPos().getY() != signLocation.getBlockY()
            || packet.getPos().getZ() != signLocation.getBlockZ()) {
            return false;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            session.signLocation.getBlock().setBlockData(session.originalBlockData, false);
        });

        try {
            String[] lines = packet.getLines();
            for (int i = 0; i < session.currentBatch.size(); i++) {
                TranslationModConfig config = session.currentBatch.get(i);
                String line = lines.length > i ? lines[i] : "";
                String linePrefix = getLinePrefix(session, i);
                if (!line.startsWith(linePrefix)) {
                    plugin.getLogger().warning("玩家 " + player.getName() + " 的翻译键检测返回了无效内容，已终止本次检测: " + line);
                    cleanupSession(player.getUniqueId(), player);
                    return true;
                }

                String translatedValue = line.substring(linePrefix.length());
                if (!translatedValue.equals(config.key)) {
                    handleModDetection(player, session, config, translatedValue);
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> openNextBatch(player, session), NEXT_BATCH_DELAY_TICKS);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to handle sign update packet", exception);
            cleanupSession(player.getUniqueId(), player);
        }

        return true;
    }

    private void handleModDetection(Player player, TranslationCheckSession session, TranslationModConfig config, String detectedValue) {
        plugin.getLogger().info("玩家 " + player.getName() + " 被检测到使用 " + config.name);
        notifyStaff("玩家 " + player.getName() + " 正在使用 " + config.name);

        if (config.action == null) {
            if (!session.sharedDetectedModNames.contains(config.name)) {
                session.sharedDetectedModNames.add(config.name);
                session.sharedDetectedValues.add(detectedValue);
            }
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", player.getName());
        placeholders.put("%mod_name%", config.name);
        placeholders.put("%detected_value%", detectedValue);
        plugin.getPunishmentExecutor().execute(player, config.action, placeholders);
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
        Player player = Bukkit.getPlayer(uuid);
        cleanupSession(uuid, player);
    }

    private String getLinePrefix(TranslationCheckSession session, int lineIndex) {
        return "⟦⌁" + session.markerToken + "⌁" + session.currentBatchId + ":" + lineIndex + "⟧ ";
    }

    private static String generateMarkerToken() {
        long value = ThreadLocalRandom.current().nextLong();
        return Long.toUnsignedString(value, 36).toUpperCase();
    }

    private void completeSession(UUID uuid, Player player) {
        TranslationCheckSession session = sessions.get(uuid);
        if (session != null && !session.sharedDetectedModNames.isEmpty()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%player%", player == null ? "" : player.getName());
            placeholders.put("%mod_name%", String.join(", ", session.sharedDetectedModNames));
            placeholders.put("%detected_value%", String.join(" | ", session.sharedDetectedValues));
            plugin.getPunishmentExecutor().execute(player, plugin.getConfigManager().getTranslationAction(), placeholders);
        }

        cleanupSession(uuid, player);
    }

    private void cleanupSession(UUID uuid, Player player) {
        TranslationCheckSession session = sessions.remove(uuid);
        if (session == null || player == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            session.signLocation.getBlock().setBlockData(session.originalBlockData, false);
        });
    }
}
