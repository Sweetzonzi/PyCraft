package io.github.sweetzonzi.py_port.common.block;

import io.github.sweetzonzi.py_port.PyCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Runs server-side, timed block-breaking actions controlled by Python payloads. */
@EventBusSubscriber(modid = PyCraft.MOD_ID)
public final class MobBlockBreakManager {
    public static final double MAX_DISTANCE = 2.5;
    private static final Map<UUID, BreakTask> TASKS = new HashMap<>();

    private MobBlockBreakManager() {
    }

    public static void start(Husk husk, ServerLevel level, BlockPos pos, int breakTicks) {
        start(husk, level, List.of(pos), breakTicks);
    }

    /** Starts one synchronized task for all supplied blocks. */
    public static void start(Husk husk, ServerLevel level, List<BlockPos> positions, int breakTicks) {
        cancel(husk.getUUID());
        List<BlockPos> uniquePositions = positions.stream().map(BlockPos::immutable).distinct().toList();
        TASKS.put(husk.getUUID(), new BreakTask(level, husk.getUUID(), uniquePositions, Math.max(1, breakTicks)));
    }

    /** A group is in range when at least one remaining target is close enough. */
    public static boolean isInRange(Husk husk, List<BlockPos> positions) {
        double maxDistanceSqr = MAX_DISTANCE * MAX_DISTANCE;
        return positions.stream().anyMatch(pos -> husk.distanceToSqr(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5
        ) <= maxDistanceSqr);
    }

    public static void cancel(UUID huskId) {
        BreakTask oldTask = TASKS.remove(huskId);
        if (oldTask != null) {
            oldTask.clearProgress();
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Iterator<BreakTask> iterator = TASKS.values().iterator();
        while (iterator.hasNext()) {
            BreakTask task = iterator.next();
            if (task.level != level) {
                continue;
            }
            if (!task.tick()) {
                task.clearProgress();
                iterator.remove();
            }
        }
    }

    private static final class BreakTask {
        private final ServerLevel level;
        private final UUID huskId;
        private final List<BlockPos> positions;
        private final List<Integer> crackIds;
        private final int breakTicks;
        private int elapsedTicks;

        private BreakTask(ServerLevel level, UUID huskId, List<BlockPos> positions, int breakTicks) {
            this.level = level;
            this.huskId = huskId;
            this.positions = positions;
            // Client crack animations are keyed by breaker ID, so every block needs a distinct ID.
            this.crackIds = positions.stream()
                    .map(pos -> Objects.hash(huskId, pos) | Integer.MIN_VALUE)
                    .toList();
            this.breakTicks = breakTicks;
        }

        /** @return true while the task should remain registered. */
        private boolean tick() {
            if (!(level.getEntity(huskId) instanceof Husk husk) || !husk.isAlive()) {
                return false;
            }

            List<BlockPos> remaining = positions.stream()
                    .filter(pos -> !level.getBlockState(pos).isAir())
                    .toList();
            if (remaining.isEmpty() || !isInRange(husk, remaining)) {
                return false;
            }

            BlockPos lookPos = remaining.getFirst();
            husk.getNavigation().stop();
            husk.getLookControl().setLookAt(lookPos.getX() + 0.5, lookPos.getY() + 0.5, lookPos.getZ() + 0.5);
            elapsedTicks++;

            if (elapsedTicks % 10 == 0) {
                husk.swing(InteractionHand.MAIN_HAND);
            }
            if (elapsedTicks % 20 == 0) {
                level.levelEvent(1019, lookPos, 0);
            }

            int crackStage = Math.min(9, (int) ((long) elapsedTicks * 10L / breakTicks));
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                if (!level.getBlockState(pos).isAir()) {
                    level.destroyBlockProgress(crackIds.get(i), pos, crackStage);
                }
            }

            if (elapsedTicks < breakTicks) {
                return true;
            }

            for (BlockPos pos : positions) {
                BlockState oldState = level.getBlockState(pos);
                if (!oldState.isAir() && level.destroyBlock(pos, false, husk)) {
                    level.levelEvent(2001, pos, Block.getId(oldState));
                }
            }
            return false;
        }

        private void clearProgress() {
            for (int i = 0; i < positions.size(); i++) {
                level.destroyBlockProgress(crackIds.get(i), positions.get(i), -1);
            }
        }
    }
}
