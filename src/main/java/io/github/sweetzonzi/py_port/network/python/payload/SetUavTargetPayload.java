package io.github.sweetzonzi.py_port.network.python.payload;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import io.github.sweetzonzi.py_port.common.agent.QuatUavAgent;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;

public record SetUavTargetPayload(
        int agent_id,
        float x,
        float y,
        float z
) implements PyPayload {

    public static final Codec<SetUavTargetPayload> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("agent_id").forGetter(SetUavTargetPayload::agent_id),
                    Codec.FLOAT.fieldOf("x").forGetter(SetUavTargetPayload::x),
                    Codec.FLOAT.fieldOf("y").forGetter(SetUavTargetPayload::y),
                    Codec.FLOAT.fieldOf("z").forGetter(SetUavTargetPayload::z)
            ).apply(instance, SetUavTargetPayload::new)
    );

    public static final PyPayloadType<SetUavTargetPayload> TYPE = new PyPayloadType<>("set_uav_target", CODEC);

    @Override
    public PyPayloadType<?> type() { return TYPE; }

    public static PyHandleResult handle(SetUavTargetPayload payload, PyContext context) {
        var server = context.getServer();
        if (server == null) return PyHandleResult.fail("Server not running");

        server.execute(() -> {
            for (var serverLevel : server.getAllLevels()) {
                AbstractAgent agent = AgentManager.getAgent(serverLevel, payload.agent_id());

                if (agent instanceof QuatUavAgent uav) {
                    uav.controller.setTarget(new com.jme3.math.Vector3f(payload.x(), payload.y(), payload.z()));
                    break;
                }
            }
        });

        return PyHandleResult.success(new com.google.gson.JsonObject());
    }
}