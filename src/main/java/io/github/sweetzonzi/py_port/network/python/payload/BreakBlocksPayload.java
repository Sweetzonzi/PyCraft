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
import net.minecraft.world.entity.monster.Husk;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public record BreakBlocksPayload(int entity_id, ResourceLocation level, List<TargetBlock> blocks, int break_ticks)
        implements PyPayload {
    private static final int MAX_BLOCKS = 256;

    public record TargetBlock(int x, int y, int z) {
        public static final Codec<TargetBlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(TargetBlock::x),
                Codec.INT.fieldOf("y").forGetter(TargetBlock::y),
                Codec.INT.fieldOf("z").forGetter(TargetBlock::z)
        ).apply(instance, TargetBlock::new));

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }

    public static final Codec<BreakBlocksPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("entity_id").forGetter(BreakBlocksPayload::entity_id),
            ResourceLocation.CODEC.fieldOf("level").forGetter(BreakBlocksPayload::level),
            TargetBlock.CODEC.listOf().fieldOf("blocks").forGetter(BreakBlocksPayload::blocks),
            Codec.INT.optionalFieldOf("break_ticks", 100).forGetter(BreakBlocksPayload::break_ticks)
    ).apply(instance, BreakBlocksPayload::new));

    public static final PyPayloadType<BreakBlocksPayload> TYPE = new PyPayloadType<>("break_blocks", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(BreakBlocksPayload payload, PyContext context) {
        var server = context.getServer();
        if (server == null) return PyHandleResult.fail("Server is not running");
        if (payload.break_ticks() <= 0) return PyHandleResult.fail("break_ticks must be greater than zero");
        if (payload.blocks().isEmpty() || payload.blocks().size() > MAX_BLOCKS) {
            return PyHandleResult.fail("blocks must contain between 1 and " + MAX_BLOCKS + " positions");
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        server.execute(() -> {
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, payload.level()));
            if (level == null) {
                future.completeExceptionally(new IllegalArgumentException("Level not found: " + payload.level()));
                return;
            }
            if (!(level.getEntity(payload.entity_id()) instanceof Husk husk)) {
                future.completeExceptionally(new IllegalArgumentException("Entity is not a husk: " + payload.entity_id()));
                return;
            }

            List<BlockPos> positions = payload.blocks().stream().map(TargetBlock::toBlockPos).distinct().toList();
            if (positions.stream().anyMatch(pos -> level.getBlockState(pos).isAir())) {
                future.completeExceptionally(new IllegalArgumentException("All target positions must contain blocks"));
                return;
            }
            if (!MobBlockBreakManager.isInRange(husk, positions)) {
                future.completeExceptionally(new IllegalArgumentException("All target blocks are out of range"));
                return;
            }

            MobBlockBreakManager.start(husk, level, positions, payload.break_ticks());
            JsonObject result = new JsonObject();
            result.addProperty("started", true);
            result.addProperty("block_count", positions.size());
            result.addProperty("break_ticks", payload.break_ticks());
            future.complete(result);
        });

        try {
            return PyHandleResult.success(future.get());
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return PyHandleResult.fail("Break blocks failed: " + cause.getMessage());
        }
    }
}
