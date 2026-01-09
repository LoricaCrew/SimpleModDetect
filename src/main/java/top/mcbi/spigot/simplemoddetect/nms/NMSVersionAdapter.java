package top.mcbi.spigot.simplemoddetect.nms;

import io.netty.channel.Channel;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import org.bukkit.entity.Player;

/**
 * NMS版本适配器接口
 * 用于处理不同版本之间的NMS API差异
 */
public interface NMSVersionAdapter {
    
    /**
     * 获取玩家的Netty Channel
     * @param player Bukkit玩家对象
     * @return Netty Channel对象
     */
    Channel getPlayerChannel(Player player);
    
    /**
     * 检查数据包类型是否为自定义载荷包
     * @param packet 数据包对象
     * @return 是否为自定义载荷包
     */
    boolean isCustomPayloadPacket(Object packet);
    
    /**
     * 获取数据包的有效载荷对象
     * @param packet 数据包对象
     * @return 有效载荷对象
     */
    Object getPacketPayload(Object packet);
    
    /**
     * 从DiscardedPayload中获取频道名称
     * 不同版本的id()方法返回类型可能不同，需要统一处理
     * @param discardedPayload DiscardedPayload对象
     * @return 频道名称字符串，如 "minecraft:register"
     */
    String getChannelName(DiscardedPayload discardedPayload);
    
    /**
     * 获取版本名称
     * @return 版本名称，如 "1.21.4" 或 "1.21.8"
     */
    String getVersionName();
}

