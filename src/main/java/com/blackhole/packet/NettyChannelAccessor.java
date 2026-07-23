package com.blackhole.packet;

import com.comphenix.protocol.utility.MinecraftReflection;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Locates a player's Netty Channel by walking EntityPlayer -> PlayerConnection
 * -> NetworkManager -> Channel through generic (type-matched, not name-matched)
 * reflection. This is the "hybrid" half of the injection design (section 3):
 * ProtocolLib's MinecraftReflection resolves the NMS class handles for the
 * current server version so we never hardcode a package like
 * org.bukkit.craftbukkit.v1_8_R3, and we only ever reflect field *types*, not
 * obfuscated field names - no BuildTools/NMS compile dependency either way.
 */
public final class NettyChannelAccessor {

    private NettyChannelAccessor() {
    }

    public static Channel getChannel(Player player) {
        try {
            Object entityPlayer = invokeNoArg(player, "getHandle");
            Object playerConnection = findFieldOfType(entityPlayer, MinecraftReflection.getPlayerConnectionClass());
            Object networkManager = findFieldOfType(playerConnection, MinecraftReflection.getNetworkManagerClass());
            return (Channel) findFieldOfType(networkManager, Channel.class);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo obtener el Channel de Netty para " + player.getName(), e);
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object findFieldOfType(Object instance, Class<?> fieldType) throws IllegalAccessException {
        Class<?> current = instance.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (fieldType.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field.get(instance);
                }
            }
            current = current.getSuperclass();
        }
        throw new IllegalStateException("No field of type " + fieldType.getName() + " found on " + instance.getClass());
    }
}
