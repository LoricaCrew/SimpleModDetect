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
 * 1.21.8 版本适配器实现
 */
public class V1_21_8Adapter implements NMSVersionAdapter {

    @Override
    public Channel getPlayerChannel(Player player) {
        try {
            CraftPlayer craftPlayer = (CraftPlayer) player;
            ServerPlayer serverPlayer = craftPlayer.getHandle();
            ServerGamePacketListenerImpl connection = serverPlayer.connection;
            return connection.connection.channel;
        } catch (Exception e) {
            throw new RuntimeException("无法获取玩家Channel (1.21.8)", e);
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
            // 在1.21.8中，id()可能返回不同的类型
            // 由于编译时可能使用不同版本的API，使用反射避免直接类型检查
            Object id = discardedPayload.id();
            if (id == null) {
                return "unknown";
            }
            
            // 如果是字符串类型
            if (id instanceof String) {
                return (String) id;
            }
            
            // 检查类名，判断是否是ResourceLocation类型（可能在不同的包中）
            Class<?> idClass = id.getClass();
            String className = idClass.getName();
            
            // 如果是ResourceLocation或ResourceKey类型
            if (className.contains("ResourceLocation") || className.contains("ResourceKey")) {
                // 直接调用toString()方法
                return id.toString();
            }
            
            // 其他情况，直接toString
            return id.toString();
        } catch (Exception e) {
            // 如果出现异常，尝试使用反射获取（以防方法签名在不同版本间有变化）
            try {
                java.lang.reflect.Method idMethod = discardedPayload.getClass().getMethod("id");
                Object id = idMethod.invoke(discardedPayload);
                if (id != null) {
                    return id.toString();
                }
            } catch (Exception ex) {
                // 如果反射也失败，抛出原始异常
            }
            throw new RuntimeException("无法获取DiscardedPayload的频道名称 (1.21.8): " + e.getMessage(), e);
        }
    }

    @Override
    public String getVersionName() {
        return "1.21.8";
    }
}

