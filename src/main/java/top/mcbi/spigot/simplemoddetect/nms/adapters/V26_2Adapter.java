package top.mcbi.spigot.simplemoddetect.nms.adapters;

import io.netty.channel.Channel;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.nms.NMSVersionAdapter;

/**
 * Paper 26.2 版本适配器实现
 */
public class V26_2Adapter implements NMSVersionAdapter {

    @Override
    public Channel getPlayerChannel(Player player) {
        try {
            CraftPlayer craftPlayer = (CraftPlayer) player;
            ServerPlayer serverPlayer = craftPlayer.getHandle();
            ServerGamePacketListenerImpl connection = serverPlayer.connection;
            return connection.connection.channel;
        } catch (Exception e) {
            throw new RuntimeException("无法获取玩家Channel (26.2)", e);
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
        Identifier id = discardedPayload.id();
        return id.toString();
    }

    @Override
    public String getVersionName() {
        return "26.2";
    }
}
