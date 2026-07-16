package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.common.block.MobBlockBreakManager;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Husk;

import java.util.concurrent.CompletableFuture;

public record BreakBlockPayload(
        int entity_id,
        ResourceLocation level,
        int x,
        int y,
        int z,
        int break_ticks
) implements PyPayload {
    public static final Codec<BreakBlockPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("entity_id").forGetter(BreakBlockPayload::entity_id),
            ResourceLocation.CODEC.fieldOf("level").forGetter(BreakBlockPayload::level),
            Codec.INT.fieldOf("x").forGetter(BreakBlockPayload::x),
            Codec.INT.fieldOf("y").forGetter(BreakBlockPayload::y),
            Codec.INT.fieldOf("z").forGetter(BreakBlockPayload::z),
            Codec.INT.optionalFieldOf("break_ticks", 100).forGetter(BreakBlockPayload::break_ticks)
    ).apply(instance, BreakBlockPayload::new));

    public static final PyPayloadType<BreakBlockPayload> TYPE =
            new PyPayloadType<>("break_block", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(BreakBlockPayload payload, PyContext context) {
        var server = context.getServer();
        if (server == null) {
            return PyHandleResult.fail("Server is not running");
        }
        if (payload.break_ticks() <= 0) {
            return PyHandleResult.fail("break_ticks must be greater than zero");
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        server.execute(() -> {
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, payload.level()));
            if (level == null) {
                future.completeExceptionally(new IllegalArgumentException("Level not found: " + payload.level()));
                return;
            }

            Entity entity = level.getEntity(payload.entity_id());
            if (!(entity instanceof Husk husk)) {
                future.completeExceptionally(new IllegalArgumentException("Entity is not a husk: " + payload.entity_id()));
                return;
            }

            BlockPos pos = new BlockPos(payload.x(), payload.y(), payload.z());
            if (level.getBlockState(pos).isAir()) {
                future.completeExceptionally(new IllegalArgumentException("Target block is air: " + pos.toShortString()));
                return;
            }
            if (!MobBlockBreakManager.isInRange(husk, java.util.List.of(pos))) {
                future.completeExceptionally(new IllegalArgumentException("Target block is out of range"));
                return;
            }

            MobBlockBreakManager.start(husk, level, pos, payload.break_ticks());
            JsonObject result = new JsonObject();
            result.addProperty("started", true);
            result.addProperty("break_ticks", payload.break_ticks());
            future.complete(result);
        });

        try {
            return PyHandleResult.success(future.get());
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return PyHandleResult.fail("Break block failed: " + cause.getMessage());
        }
    }
}
