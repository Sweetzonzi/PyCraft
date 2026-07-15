package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.concurrent.CompletableFuture;

import io.github.sweetzonzi.py_port.common.agent.AlgorithmAgent;

import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;

public record SpawnAlgorithmPayload(
        float x,
        float y,
        float z
) implements PyPayload {
    public static final Codec<SpawnAlgorithmPayload> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.FLOAT.fieldOf("x")
                                    .forGetter(SpawnAlgorithmPayload::x),
                            Codec.FLOAT.fieldOf("y")
                                    .forGetter(SpawnAlgorithmPayload::y),
                            Codec.FLOAT.fieldOf("z")
                                    .forGetter(SpawnAlgorithmPayload::z)
                    ).apply(instance, SpawnAlgorithmPayload::new)
            );

    public static final PyPayloadType<SpawnAlgorithmPayload> TYPE =
            new PyPayloadType<>("spawn_algorithm", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(
            SpawnAlgorithmPayload payload,
            PyContext context
    ) {
        var server = context.getServer();
        if (server == null) {
            return PyHandleResult.fail(
                    "Server not running"
            );
        }
        CompletableFuture<JsonObject> future =
                new CompletableFuture<>();
        server.execute(() -> {

            try {
                JsonObject result =
                        new JsonObject();
                var level = server.overworld();
                AlgorithmAgent agent = new AlgorithmAgent(level);
                agent.setPosition(
                        new Vector3f(
                                payload.x(),
                                payload.y(),
                                payload.z()
                        )
                );
                agent.addToLevel();
                result.addProperty(
                        "agent_id",
                        agent.getId()
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
