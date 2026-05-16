package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import io.github.sweetzonzi.py_port.common.agent.QuatUavAgent;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;
import java.util.ArrayList;
import java.util.List;

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

        JsonObject result = new JsonObject();
        server.execute(() -> {
            int removedCount = 0;
            for (var level : server.getAllLevels()) {
                // 复制一份列表，避免遍历时修改集合
                List<AbstractAgent> agents =
                        new ArrayList<>(
                                AgentManager.getLevelAgents(level)
                        );

                for (AbstractAgent agent : agents) {
                    if (agent instanceof QuatUavAgent uav) {
                        uav.removeFromLevel();
                        removedCount++;
                    }
                }
            }
            result.addProperty("removed_count", removedCount);
        });

        return PyHandleResult.success(result);
    }
}