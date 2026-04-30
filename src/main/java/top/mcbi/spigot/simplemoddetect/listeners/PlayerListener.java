package top.mcbi.spigot.simplemoddetect.listeners;

import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;

import java.util.List;

public class PlayerListener implements Listener {
    private final SimpleModDetect plugin;

    public PlayerListener(SimpleModDetect plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getChannelInjector().injectPlayer(event.getPlayer());
        plugin.getTranslationDetectionManager().checkPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerClientLoadedWorld(PlayerClientLoadedWorldEvent event) {
        plugin.getModDetectionManager().checkPlayerChannels(event.getPlayer());
        if (plugin.getConfigManager().isDisableMarlowCrystalOptimizer() &&
                plugin.getModDetectionManager().getPlayerChannels()
                        .getOrDefault(event.getPlayer().getName(), List.of()).contains("marlowcrystal:version")) {
            plugin.getMarlowCrystalOptimizerDisabler().sendOptOutPacket(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        plugin.getChannelInjector().removePlayer(event.getPlayer());
        plugin.getTranslationDetectionManager().removePlayer(event.getPlayer().getUniqueId());
        plugin.getModDetectionManager().removePlayer(playerName);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getTranslationDetectionManager().isPlayerLocked(event.getPlayer().getUniqueId())) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (event.getFrom().getX() != event.getTo().getX()
            || event.getFrom().getY() != event.getTo().getY()
            || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (plugin.getTranslationDetectionManager().isPlayerLocked(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (plugin.getTranslationDetectionManager().isPlayerLocked(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (plugin.getTranslationDetectionManager().isPlayerLocked(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}

