package io.github.sweetzonzi.py_port.network.python.infrastructure;

import com.google.gson.JsonObject;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PyContext {
    private final ChannelHandlerContext channelCtx;
    private final String type;
    private final UUID uuid;

    public PyContext(ChannelHandlerContext channelCtx, String type, UUID uuid) {
        this.channelCtx = channelCtx;
        this.type = type;
        this.uuid = uuid;
    }

    /**
     * 获取当前服务器实例（确保仅在服务端调用）
     */
    public MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }


    /**
     * 提交任务到主线程执行（类似 enqueueWork）
     */
    public CompletableFuture<Void> enqueueWork(Runnable task) {
        MinecraftServer server = getServer();
        if (server == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Server not available"));
        }
        return CompletableFuture.runAsync(task, server);
    }

    /**
     * 带返回值的任务
     */
    public <T> CompletableFuture<T> enqueueWork(java.util.function.Supplier<T> task) {
        MinecraftServer server = getServer();
        if (server == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Server not available"));
        }
        return CompletableFuture.supplyAsync(task, server);
    }

    /**
     * 发送响应给 Python（根据 PyHandleResult 构建响应）
     * @param result 网络包处理结果，包含是否成功，错误消息和返回数据
     */
    public void reply(PyHandleResult result) {
        JsonObject response = new JsonObject();
        response.addProperty("type", type);
        response.addProperty("uuid", uuid.toString());
        response.addProperty("success", result.isSuccess());
        if (result.isSuccess()) {
            if (result.data() != null)
                response.add("data", result.data());
        } else {
            response.addProperty("error_message", result.errorMessage());
        }
        // 回复给Python
        channelCtx.writeAndFlush(response);
    }

    /**
     * 直接获取 Netty 的 ChannelHandlerContext，用于高级操作
     */
    public ChannelHandlerContext channelHandlerContext() {
        return channelCtx;
    }

    public UUID getUuid() {
        return uuid;
    }

    /**
     * Resolves the player targeted by a Python API request.
     *
     * <p>An entity id is required when multiple players are online. A missing id
     * ({@code -1}) remains supported for old single-player Python clients.</p>
     *
     * <p>This method must be called from the Minecraft server thread.</p>
     */
    public ServerPlayer requirePlayer(int entityId) {
        MinecraftServer server = getServer();
        if (server == null) {
            throw new IllegalStateException("Server not available");
        }

        var players = server.getPlayerList().getPlayers();
        if (entityId >= 0) {
            for (ServerPlayer player : players) {
                if (player.getId() == entityId) {
                    return player;
                }
            }
            throw new IllegalArgumentException("Player entity " + entityId + " is not online");
        }

        if (players.size() == 1) {
            return players.getFirst();
        }
        if (players.isEmpty()) {
            throw new IllegalStateException("No player is online");
        }
        throw new IllegalArgumentException("entity_id is required when multiple players are online");
    }
}
