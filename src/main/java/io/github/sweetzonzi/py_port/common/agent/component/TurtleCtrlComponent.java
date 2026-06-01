package io.github.sweetzonzi.py_port.common.agent.component;

import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.agent.CarEntity;
import io.github.sweetzonzi.py_port.util.control.PIDController;
import org.joml.Vector3f;

import java.util.LinkedList;
import java.util.Queue;

/**
 * 维护一个命令队列，按顺序执行 front/back/turn 等离散动作。
 * 每个命令使用 PID 控制器在 physics tick 上精确驱动小车到达目标位置/角度。
 * <p>
 * Python 调用示例:
 *   turtle_front {blocks: 10}  → 向前移动 10 格
 *   turtle_back  {blocks: 5}   → 后退 5 格
 *   turtle_turn_left  {degrees: 90}  → 左转 90 度
 *   turtle_turn_right {degrees: 45}  → 右转 45 度
 *   turtle_is_busy → 返回 {busy: true/false, queue_size: N}
 */
public class TurtleCtrlComponent extends AbstractAgentComponent {

    public enum CommandType {
        FORWARD, BACK, TURN_LEFT, TURN_RIGHT
    }

    public static class TurtleCommand {
        public final CommandType type;
        public final double value;         // 距离(格) 或 角度(度)
        // 命令开始执行时计算的绝对目标
        public Vector3f targetPos;         // 绝对值世界坐标
        public double targetYaw;           // 绝对目标偏航角(弧度)
        public boolean started = false;

        public TurtleCommand(CommandType type, double value) {
            this.type = type;
            this.value = value;
        }
    }

    // 命令队列（线程安全）
    private final Queue<TurtleCommand> commandQueue = new LinkedList<>();
    private final Object queueLock = new Object();

    // 当前正在执行的命令
    private TurtleCommand currentCommand = null;

    // 命令间刹车稳定 tick（确保上一个命令的刹车真正生效再开始下一个命令）
    private int settleTicks = 0;
    private static final int SETTLE_TICK_COUNT = 3;

    // PID 控制器
    private final PIDController distancePID;   // 前进/后退油门控制
    private final PIDController turnPID;       // 转向角度控制

    // 容差
    private static final double POSITION_TOLERANCE = 0.3;    // 格（增大防止震荡无法收敛）
    private static final double ANGLE_TOLERANCE = Math.toRadians(3);  // 3度
    private static final double SPEED_TOLERANCE = 0.15;      // m/s

    // 速度控制参数
    private static final double MAX_APPROACH_SPEED = 4.0;    // 接近目标时最大速度 (m/s)，防止高速过冲

    // 临时向量（避免 GC）
    private final Vector3f tempTargetDir = new Vector3f();
    private final Vector3f tempToTarget = new Vector3f();

    public TurtleCtrlComponent(String name, CarEntity car) {
        super(name, car);
        this.distancePID = new PIDController(1.0, 0.02, 0.6, 0.05, -1.0, 1.0);
        this.turnPID = new PIDController(2.0, 0.12, 0.15, 0.05, -1.0, 1.0);
        PyCraft.LOGGER.info("TurtleCtrlComponent created for car");
    }

    // ========== 命令队列 API（线程安全） ==========

    public void enqueueFront(double blocks) {
        synchronized (queueLock) {
            commandQueue.add(new TurtleCommand(CommandType.FORWARD, blocks));
        }
    }

    public void enqueueBack(double blocks) {
        synchronized (queueLock) {
            commandQueue.add(new TurtleCommand(CommandType.BACK, blocks));
        }
    }

    public void enqueueTurnLeft(double degrees) {
        synchronized (queueLock) {
            commandQueue.add(new TurtleCommand(CommandType.TURN_LEFT, degrees));
        }
    }

    public void enqueueTurnRight(double degrees) {
        synchronized (queueLock) {
            commandQueue.add(new TurtleCommand(CommandType.TURN_RIGHT, degrees));
        }
    }

    /**
     * 清空所有未执行的命令，并中断当前正在执行的命令。
     */
    public void clearQueue() {
        synchronized (queueLock) {
            commandQueue.clear();
            currentCommand = null;
        }
        var car = (CarEntity) agent;
        car.drive(0, 0, true);
    }

