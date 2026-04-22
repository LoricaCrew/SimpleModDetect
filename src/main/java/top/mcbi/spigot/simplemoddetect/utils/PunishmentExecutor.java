package top.mcbi.spigot.simplemoddetect.utils;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;
import top.mcbi.spigot.simplemoddetect.managers.ConfigManager.PunishmentAction;
import top.mcbi.spigot.simplemoddetect.managers.ConfigManager.PunishmentType;

import java.util.Map;
import java.util.Objects;

public class PunishmentExecutor {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final SimpleModDetect plugin;

    public PunishmentExecutor(SimpleModDetect plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, PunishmentAction action, Map<String, String> placeholders) {
        if (action == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (action.type == PunishmentType.COMMAND) {
                for (String command : action.commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), applyPlaceholders(command, placeholders));
                }
                return;
            }

            if (action.type == PunishmentType.SEND) {
                if (!player.isOnline()) {
                    return;
                }

                String message = applyPlaceholders(action.message, placeholders);
                if (!message.isBlank()) {
                    player.sendMessage(LEGACY_SERIALIZER.deserialize(message));
                }

                String server = applyPlaceholders(action.server, placeholders);
                if (!server.isBlank()) {
                    sendPlayer(player, server);
                }
            }
        });
    }

    public String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }

        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), Objects.toString(entry.getValue(), ""));
        }
        return result;
    }

    public void sendPlayer(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }
}
