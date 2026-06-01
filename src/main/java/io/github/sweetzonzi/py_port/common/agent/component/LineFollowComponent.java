package io.github.sweetzonzi.py_port.common.agent.component;

import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.agent.CarEntity;
import io.github.sweetzonzi.py_port.util.control.PIDController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.joml.Vector3f;

public class LineFollowComponent extends AbstractAgentComponent {
    // 7个传感器定义 [localX, localZ, weight]
    // 前5个: 车头前方，用于正常巡线
    // 后2个: 车底左右两侧，仅当前5个全部丢失时用于感知转弯方向
    private static final float[][] SENSORS = {
            {-0.40f, -0.5f, -3.0f},   // s0 最左
            {-0.20f, -0.5f, -1.5f},   // s1 左
            { 0.00f, -0.6f,  0.0f},   // s2 中
            { 0.20f, -0.5f,  1.5f},   // s3 右
            { 0.40f, -0.5f,  3.0f},   // s4 最右
            {-1.00f,  0.0f, -2.0f},   // s5 左侧底盘
            { 1.00f,  0.0f,  2.0f}    // s6 右侧底盘
    };

    private static final int SENSOR_COUNT = 7;
    private static final int FRONT_SENSOR_COUNT = 5;  // 前5个是前方传感器

    // 运行状态
    private boolean enabled = false;
    private boolean hasStarted = false;  // 是否已检测到启动
    private float baseThrottle = 0.75f;
    private float lastError = 0f;

    // preTick 缓存的传感器结果
    private final boolean[] sensorResults = new boolean[SENSOR_COUNT];

    // PID 控制器: P=0.8 比例响应, I=0.05 消去弯道稳态误差, D=0.3 抑制振荡
    private final PIDController steeringPID;

    // 临时向量（避免GC）
    private final Vector3f tempLocal = new Vector3f();
    private final Vector3f tempWorld = new Vector3f();

    // 缓存地面 Y 层，避免每传感器重复计算
    private float groundBlockY = 0f;

    public LineFollowComponent(String name, CarEntity car) {
        super(name, car);
        this.steeringPID = new PIDController(0.7, 0, 0.5, 0.05, -1.0, 1.0);
        PyCraft.LOGGER.info("LineFollowComponent created for car");
    }

    /**
     * 主线程 tick：读取 5 个传感器位置的方块，缓存结果供物理线程使用。
     * level.getBlockState() 只能在主线程调用。
     */
    @Override
    public void preTick() {
        if (agent.getLevel().isClientSide() || !enabled) return;

        Level level = agent.getLevel();
        var quat = agent.getQuaternionf();
        var pos = agent.getPosition(); // com.jme3.math.Vector3f

        // 扫描找到实际地面 Y 层
        // 从车身中心方块层向下找第一个非空气方块即地面
        int scanStartY = BlockPos.containing(pos.x, pos.y, pos.z).getY();
        groundBlockY = scanStartY + 0.01f; // 兜底
        for (int y = scanStartY; y > scanStartY - 5; y--) {
            BlockPos bp = BlockPos.containing(pos.x, y, pos.z);
            if (!level.getBlockState(bp).isAir()) {
                groundBlockY = y + 0.01f;
                break;
            }
        }

        boolean anySensorDetected = false;
        boolean greenDetected = false;
        boolean redDetected = false;
        StringBuilder debugStr = new StringBuilder("Sensors: ");

        for (int i = 0; i < SENSOR_COUNT; i++) {
            // 只旋转 (localX, 0, localZ) 得到水平方向的偏移
            tempLocal.set(SENSORS[i][0], 0, SENSORS[i][1]);
            quat.transform(tempLocal, tempWorld);
            // 加上车身位置
            tempWorld.x += pos.x;
            tempWorld.z += pos.z;
            // Y 坐标固定为地面表面层（无论世界高度如何）
            tempWorld.y = groundBlockY;

            // 转换为 BlockPos 并检测方块
            BlockPos blockPos = BlockPos.containing(tempWorld.x, tempWorld.y, tempWorld.z);
            BlockState state = level.getBlockState(blockPos);
            sensorResults[i] = isLineBlock(level, blockPos);

            // 检查绿色（启动）和红色（停止）方块
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String blockPath = blockId.getPath();
            if (blockPath.contains("green")) greenDetected = true;
            if (blockPath.contains("red")) redDetected = true;

            if (sensorResults[i]) anySensorDetected = true;
            debugStr.append(String.format("[s%d] world=(%.2f,%.2f,%.2f) block=%s detect=%b  ",
                    i, tempWorld.x, tempWorld.y, tempWorld.z,
                    blockPath,
                    sensorResults[i]));
        }

        // 红色方块检测优先：结束巡线
        if (redDetected && hasStarted) {
            PyCraft.LOGGER.info("Red block detected! Car finished.");
            hasStarted = false;
            enabled = false;
            ((CarEntity) agent).drive(0, 0, true);
            return;
        }

        // 绿色或黑色方块检测：启动巡线
        if ((greenDetected || anySensorDetected) && !hasStarted) {
            PyCraft.LOGGER.info("Line/Green detected! Starting line follow.");
            hasStarted = true;
            resetPID();
        }

        if (!anySensorDetected) {
            PyCraft.LOGGER.warn("[carY={}, groundY={}] {}",
                    String.format("%.3f", pos.y),
                    String.format("%.3f", groundBlockY),
                    debugStr.toString());
        } else {
            // 检测到线时每 20 tick 输出一次，方便观察
            if (agent.tickCount % 20 == 0) {
                PyCraft.LOGGER.info("[carY={}, groundY={}] {}",
                        String.format("%.3f", pos.y),
                        String.format("%.3f", groundBlockY),
                        debugStr.toString());
            }
        }
    }

