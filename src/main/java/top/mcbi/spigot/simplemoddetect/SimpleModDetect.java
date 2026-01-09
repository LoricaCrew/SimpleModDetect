package top.mcbi.spigot.simplemoddetect;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import top.mcbi.spigot.simplemoddetect.commands.SimpleModDetectCommand;
import top.mcbi.spigot.simplemoddetect.listeners.PlayerListener;
import top.mcbi.spigot.simplemoddetect.managers.ConfigManager;
import top.mcbi.spigot.simplemoddetect.managers.ModDetectionManager;
import top.mcbi.spigot.simplemoddetect.nms.VersionAdapterManager;
import top.mcbi.spigot.simplemoddetect.nms.VersionDetector;
import top.mcbi.spigot.simplemoddetect.protocol.ChannelInjector;
import top.mcbi.spigot.simplemoddetect.protocol.PacketHandler;
import top.mcbi.spigot.simplemoddetect.utils.ModChecker;

@Getter
public class SimpleModDetect extends JavaPlugin {

    // Getter方法，供其他类访问
    private ConfigManager configManager;
    private ModChecker modChecker;
    private PacketHandler packetHandler;
    private ChannelInjector channelInjector;
    private ModDetectionManager modDetectionManager;
    private SimpleModDetectCommand simpleModDetectCommand;

    @Override
    public void onEnable() {
        // 检测并初始化版本适配器
        String serverVersion = VersionDetector.detectVersion();
        if (!VersionAdapterManager.isVersionSupported()) {
            getLogger().warning("警告: 当前服务器版本 " + serverVersion + " 可能不受支持");
            getLogger().warning("支持的版本: 1.21.4, 1.21.8");
            getLogger().warning("将尝试使用默认适配器，可能会出现兼容性问题");
        }
        VersionAdapterManager.initializeAdapter();

        // 初始化配置管理器
        configManager = new ConfigManager(this);
        configManager.createConfig();
        configManager.loadConfig();

        // 初始化Mod检查器
        modChecker = new ModChecker(configManager);

        // 初始化Mod检测管理器
        modDetectionManager = new ModDetectionManager(this, modChecker);

        // 初始化数据包处理器
        packetHandler = new PacketHandler(this, modChecker);

        // 初始化通道注入器
        channelInjector = new ChannelInjector(this, packetHandler);

        // 初始化命令处理器
        simpleModDetectCommand = new SimpleModDetectCommand(this);
        getCommand("simplemoddetect").setExecutor(simpleModDetectCommand);
        getCommand("simplemoddetect").setTabCompleter(simpleModDetectCommand);

        // 注册玩家监听器
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        // 注册插件频道
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // 为已在线玩家注入监听器
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                channelInjector.injectPlayer(player);
            }
        }, 20L);

        getLogger().info("SimpleModDetect 已启用");
        getLogger().info("服务器版本: " + serverVersion);
        getLogger().info("使用适配器: " + VersionAdapterManager.getAdapter().getVersionName());
        getLogger().info("调试模式: " + (configManager.isDebugMode() ? "开启" : "关闭"));
        if (configManager.getFallbackServer() != null && !configManager.getFallbackServer().isEmpty()) {
            getLogger().info("Fallback服务器: " + configManager.getFallbackServer());
        }
    }

    @Override
    public void onDisable() {
        // 移除所有监听器
        if (channelInjector != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    channelInjector.removePlayer(player);
                } catch (Exception e) {
                    // 忽略错误
                }
            }
        }
        getLogger().info("SimpleModDetect 已禁用");
    }

}