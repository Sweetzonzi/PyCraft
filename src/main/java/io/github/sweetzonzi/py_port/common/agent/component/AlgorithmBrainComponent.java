package io.github.sweetzonzi.py_port.common.agent.component;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.agent.AlgorithmPathfinder;
import io.github.sweetzonzi.py_port.common.agent.ScanResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * AlgorithmAgent 的大脑组件。
 * <p>
 * 状态机：IDLE → SCANNING → PLANNING → MOVING → MINING → RETURNING → IDLE
 * <p>
 * 所有世界操作（getBlockState、destroyBlock）在主线程 postTick 中执行，
 * 物理体操作（setLinearVelocity、setPosition）通过 agent 的任务队列安全提交。
 */
public class AlgorithmBrainComponent extends AbstractAgentComponent {

    // 状态枚举
    public enum State {
        IDLE, SCANNING, PLANNING, MOVING, MINING, RETURNING
    }

    // 状态
    private State state = State.IDLE;

    // 配置
    private Block targetBlock = Blocks.OAK_LOG;
    private int scanRadius = 20;
    private final Map<Block, Integer> blockValues = new HashMap<>();

    // 扫描结果
    private final List<BlockPos> foundTargets = new ArrayList<>();
    private int scanProgress = 0;                  // 当前正在扫描的半径环
    private int scanBatchX = 0;                    // 批量扫描：当前 x 偏移
    private int scanBatchZ = 0;                    // 批量扫描：当前 z 偏移
    private boolean scanInBatch = false;           // 是否在批量模式
    private boolean isRescan = false;              // 是否在任务结束后重新扫描遗漏

    private static final int MAX_SCAN_CHECKS_PER_TICK = 400; // 每 tick 最多检查 400 个方块

    // 路径
    private List<BlockPos> fullPath = new ArrayList<>();
    private int pathIndex = 0;
    private BlockPos startStandPos;

    // 移动控制
    private static final double ARRIVAL_DISTANCE_SQ = 0.5 * 0.5;
    private static final double MOVE_SPEED = 0.45;
    private int settleTicks = 0;
    private static final int SETTLE_TICK_COUNT = 2;

    // 采集
    private BlockPos currentMiningPos = null;
    private BlockPos miningStandPos = null;         // 进入 MINING 时的站立位置，用于采后重新扫描
    private int miningTicks = 0;
    private static final int MINING_TICKS_REQUIRED = 15;
    /** 已成功采集的方块坐标集合，避免重复采集 */
    private final Set<Long> collectedPositions = new HashSet<>();

    // 背包
    private final List<ItemStack> inventory = new ArrayList<>();

    // 默认价值权重
    private static final Map<Block, Integer> DEFAULT_VALUES = new HashMap<>();

    static {
        DEFAULT_VALUES.put(Blocks.DIAMOND_ORE, 50);
        DEFAULT_VALUES.put(Blocks.DEEPSLATE_DIAMOND_ORE, 50);
        DEFAULT_VALUES.put(Blocks.EMERALD_ORE, 40);
        DEFAULT_VALUES.put(Blocks.DEEPSLATE_EMERALD_ORE, 40);
        DEFAULT_VALUES.put(Blocks.GOLD_ORE, 25);
        DEFAULT_VALUES.put(Blocks.DEEPSLATE_GOLD_ORE, 25);
        DEFAULT_VALUES.put(Blocks.IRON_ORE, 20);
        DEFAULT_VALUES.put(Blocks.DEEPSLATE_IRON_ORE, 20);
        DEFAULT_VALUES.put(Blocks.COAL_ORE, 10);
        DEFAULT_VALUES.put(Blocks.DEEPSLATE_COAL_ORE, 10);
        DEFAULT_VALUES.put(Blocks.COPPER_ORE, 8);
        DEFAULT_VALUES.put(Blocks.DEEPSLATE_COPPER_ORE, 8);
        DEFAULT_VALUES.put(Blocks.OAK_LOG, 5);
        DEFAULT_VALUES.put(Blocks.SPRUCE_LOG, 5);
        DEFAULT_VALUES.put(Blocks.BIRCH_LOG, 5);
        DEFAULT_VALUES.put(Blocks.STONE, 1);
        DEFAULT_VALUES.put(Blocks.DIRT, 1);
    }

    public AlgorithmBrainComponent(String name, io.github.sweetzonzi.py_port.common.agent.AbstractAgent agent) {
        super(name, agent);
        blockValues.putAll(DEFAULT_VALUES);
        PyCraft.LOGGER.info("AlgorithmBrainComponent created for agent #{}", agent.getId());
    }

