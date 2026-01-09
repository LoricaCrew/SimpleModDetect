package top.mcbi.spigot.simplemoddetect.nms;

import org.bukkit.Bukkit;

import java.util.logging.Logger;

public class VersionDetector {
    private static String serverVersion;
    private static int majorVersion;
    private static int minorVersion;
    private static int patchVersion;

    /**
     * 检测服务器版本
     * @return 版本字符串，例如 "1.21.4" 或 "1.21.8"
     */
    public static String detectVersion() {
        if (serverVersion != null) {
            return serverVersion;
        }

        String bukkitVersion = Bukkit.getServer().getBukkitVersion();
        // bukkitVersion格式通常是 "1.21.4-R0.1-SNAPSHOT"
        
        try {
            // 提取版本号部分（去掉-R0.1-SNAPSHOT等后缀）
            String[] parts = bukkitVersion.split("-");
            String versionPart = parts[0];
            
            // 解析主版本、次版本和补丁版本
            String[] versionNumbers = versionPart.split("\\.");
            if (versionNumbers.length >= 3) {
                majorVersion = Integer.parseInt(versionNumbers[0]);
                minorVersion = Integer.parseInt(versionNumbers[1]);
                patchVersion = Integer.parseInt(versionNumbers[2]);
                
                serverVersion = majorVersion + "." + minorVersion + "." + patchVersion;
            } else {
                // 如果解析失败，使用完整版本字符串
                serverVersion = versionPart;
            }
            
            Logger.getLogger("SimpleModDetect").info("检测到服务器版本: " + serverVersion);
        } catch (Exception e) {
            Logger.getLogger("SimpleModDetect").warning("无法解析服务器版本: " + bukkitVersion);
            serverVersion = bukkitVersion;
        }

        return serverVersion;
    }

    /**
     * 获取主版本号
     */
    public static int getMajorVersion() {
        if (serverVersion == null) {
            detectVersion();
        }
        return majorVersion;
    }

    /**
     * 获取次版本号
     */
    public static int getMinorVersion() {
        if (serverVersion == null) {
            detectVersion();
        }
        return minorVersion;
    }

    /**
     * 获取补丁版本号
     */
    public static int getPatchVersion() {
        if (serverVersion == null) {
            detectVersion();
        }
        return patchVersion;
    }

    /**
     * 检查是否为支持的版本
     */
    public static boolean isSupportedVersion() {
        String version = detectVersion();
        return version.startsWith("1.21.4") || version.startsWith("1.21.8");
    }

    /**
     * 获取版本标识（用于加载适配器）
     */
    public static String getVersionIdentifier() {
        String version = detectVersion();
        if (version.startsWith("1.21.4")) {
            return "v1_21_4";
        } else if (version.startsWith("1.21.8")) {
            return "v1_21_8";
        }
        // 默认返回1.21.8（最新版本）
        return "v1_21_8";
    }
}

