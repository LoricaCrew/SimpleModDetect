package top.mcbi.spigot.simplemoddetect.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;
import top.mcbi.spigot.simplemoddetect.managers.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimpleModDetectCommand implements CommandExecutor, TabCompleter {
    private final SimpleModDetect plugin;

    public SimpleModDetectCommand(SimpleModDetect plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "check" -> handleCheck(sender, args);
            case "list" -> handleList(sender);
            case "debug" -> handleDebug(sender);
            case "fallback" -> handleFallback(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6SimpleModDetect 命令:");
        sender.sendMessage("§e/simplemoddetect reload §7- 重新加载配置");
        sender.sendMessage("§e/simplemoddetect check <玩家> §7- 检查玩家mod列表");
        sender.sendMessage("§e/simplemoddetect list §7- 查看所有已检测玩家的mod");
        sender.sendMessage("§e/simplemoddetect debug §7- 切换调试模式");
        sender.sendMessage("§e/simplemoddetect fallback <服务器地址> §7- 设置Fallback服务器");
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("simplemoddetect.reload")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        plugin.getConfigManager().loadConfig();
        sender.sendMessage("§aSimpleModDetect 配置已重新加载");
        sender.sendMessage("§a调试模式: " + (plugin.getConfigManager().isDebugMode() ? "开启" : "关闭"));
        sender.sendMessage("§aFallback服务器: " + 
            (plugin.getConfigManager().getFallbackServer().isEmpty() ? "未设置" : plugin.getConfigManager().getFallbackServer()));
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplemoddetect.check")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /simplemoddetect check <玩家>");
            return true;
        }

        String playerName = args[1];
        List<String> mods = plugin.getModDetectionManager().getPlayerMods(playerName);
        if (mods != null) {
            sender.sendMessage("§6玩家 " + playerName + " 的模组列表 (" + mods.size() + " 个):");
            for (String mod : mods) {
                sender.sendMessage("§7- " + mod);
            }
        } else {
            sender.sendMessage("§c未找到玩家 " + playerName + " 的模组数据");
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("simplemoddetect.list")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        Map<String, List<String>> allMods = plugin.getModDetectionManager().getAllPlayerMods();
        if (allMods.isEmpty()) {
            sender.sendMessage("§c暂无玩家的模组数据");
            return true;
        }

        sender.sendMessage("§6已检测到的玩家模组列表:");
        for (Map.Entry<String, List<String>> entry : allMods.entrySet()) {
            sender.sendMessage("§e" + entry.getKey() + " §7- " + entry.getValue().size() + " 个模组");
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("simplemoddetect.debug")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        ConfigManager configManager = plugin.getConfigManager();
        boolean newDebugMode = !configManager.isDebugMode();
        configManager.setDebugMode(newDebugMode);
        sender.sendMessage("§a调试模式已" + (newDebugMode ? "开启" : "关闭"));
        return true;
    }

    private boolean handleFallback(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplemoddetect.fallback")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        ConfigManager configManager = plugin.getConfigManager();
        if (args.length > 1) {
            String server = args[1];
            configManager.setFallbackServer(server);
            sender.sendMessage("§aFallback服务器已设置为: " + server);
        } else {
            String currentServer = configManager.getFallbackServer();
            sender.sendMessage("§e当前Fallback服务器: " + (currentServer.isEmpty() ? "未设置" : currentServer));
            sender.sendMessage("§e用法: /simplemoddetect fallback <服务器地址>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
            completions.add("check");
            completions.add("list");
            completions.add("debug");
            completions.add("fallback");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            // 为 check 命令提供玩家名补全
            for (String playerName : plugin.getModDetectionManager().getAllPlayerMods().keySet()) {
                if (playerName.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(playerName);
                }
            }
        }

        return completions;
    }
}