    // 公开指令 API

    public synchronized void startFinding(Block block, int radius) {
        if (state != State.IDLE) {
            PyCraft.LOGGER.warn("Agent #{} is already busy (state={})", agent.getId(), state);
            return;
        }
        this.targetBlock = block;
        this.scanRadius = Math.min(radius, 50);
        this.foundTargets.clear();
        this.fullPath.clear();
        this.inventory.clear();
        this.pathIndex = 0;
        this.scanProgress = 0;
        this.currentMiningPos = null;
        this.isRescan = false;
        this.collectedPositions.clear();

        Vector3f pos = agent.getPosition();
        this.startStandPos = findStandPosition(
                (int) Math.floor(pos.x),
                (int) Math.floor(pos.y),
                (int) Math.floor(pos.z));

        if (this.startStandPos == null) {
            PyCraft.LOGGER.warn("Agent #{} cannot find a valid starting position!", agent.getId());
            return;
        }

        setState(State.SCANNING);
        PyCraft.LOGGER.info("Agent #{} started finding {} (radius={})", agent.getId(),
                BuiltInRegistries.BLOCK.getKey(block), radius);
    }

    public synchronized void stop() {
        setState(State.IDLE);
        fullPath.clear();
        foundTargets.clear();
        currentMiningPos = null;
        pathIndex = 0;
        stopMovement();
        PyCraft.LOGGER.info("Agent #{} stopped", agent.getId());
    }

    public void setBlockValue(Block block, int value) {
        blockValues.put(block, value);
    }

    // 查询 API

    public State getState() { return state; }

    public Block getTargetBlock() { return targetBlock; }

    public int getScanRadius() { return scanRadius; }

    public List<ItemStack> getInventory() {
        return Collections.unmodifiableList(inventory);
    }

    public String getStatus() {
        return switch (state) {
            case IDLE -> "空闲";
            case SCANNING -> "扫描中 (" + foundTargets.size() + " 个目标已发现)";
            case PLANNING -> "规划路径中...";
            case MOVING -> "移动中 (" + pathIndex + "/" + fullPath.size() + ")";
            case MINING -> "采集中 (" + (MINING_TICKS_REQUIRED - miningTicks) + "/" + MINING_TICKS_REQUIRED + ")";
            case RETURNING -> "返回起点中";
        };
    }

    // Tick 逻辑
    // postTick() 在主线程运行，安全调用 level.getBlockState()/destroyBlock()
    // prePhysicsTick() 在物理线程，不做任何世界操作

    @Override
    public void postTick() {
        if (agent.getLevel().isClientSide()) return;
        if (state == State.IDLE) return;

        switch (state) {
            case SCANNING -> tickScanning();
            case PLANNING -> tickPlanning();
            case MOVING -> tickMoving();
            case MINING -> tickMining();
            case RETURNING -> tickMoving();
        }
    }

    @Override
    public void prePhysicsTick() {
        if (agent.getLevel().isClientSide()) return;
        // 在物理 tick 中设置速度，确保速度在物理模拟前生效，防止控制延迟导致的抖动
        if (state == State.MOVING || state == State.RETURNING) {
            tickMoveVelocity();
        }
    }

    // 扫描（主线程）

    private void tickScanning() {
        Vector3f center = agent.getPosition();
        int cx = (int) Math.floor(center.x);
        int cy = (int) Math.floor(center.y);
        int cz = (int) Math.floor(center.z);

        if (!scanInBatch) {
            // 开始新的一层半径环
            if (scanProgress > scanRadius) {
                finishScanning();
                return;
            }
            scanBatchX = -scanProgress;
            scanBatchZ = -scanProgress;
            scanInBatch = true;
        }

        Level level = agent.getLevel();
        int r = scanProgress;
        int checksThisTick = 0;

        // 批量扫描当前半径环的外壳，限制每 tick 检查数
        while (scanBatchX <= r && checksThisTick < MAX_SCAN_CHECKS_PER_TICK) {
            while (scanBatchZ <= r && checksThisTick < MAX_SCAN_CHECKS_PER_TICK) {
                // 只扫外壳
                boolean isEdge = (Math.abs(scanBatchX) == r || Math.abs(scanBatchZ) == r);
                if (isEdge) {
                    int wx = cx + scanBatchX;
                    int wz = cz + scanBatchZ;
                    // 宽范围查找地表：从 cy+30 到 cy-10，覆盖常见地形起伏
                    BlockPos surface = AlgorithmPathfinder.findStandPositionRange(
                            level, wx, wz, cy, 30, 10);
                    if (surface != null) {
                        int surfY = surface.getY();
                        // 检查 surfY-3 到 surfY+2 范围内的目标方块（覆盖地表到地下浅层）
                        for (int dy = -3; dy <= 2; dy++) {
                            BlockPos checkPos = new BlockPos(wx, surfY + dy, wz);
                            if (collectedPositions.contains(packPos(checkPos))) continue;
                            BlockState bs = level.getBlockState(checkPos);
                            if (bs.is(targetBlock)) {
                                foundTargets.add(checkPos);
                            }
                        }
                    }
                    checksThisTick++;
                }
                scanBatchZ++;
            }
            scanBatchZ = -r;
            scanBatchX++;
        }

        // 检查这一层是否扫完
        if (scanBatchX > r) {
            scanProgress++;
            scanInBatch = false;
        }
    }

