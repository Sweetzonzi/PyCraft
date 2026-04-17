package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import io.github.sweetzonzi.py_port.common.agent.QuatUavAgent;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;
import com.jme3.math.Vector3f;

public record GetUavListPayload() implements PyPayload {

    public static final Codec<GetUavListPayload> CODEC = Codec.unit(new GetUavListPayload());
    public static final PyPayloadType<GetUavListPayload> TYPE =
            new PyPayloadType<>("get_uav_list", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(GetUavListPayload payload, PyContext context) {
        var server = context.getServer();
        if (server == null) return PyHandleResult.fail("Server not running");
        JsonArray arr = new JsonArray();

        for (var level : server.getAllLevels()) {
            for (AbstractAgent agent : AgentManager.getLevelAgents(level)) {
                if (agent instanceof QuatUavAgent uav) {
                    JsonObject obj = new JsonObject();
                    int id = uav.getId();
                    obj.addProperty("agent_id", id);

                    Vector3f pos = uav.getPosition();
                    obj.addProperty("x", pos.x);
                    obj.addProperty("y", pos.y);
                    obj.addProperty("z", pos.z);
                    arr.add(obj);
                }
            }
        }

        JsonObject data = new JsonObject();
        data.add("uavs", arr);
        return PyHandleResult.success(data);
    }
}