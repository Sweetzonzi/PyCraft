package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public record MoveEntityPayload(
        int entity_id,
        double x,
        double y,
        double z,
        double speed
) implements PyPayload {

    public static final Codec<MoveEntityPayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("entity_id").forGetter(MoveEntityPayload::entity_id),
                    Codec.DOUBLE.fieldOf("x").forGetter(MoveEntityPayload::x),
                    Codec.DOUBLE.fieldOf("y").forGetter(MoveEntityPayload::y),
                    Codec.DOUBLE.fieldOf("z").forGetter(MoveEntityPayload::z),
                    Codec.DOUBLE.fieldOf("speed").forGetter(MoveEntityPayload::speed)
            ).apply(instance, MoveEntityPayload::new));

    public static final PyPayloadType<MoveEntityPayload> TYPE = new PyPayloadType<>("move_entity", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(MoveEntityPayload payload, PyContext context) {
        var server = context.getServer();
        if (server == null) {
            return PyHandleResult.fail("Server not running");
        }

        Entity entity = null;
        for (ServerLevel level : server.getAllLevels()) {
            entity = level.getEntity(payload.entity_id());
            if (entity != null) break;
        }

        if (entity == null) {
            return PyHandleResult.fail("Entity not found");
        }

        double targetX = payload.x();
        double targetY = payload.y();
        double targetZ = payload.z();
        double maxSpeed = payload.speed();

        Vec3 current = entity.position();
        Vec3 target = new Vec3(targetX, targetY, targetZ);
        Vec3 delta = target.subtract(current);

        // 水平距离（忽略Y轴用于判断到达）
        double horizontalDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double verticalDist = delta.y;

        if (horizontalDist < 0.05 && Math.abs(verticalDist) < 0.1) {
            // 已到达，停止
            entity.setDeltaMovement(Vec3.ZERO);
            if (entity instanceof ServerPlayer) {
                ((ServerPlayer) entity).hurtMarked = true;
            } else {
                entity.hasImpulse = true;
            }
            return PyHandleResult.success(new JsonObject());
        }

        // 计算方向（归一化）
        Vec3 direction = delta.normalize();

        // P控制：距离越近速度越小
        double k = 0.5;
        double speed = Math.min(maxSpeed, horizontalDist * k);

        // 构建速度向量
        Vec3 velocity;
        if (entity instanceof ServerPlayer) {
            // 玩家：保持原有Y轴运动（重力/跳跃），只控制水平
            velocity = new Vec3(
                    direction.x * speed,
                    entity.getDeltaMovement().y, // 保留Y轴（重力）
                    direction.z * speed
            );
        } else {
            // 普通实体：包含Y轴但受重力影响
            velocity = new Vec3(
                    direction.x * speed,
                    direction.y * speed + (entity.onGround() ? 0 : -0.08), // 简单重力补偿
                    direction.z * speed
            );
        }

        entity.setDeltaMovement(velocity);

        if (entity instanceof ServerPlayer player) {
            player.hurtMarked = true;  // 玩家需要这个来同步客户端
        } else {
            entity.hasImpulse = true;  // 非玩家实体需要这个标记
        }

        // 如果是生物，额外设置AI目标（防止AI覆盖）
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop(); // 停止原有导航
        }

        return PyHandleResult.success(new JsonObject());
    }
}