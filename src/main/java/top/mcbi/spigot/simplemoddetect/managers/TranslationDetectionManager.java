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
    private static final int SIGN_LINE_COUNT = 4;
    private static final int MAX_ESTIMATED_LINE_LENGTH = 320;

    private final SimpleModDetect plugin;
    private final Map<UUID, TranslationCheckSession> sessions = new HashMap<>();

    private static class TranslationCheckSession {
        private final Location signLocation;
        private final BlockData originalBlockData;
        private final List<TranslationModConfig> pendingMods;
        private final List<String> sharedDetectedModNames = new ArrayList<>();
        private final List<String> sharedDetectedValues = new ArrayList<>();
        private final String markerToken;
        private List<List<TranslationModConfig>> currentLineGroups = List.of();
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
    }

    public TranslationDetectionManager(SimpleModDetect plugin) {
        this.plugin = plugin;
    }

    public void checkPlayer(Player player) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isTranslationEnabled() || config.getTranslationMods().isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> startCheck(player), config.getTranslationInitialCheckDelayTicks());
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
                    List<List<TranslationModConfig>> currentLineGroups = prepareNextBatch(session);
                    if (currentLineGroups.isEmpty()) {
                        completeSession(uuid, player);
                        return;
                    }

                    SignSide backSide = sign.getSide(Side.BACK);
                    for (int i = 0; i < SIGN_LINE_COUNT; i++) {
                        if (i < currentLineGroups.size()) {
                            backSide.line(i, buildLineContent(session, i, currentLineGroups.get(i)));
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
                    }, plugin.getConfigManager().getTranslationOpenSignDelayTicks());
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
        if (session == null || session.currentLineGroups.isEmpty()) {
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
            for (int lineIndex = 0; lineIndex < session.currentLineGroups.size(); lineIndex++) {
                List<TranslationModConfig> lineGroup = session.currentLineGroups.get(lineIndex);
                String line = lines.length > lineIndex ? lines[lineIndex] : "";
                String linePrefix = getLinePrefix(session, lineIndex);
                if (!line.startsWith(linePrefix)) {
                    plugin.getLogger().warning("玩家 " + player.getName() + " 的翻译键检测返回了无效内容，已终止本次检测: " + line);
                    cleanupSession(player.getUniqueId(), player);
                    return true;
                }

                String lineContent = line.substring(linePrefix.length());
                if (!handleLineResponse(player, session, lineGroup, lineIndex, lineContent)) {
                    cleanupSession(player.getUniqueId(), player);
                    return true;
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> openNextBatch(player, session), plugin.getConfigManager().getTranslationNextBatchDelayTicks());
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

    private List<List<TranslationModConfig>> prepareNextBatch(TranslationCheckSession session) {
        if (session.pendingMods.isEmpty()) {
            session.currentLineGroups = List.of();
            return session.currentLineGroups;
        }

        int modsPerLine = plugin.getConfigManager().getTranslationModsPerLine();
        session.currentBatchId++;

        List<List<TranslationModConfig>> lineGroups = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < SIGN_LINE_COUNT && !session.pendingMods.isEmpty(); lineIndex++) {
            List<TranslationModConfig> lineGroup = new ArrayList<>();
            while (lineGroup.size() < modsPerLine && !session.pendingMods.isEmpty()) {
                TranslationModConfig nextMod = session.pendingMods.getFirst();
                int estimatedLength = estimateLineLength(session, lineIndex, lineGroup, nextMod);
                if (!lineGroup.isEmpty() && estimatedLength > MAX_ESTIMATED_LINE_LENGTH) {
                    break;
                }

                lineGroup.add(session.pendingMods.removeFirst());
                if (estimatedLength > MAX_ESTIMATED_LINE_LENGTH) {
                    break;
                }
            }

            if (!lineGroup.isEmpty()) {
                lineGroups.add(lineGroup);
            }
        }

        session.currentLineGroups = lineGroups;
        return lineGroups;
    }

    private Component buildLineContent(TranslationCheckSession session, int lineIndex, List<TranslationModConfig> lineGroup) {
        Component content = Component.text(getLinePrefix(session, lineIndex));
        for (int segmentIndex = 0; segmentIndex < lineGroup.size(); segmentIndex++) {
            TranslationModConfig config = lineGroup.get(segmentIndex);
            content = content
                .append(Component.text(getSegmentPrefix(session, lineIndex, segmentIndex)))
                .append(Component.translatable(config.key));
        }
        return content;
    }

    private int estimateLineLength(TranslationCheckSession session, int lineIndex, List<TranslationModConfig> lineGroup, TranslationModConfig nextMod) {
        int length = getLinePrefix(session, lineIndex).length();
        for (int segmentIndex = 0; segmentIndex < lineGroup.size(); segmentIndex++) {
            TranslationModConfig config = lineGroup.get(segmentIndex);
            length += getSegmentPrefix(session, lineIndex, segmentIndex).length() + config.key.length();
        }

        length += getSegmentPrefix(session, lineIndex, lineGroup.size()).length() + nextMod.key.length();
        return length;
    }

    private boolean handleLineResponse(Player player, TranslationCheckSession session, List<TranslationModConfig> lineGroup, int lineIndex, String lineContent) {
        int cursor = 0;
        for (int segmentIndex = 0; segmentIndex < lineGroup.size(); segmentIndex++) {
            TranslationModConfig config = lineGroup.get(segmentIndex);
            String segmentPrefix = getSegmentPrefix(session, lineIndex, segmentIndex);
            if (!lineContent.startsWith(segmentPrefix, cursor)) {
                plugin.getLogger().warning("玩家 " + player.getName() + " 的翻译键检测返回了无效段内容，已终止本次检测: " + lineContent);
                return false;
            }

            int valueStart = cursor + segmentPrefix.length();
            int valueEnd;
            if (segmentIndex + 1 < lineGroup.size()) {
                String nextSegmentPrefix = getSegmentPrefix(session, lineIndex, segmentIndex + 1);
                valueEnd = lineContent.indexOf(nextSegmentPrefix, valueStart);
                if (valueEnd < 0) {
                    plugin.getLogger().warning("玩家 " + player.getName() + " 的翻译键检测无法解析下一段内容，已终止本次检测: " + lineContent);
                    return false;
                }
            } else {
                valueEnd = lineContent.length();
            }

            String translatedValue = lineContent.substring(valueStart, valueEnd);
            if (!translatedValue.equals(config.key)) {
                handleModDetection(player, session, config, translatedValue);
            }
            cursor = valueEnd;
        }

        return cursor == lineContent.length();
    }

    private String getLinePrefix(TranslationCheckSession session, int lineIndex) {
        return "⟦⌁" + session.markerToken + "⌁" + session.currentBatchId + ":" + lineIndex + "⟧ ";
    }

    private String getSegmentPrefix(TranslationCheckSession session, int lineIndex, int segmentIndex) {
        return "⟬" + session.markerToken + ":" + session.currentBatchId + ":" + lineIndex + ":" + segmentIndex + "⟭";
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
