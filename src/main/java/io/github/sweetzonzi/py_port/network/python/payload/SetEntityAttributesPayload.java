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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public record SetEntityAttributesPayload(
        int entity_id,
        Optional<Double> max_health,
        Optional<Double> health,
        Optional<Double> movement_speed,
        Optional<Double> attack_damage,
        Optional<Double> follow_range
) implements PyPayload {

    public static final Codec<SetEntityAttributesPayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("entity_id")
                            .forGetter(SetEntityAttributesPayload::entity_id),
                    Codec.DOUBLE.optionalFieldOf("max_health")
                            .forGetter(SetEntityAttributesPayload::max_health),
                    Codec.DOUBLE.optionalFieldOf("health")
                            .forGetter(SetEntityAttributesPayload::health),
                    Codec.DOUBLE.optionalFieldOf("movement_speed")
                            .forGetter(SetEntityAttributesPayload::movement_speed),
                    Codec.DOUBLE.optionalFieldOf("attack_damage")
                            .forGetter(SetEntityAttributesPayload::attack_damage),
                    Codec.DOUBLE.optionalFieldOf("follow_range")
                            .forGetter(SetEntityAttributesPayload::follow_range)
            ).apply(instance, SetEntityAttributesPayload::new));

    public static final PyPayloadType<SetEntityAttributesPayload> TYPE =
            new PyPayloadType<>("set_entity_attributes", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    /**
     * 安全地设置属性值，如果实体不支持该属性则跳过
     */
    private static boolean trySetBaseValue(
            LivingEntity entity,
            net.minecraft.core.Holder<
                    net.minecraft.world.entity.ai.attributes.Attribute
                    > attribute,
            double value,
            String attributeName
    ) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            // 实体不支持此属性，跳过（不抛异常）
            return false;
        }
        instance.setBaseValue(value);
        return true;
    }

    public static PyHandleResult handle(
            SetEntityAttributesPayload payload,
            PyContext context
    ) {
        var server = context.getServer();

        if (server == null) {
            return PyHandleResult.fail("Server not running");
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        server.execute(() -> {
            try {
                Entity found = null;

                for (ServerLevel level : server.getAllLevels()) {
                    found = level.getEntity(payload.entity_id());
                    if (found != null) {
                        break;
                    }
                }

                if (!(found instanceof LivingEntity entity)) {
                    future.completeExceptionally(
                            new RuntimeException(
                                    "Entity is not a living entity: "
                                            + payload.entity_id()
                            )
                    );
                    return;
                }

                JsonObject skipped = new JsonObject();  // 记录被跳过的属性

                if (payload.max_health().isPresent()) {
                    double value = payload.max_health().get();
                    if (value <= 0.0) {
                        throw new IllegalArgumentException(
                                "max_health must be greater than 0"
                        );
                    }
                    boolean ok = trySetBaseValue(
                            entity, Attributes.MAX_HEALTH, value, "max_health"
                    );
                    if (!ok) skipped.addProperty("max_health", "not supported");

                    if (entity.getHealth() > value) {
                        entity.setHealth((float) value);
                    }
                }

                if (payload.movement_speed().isPresent()) {
                    double value = payload.movement_speed().get();
                    if (value < 0.0) {
                        throw new IllegalArgumentException(
                                "movement_speed cannot be negative"
                        );
                    }
                    boolean ok = trySetBaseValue(
                            entity, Attributes.MOVEMENT_SPEED, value, "movement_speed"
                    );
                    if (!ok) skipped.addProperty("movement_speed", "not supported");
                }

                if (payload.attack_damage().isPresent()) {
                    double value = payload.attack_damage().get();
                    if (value < 0.0) {
                        throw new IllegalArgumentException(
                                "attack_damage cannot be negative"
                        );
                    }
                    boolean ok = trySetBaseValue(
                            entity, Attributes.ATTACK_DAMAGE, value, "attack_damage"
                    );
                    if (!ok) skipped.addProperty("attack_damage", "not supported");
                }

                if (payload.follow_range().isPresent()) {
                    double value = payload.follow_range().get();
                    if (value < 0.0) {
                        throw new IllegalArgumentException(
                                "follow_range cannot be negative"
                        );
                    }
                    // FOLLOW_RANGE只有 Mob 有
                    if (entity instanceof Mob mob) {
                        boolean ok = trySetBaseValue(
                                mob, Attributes.FOLLOW_RANGE, value, "follow_range"
                        );
                        if (!ok) skipped.addProperty("follow_range", "not supported");
                    } else {
                        skipped.addProperty("follow_range", "only supported by Mob");
                    }
                }

                if (payload.health().isPresent()) {
                    double value = payload.health().get();
                    if (value < 0.0) {
                        throw new IllegalArgumentException(
                                "health cannot be negative"
                        );
                    }
                    entity.setHealth(
                            (float) Math.min(value, entity.getMaxHealth())
                    );
                }

                JsonObject result = new JsonObject();
                result.addProperty("entity_id", entity.getId());
                result.addProperty("entity_type", entity.getType().toString());
                result.addProperty("max_health", entity.getMaxHealth());
                result.addProperty("health", entity.getHealth());

                // 安全获取可能不存在的属性
                result.addProperty("movement_speed",
                        entity.getAttributeValue(Attributes.MOVEMENT_SPEED));
                result.addProperty("attack_damage",
                        entity.getAttributeValue(Attributes.ATTACK_DAMAGE));

                // FOLLOW_RANGE可能不存在（比如对于player而言）
                if (entity instanceof Mob mob) {
                    result.addProperty("follow_range",
                            mob.getAttributeValue(Attributes.FOLLOW_RANGE));
                } else {
                    result.addProperty("follow_range", -1.0);  // 表示不适用
                }

                if (skipped.size() > 0) {
                    result.add("skipped", skipped);
                }

                future.complete(result);

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return PyHandleResult.success(future.get());
        } catch (Exception e) {
            return PyHandleResult.fail(
                    "Set entity attributes failed: " + e.getMessage()
            );
        }
    }
}