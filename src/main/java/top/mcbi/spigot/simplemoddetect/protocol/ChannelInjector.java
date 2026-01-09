package top.mcbi.spigot.simplemoddetect.protocol;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;
import top.mcbi.spigot.simplemoddetect.nms.NMSVersionAdapter;
import top.mcbi.spigot.simplemoddetect.nms.VersionAdapterManager;

import java.util.logging.Level;

public class ChannelInjector {
    private final SimpleModDetect plugin;
    private final PacketHandler packetHandler;
    private final NMSVersionAdapter versionAdapter;

    public ChannelInjector(SimpleModDetect plugin, PacketHandler packetHandler) {
        this.plugin = plugin;
        this.packetHandler = packetHandler;
        this.versionAdapter = VersionAdapterManager.getAdapter();
    }

    public void injectPlayer(Player player) {
        try {
            Channel channel = versionAdapter.getPlayerChannel(player);
            if (channel == null) {
                plugin.getLogger().warning("无法获取玩家 " + player.getName() + " 的Channel");
                return;
            }

            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get("simplemoddetect_" + player.getName()) == null) {
                ChannelDuplexHandler handler = new ChannelDuplexHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        try {
                            if (versionAdapter.isCustomPayloadPacket(msg)) {
                                if (msg instanceof ServerboundCustomPayloadPacket packet) {
                                    packetHandler.handleCustomPayload(player, packet);
                                }
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "处理数据包时出错", e);
                        }
                        super.channelRead(ctx, msg);
                    }

                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        super.write(ctx, msg, promise);
                    }
                };

                pipeline.addBefore("packet_handler", "simplemoddetect_" + player.getName(), handler);
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("已为玩家 " + player.getName() + " 注入数据包监听器 (" + versionAdapter.getVersionName() + ")");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "注入玩家 " + player.getName() + " 时出错", e);
        }
    }

    public void removePlayer(Player player) {
        try {
            Channel channel = versionAdapter.getPlayerChannel(player);
            if (channel == null) {
                return;
            }

            channel.eventLoop().submit(() -> {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get("simplemoddetect_" + player.getName()) != null) {
                    pipeline.remove("simplemoddetect_" + player.getName());
                }
                return null;
            });
        } catch (Exception e) {
            // 忽略错误
        }
    }
}

