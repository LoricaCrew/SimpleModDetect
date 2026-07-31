package top.mcbi.spigot.simplemoddetect.nms;

import top.mcbi.spigot.simplemoddetect.nms.adapters.V26_1_2Adapter;
import top.mcbi.spigot.simplemoddetect.nms.adapters.V26_2Adapter;

import java.util.logging.Logger;

/**
 * 版本适配器管理器
 * 负责根据服务器版本选择合适的适配器
 */
public class VersionAdapterManager {
    private static NMSVersionAdapter adapter;
    private static final Logger logger = Logger.getLogger("SimpleModDetect");

    public static NMSVersionAdapter initializeAdapter() {
        if (adapter != null) {
            return adapter;
        }

        String versionIdentifier = VersionDetector.getVersionIdentifier();
        String serverVersion = VersionDetector.detectVersion();

        try {
            if ("v26_2".equals(versionIdentifier)) {
                adapter = new V26_2Adapter();
                logger.info("已加载 26.2 版本适配器");
            } else if ("v26_1_2".equals(versionIdentifier)) {
                adapter = new V26_1_2Adapter();
                logger.info("已加载 26.1.2 版本适配器");
            } else {
                logger.warning("未知版本 " + serverVersion + "，使用默认适配器 26.2");
                adapter = new V26_2Adapter();
            }
        } catch (Exception e) {
            logger.severe("无法初始化版本适配器: " + e.getMessage());
            e.printStackTrace();
            adapter = new V26_2Adapter();
        }

        return adapter;
    }

    public static NMSVersionAdapter getAdapter() {
        if (adapter == null) {
            return initializeAdapter();
        }
        return adapter;
    }

    public static boolean isVersionSupported() {
        return VersionDetector.isSupportedVersion();
    }
}
