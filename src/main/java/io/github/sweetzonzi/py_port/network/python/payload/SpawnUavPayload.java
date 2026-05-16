package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.concurrent.CompletableFuture;

import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import io.github.sweetzonzi.py_port.common.agent.QuatUavAgent;

import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;

public record SpawnUavPayload(
        float x,
        float y,
        float z
) implements PyPayload {
    public static final Codec<SpawnUavPayload> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.FLOAT.fieldOf("x")
                                    .forGetter(SpawnUavPayload::x),
                            Codec.FLOAT.fieldOf("y")
                                    .forGetter(SpawnUavPayload::y),
                            Codec.FLOAT.fieldOf("z")
                                    .forGetter(SpawnUavPayload::z)
                    ).apply(instance, SpawnUavPayload::new)
            );

    public static final PyPayloadType<SpawnUavPayload> TYPE =
            new PyPayloadType<>("spawn_uav", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(
            SpawnUavPayload payload,
            PyContext context
    ) {
        var server = context.getServer();
        if (server == null) {
            return PyHandleResult.fail(
                    "Server not running"
            );
        }

        // 用于等待主线程执行完成
        CompletableFuture<JsonObject> future =
                new CompletableFuture<>();
        server.execute(() -> {

            try {
                JsonObject result =
                        new JsonObject();
                // 默认在主世界生成
                var level = server.overworld();
                // 创建 UAV
                QuatUavAgent uav = new QuatUavAgent(level);
                // 设置初始位置
                uav.setPosition(
                        new Vector3f(
                                payload.x(),
                                payload.y(),
                                payload.z()
                        )
                );
                // 真正加入世界
                uav.addToLevel();
                // 初始化悬停
                uav.initHover();
                // 返回 agent_id
                result.addProperty(
                        "agent_id",
                        uav.getId()
                );
                future.complete(result);

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return PyHandleResult.success(
                    future.get()
            );

        } catch (Exception e) {
            return PyHandleResult.fail(
                    e.getMessage()
            );
        }
    }
}