    /**
     * @return 是否有命令正在执行或队列非空
     */
    public boolean isBusy() {
        synchronized (queueLock) {
            return currentCommand != null || !commandQueue.isEmpty();
        }
    }

    /**
     * @return 当前队列中的命令数量（不含正在执行的）
     */
    public int queueSize() {
        synchronized (queueLock) {
            return commandQueue.size();
        }
    }

    @Override
    public void prePhysicsTick() {
        if (agent.getLevel().isClientSide()) return;

        CarEntity car = (CarEntity) agent;

        // 取下一个命令
        if (currentCommand == null) {
            if (settleTicks > 0) {
                // 在命令间保持刹车状态，确保完全停稳
                car.drive(0, 0, true);
                settleTicks--;
                return;
            }
            synchronized (queueLock) {
                currentCommand = commandQueue.poll();
            }
            if (currentCommand == null) return;
            initCommand(currentCommand, car);
        }

        // 执行当前命令
        boolean done = false;
        switch (currentCommand.type) {
            case FORWARD, BACK -> done = executePositionCommand(currentCommand, car);
            case TURN_LEFT, TURN_RIGHT -> done = executeTurnCommand(currentCommand, car);
        }

        if (done) {
            car.drive(0, 0, true); // 刹车
            synchronized (queueLock) {
                currentCommand = null;
            }
            distancePID.resetError(); // 重置 PID，防止积分累积影响下个命令
            turnPID.resetError();
            settleTicks = SETTLE_TICK_COUNT; // 先刹 3 tick 才开始下一个命令
            return;
        }
    }

    //初始化命令：根据起始位姿计算绝对目标。

    private void initCommand(TurtleCommand cmd, CarEntity car) {
        if (cmd.started) return;
        cmd.started = true;

        switch (cmd.type) {
            case FORWARD -> {
                // 目标位置 = 当前位置 + 前向量 * 距离
                var front = car.getFrontVector(); // JME Vector3f
                var pos = car.getPosition();      // JME Vector3f
                cmd.targetPos = new Vector3f(
                        pos.x + front.x * (float) cmd.value,
                        pos.y,
                        pos.z + front.z * (float) cmd.value
                );
            }
            case BACK -> {
                // 目标位置 = 当前位置 - 前向量 * 距离
                var front = car.getFrontVector();
                var pos = car.getPosition();
                cmd.targetPos = new Vector3f(
                        pos.x - front.x * (float) cmd.value,
                        pos.y,
                        pos.z - front.z * (float) cmd.value
                );
            }
            case TURN_LEFT -> {
                // yaw 减小 = 左转（逆时针）
                double startYaw = car.getYaw();
                cmd.targetYaw = startYaw - Math.toRadians(cmd.value);
            }
            case TURN_RIGHT -> {
                // yaw 增大 = 右转（顺时针）
                double startYaw = car.getYaw();
                cmd.targetYaw = startYaw + Math.toRadians(cmd.value);
            }
        }
    }

    /**
     * 执行位置命令（FORWARD / BACK）。
     * 使用距离 PID 控制油门，同时用横向偏差做轻微的转向矫正。
     * <p>
     * 速度管理策略（统一规则，不分区域）：
     * - 最大允许速度 = max(absDist × 1.5, 0.3)，上限 MAX_APPROACH_SPEED
     * - 超速时刹车，否则 PID 正常控制
     * - 油门幅度根据距离限制（absDist × 0.6），防止近处 PID 过冲
     */
    private boolean executePositionCommand(TurtleCommand cmd, CarEntity car) {
        var currentPos = car.getPosition(); // JME Vector3f
        var front = car.getFrontVector();

        // 计算带符号的到目标距离（前方为正，后方为负）
        double dx = cmd.targetPos.x - currentPos.x;
        double dz = cmd.targetPos.z - currentPos.z;
        double signedDist = dx * front.x + dz * front.z;
        double absDist = Math.sqrt(dx * dx + dz * dz);

        double speed = car.getLinearVelocity().length();

        // 完成条件：足够近且速度基本为零
        if (absDist < POSITION_TOLERANCE && speed < SPEED_TOLERANCE) {
            return true;
        }

        // 统一速度管理
        // 最大允许速度 = 比例于剩余距离（越近越慢）+ 兜底 0.3 m/s
        double maxAllowedSpeed = Math.max(absDist * 1.5, 0.3);
        maxAllowedSpeed = Math.min(maxAllowedSpeed, MAX_APPROACH_SPEED);
        if (speed > maxAllowedSpeed + 0.3) {
            car.drive(0, 0, true); // 超速 → 刹车
            return false;
        }

        // 距离 PID → 油门
        // step(signedDist, 0) → pid_error = signedDist > 0 → 正油门 → 前进
        double throttle = distancePID.step(signedDist, 0);
        throttle = Math.clamp(throttle, -1.0, 1.0);

        // 油门幅度限制：近处限幅防止 PID 过冲
        double throttleLimit = Math.min(1.0, absDist * 0.6);
        throttle = Math.clamp(throttle, -throttleLimit, throttleLimit);

        // 横向矫正
        applyLateralCorrection(car, (float) dx, (float) dz, front, absDist, (float) throttle);
        return false;
    }

