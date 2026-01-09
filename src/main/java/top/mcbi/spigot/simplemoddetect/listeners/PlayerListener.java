package top.mcbi.spigot.simplemoddetect.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;

public class PlayerListener implements Listener {
    private final SimpleModDetect plugin;

    public PlayerListener(SimpleModDetect plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getChannelInjector().injectPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        plugin.getChannelInjector().removePlayer(event.getPlayer());
        plugin.getModDetectionManager().removePlayer(playerName);
    }
}

