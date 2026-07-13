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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public record GetEntityAttributesPayload(
        int entity_id,
        Optional<List<String>> attributes  // null/empty 表示获取全部
) implements PyPayload {

    public static final Codec<GetEntityAttributesPayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("entity_id")
                            .forGetter(GetEntityAttributesPayload::entity_id),
                    Codec.STRING.listOf().optionalFieldOf("attributes")
                            .forGetter(GetEntityAttributesPayload::attributes)
            ).apply(instance, GetEntityAttributesPayload::new));

    public static final PyPayloadType<GetEntityAttributesPayload> TYPE =
            new PyPayloadType<>("get_entity_attributes", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    private static void addAttributeToResult(
            JsonObject result,
            LivingEntity entity,
            String name,
            net.minecraft.core.Holder<
                    net.minecraft.world.entity.ai.attributes.Attribute
                    > attribute
    ) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            JsonObject attrObj = new JsonObject();
            attrObj.addProperty("base", instance.getBaseValue());
            attrObj.addProperty("value", instance.getValue());
            result.add(name, attrObj);
        }
    }

    public static PyHandleResult handle(
            GetEntityAttributesPayload payload,
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

                JsonObject result = new JsonObject();
                result.addProperty("entity_id", entity.getId());
                result.addProperty("entity_type", entity.getType().toString());

                List<String> requested = payload.attributes().orElse(List.of());

                // 如果指定了属性列表，只返回请求的；否则返回全部
                boolean getAll = requested.isEmpty();

                if (getAll || requested.contains("max_health")) {
                    addAttributeToResult(result, entity, "max_health", Attributes.MAX_HEALTH);
                    result.addProperty("health", entity.getHealth());
                }

                if (getAll || requested.contains("health")) {
                    // health不是属性，需要单独处理
                    if (!result.has("health")) {
                        result.addProperty("health", entity.getHealth());
                    }
                }

                if (getAll || requested.contains("movement_speed")) {
                    addAttributeToResult(result, entity, "movement_speed", Attributes.MOVEMENT_SPEED);
                }

                if (getAll || requested.contains("attack_damage")) {
                    addAttributeToResult(result, entity, "attack_damage", Attributes.ATTACK_DAMAGE);
                }

                if (getAll || requested.contains("follow_range")) {
                    addAttributeToResult(result, entity, "follow_range", Attributes.FOLLOW_RANGE);
                }

                if (getAll || requested.contains("armor")) {
                    addAttributeToResult(result, entity, "armor", Attributes.ARMOR);
                }

                if (getAll || requested.contains("armor_toughness")) {
                    addAttributeToResult(result, entity, "armor_toughness", Attributes.ARMOR_TOUGHNESS);
                }

                if (getAll || requested.contains("knockback_resistance")) {
                    addAttributeToResult(result, entity, "knockback_resistance", Attributes.KNOCKBACK_RESISTANCE);
                }

                if (getAll || requested.contains("attack_speed")) {
                    addAttributeToResult(result, entity, "attack_speed", Attributes.ATTACK_SPEED);
                }

                if (getAll || requested.contains("luck")) {
                    addAttributeToResult(result, entity, "luck", Attributes.LUCK);
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
                    "Get entity attributes failed: "
                            + e.getMessage()
            );
        }
    }
}