    /**
     * 横向矫正：计算小车前向与到目标方向的偏差，输出转向值。
     */
    private void applyLateralCorrection(CarEntity car, float dx, float dz,
                                        com.jme3.math.Vector3f front, double absDist, float throttle) {
        if (absDist > 0.01) {
            tempToTarget.set(dx, 0, dz).normalize();
            tempTargetDir.set(front.x, 0, front.z).normalize();
            // 横向误差：叉积 Y 分量 > 0 = 目标在右侧 → 右打方向(steering负)
            float lateralError = tempTargetDir.x * tempToTarget.z - tempTargetDir.z * tempToTarget.x;
            float steering = (float) Math.clamp(-lateralError * 3.0, -0.6, 0.6);
            car.drive(throttle, steering, false);
        } else {
            car.drive(throttle, 0, false);
        }
    }

    /**
     * 执行转向命令（TURN_LEFT / TURN_RIGHT）
     */
    private boolean executeTurnCommand(TurtleCommand cmd, CarEntity car) {
        double currentYaw = car.getYaw();

        // 计算归一化的偏航角误差 [-PI, PI]
        double yawError = normalizeAngle(cmd.targetYaw - currentYaw);

        // 完成条件：角度误差足够小
        if (Math.abs(yawError) < ANGLE_TOLERANCE) {
            // 停转：设角速度为 0
            car.getBody().setAngularVelocity(new com.jme3.math.Vector3f(0, 0, 0));
            car.drive(0, 0, true); // 停车
            return true;
        }

        // PID 计算这一 tick 的旋转步进角
        double steering = turnPID.step(0, yawError);
        float deltaYaw = (float) Math.clamp(steering * 0.05, -0.05, 0.05); // max ~3°/tick

        // 直接操纵旋转：当前四元数 × 增量旋转
        // 增量旋转 = 绕世界 Y 轴转 deltaYaw 弧度
        // 手动构造 Y 轴旋转四元数: (x=0, y=sin(θ/2), z=0, w=cos(θ/2))
        com.jme3.math.Quaternion currentRot = car.getBody().getPhysicsRotation(null);
        float halfAngle = deltaYaw * 0.5f;
        com.jme3.math.Quaternion deltaRot = new com.jme3.math.Quaternion();
        deltaRot.set(0f, (float) Math.sin(halfAngle), 0f, (float) Math.cos(halfAngle));
        com.jme3.math.Quaternion newRot = deltaRot.mult(currentRot);
        newRot.normalizeLocal();

        // 直接设置刚体旋转（physics thread 上安全）
        car.getBody().setPhysicsRotation(newRot);

        // 用刹车防止线性漂移，同时角速度阻尼被旋转 teleport 覆盖，不会影响
        car.drive(0, 0, true);

        // 同步角速度为 0（防止物理引擎的摩擦求解器基于旧角速度产生反作用力）
        car.getBody().setAngularVelocity(new com.jme3.math.Vector3f(0, 0, 0));

        return false;
    }

    /**
     * 将角度归一化到 [-PI, PI] 范围。
     */
    private static double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }

    /**
     * 重置所有 PID 控制器。
     */
    public void resetPID() {
        distancePID.resetError();
        turnPID.resetError();
    }

    @Override
    public String toString() {
        return "TurtleCtrlComponent{queue=" + commandQueue.size() + ", busy=" + (currentCommand != null) + "}";
    }
}
