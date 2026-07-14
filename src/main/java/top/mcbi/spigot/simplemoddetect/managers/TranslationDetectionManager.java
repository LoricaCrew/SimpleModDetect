package top.mcbi.spigot.simplemoddetect.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;
import top.mcbi.spigot.simplemoddetect.managers.ConfigManager.TranslationModConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class TranslationDetectionManager {
    private static final int SIGN_LINE_COUNT = 4;
    private static final int MAX_ESTIMATED_LINE_LENGTH = 320;
    private static final int MAX_INVALID_RESPONSE_RETRIES = 3;

    private final SimpleModDetect plugin;
    private final Map<UUID, TranslationCheckSession> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> lockedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> stuckWatchdogTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> stuckRestartAttempts = new ConcurrentHashMap<>();

    private record ParsedPacketLine(int batchId, int lineIndex, String content) {
    }

    private static class TranslationCheckSession {
        private final Location signLocation;
        private final BlockData originalClientBlockData;
        private final List<TranslationModConfig> pendingMods;
        private final List<String> detectedModNames = new ArrayList<>();
        private final List<String> sharedDetectedModNames = new ArrayList<>();
        private final List<String> sharedDetectedValues = new ArrayList<>();
        private final String markerToken;
        private final Set<Integer> completedBatchIds = new HashSet<>();
        private List<List<TranslationModConfig>> currentLineGroups = List.of();
        private int currentBatchId;
        private int invalidResponseCount;
        private boolean finished;
        private boolean fakeSignVisible;

        private TranslationCheckSession(Location signLocation, BlockData originalClientBlockData, List<TranslationModConfig> pendingMods) {
            this.signLocation = signLocation;
            this.originalClientBlockData = originalClientBlockData;
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

        stuckRestartAttempts.remove(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> startCheck(player), config.getTranslationInitialCheckDelayTicks());
    }

    private void startCheck(Player player) {
        if (!player.isOnline()) {
            return;
        }

        lockedPlayers.add(player.getUniqueId());

        TranslationCheckSession previousSession = sessions.remove(player.getUniqueId());
        if (previousSession != null) {
            restoreClientBlock(player, previousSession);
        }

        Location signLocation = player.getLocation().clone().add(0.0, -5.0, 0.0);
        signLocation.setX(signLocation.getBlockX());
        signLocation.setY(signLocation.getBlockY());
        signLocation.setZ(signLocation.getBlockZ());

        TranslationCheckSession session = new TranslationCheckSession(
            signLocation,
            signLocation.getBlock().getBlockData().clone(),
            new ArrayList<>(plugin.getConfigManager().getTranslationMods())
        );
        sessions.put(player.getUniqueId(), session);
        scheduleStuckWatchdog(player);
        openNextBatch(player, session);
    }

    private void scheduleStuckWatchdog(Player player) {
        UUID uuid = player.getUniqueId();
        cancelStuckWatchdog(uuid);

        int stuckSeconds = plugin.getConfigManager().getTranslationStuckRestartSeconds();
        if (stuckSeconds <= 0) {
            return;
        }

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            stuckWatchdogTasks.remove(uuid);
            handleStuckWatchdog(player);
        }, stuckSeconds * 20L).getTaskId();
        stuckWatchdogTasks.put(uuid, taskId);
    }

    private void handleStuckWatchdog(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.isOnline() || !isPlayerLocked(uuid)) {
            return;
        }

        int maxAttempts = plugin.getConfigManager().getTranslationStuckRestartMaxAttempts();
        int attempts = stuckRestartAttempts.getOrDefault(uuid, 0);
        if (attempts >= maxAttempts) {
            plugin.getLogger().warning("玩家 " + player.getName()
                + " 的木牌检测多次超时仍未完成，已解除行动限制并结束检测");
            stuckRestartAttempts.remove(uuid);
            cleanupSession(uuid, player);
            return;
        }

        stuckRestartAttempts.put(uuid, attempts + 1);
        plugin.getLogger().warning("玩家 " + player.getName()
            + " 的木牌检测超时未解除限制，正在重新开始检测 (" + (attempts + 1) + "/" + maxAttempts + ")");
        startCheck(player);
    }

    private void cancelStuckWatchdog(UUID uuid) {
        Integer taskId = stuckWatchdogTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void openNextBatch(Player player, TranslationCheckSession session) {
        synchronized (session) {
            if (sessions.get(player.getUniqueId()) != session) {
                return;
            }
        }

        if (!player.isOnline()) {
            cleanupSession(player.getUniqueId(), player);
            return;
        }

        try {
            UUID uuid = player.getUniqueId();
            List<List<TranslationModConfig>> currentLineGroups;
            synchronized (session) {
                if (session.finished || !session.hasPendingMods()) {
                    completeSession(uuid, player);
                    return;
                }

                currentLineGroups = prepareNextBatch(session);
                if (currentLineGroups.isEmpty()) {
                    completeSession(uuid, player);
                    return;
                }
            }

            sendFakeSignAndOpen(player, session, currentLineGroups);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open fake sign editor: " + e.getMessage(), e);
            abortDetection(player.getUniqueId(), player, null);
        }
    }

    private void sendFakeSignAndOpen(
        Player player,
        TranslationCheckSession session,
        List<List<TranslationModConfig>> currentLineGroups
    ) {
        Location location = session.signLocation;
        BlockData signData = Material.OAK_SIGN.createBlockData();
        if (!(signData.createBlockState() instanceof Sign signState)) {
            abortDetection(player.getUniqueId(), player, "玩家 " + player.getName() + " 的翻译键检测无法创建虚拟木牌，已终止本次检测");
            return;
        }

        SignSide backSide = signState.getSide(Side.BACK);
        for (int i = 0; i < SIGN_LINE_COUNT; i++) {
            if (i < currentLineGroups.size()) {
                backSide.line(i, buildLineContent(session, i, currentLineGroups.get(i)));
            } else {
                backSide.line(i, Component.empty());
            }
        }

        player.sendBlockChange(location, signData);
        player.sendBlockUpdate(location, signState);
        session.fakeSignVisible = true;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) {
                return;
            }

            player.openVirtualSign(location, Side.BACK);
            player.closeInventory();
        }, plugin.getConfigManager().getTranslationOpenSignDelayTicks());
    }

    private void restoreClientBlock(Player player, TranslationCheckSession session) {
        if (!session.fakeSignVisible || player == null || !player.isOnline()) {
            session.fakeSignVisible = false;
            return;
        }

        player.sendBlockChange(session.signLocation, session.originalClientBlockData);
        session.fakeSignVisible = false;
    }

    public boolean handleSignUpdatePacket(Player player, ServerboundSignUpdatePacket packet) {
        TranslationCheckSession session = sessions.get(player.getUniqueId());
        if (session == null) {
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

        String[] lines = packet.getLines();
        Map<Integer, String> currentBatchLineContents = new HashMap<>();
        boolean hasSessionMarker = false;
        boolean hasMalformedSessionLine = false;
        boolean hasStaleBatchLine = false;
        List<List<TranslationModConfig>> unresolvedLineGroups = new ArrayList<>();

        try {
            synchronized (session) {
                if (sessions.get(player.getUniqueId()) != session) {
                    return true;
                }

                for (String line : lines) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }

                    ParsedPacketLine parsedPacketLine = parsePacketLine(session, line);
                    if (parsedPacketLine != null) {
                        hasSessionMarker = true;
                        if (!session.currentLineGroups.isEmpty() && parsedPacketLine.batchId() == session.currentBatchId) {
                            currentBatchLineContents.putIfAbsent(parsedPacketLine.lineIndex(), parsedPacketLine.content());
                        } else {
                            hasStaleBatchLine = true;
                        }
                        continue;
                    }

                    if (line.contains(session.markerToken)) {
                        hasSessionMarker = true;
                        hasMalformedSessionLine = true;
                    }
                }

                if (session.currentLineGroups.isEmpty()) {
                    return hasSessionMarker;
                }

                if (currentBatchLineContents.isEmpty()) {
                    if (hasStaleBatchLine && !hasMalformedSessionLine) {
                        if (plugin.getConfigManager().isDebugMode()) {
                            plugin.getLogger().info("玩家 " + player.getName() + " 的木牌检测收到旧批次回包，已忽略");
                        }
                        return true;
                    }

                    retryOrAbortCurrentBatch(
                        player.getUniqueId(),
                        player,
                        session,
                        new ArrayList<>(session.currentLineGroups),
                        "玩家 " + player.getName() + " 的翻译键检测返回了无效内容，已重试当前批次: " + firstNonEmptyLine(lines)
                    );
                    return true;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (sessions.get(player.getUniqueId()) == session) {
                        restoreClientBlock(player, session);
                    }
                });

                for (int lineIndex = 0; lineIndex < session.currentLineGroups.size(); lineIndex++) {
                    List<TranslationModConfig> lineGroup = session.currentLineGroups.get(lineIndex);
                    String lineContent = currentBatchLineContents.get(lineIndex);
                    if (lineContent == null) {
                        unresolvedLineGroups.add(lineGroup);
                        continue;
                    }

                    if (!handleLineResponse(player, session, lineGroup, lineIndex, lineContent)) {
                        unresolvedLineGroups.add(lineGroup);
                    }
                }

                if (!unresolvedLineGroups.isEmpty()) {
                    retryOrAbortCurrentBatch(
                        player.getUniqueId(),
                        player,
                        session,
                        unresolvedLineGroups,
                        "玩家 " + player.getName() + " 的翻译键检测部分回包无效，已重试当前批次"
                    );
                    return true;
                }

                session.invalidResponseCount = 0;
                markCurrentBatchHandled(session);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> openNextBatch(player, session), plugin.getConfigManager().getTranslationNextBatchDelayTicks());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to handle sign update packet", exception);
            retryOrAbortCurrentBatch(
                player.getUniqueId(),
                player,
                session,
                new ArrayList<>(session.currentLineGroups),
                null
            );
        }

        return true;
    }

    private void handleModDetection(Player player, TranslationCheckSession session, TranslationModConfig config, String detectedValue) {
        plugin.getLogger().info("玩家 " + player.getName() + " 被检测到使用 " + config.name);
        notifyStaff("玩家 " + player.getName() + " 正在使用 " + config.name);

        if (!session.detectedModNames.contains(config.name)) {
            session.detectedModNames.add(config.name);
        }

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
        stuckRestartAttempts.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        cleanupSession(uuid, player);
    }

    public boolean isPlayerLocked(UUID uuid) {
        return lockedPlayers.contains(uuid);
    }

    private ParsedPacketLine parsePacketLine(TranslationCheckSession session, String line) {
        String prefixStart = "⟦⌁" + session.markerToken + "⌁";
        if (!line.startsWith(prefixStart)) {
            return null;
        }

        int separatorIndex = line.indexOf(':', prefixStart.length());
        int suffixIndex = line.indexOf("⟧ ", separatorIndex + 1);
        if (separatorIndex < 0 || suffixIndex < 0) {
            return null;
        }

        try {
            int batchId = Integer.parseInt(line.substring(prefixStart.length(), separatorIndex));
            int lineIndex = Integer.parseInt(line.substring(separatorIndex + 1, suffixIndex));
            String content = line.substring(suffixIndex + 2);
            return new ParsedPacketLine(batchId, lineIndex, content);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void retryOrAbortCurrentBatch(
        UUID uuid,
        Player player,
        TranslationCheckSession session,
        List<List<TranslationModConfig>> unresolvedLineGroups,
        String reason
    ) {
        if (reason != null && !reason.isBlank() && plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().warning(reason);
        }

        synchronized (session) {
            if (sessions.get(uuid) != session) {
                return;
            }

            requeueLineGroups(session, unresolvedLineGroups);
            markCurrentBatchHandled(session);
            session.invalidResponseCount++;

            if (session.invalidResponseCount >= MAX_INVALID_RESPONSE_RETRIES) {
                abortDetection(uuid, player, "玩家 " + (player == null ? uuid : player.getName()) + " 的翻译键检测连续收到无效回包，已结束本次检测");
                return;
            }
        }

        if (player != null && player.isOnline()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openNextBatch(player, session), plugin.getConfigManager().getTranslationNextBatchDelayTicks());
        } else {
            cleanupSession(uuid, player);
        }
    }

    private void requeueLineGroups(TranslationCheckSession session, List<List<TranslationModConfig>> unresolvedLineGroups) {
        for (List<TranslationModConfig> lineGroup : unresolvedLineGroups) {
            session.pendingMods.addAll(lineGroup);
        }
    }

    private void markCurrentBatchHandled(TranslationCheckSession session) {
        if (session.currentBatchId > 0) {
            session.completedBatchIds.add(session.currentBatchId);
        }
        session.currentLineGroups = List.of();
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
                return false;
            }

            int valueStart = cursor + segmentPrefix.length();
            int valueEnd;
            if (segmentIndex + 1 < lineGroup.size()) {
                String nextSegmentPrefix = getSegmentPrefix(session, lineIndex, segmentIndex + 1);
                valueEnd = lineContent.indexOf(nextSegmentPrefix, valueStart);
                if (valueEnd < 0) {
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

    private void abortDetection(UUID uuid, Player player, String reason) {
        TranslationCheckSession session = sessions.get(uuid);
        if (reason != null && !reason.isBlank()) {
            plugin.getLogger().warning(reason);
        }

        if (session != null) {
            synchronized (session) {
                if (sessions.get(uuid) != session) {
                    return;
                }

                if (!session.detectedModNames.isEmpty()) {
                    completeSession(uuid, player);
                    return;
                }
            }
        }

        cleanupSession(uuid, player);
    }

    private String firstNonEmptyLine(String[] lines) {
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                return line;
            }
        }
        return "";
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
        if (session != null) {
            synchronized (session) {
                if (sessions.get(uuid) != session) {
                    return;
                }

                if (!session.sharedDetectedModNames.isEmpty()) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("%player%", player == null ? "" : player.getName());
                    placeholders.put("%mod_name%", String.join(", ", session.sharedDetectedModNames));
                    placeholders.put("%detected_value%", String.join(" | ", session.sharedDetectedValues));
                    plugin.getPunishmentExecutor().execute(player, plugin.getConfigManager().getTranslationAction(), placeholders);
                }

                String playerName = player == null ? uuid.toString() : player.getName();
                if (session.detectedModNames.isEmpty()) {
                    plugin.getLogger().info("玩家 " + playerName + " 木牌检测结束，结果: 通过");
                } else {
                    plugin.getLogger().warning("玩家 " + playerName + " 木牌检测结束，结果: 未通过，检测到: " + String.join(", ", session.detectedModNames));
                }
            }
        }

        stuckRestartAttempts.remove(uuid);
        cleanupSession(uuid, player);
    }

    private void cleanupSession(UUID uuid, Player player) {
        cancelStuckWatchdog(uuid);
        lockedPlayers.remove(uuid);
        TranslationCheckSession session = sessions.remove(uuid);
        if (session == null) {
            return;
        }

        if (player != null && player.isOnline()) {
            restoreClientBlock(player, session);
        } else {
            session.fakeSignVisible = false;
        }
    }
}
