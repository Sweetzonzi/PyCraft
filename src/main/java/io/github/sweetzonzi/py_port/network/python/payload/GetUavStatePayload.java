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
import com.jme3.math.Vector3f;

public record GetUavStatePayload(int agent_id) implements PyPayload {

    public static final Codec<GetUavStatePayload> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("agent_id").forGetter(GetUavStatePayload::agent_id)
            ).apply(instance, GetUavStatePayload::new)
    );

    public static final PyPayloadType<GetUavStatePayload> TYPE = new PyPayloadType<>("get_uav_state", CODEC);

    @Override
    public PyPayloadType<?> type() { return TYPE; }

    public static PyHandleResult handle(GetUavStatePayload payload, PyContext context) {
        var server = context.getServer();
        if (server == null) return PyHandleResult.fail("Server is not running");

        // 查找目标 UAV
        QuatUavAgent uav = null;
        for (var serverLevel : server.getAllLevels()) {
            AbstractAgent agent = AgentManager.getAgent(serverLevel, payload.agent_id());
            if (agent instanceof QuatUavAgent targetUav) {
                uav = targetUav;
                break;
            }
        }

        if (uav == null) {
            return PyHandleResult.fail("UAV Agent not found with ID: " + payload.agent_id());
        }

        // 构造返回数据
        JsonObject data = new JsonObject();
        Vector3f pos = uav.getPosition();
        Vector3f vel = uav.getLinearVelocity();

        // 坐标
        data.addProperty("x", pos.x);
        data.addProperty("y", pos.y);
        data.addProperty("z", pos.z);
        // 速度
        data.addProperty("vx", vel.x);
        data.addProperty("vy", vel.y);
        data.addProperty("vz", vel.z);
        // 朝向
        data.addProperty("yaw", uav.getYaw());

        return PyHandleResult.success(data);
    }
}