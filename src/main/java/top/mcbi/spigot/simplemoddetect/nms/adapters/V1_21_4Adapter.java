package top.mcbi.spigot.simplemoddetect.nms.adapters;

import io.netty.channel.Channel;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.nms.NMSVersionAdapter;

/**
 * 1.21.4 版本适配器实现
 */
public class V1_21_4Adapter implements NMSVersionAdapter {

    @Override
    public Channel getPlayerChannel(Player player) {
        try {
            CraftPlayer craftPlayer = (CraftPlayer) player;
            ServerPlayer serverPlayer = craftPlayer.getHandle();
            ServerGamePacketListenerImpl connection = serverPlayer.connection;
            return connection.connection.channel;
        } catch (Exception e) {
            throw new RuntimeException("无法获取玩家Channel (1.21.4)", e);
        }
    }

    @Override
    public boolean isCustomPayloadPacket(Object packet) {
        return packet instanceof ServerboundCustomPayloadPacket;
    }

    @Override
    public Object getPacketPayload(Object packet) {
        if (packet instanceof ServerboundCustomPayloadPacket customPayloadPacket) {
            return customPayloadPacket.payload();
        }
        return null;
    }

    @Override
    public String getChannelName(DiscardedPayload discardedPayload) {
        try {
            // 在1.21.4中，id()返回ResourceLocation类型
            // 但由于编译时可能使用不同版本，使用反射避免直接导入
            Object id = discardedPayload.id();
            if (id == null) {
                return "unknown";
            }
            
            // 尝试通过反射检查是否是ResourceLocation类型
            Class<?> idClass = id.getClass();
            String className = idClass.getName();
            
            // 如果是ResourceLocation（可能在net.minecraft.resources或net.minecraft.core包中）
            if (className.contains("ResourceLocation") || className.contains("ResourceKey")) {
                // 直接调用toString()方法
                return id.toString();
            }
            
            // 如果是String类型
            if (id instanceof String) {
                return (String) id;
            }
            
            // 其他情况，直接toString
            return id.toString();
        } catch (Exception e) {
            throw new RuntimeException("无法获取DiscardedPayload的频道名称 (1.21.4): " + e.getMessage(), e);
        }
    }

    @Override
    public String getVersionName() {
        return "1.21.4";
    }
}

