package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;

import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.common.agent.QuatUavAgent;

import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;

public record ClearUavPayload() implements PyPayload {
    public static final Codec<ClearUavPayload> CODEC =
            Codec.unit(new ClearUavPayload());
    public static final PyPayloadType<ClearUavPayload> TYPE =
            new PyPayloadType<>("clear_uav", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(
            ClearUavPayload payload,
            PyContext context
    ) {
        var server = context.getServer();
        if (server == null) {
            return PyHandleResult.fail("Server not running");
        }

        server.execute(() -> {
            for (var level : server.getAllLevels()) {
                var map = AgentManager.levelAgents.get(level);
                if (map == null) continue;
                map.entrySet().removeIf(entry ->
                        entry.getValue() instanceof QuatUavAgent
                );
            }
        });

        return PyHandleResult.success(new JsonObject());
    }
}