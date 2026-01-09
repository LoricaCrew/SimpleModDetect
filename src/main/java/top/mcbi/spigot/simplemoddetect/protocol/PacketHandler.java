package top.mcbi.spigot.simplemoddetect.protocol;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.nms.NMSVersionAdapter;
import top.mcbi.spigot.simplemoddetect.nms.VersionAdapterManager;
import top.mcbi.spigot.simplemoddetect.utils.ModChecker;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class PacketHandler {
    private final SimpleModDetect plugin;
    private final ModChecker modChecker;
    private final NMSVersionAdapter versionAdapter;

    public PacketHandler(SimpleModDetect plugin, ModChecker modChecker) {
        this.plugin = plugin;
        this.modChecker = modChecker;
        this.versionAdapter = VersionAdapterManager.getAdapter();
    }

    public void handleCustomPayload(Player player, ServerboundCustomPayloadPacket packet) {
        try {
            CustomPacketPayload payload = packet.payload();

            if (payload instanceof DiscardedPayload discardedPayload) {
                // 使用版本适配器获取频道名称，以处理不同版本的id()方法差异
                String channelName = versionAdapter.getChannelName(discardedPayload);
                byte[] rawData = discardedPayload.data();

                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[SimpleModDetect] 收到数据包 - 通道: " + channelName);
                    debugRawData(rawData);
                }

                if (channelName.equals("minecraft:register")) {
                    parseMinecraftRegisterProtocol(player, rawData);
                } else if (channelName.equals("minecraft:brand")) {
                    parseMinecraftBrandProtocol(player, rawData);
                }

            } else if (payload instanceof BrandPayload brandPayload) {
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[SimpleModDetect] 客户端品牌: " + brandPayload.brand());
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "处理自定义载荷包时出错", e);
        }
    }

    private void debugRawData(byte[] data) {
        if (data == null || data.length == 0) {
            plugin.getLogger().info("[SimpleModDetect] 空数据");
            return;
        }

        plugin.getLogger().info("[SimpleModDetect] 数据长度: " + data.length + " 字节");

        String directString = new String(data, StandardCharsets.UTF_8);
        plugin.getLogger().info("[SimpleModDetect] 直接字符串解析: " + directString);

        String[] parts = directString.split("\0");
        plugin.getLogger().info("[SimpleModDetect] 分割后部分数量: " + parts.length);

        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                plugin.getLogger().info("[SimpleModDetect] 部分[" + i + "]: " + parts[i]);
            }
        }

        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, 100); i++) {
            hex.append(String.format("%02X ", data[i]));
        }
        plugin.getLogger().info("[SimpleModDetect] 十六进制: " + hex.toString());
    }

    private void parseMinecraftRegisterProtocol(Player player, byte[] data) {
        try {
            List<String> channels = new ArrayList<>();

            // 方法1：直接按空字符分割字符串
            String directString = new String(data, StandardCharsets.UTF_8);
            String[] channelArray = directString.split("\0");

            for (String channel : channelArray) {
                if (!channel.isEmpty()) {
                    channels.add(channel);
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("[SimpleModDetect] 注册频道: " + channel);
                    }
                }
            }

            // 方法2：如果方法1没有结果，尝试使用FriendlyByteBuf解析
            if (channels.isEmpty()) {
                FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
                try {
                    while (buf.readableBytes() > 0) {
                        StringBuilder channelBuilder = new StringBuilder();
                        while (true) {
                            byte b = buf.readByte();
                            if (b == 0) break;
                            channelBuilder.append((char) b);
                            if (buf.readableBytes() == 0) break;
                        }

                        String channel = channelBuilder.toString();
                        if (!channel.isEmpty()) {
                            channels.add(channel);
                            if (plugin.getConfigManager().isDebugMode()) {
                                plugin.getLogger().info("[SimpleModDetect] 注册频道(方法2): " + channel);
                            }
                        }
                    }
                    buf.release();
                } catch (Exception e) {
                    buf.release();
                }
            }

            if (!channels.isEmpty()) {
                extractModsFromRegisterChannels(player, channels);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "解析register协议时出错", e);
        }
    }

    private void parseMinecraftBrandProtocol(Player player, byte[] data) {
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
            String brand = buf.readUtf(32767);
            buf.release();

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[SimpleModDetect] 客户端品牌: " + brand);
                if (brand.toLowerCase().contains("fabric")) {
                    plugin.getLogger().info("[SimpleModDetect] 检测到Fabric客户端");
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "解析brand协议时出错", e);
        }
    }

    private void extractModsFromRegisterChannels(Player player, List<String> channels) {
        Set<String> detectedMods = new LinkedHashSet<>();

        for (String channel : channels) {
            if (channel.contains(":")) {
                String modId = channel.substring(0, channel.indexOf(":"));

                if (!modChecker.isBaseChannel(modId)) {
                    detectedMods.add(modId);
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("[SimpleModDetect] 检测到mod: " + modId + " (来自频道: " + channel + ")");
                    }
                }
            }
        }

        List<String> modList = new ArrayList<>(detectedMods);

        if (!modList.isEmpty()) {
            plugin.getModDetectionManager().handleDetectedMods(player, modList);
        }
    }
}