    private void finishScanning() {
        if (foundTargets.isEmpty()) {
            if (isRescan) {
                // 重新扫描后确认没有遗漏，完成任务
                isRescan = false;
                int count = inventory.size();
                dropInventory();
                PyCraft.LOGGER.info("Agent #{} finished, dropped {} items", agent.getId(), count);
            }
            setState(State.IDLE);
            return;
        }
        isRescan = false;
        setState(State.PLANNING);
    }

    // 路径规划（主线程）

    private void tickPlanning() {
        Level level = agent.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            setState(State.IDLE);
            return;
        }

        // 调试：打印扫描到的目标数量
        PyCraft.LOGGER.info("Agent #{} planning: {} targets found, startStandPos={}",
                agent.getId(), foundTargets.size(), startStandPos);

        List<ScanResult> scanResults = new ArrayList<>();
        for (BlockPos pos : foundTargets) {
            int value = blockValues.getOrDefault(targetBlock, 1);
            scanResults.add(new ScanResult(pos, level.getBlockState(pos), value));
        }

        // 检查每个目标周围 26 方向是否有可站立位置
        int validTargets = 0;
        for (ScanResult sr : scanResults) {
            List<BlockPos> standPositions = AlgorithmPathfinder.findStandPositionsAround(serverLevel, sr.pos());
            if (!standPositions.isEmpty()) {
                validTargets++;
            } else {
                PyCraft.LOGGER.warn("  target at {} has NO stand position around it!", sr.pos());
            }
        }
        PyCraft.LOGGER.info("Agent #{} planning: {}/{} targets have valid stand positions",
                agent.getId(), validTargets, scanResults.size());

        long startTime = System.nanoTime();
        List<BlockPos> path = AlgorithmPathfinder.findOptimalRoute(serverLevel, startStandPos, scanResults);
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        if (path == null || path.isEmpty()) {
            PyCraft.LOGGER.warn("Agent #{} cannot find a path to any target ({}ms)", agent.getId(), elapsedMs);
            setState(State.IDLE);
            return;
        }

        this.fullPath = path;
        this.pathIndex = 0;
        PyCraft.LOGGER.info("Agent #{} planned path with {} waypoints to {} targets (took {}ms)",
                agent.getId(), path.size(), scanResults.size(), elapsedMs);

