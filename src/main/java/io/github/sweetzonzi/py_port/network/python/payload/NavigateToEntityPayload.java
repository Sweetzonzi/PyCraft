package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

import java.util.concurrent.CompletableFuture;

/**
 * 使用 Minecraft 原生 Mob PathNavigation，
 * 让一个 Mob 向目标实体移动。
 *
 * 注意：
 * 1. 本接口只负责寻路和移动；
 * 2. 不调用 mob.setTarget(target)；
 * 3. 不调用 mob.doHurtTarget(target)；
 * 4. 攻击由 Python 端的 attack_entity 接口完成。
 */
public record NavigateToEntityPayload(
        int entity_id,
        int target_id,
        double speed
) implements PyPayload {

    public static final Codec<NavigateToEntityPayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("entity_id")
                            .forGetter(NavigateToEntityPayload::entity_id),

                    Codec.INT.fieldOf("target_id")
                            .forGetter(NavigateToEntityPayload::target_id),

                    Codec.DOUBLE.optionalFieldOf("speed", 1.0)
                            .forGetter(NavigateToEntityPayload::speed)
            ).apply(instance, NavigateToEntityPayload::new));

    public static final PyPayloadType<NavigateToEntityPayload> TYPE =
            new PyPayloadType<>(
                    "navigate_to_entity",
                    CODEC
            );

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    /**
     * 在服务器所有维度中，根据 entity ID 查找实体。
     */
    private static Entity findEntity(
            Iterable<ServerLevel> levels,
            int entityId
    ) {
        for (ServerLevel level : levels) {
            Entity entity = level.getEntity(entityId);

            if (entity != null) {
                return entity;
            }
        }

        return null;
    }

    public static PyHandleResult handle(
            NavigateToEntityPayload payload,
            PyContext context
    ) {
        var server = context.getServer();

        if (server == null) {
            return PyHandleResult.fail(
                    "Server not running"
            );
        }

        if (!Double.isFinite(payload.speed())) {
            return PyHandleResult.fail(
                    "Navigation speed must be finite"
            );
        }

        if (payload.speed() <= 0.0) {
            return PyHandleResult.fail(
                    "Navigation speed must be greater than 0"
            );
        }

        /*
         * speed 是导航速度倍率，不建议设置得太大。
         * 这里设置一个保护上限，避免异常值导致移动失控。
         */
        if (payload.speed() > 10.0) {
            return PyHandleResult.fail(
                    "Navigation speed cannot be greater than 10"
            );
        }

        CompletableFuture<JsonObject> future =
                new CompletableFuture<>();

        server.execute(() -> {
            try {
                Entity sourceEntity = findEntity(
                        server.getAllLevels(),
                        payload.entity_id()
                );

                if (sourceEntity == null) {
                    throw new IllegalArgumentException(
                            "Navigation entity not found: "
                                    + payload.entity_id()
                    );
                }

                if (!(sourceEntity instanceof Mob mob)) {
                    throw new IllegalArgumentException(
                            "Entity is not a Mob and cannot use "
                                    + "Minecraft PathNavigation: "
                                    + payload.entity_id()
                    );
                }

                Entity targetEntity = findEntity(
                        server.getAllLevels(),
                        payload.target_id()
                );

                if (targetEntity == null) {
                    throw new IllegalArgumentException(
                            "Target entity not found: "
                                    + payload.target_id()
                    );
                }

                if (!(targetEntity instanceof LivingEntity target)) {
                    throw new IllegalArgumentException(
                            "Target entity is not a LivingEntity: "
                                    + payload.target_id()
                    );
                }

                if (!mob.isAlive()) {
                    throw new IllegalStateException(
                            "Navigation Mob is not alive: "
                                    + mob.getId()
                    );
                }

                if (!target.isAlive()) {
                    throw new IllegalStateException(
                            "Target entity is not alive: "
                                    + target.getId()
                    );
                }

                Level mobLevel = mob.level();
                Level targetLevel = target.level();

                /*
                 * 原生 PathNavigation 无法跨维度导航。
                 */
                if (mobLevel != targetLevel) {
                    throw new IllegalArgumentException(
                            "Mob and target are not in the same level"
                    );
                }

                PathNavigation navigation =
                        mob.getNavigation();

                /*
                 * 只调用原生导航，不调用 setTarget，
                 * 避免 Husk 的原版攻击 AI 自动接管。
                 *
                 * moveTo(Entity, speed) 会计算一条到目标实体附近的路径，
                 * 返回值表示本次是否成功开始导航。
                 */
                boolean started = navigation.moveTo(
                        target,
                        payload.speed()
                );

                double distance = mob.distanceTo(target);
                double distanceSquared =
                        mob.distanceToSqr(target);

                boolean hasPath =
                        navigation.getPath() != null;

                boolean navigationDone =
                        navigation.isDone();

                JsonObject result = new JsonObject();

                result.addProperty(
                        "entity_id",
                        mob.getId()
                );

                result.addProperty(
                        "target_id",
                        target.getId()
                );

                result.addProperty(
                        "entity_type",
                        mob.getType().toString()
                );

                result.addProperty(
                        "target_type",
                        target.getType().toString()
                );

                result.addProperty(
                        "speed",
                        payload.speed()
                );

                result.addProperty(
                        "started",
                        started
                );

                result.addProperty(
                        "has_path",
                        hasPath
                );

                result.addProperty(
                        "navigation_done",
                        navigationDone
                );

                result.addProperty(
                        "distance",
                        distance
                );

                result.addProperty(
                        "distance_squared",
                        distanceSquared
                );

                result.addProperty(
                        "mob_x",
                        mob.getX()
                );

                result.addProperty(
                        "mob_y",
                        mob.getY()
                );

                result.addProperty(
                        "mob_z",
                        mob.getZ()
                );

                result.addProperty(
                        "target_x",
                        target.getX()
                );

                result.addProperty(
                        "target_y",
                        target.getY()
                );

                result.addProperty(
                        "target_z",
                        target.getZ()
                );

                /*
                 * 明确告诉 Python 端：
                 * 该接口没有触发原版攻击。
                 */
                result.addProperty(
                        "attack_triggered",
                        false
                );

                future.complete(result);

            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });

        try {
            return PyHandleResult.success(
                    future.get()
            );
        } catch (Exception exception) {
            Throwable cause = exception.getCause();

            String message;

            if (cause != null && cause.getMessage() != null) {
                message = cause.getMessage();
            } else if (exception.getMessage() != null) {
                message = exception.getMessage();
            } else {
                message = exception.getClass()
                        .getSimpleName();
            }

            return PyHandleResult.fail(
                    "Navigate to entity failed: " + message
            );
        }
    }
}