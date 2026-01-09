package top.mcbi.spigot.simplemoddetect.nms;

import top.mcbi.spigot.simplemoddetect.nms.adapters.V1_21_4Adapter;
import top.mcbi.spigot.simplemoddetect.nms.adapters.V1_21_8Adapter;

import java.util.logging.Logger;

/**
 * 版本适配器管理器
 * 负责根据服务器版本选择合适的适配器
 */
public class VersionAdapterManager {
    private static NMSVersionAdapter adapter;
    private static final Logger logger = Logger.getLogger("SimpleModDetect");

    /**
     * 初始化适配器
     * @return 适配器实例
     */
    public static NMSVersionAdapter initializeAdapter() {
        if (adapter != null) {
            return adapter;
        }

        String versionIdentifier = VersionDetector.getVersionIdentifier();
        String serverVersion = VersionDetector.detectVersion();

        try {
            switch (versionIdentifier) {
                case "v1_21_4":
                    adapter = new V1_21_4Adapter();
                    logger.info("已加载 1.21.4 版本适配器");
                    break;
                case "v1_21_8":
                    adapter = new V1_21_8Adapter();
                    logger.info("已加载 1.21.8 版本适配器");
                    break;
                default:
                    // 默认使用1.21.8适配器
                    logger.warning("未知版本 " + serverVersion + "，使用默认适配器 1.21.8");
                    adapter = new V1_21_8Adapter();
                    break;
            }
        } catch (Exception e) {
            logger.severe("无法初始化版本适配器: " + e.getMessage());
            e.printStackTrace();
            // 尝试使用默认适配器
            adapter = new V1_21_8Adapter();
        }

        return adapter;
    }

    /**
     * 获取当前适配器
     * @return 适配器实例
     */
    public static NMSVersionAdapter getAdapter() {
        if (adapter == null) {
            return initializeAdapter();
        }
        return adapter;
    }

    /**
     * 检查当前版本是否受支持
     * @return 是否支持
     */
    public static boolean isVersionSupported() {
        return VersionDetector.isSupportedVersion();
    }
}

