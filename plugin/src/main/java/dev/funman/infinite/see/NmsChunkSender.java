package dev.funman.infinite.see;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

/**
 * Sends a neighbor chunk to a player at remapped chunk coordinates so vanilla
 * Java and Bedrock (Geyser) clients can see the next seed past the rim.
 */
public final class NmsChunkSender {
    private final Logger log;
    private boolean available = true;
    private boolean loggedFailure = false;
    private boolean bound;

    private Method worldGetHandle;
    private Method playerGetHandle;
    private Method getChunk;
    private Method getLightEngine;
    private Field connectionField;
    private Method connectionSend;
    private Constructor<?> chunkPacketCtor;
    private Field packetX;
    private Field packetZ;
    private Constructor<?> forgetCtor;
    private Constructor<?> chunkPosCtor;

    public NmsChunkSender(Logger log) {
        this.log = log;
    }

    public boolean available() {
        return available;
    }

    public void sendMappedChunk(Player player, Chunk source, int destChunkX, int destChunkZ) {
        if (!available) {
            return;
        }
        try {
            ensureBound(player);
            Object nmsWorld = worldGetHandle.invoke(source.getWorld());
            Object nmsChunk = getChunk.invoke(nmsWorld, source.getX(), source.getZ());
            Object light = getLightEngine.invoke(nmsWorld);
            Object packet = newChunkPacket(nmsChunk, light);
            packetX.setInt(packet, destChunkX);
            packetZ.setInt(packet, destChunkZ);
            send(player, packet);
        } catch (ReflectiveOperationException ex) {
            fail(ex);
        }
    }

    public void forget(Player player, int chunkX, int chunkZ) {
        if (!available) {
            return;
        }
        try {
            ensureBound(player);
            Object pos = chunkPosCtor.newInstance(chunkX, chunkZ);
            send(player, forgetCtor.newInstance(pos));
        } catch (ReflectiveOperationException ex) {
            fail(ex);
        }
    }

    private void ensureBound(Player player) throws ReflectiveOperationException {
        if (bound) {
            return;
        }
        worldGetHandle = player.getWorld().getClass().getMethod("getHandle");
        Object nmsWorld = worldGetHandle.invoke(player.getWorld());
        getChunk = firstMethod(nmsWorld.getClass(), "getChunk", "getChunkAt");
        getLightEngine = firstMethod(nmsWorld.getClass(), "getLightEngine");

        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket");
        chunkPacketCtor = pickChunkCtor(packetClass);
        packetX = firstIntField(packetClass, "x", "chunkX");
        packetZ = firstIntField(packetClass, "z", "chunkZ");

        Class<?> forgetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket");
        Class<?> chunkPos = Class.forName("net.minecraft.world.level.ChunkPos");
        chunkPosCtor = chunkPos.getConstructor(int.class, int.class);
        forgetCtor = forgetClass.getConstructor(chunkPos);

        playerGetHandle = player.getClass().getMethod("getHandle");
        Object nmsPlayer = playerGetHandle.invoke(player);
        connectionField = firstField(nmsPlayer.getClass(), "connection", "f", "c");
        Object connection = connectionField.get(nmsPlayer);
        connectionSend = firstSend(connection.getClass());
        bound = true;
        log.info("See-through chunk remap bound (" + packetClass.getSimpleName() + ")");
    }

    private Object newChunkPacket(Object nmsChunk, Object light) throws ReflectiveOperationException {
        Class<?>[] params = chunkPacketCtor.getParameterTypes();
        Object[] args = new Object[params.length];
        args[0] = nmsChunk;
        args[1] = light;
        for (int i = 2; i < params.length; i++) {
            if (params[i] == boolean.class || params[i] == Boolean.class) {
                args[i] = Boolean.FALSE;
            } else {
                args[i] = null;
            }
        }
        return chunkPacketCtor.newInstance(args);
    }

    private void send(Player player, Object packet) throws ReflectiveOperationException {
        Object nmsPlayer = playerGetHandle.invoke(player);
        Object connection = connectionField.get(nmsPlayer);
        connectionSend.invoke(connection, packet);
    }

    private static Constructor<?> pickChunkCtor(Class<?> packetClass) {
        Constructor<?> fallback = null;
        for (Constructor<?> ctor : packetClass.getConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length >= 2 && p[0].getSimpleName().contains("Chunk")) {
                fallback = ctor;
                if (p.length >= 4) {
                    return ctor;
                }
            }
        }
        if (fallback == null) {
            throw new IllegalStateException("No chunk packet constructor on " + packetClass.getName());
        }
        return fallback;
    }

    private static Method firstMethod(Class<?> type, String... names) throws NoSuchMethodException {
        for (String name : names) {
            Class<?> c = type;
            while (c != null) {
                for (Method m : c.getMethods()) {
                    if (!m.getName().equals(name)) {
                        continue;
                    }
                    if (name.startsWith("getChunk") && m.getParameterCount() == 2
                            && m.getParameterTypes()[0] == int.class) {
                        m.setAccessible(true);
                        return m;
                    }
                    if (m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        return m;
                    }
                }
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(String.join("/", names) + " on " + type.getName());
    }

    private static Field firstField(Class<?> type, String... names) throws NoSuchFieldException {
        for (String name : names) {
            Class<?> c = type;
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
        }
        throw new NoSuchFieldException(String.join("/", names) + " on " + type.getName());
    }

    private static Field firstIntField(Class<?> type, String... names) throws NoSuchFieldException {
        for (String name : names) {
            Class<?> c = type;
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    if (f.getType() == int.class) {
                        f.setAccessible(true);
                        return f;
                    }
                } catch (NoSuchFieldException ignored) {
                    // continue
                }
                c = c.getSuperclass();
            }
        }
        int seen = 0;
        for (Field f : type.getDeclaredFields()) {
            if (f.getType() == int.class) {
                f.setAccessible(true);
                if (seen == 0 && names[0].contains("x")) {
                    return f;
                }
                seen++;
            }
        }
        throw new NoSuchFieldException("int field " + String.join("/", names) + " on " + type.getName());
    }

    private static Method firstSend(Class<?> connType) throws NoSuchMethodException {
        for (Method m : connType.getMethods()) {
            if (m.getParameterCount() != 1) {
                continue;
            }
            if (!m.getName().equals("send") && !m.getName().equals("sendPacket")) {
                continue;
            }
            m.setAccessible(true);
            return m;
        }
        throw new NoSuchMethodException("send(Packet) on " + connType.getName());
    }

    private void fail(Exception ex) {
        available = false;
        if (!loggedFailure) {
            loggedFailure = true;
            log.warning("See-through disabled (chunk remap failed): " + ex);
        }
    }
}