        if (fullPath.isEmpty()) {
            setState(State.IDLE);
        } else {
            setState(State.MOVING);
        }
    }

    // 移动（物理线程，prePhysicsTick）

    /**
     * 在 prePhysicsTick 中调用，确保速度在物理模拟前生效。
     * 不含任何世界操作（getBlockState 等），仅设置速度。
     */
    private void tickMoveVelocity() {
        if (fullPath.isEmpty() || pathIndex >= fullPath.size()) {
            agent.getBody().setLinearVelocity(new Vector3f(0, 0, 0));
            agent.getBody().setAngularVelocity(new Vector3f(0, 0, 0));
            return;
        }
        if (settleTicks > 0) return;

        BlockPos targetWaypoint = fullPath.get(pathIndex);
        // 在物理线程上直接读取刚体位姿
        Vector3f currentPos = agent.getBody().getPhysicsLocation(null);

        double targetX = targetWaypoint.getX() + 0.5;
        double targetY = targetWaypoint.getY() + 1.3;
        double targetZ = targetWaypoint.getZ() + 0.5;

        double dx = targetX - currentPos.x;
        double dy = targetY - currentPos.y;
        double dz = targetZ - currentPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist > 0.05) {
            // 直接设置位置（逐 tick 移动），绕过速度积分，彻底避免抖动
            float step = (float) (MOVE_SPEED / 20.0);
            float ratio = Math.min(1.0f, (float) (step / dist));
            float newX = currentPos.x + (float) dx * ratio;
            float newY = currentPos.y + (float) dy * ratio;
            float newZ = currentPos.z + (float) dz * ratio;
            agent.getBody().setPhysicsLocation(new Vector3f(newX, newY, newZ));
            agent.getBody().setLinearVelocity(new Vector3f(0, 0, 0));
            agent.getBody().setAngularVelocity(new Vector3f(0, 0, 0));
        }
    }

    // 移动（主线程，postTick）

    private void tickMoving() {
        if (fullPath.isEmpty() || pathIndex >= fullPath.size()) {
            if (state == State.RETURNING) {
                dropInventory();
                PyCraft.LOGGER.info("Agent #{} returned to start and dropped items", agent.getId());
                setState(State.IDLE);
            } else {
                // MOVING 结束，重新扫描检查遗漏目标
                PyCraft.LOGGER.info("Agent #{} path exhausted, rescanning for missed targets", agent.getId());
                foundTargets.clear();
                scanProgress = 0;
                scanInBatch = false;
                isRescan = true;
                setState(State.SCANNING);
            }
            stopMovement();
            return;
        }

        if (settleTicks > 0) {
            settleTicks--;
            stopMovement();
            return;
        }

        BlockPos targetWaypoint = fullPath.get(pathIndex);
        Vector3f currentPos = agent.getPosition();

        double targetX = targetWaypoint.getX() + 0.5;
        double targetY = targetWaypoint.getY() + 1.3;
        double targetZ = targetWaypoint.getZ() + 0.5;

        double dx = targetX - currentPos.x;
        double dy = targetY - currentPos.y;
        double dz = targetZ - currentPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq < ARRIVAL_DISTANCE_SQ) {
            // 到达 waypoint
            if (state == State.MOVING && findAdjacentTarget(targetWaypoint) != null) {
                // 附近有目标 → 采矿
                currentMiningPos = findAdjacentTarget(targetWaypoint);
                miningStandPos = targetWaypoint;
                miningTicks = 0;
                PyCraft.LOGGER.info("Agent #{} arrived at waypoint {}, found target {} -> MINING",
                        agent.getId(), targetWaypoint, currentMiningPos);
                setState(State.MINING);
                stopMovement();
                return;
            }

            pathIndex++;
            settleTicks = SETTLE_TICK_COUNT;
            stopMovement();
            return;
        }

        // 上台阶检测
        BlockPos currentStand = findStandPosition(
                (int) Math.floor(currentPos.x),
                (int) Math.floor(currentPos.y),
                (int) Math.floor(currentPos.z));
        if (currentStand != null && targetWaypoint.getY() > currentStand.getY()) {
            Vector3f raisedPos = new Vector3f(
                    (float) targetX,
                    (float) (currentPos.y + 1.0),
                    (float) targetZ);
            agent.setPosition(raisedPos);
            pathIndex++;
            settleTicks = SETTLE_TICK_COUNT;
            stopMovement();
            return;
        }
    }

    // 采集（主线程）

    private void tickMining() {
        if (currentMiningPos == null) {
            setState(State.MOVING);
            return;
        }

        Level level = agent.getLevel();
        BlockState currentState = level.getBlockState(currentMiningPos);

        PyCraft.LOGGER.debug("Agent #{} tickMining: miningPos={}, state={}, isTarget={}, ticks={}",
                agent.getId(), currentMiningPos,
                BuiltInRegistries.BLOCK.getKey(currentState.getBlock()),
                currentState.is(targetBlock), miningTicks);

        if (!currentState.is(targetBlock)) {
            // 方块已消失，重新扫描站立点附近有无其他目标
            PyCraft.LOGGER.info("Agent #{} block at {} no longer target (now {}), tryMineNext",
                    agent.getId(), currentMiningPos,
                    BuiltInRegistries.BLOCK.getKey(currentState.getBlock()));
            tryMineNext(level);
            return;
        }

        miningTicks++;

        if (miningTicks >= MINING_TICKS_REQUIRED) {
            if (level instanceof ServerLevel serverLevel) {
                level.destroyBlock(currentMiningPos, false);
                collectedPositions.add(packPos(currentMiningPos));

                ItemStack stack = new ItemStack(targetBlock, 1);
                inventory.add(stack);

                PyCraft.LOGGER.info("Agent #{} mined {} at {}", agent.getId(),
                        BuiltInRegistries.BLOCK.getKey(targetBlock), currentMiningPos);
            }

            // 采完一个，重新扫描站立点附近是否还有剩余目标
            tryMineNext(level);
        }
    }

    /**
     * 从 miningStandPos 重新扫描附近的目标方块。
     * 每次采完后都重新扫描，以应对方块被采后上层方块掉落/消失的情况。
     */
    private void tryMineNext(Level level) {
        // 1. 优先检查刚采掉方块的正上方（叠放场景：采了下方的，上面还有）
        if (currentMiningPos != null) {
            BlockPos above = currentMiningPos.above();
            if (level.getBlockState(above).is(targetBlock)) {
                PyCraft.LOGGER.info("Agent #{} tryMineNext: found stacked above at {}",
                        agent.getId(), above);
                currentMiningPos = above;
                miningTicks = 0;
                return;
            }
        }
        // 2. 从原始站立位置全面扫描（在非叠放场景找其他水平方向的目标）
        if (miningStandPos != null) {
            BlockPos nextTarget = findAdjacentTarget(miningStandPos);
            if (nextTarget != null) {
                PyCraft.LOGGER.info("Agent #{} tryMineNext found next target at {} from standPos {}",
                        agent.getId(), nextTarget, miningStandPos);
                currentMiningPos = nextTarget;
                miningTicks = 0;
                return;
            } else {
                PyCraft.LOGGER.info("Agent #{} tryMineNext: findAdjacentTarget({}) found nothing",
                        agent.getId(), miningStandPos);
            }
        }
        // 3. 无更多目标
        PyCraft.LOGGER.info("Agent #{} tryMineNext: no more targets, moving to next waypoint (pathIndex={}->{})",
                agent.getId(), pathIndex, pathIndex + 1);
        currentMiningPos = null;
        miningTicks = 0;
        pathIndex++;
        settleTicks = SETTLE_TICK_COUNT;
        setState(State.MOVING);
    }

    // 内部辅助

    private BlockPos findAdjacentTarget(BlockPos standPos) {
        Level level = agent.getLevel();
        int x = standPos.getX();
        int y = standPos.getY();
        int z = standPos.getZ();

        // 检查周围方向（水平 3×3，垂直 -10 到 10 从下往上）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    // 垂直方向只检查正上方和正下方（不重复检查 standPos 本身）
                    for (int dy = -10; dy <= 10; dy++) {
                        if (dy == 0) continue;
                        BlockPos candidate = new BlockPos(x, y + dy, z);
                        long key = packPos(candidate);
                        if (collectedPositions.contains(key)) continue;
                        BlockState state = level.getBlockState(candidate);
                        if (state.is(targetBlock)) {
                            PyCraft.LOGGER.info("Agent #{} findAdjacentTarget: found at {} (dx=0, dy={}, dz=0) from {}",
                                    agent.getId(), candidate, dy, standPos);
                            return candidate;
                        }
                    }
                } else {
                    // 水平周围 8 格，垂直范围 -10 到 10
                    for (int dy = -10; dy <= 10; dy++) {
                        BlockPos candidate = new BlockPos(x + dx, y + dy, z + dz);
                        long key = packPos(candidate);
                        if (collectedPositions.contains(key)) continue;
                        BlockState state = level.getBlockState(candidate);
                        if (state.is(targetBlock)) {
                            PyCraft.LOGGER.info("Agent #{} findAdjacentTarget: found at {} (dx={}, dy={}, dz={}) from {}",
                                    agent.getId(), candidate, dx, dy, dz, standPos);
                            return candidate;
                        }
                    }
                }
            }
        }

        PyCraft.LOGGER.info("Agent #{} findAdjacentTarget({}): no target found, collected={}",
                agent.getId(), standPos, collectedPositions.size());
        return null;
    }

    private BlockPos findStandPosition(int x, int y, int z) {
        return AlgorithmPathfinder.findStandPosition(agent.getLevel(), x, z, y);
    }

    private static long packPos(BlockPos pos) {
        return ((long) pos.getX() & 0x3FFFFFF) << 38
                | ((long) pos.getY() & 0xFFFFFF) << 12
                | ((long) pos.getZ() & 0x3FFFFFF);
    }

    private void stopMovement() {
        agent.setLinearVelocity(new Vector3f(0, 0, 0));
    }

    private void setState(State newState) {
        this.state = newState;
    }

    private void dropInventory() {
        if (inventory.isEmpty()) return;
        Level level = agent.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        Vector3f pos = agent.getPosition();
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                        serverLevel, pos.x, pos.y, pos.z, stack);
                serverLevel.addFreshEntity(drop);
            }
        }
        inventory.clear();
    }
}