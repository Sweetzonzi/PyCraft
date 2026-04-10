package io.github.sweetzonzi.py_port.common.agent.component;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class QuatUavCtrlComponent extends AbstractAgentComponent {
    private final List<ThrusterComponent> thrusters;

    // 同步数据定义
    /**
     * 目标位置X
     */
    protected static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(QuatUavCtrlComponent.class, EntityDataSerializers.FLOAT);
    /**
     * 目标位置Y
     */
    protected static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(QuatUavCtrlComponent.class, EntityDataSerializers.FLOAT);
    /**
     * 目标位置Z
     */
    protected static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(QuatUavCtrlComponent.class, EntityDataSerializers.FLOAT);

    public QuatUavCtrlComponent(String name, AbstractAgent agent, List<ThrusterComponent> thrusters) {
        super(name, agent);
        this.thrusters = thrusters;
    }

    // 公共接口
    /**
     * 设置目标位置（JME Vector3f）
     */
    public void setTarget(Vector3f pos) {
        this.syncedData.set(TARGET_X, pos.x);
        this.syncedData.set(TARGET_Y, pos.y);
        this.syncedData.set(TARGET_Z, pos.z);
    }

    /**
     * 设置目标位置（Minecraft Vec3）
     */
    public void setTarget(Vec3 pos) {
        setTarget(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
    }

    /**
     * 获取目标位置
     */
    public Vector3f getTarget() {
        return new Vector3f(
                this.syncedData.get(TARGET_X),
                this.syncedData.get(TARGET_Y),
                this.syncedData.get(TARGET_Z)
        );
    }

    /**
     * 悬停在当前位置
     */
    public void hover() {
        setTarget(agent.getPosition());
    }


    // 定义同步数据
    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET_X, 0f);
        builder.define(TARGET_Y, 0f);
        builder.define(TARGET_Z, 0f);
    }

    @Override
    public void prePhysicsTick() {
        super.prePhysicsTick();

        // 只在服务端计算
        if (getLevel().isClientSide()) return;

        // 获取当前状态
        Vector3f currentPos = agent.getPosition();
        Vector3f currentVel = new Vector3f();
        agent.getBody().getLinearVelocity(currentVel);

        // 获取目标
        Vector3f target = getTarget();

        // PID：目前只控制高度
        float errorY = target.y - currentPos.y;          // 位置误差
        float errorVelY = -currentVel.y;                  // 速度误差（目标速度为0）

        // PID参数（还没加上I）
        float kp = 0.3f;  // 位置比例增益
        float kd = 0.3f;  // 速度微分增益（阻尼）

        // 计算期望加速度（向上为正）
        float accelY = errorY * kp + errorVelY * kd;

        // 重力补偿与控制输出
        float gravity = 9.81f;
        float mass = agent.getBody().getMass();
        float maxAccel = 5.0f;  // 限制加速度，最大±5m/s²
        accelY = Math.max(-maxAccel, Math.min(maxAccel, accelY));
        float totalThrust = mass * (gravity + accelY);  // 总推力（牛顿）

        // 限制总推力范围（留余量给姿态调整）
        float maxTotalThrust = 4 * thrusters.get(0).getMaxThrust() * 0.8f;
        totalThrust = Math.max(0, Math.min(maxTotalThrust, totalThrust));

        // 分配到4个电机（先平均分配）
        float thrustPerMotor = totalThrust / 4.0f;
        float maxThrust = thrusters.get(0).getMaxThrust();

        // 归一化到0~1
        float thrustRatio = thrustPerMotor / maxThrust;
        thrustRatio = Math.max(0.0f, Math.min(0.9f, thrustRatio));

        // 应用到所有推进器，暂未设置力矩
        for (ThrusterComponent t : thrusters) {
            t.setTargetThrust(thrustRatio);
        }
    }
}
