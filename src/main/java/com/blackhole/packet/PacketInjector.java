package com.blackhole.packet;

import com.comphenix.protocol.events.PacketContainer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Injects a ChannelDuplexHandler directly into each player's Netty pipeline
 * (section 3): we own timing/thread control end to end and never depend on
 * ProtocolLib's scheduler. ProtocolLib is only used downstream, to turn the
 * raw packet objects this handler sees into typed PacketContainers.
 */
public final class PacketInjector {

    private static final String HANDLER_PREFIX = "anticheat-injector-";

    private final Plugin plugin;
    private final PacketListener packetListener;
    private final Map<String, Channel> injectedChannels = new ConcurrentHashMap<>();

    public PacketInjector(Plugin plugin, PacketListener packetListener) {
        this.plugin = plugin;
        this.packetListener = packetListener;
    }

    public void inject(Player player) {
        Channel channel = NettyChannelAccessor.getChannel(player);
        String handlerName = HANDLER_PREFIX + player.getUniqueId();

        if (channel.pipeline().get(handlerName) != null) {
            return;
        }

        channel.pipeline().addBefore("packet_handler", handlerName, new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                try {
                    PacketContainer container = PacketContainer.fromPacket(msg);
                    if (container != null) {
                        packetListener.onPacketReceived(player, container, System.nanoTime());
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.FINE,
                            "No se pudo envolver paquete entrante de " + player.getName(), e);
                }
                super.channelRead(ctx, msg);
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                try {
                    PacketContainer container = PacketContainer.fromPacket(msg);
                    if (container != null) {
                        packetListener.onPacketSent(player, container, System.nanoTime());
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.FINE,
                            "No se pudo envolver paquete saliente hacia " + player.getName(), e);
                }
                super.write(ctx, msg, promise);
            }
        });

        injectedChannels.put(handlerName, channel);
    }

    public void uninject(Player player) {
        String handlerName = HANDLER_PREFIX + player.getUniqueId();
        Channel channel = injectedChannels.remove(handlerName);
        if (channel == null) {
            return;
        }
        channel.eventLoop().execute(() -> {
            if (channel.pipeline().get(handlerName) != null) {
                channel.pipeline().remove(handlerName);
            }
        });
    }
}
