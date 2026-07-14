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
     * @return 版本字符串，例如 "26.1.2"
     */
    public static String detectVersion() {
        if (serverVersion != null) {
            return serverVersion;
        }

        String bukkitVersion = Bukkit.getServer().getBukkitVersion();
        // bukkitVersion格式通常是 "26.1.2-R0.1-SNAPSHOT"

        try {
            String[] parts = bukkitVersion.split("-");
            String versionPart = parts[0];

            String[] versionNumbers = versionPart.split("\\.");
            if (versionNumbers.length >= 3) {
                majorVersion = Integer.parseInt(versionNumbers[0]);
                minorVersion = Integer.parseInt(versionNumbers[1]);
                patchVersion = Integer.parseInt(versionNumbers[2]);

                serverVersion = majorVersion + "." + minorVersion + "." + patchVersion;
            } else if (versionNumbers.length == 2) {
                majorVersion = Integer.parseInt(versionNumbers[0]);
                minorVersion = Integer.parseInt(versionNumbers[1]);
                patchVersion = 0;
                serverVersion = majorVersion + "." + minorVersion;
            } else {
                serverVersion = versionPart;
            }

            Logger.getLogger("SimpleModDetect").info("检测到服务器版本: " + serverVersion);
        } catch (Exception e) {
            Logger.getLogger("SimpleModDetect").warning("无法解析服务器版本: " + bukkitVersion);
            serverVersion = bukkitVersion;
        }

        return serverVersion;
    }

    public static int getMajorVersion() {
        if (serverVersion == null) {
            detectVersion();
        }
        return majorVersion;
    }

    public static int getMinorVersion() {
        if (serverVersion == null) {
            detectVersion();
        }
        return minorVersion;
    }

    public static int getPatchVersion() {
        if (serverVersion == null) {
            detectVersion();
        }
        return patchVersion;
    }

    public static boolean isSupportedVersion() {
        String version = detectVersion();
        return version.startsWith("26.1.");
    }

    public static String getVersionIdentifier() {
        String version = detectVersion();
        if (version.startsWith("26.1.")) {
            return "v26_1_2";
        }
        return "v26_1_2";
    }
}
