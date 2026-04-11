package top.mcbi.spigot.simplemoddetect.utils;

import io.netty.channel.Channel;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;
import top.mcbi.spigot.simplemoddetect.nms.NMSVersionAdapter;
import top.mcbi.spigot.simplemoddetect.nms.VersionAdapterManager;

import java.util.logging.Level;

public class MarlowCrystalOptimizerDisabler {
    private final SimpleModDetect plugin;
    private final NMSVersionAdapter versionAdapter;

    private static final String OPT_OUT_CHANNEL = "marlowcrystal:opt_out";
    private static final String OPT_OUT_ACK_CHANNEL = "marlowcrystal:opt_out_ack";
    private static final String MOD_IDENTIFIER = "marlowcrystal";

    public MarlowCrystalOptimizerDisabler(SimpleModDetect plugin) {
        this.plugin = plugin;
        this.versionAdapter = VersionAdapterManager.getAdapter();
    }

    public void handleIncomingPacket(Player player, Object packet) {
        if (!versionAdapter.isCustomPayloadPacket(packet)) {
            return;
        }

        Object payloadObj = versionAdapter.getPacketPayload(packet);
        if (!(payloadObj instanceof DiscardedPayload discardedPayload)) {
            return;
        }

        String channelName = versionAdapter.getChannelName(discardedPayload);
        byte[] rawData = discardedPayload.data();

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[MarlowCrystalOptimizerDisabler] 处理通道: " + channelName);
        }

        if (OPT_OUT_ACK_CHANNEL.equals(channelName)) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[MarlowCrystalOptimizerDisabler] 玩家 " + player.getName() + " 已确认禁用 " + MOD_IDENTIFIER + " 模组功能。");
            }
        }
//        if (LEGACY_MCO_CHANNEL.equals(channelName)) {
//            handleLegacyMcoChannel(player);
//        }
//        已在其它地方处理
    }

    public void sendOptOutPacket(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Channel channel = versionAdapter.getPlayerChannel(player);
                if (channel == null) {
                    plugin.getLogger().warning("[MarlowCrystalOptimizerDisabler] 无法获取玩家 " + player.getName() + " 的网络通道来发送退出包。");
                    return;
                }

                byte[] emptyData = new byte[0];
                CustomPacketPayload payload = createCustomPayload(OPT_OUT_CHANNEL, emptyData);
                if (payload == null) {
                    plugin.getLogger().warning("[MarlowCrystalOptimizerDisabler] 无法为通道 " + OPT_OUT_CHANNEL + " 创建载荷对象。");
                    return;
                }

                ClientboundCustomPayloadPacket packet = new ClientboundCustomPayloadPacket(payload);

                channel.writeAndFlush(packet).addListener(future -> {
                    if (future.isSuccess()) {
                        if (plugin.getConfigManager().isDebugMode()) {
                            plugin.getLogger().info("[MarlowCrystalOptimizerDisabler] 已向玩家 " + player.getName() + " 发送禁用请求。");
                        }
                    } else {
                        plugin.getLogger().warning("[MarlowCrystalOptimizerDisabler] 向玩家 " + player.getName() + " 发送禁用请求失败: " + future.cause().getMessage());
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[MarlowCrystalOptimizerDisabler] 发送退出数据包时出错", e);
            }
        });
    }


    private CustomPacketPayload createCustomPayload(String channel, byte[] data) {
        try {
            // 版本相关
            Identifier id = Identifier.parse(channel);
            return new DiscardedPayload(id, data);
        } catch (Exception e1) {
            plugin.getLogger().warning("[MarlowCrystalOptimizerDisabler] 无法通过DiscardedPayload创建载荷: " + e1);
            return null;
        }
    }

}