    /**
     * 物理线程 tick：根据缓存的传感器值计算加权误差，运行 PID，控制小车。
     */
    @Override
    public void prePhysicsTick() {
        if (agent.getLevel().isClientSide() || !enabled || !hasStarted) return;

        // 1. 前5个前方传感器：正常巡线
        float frontWeightedSum = 0f;
        float frontTotalWeight = 0f;
        boolean anyFrontSeesLine = false;

        for (int i = 0; i < FRONT_SENSOR_COUNT; i++) {
            if (sensorResults[i]) {
                float w = SENSORS[i][2];
                frontWeightedSum += w;
                frontTotalWeight += Math.abs(w);
                anyFrontSeesLine = true;
            }
        }

        float error;
        if (anyFrontSeesLine && frontTotalWeight > 0.01f) {
            // 前方传感器能看见线 → 正常巡线，忽略侧向传感器
            error = (frontWeightedSum / frontTotalWeight) / 3.0f;
        } else {
            // 2. 前5个全部丢失 → 用侧向传感器感知转弯方向
            float latWeightedSum = 0f;
            float latTotalWeight = 0f;
            for (int i = FRONT_SENSOR_COUNT; i < SENSOR_COUNT; i++) {
                if (sensorResults[i]) {
                    latWeightedSum += SENSORS[i][2];
                    latTotalWeight += Math.abs(SENSORS[i][2]);
                }
            }

            if (latTotalWeight > 0.01f) {
                error = (latWeightedSum / latTotalWeight) / 3.0f;
            } else {
                // 侧向也无信号 → 直行，等待前向传感器重新捕获黑线
                error = 0;
            }
        }
        lastError = error;

        // PID 计算转向值
        float steering = (float) steeringPID.step(0, error);

        // 动态油门：急弯减速，直道全速
        float throttle = baseThrottle * (1.0f - Math.abs(steering) * 0.7f);

        // 发送控制信号给 CarCtrlComponent
        ((CarEntity) agent).drive(throttle, steering, false);
    }

    /**
     * 判断指定位置的方块是否为线路（黑色）或背景（白色）。
     * 优先使用方法A（方块ID检测），方法B（地图颜色亮度）作为兜底。
     */
    private boolean isLineBlock(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;

        BlockState state = level.getBlockState(pos);

        // 空气不是线路（防止 MapColor 兜底将深色空气误判为线路）
        if (state.isAir()) return false;

        // 方法A：方块 ID 检测（优先，快速可靠）
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath();
        if (path.contains("black")) return true;   // 黑色 = 线路
        if (path.contains("white") || path.contains("green") || path.contains("red")) return false;

        // 方法B：地图颜色亮度检测（兜底）
        MapColor mapColor = state.getMapColor(level, pos);
        int col = mapColor.col;
        int r = col & 0xFF;
        int g = (col >> 8) & 0xFF;
        int b = (col >> 16) & 0xFF;
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return luminance < 0.3;  // 亮度 < 30% 认为是黑色线路
    }

    // Python API

    /**
     * 启用/禁用巡线控制。
     * 启用时自动重置 PID，确保控制从零开始。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            this.hasStarted = false;  // 重新布防，等待检测绿块
            resetPID();
        }
        PyCraft.LOGGER.info("LineFollowComponent " + (enabled ? "enabled" : "disabled"));
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置基础油门（0.1 ~ 1.0），弯道会自动减速。
     */
    public void setBaseThrottle(float throttle) {
        this.baseThrottle = Math.max(0.1f, Math.min(1.0f, throttle));
    }

    public float getBaseThrottle() {
        return baseThrottle;
    }

    /**
     * 获取上一次误差值，用于 Python 监控调试。
     * 负值=偏左，正值=偏右，0=居中。
     */
    public float getLastError() {
        return lastError;
    }

    /**
     * 重置 PID 控制器内部状态（误差累积、上次误差等）。
     */
    public void resetPID() {
        steeringPID.resetError();
        lastError = 0f;
    }

    /**
     * 动态调整 PID 参数（运行时调参）。
     */
    public void setPID(double p, double i, double d) {
        steeringPID.adjust(p, i, d, steeringPID.getSTEP(), steeringPID.getOutputMin(), steeringPID.getOutputMax());
    }
}
