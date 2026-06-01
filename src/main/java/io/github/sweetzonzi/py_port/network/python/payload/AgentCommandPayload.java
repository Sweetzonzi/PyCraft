package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import io.github.sweetzonzi.py_port.common.agent.CarEntity;
import io.github.sweetzonzi.py_port.common.agent.component.LineFollowComponent;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Agent 命令处理器。
 * Python 通过 TCP 发送 {"type": "agent_command", "data": {"agent_id": 1, "command": "drive", ...}}
 * 由本处理器查找 agent 并执行对应命令。
 * <p>
 * 由于各命令参数不同，使用 raw JsonObject 解析而非 RecordCodecBuilder。
 */

// 注：该代码实际上主要针对的是无人车的指令

public record AgentCommandPayload(
        String command,
        Optional<Integer> agent_id,

        Optional<Float> throttle,
        Optional<Float> steering,
        Optional<Boolean> brake,

        Optional<Float> x,
        Optional<Float> y,
        Optional<Float> z,

        Optional<Boolean> enabled,

        Optional<Float> p,
        Optional<Float> i,
        Optional<Float> d,

        Optional<Float> blocks,
        Optional<Float> degrees
) implements PyPayload {

    public static final Codec<AgentCommandPayload> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING.fieldOf("command").forGetter(AgentCommandPayload::command),
                            Codec.INT.optionalFieldOf("agent_id").forGetter(AgentCommandPayload::agent_id),

                            Codec.FLOAT.optionalFieldOf("throttle").forGetter(AgentCommandPayload::throttle),
                            Codec.FLOAT.optionalFieldOf("steering").forGetter(AgentCommandPayload::steering),
                            Codec.BOOL.optionalFieldOf("brake").forGetter(AgentCommandPayload::brake),

                            Codec.FLOAT.optionalFieldOf("x").forGetter(AgentCommandPayload::x),
                            Codec.FLOAT.optionalFieldOf("y").forGetter(AgentCommandPayload::y),
                            Codec.FLOAT.optionalFieldOf("z").forGetter(AgentCommandPayload::z),

                            Codec.BOOL.optionalFieldOf("enabled").forGetter(AgentCommandPayload::enabled),

                            Codec.FLOAT.optionalFieldOf("p").forGetter(AgentCommandPayload::p),
                            Codec.FLOAT.optionalFieldOf("i").forGetter(AgentCommandPayload::i),
                            Codec.FLOAT.optionalFieldOf("d").forGetter(AgentCommandPayload::d),

                            Codec.FLOAT.optionalFieldOf("blocks").forGetter(AgentCommandPayload::blocks),
                            Codec.FLOAT.optionalFieldOf("degrees").forGetter(AgentCommandPayload::degrees)
                    ).apply(instance, AgentCommandPayload::new)
            );

    public static final PyPayloadType<AgentCommandPayload> TYPE =
            new PyPayloadType<>("agent_command", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(AgentCommandPayload payload, PyContext context) {
        var server = context.getServer();
        if (server == null) {
            return PyHandleResult.fail("Server not running");
        }

        CompletableFuture<PyHandleResult> future = new CompletableFuture<>();

        server.execute(() -> {
            try {
                PyHandleResult result = handleOnServerThread(payload, context);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.get();
        } catch (Exception e) {
            return PyHandleResult.fail(e.getMessage());
        }
    }

    private static PyHandleResult handleOnServerThread(AgentCommandPayload payload, PyContext context) {
        var server = context.getServer();
        String command = payload.command();

        // 特殊指令：列出所有 agent（不需要 agent_id）
        if ("list_agents".equals(command)) {
            com.google.gson.JsonArray agentsList = new com.google.gson.JsonArray();

            for (ServerLevel level : server.getAllLevels()) {
                for (AbstractAgent a : AgentManager.getLevelAgents(level)) {
                    com.google.gson.JsonObject info = new com.google.gson.JsonObject();
                    info.addProperty("id", a.getId());
                    info.addProperty("type", a.getAgentType());
                    agentsList.add(info);
                }
            }

            JsonObject result = new JsonObject();
            result.add("agents", agentsList);
            return PyHandleResult.success(result);
        }

        // 删除 agent（不需要 CarEntity 检查，任何 agent 类型均可删除）
        if ("remove_agent".equals(command)) {
            int agentId = requireAgentId(payload);

            for (ServerLevel level : server.getAllLevels()) {
                AbstractAgent a = AgentManager.getAgent(level, agentId);
                if (a != null) {
                    a.removeFromLevel();
                    return PyHandleResult.success();
                }
            }

            return PyHandleResult.fail("Agent not found: " + agentId);
        }

        int agentId = requireAgentId(payload);

        // 在所有维度中查找 agent
        AbstractAgent agent = null;
        for (ServerLevel level : server.getAllLevels()) {
            agent = AgentManager.getAgent(level, agentId);
            if (agent != null) break;
        }

        if (agent == null) {
            return PyHandleResult.fail("Agent not found: " + agentId);
        }

        if (!(agent instanceof CarEntity car)) {
            return PyHandleResult.fail("Agent is not a CarEntity: " + agentId);
        }

        try {
            return dispatchCommand(car, payload);
        } catch (Exception e) {
            PyCraft.LOGGER.error("Agent command error: {} - {}", command, e.getMessage());
            return PyHandleResult.fail("Command error: " + e.getMessage());
        }
    }

    private static PyHandleResult dispatchCommand(CarEntity car, AgentCommandPayload payload) {
        JsonObject result = new JsonObject();
        String command = payload.command();

        switch (command) {
            case "drive" -> {
                float throttle = getFloat(payload.throttle(), 0f);
                float steering = getFloat(payload.steering(), 0f);
                boolean brake = getBool(payload.brake(), false);
                car.drive(throttle, steering, brake);
                return PyHandleResult.success();
            }
            case "handbrake" -> {
                car.handbrake();
                return PyHandleResult.success();
            }
            case "release_handbrake" -> {
                car.releaseHandbrake();
                return PyHandleResult.success();
            }
            case "get_speed" -> {
                result.addProperty("speed", car.getSpeed());
                return PyHandleResult.success(result);
            }
            case "get_position" -> {
                var pos = car.getPosition();
                result.addProperty("x", pos.x);
                result.addProperty("y", pos.y);
                result.addProperty("z", pos.z);
                return PyHandleResult.success(result);
            }
            case "set_position" -> {
                float x = getFloat(payload.x(), 0f);
                float y = getFloat(payload.y(), 0f);
                float z = getFloat(payload.z(), 0f);
                car.setPosition(new com.jme3.math.Vector3f(x, y, z));
                return PyHandleResult.success();
            }
            case "get_rotation" -> {
                var rot = car.getRotation();
                result.addProperty("x", rot.getX());
                result.addProperty("y", rot.getY());
                result.addProperty("z", rot.getZ());
                result.addProperty("w", rot.getW());
                return PyHandleResult.success(result);
            }
            case "line_follower_set_enabled" -> {
                LineFollowComponent lf = car.getLineFollower();
                if (lf == null) return PyHandleResult.fail("LineFollowComponent not found");
                lf.setEnabled(getBool(payload.enabled(), true));
                return PyHandleResult.success();
            }
            case "line_follower_set_throttle" -> {
                LineFollowComponent lf = car.getLineFollower();
                if (lf == null) return PyHandleResult.fail("LineFollowComponent not found");
                lf.setBaseThrottle(getFloat(payload.throttle(), 0.6f));
                return PyHandleResult.success();
            }
            case "line_follower_get_error" -> {
                LineFollowComponent lf = car.getLineFollower();
                if (lf == null) return PyHandleResult.fail("LineFollowComponent not found");
                result.addProperty("error", lf.getLastError());
                return PyHandleResult.success(result);
            }
            case "line_follower_reset_pid" -> {
                LineFollowComponent lf = car.getLineFollower();
                if (lf == null) return PyHandleResult.fail("LineFollowComponent not found");
                lf.resetPID();
                return PyHandleResult.success();
            }
            case "line_follower_set_pid" -> {
                LineFollowComponent lf = car.getLineFollower();
                if (lf == null) return PyHandleResult.fail("LineFollowComponent not found");
                lf.setPID(
                        getFloat(payload.p(), 0.8f),
                        getFloat(payload.i(), 0.05f),
                        getFloat(payload.d(), 0.3f)
                );
                return PyHandleResult.success();
            }
            // ====== Turtle 步进控制命令 ======
            case "turtle_front" -> {
                car.getTurtleController().enqueueFront(getFloat(payload.blocks(), 1f));
                return PyHandleResult.success();
            }
            case "turtle_back" -> {
                car.getTurtleController().enqueueBack(getFloat(payload.blocks(), 1f));
                return PyHandleResult.success();
            }
            case "turtle_turn_left" -> {
                car.getTurtleController().enqueueTurnLeft(getFloat(payload.degrees(), 90f));
                return PyHandleResult.success();
            }
            case "turtle_turn_right" -> {
                car.getTurtleController().enqueueTurnRight(getFloat(payload.degrees(), 90f));
                return PyHandleResult.success();
            }
            case "turtle_is_busy" -> {
                result.addProperty("busy", car.getTurtleController().isBusy());
                result.addProperty("queue_size", car.getTurtleController().queueSize());
                return PyHandleResult.success(result);
            }
            case "turtle_clear" -> {
                car.getTurtleController().clearQueue();
                return PyHandleResult.success();
            }
            default -> {
                return PyHandleResult.fail("Unknown command: " + command);
            }
        }
    }

    private static int requireAgentId(AgentCommandPayload payload) {
        return payload.agent_id()
                .orElseThrow(() -> new IllegalArgumentException("Missing required field: agent_id"));
    }

    private static float getFloat(Optional<Float> value, float defaultValue) {
        return value.orElse(defaultValue);
    }

    private static boolean getBool(Optional<Boolean> value, boolean defaultValue) {
        return value.orElse(defaultValue);
    }
}