package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import io.github.sweetzonzi.py_port.common.agent.QuatUavAgent;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;

public record RemoveUavPayload(
        int agent_id
) implements PyPayload {
    public static final Codec<RemoveUavPayload> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.INT.fieldOf("agent_id")
                                    .forGetter(RemoveUavPayload::agent_id)
                    ).apply(instance, RemoveUavPayload::new)
            );

    public static final PyPayloadType<RemoveUavPayload> TYPE =
            new PyPayloadType<>("remove_uav", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(
            RemoveUavPayload payload,
            PyContext context
    ) {
        var server = context.getServer();
        if (server == null) {
            return PyHandleResult.fail("Server not running");
        }
        JsonObject result = new JsonObject();
        server.execute(() -> {
            boolean removed = false;
            for (var level : server.getAllLevels()) {
                AbstractAgent agent =
                        AgentManager.getAgent(
                                level,
                                payload.agent_id()
                        );

                if (agent instanceof QuatUavAgent uav) {
                    uav.removeFromLevel();
                    removed = true;
                    result.addProperty("removed", true);
                    break;
                }
            }

            if (!removed) {

                result.addProperty("removed", false);
            }
        });

        return PyHandleResult.success(result);
    }
}