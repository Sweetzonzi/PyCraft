package io.github.sweetzonzi.py_port.common.agent.component;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.util.control.PIDController;
import jme3utilities.math.MyQuaternion;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.List;

public class QuatUavCtrlComponent extends AbstractAgentComponent {
    private final List<ThrusterComponent> thrusters;

    protected static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(QuatUavCtrlComponent.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(QuatUavCtrlComponent.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(QuatUavCtrlComponent.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> TARGET_YAW = SynchedEntityData.defineId(QuatUavCtrlComponent.class, EntityDataSerializers.FLOAT);

    private float mass;
    private Vector3f invInertiaLocal = new Vector3f();
    private final float gravity = 9.81f;
    private float maxThrust;

    /**
     * 控制器
     */
    private PIDController pidVx, pidVy, pidVz;
    private PIDController pidRollRate, pidPitchRate, pidYawRate;
    /**
     * 控制器参数
     */
    private float posKp = 2.0f;  // 位置P增益
    private float angVelKp = 2.0f;
    private float maxSpeed = 5.0f;
    private float maxTorque = 10.0f;

    private static final float Dt = 0.01f;

    public QuatUavCtrlComponent(String name, AbstractAgent agent, List<ThrusterComponent> thrusters) {
        super(name, agent);
        this.thrusters = thrusters;
        this.mass = agent.getBody().getMass();
        agent.getBody().getInverseInertiaLocal(this.invInertiaLocal);
        this.maxThrust = thrusters.get(0).getMaxThrust();

        double maxOut = maxThrust * 4; // 四个推进器总推力上限
        pidVx = new PIDController(5.0, 0.1, 0.5, Dt, -maxOut, maxOut);
        pidVy = new PIDController(5.0, 0.1, 0.5, Dt, -maxOut, maxOut);
        pidVz = new PIDController(5.0, 0.1, 0.5, Dt, -maxOut, maxOut);
        // 角速度环 PID，输出扭矩（Nm）
        pidRollRate = new PIDController(0.5, 0.1, 0.02, Dt, -maxTorque, maxTorque);
        pidPitchRate = new PIDController(0.5, 0.1, 0.02, Dt, -maxTorque, maxTorque);
        pidYawRate = new PIDController(1.5, 0.05, 0.01, Dt, -maxTorque, maxTorque);
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET_X, 0f);
        builder.define(TARGET_Y, 0f);
        builder.define(TARGET_Z, 0f);
        builder.define(TARGET_YAW, 0f);
    }

    public void setTarget(Vector3f pos) {
        this.syncedData.set(TARGET_X, pos.x);
        this.syncedData.set(TARGET_Y, pos.y);
        this.syncedData.set(TARGET_Z, pos.z);
    }


    public Vector3f getTarget() {
        return new Vector3f(
                this.syncedData.get(TARGET_X),
                this.syncedData.get(TARGET_Y),
                this.syncedData.get(TARGET_Z)
        );
    }

    public void setTargetYaw(float yawDegrees) {
        this.syncedData.set(TARGET_YAW, yawDegrees * FastMath.DEG_TO_RAD);
    }

    public float getTargetYaw() {
        return this.syncedData.get(TARGET_YAW);
    }

    public void hover() {
        setTarget(agent.getPosition());
        setTargetYaw(agent.getYaw() * FastMath.RAD_TO_DEG);
    }

    @Override
    public void prePhysicsTick() {
        super.prePhysicsTick();
        if (getLevel().isClientSide()) return;
        /* 速度环 - 目标位置求取目标速度 */
        Vector3f pos = agent.getPosition();
        Vector3f target = getTarget();

        Vector3f posError = target.subtract(pos);
        Vector3f targetVel = posError.mult(posKp); // 简单的P控制器
        // 限幅
        if (targetVel.length() > maxSpeed) targetVel = targetVel.normalize().mult(maxSpeed);

        /* 推力环 - 目标速度求取目标推力 */
        Vector3f currentVel = agent.getBody().getLinearVelocity(null);

        // 计算每个轴的力
        double fx = pidVx.step(targetVel.x, currentVel.x);
        double fy = pidVy.step(targetVel.y, currentVel.y);
        double fz = pidVz.step(targetVel.z, currentVel.z);
        fy += mass * gravity; // 补偿重力
        Vector3f targetForce = new Vector3f((float) fx, (float) fy, (float) fz);

        /* 姿态环 - 目标推力方向求取目标姿态 */
        Vector3f targetDir = MyQuaternion.rotate(
                new Quaternion().fromAngles(0, -getTargetYaw(), 0),
                Vector3f.UNIT_Z.negate(),
                null);
        Vector3f targetForceDir;
        if (targetForce.length() > 0.1) {
            targetForceDir = targetForce.normalize();
        } else targetForceDir = Vector3f.UNIT_Y;
        Vector3f xAxes = targetForceDir.cross(targetDir);
        Vector3f zAxes = xAxes.cross(targetForceDir); // 通过一系列叉乘得到正交的三个轴，用于获取目标姿态四元数
        Quaternion targetRot = new Quaternion().fromAxes(xAxes, targetForceDir, zAxes);
        // 目标与实际姿态的差值
        Quaternion currentRot = agent.getBody().getPhysicsRotation(null);
        Quaternion relRot = targetRot.mult(currentRot.inverse());
        Vector3f relRotAng = PhysicsHelperKt.toBVector3f( // 转为角度差异(弧度制，世界坐标系)
                SparkMathKt.toQuaternionf(relRot).getEulerAnglesXYZ(new org.joml.Vector3f()));

        /* 角速度环 - 目标姿态求取目标角速度 */
        Vector3f targetAngVelWorld = relRotAng.mult(angVelKp);
        Vector3f currentAngVelWorld = agent.getBody().getAngularVelocity(null);
        Vector3f relAngVelWorld = targetAngVelWorld.subtract(currentAngVelWorld);
        Vector3f targetAngVelLocal = worldToBody(relAngVelWorld);
        Vector3f currentAngVelLocal = worldToBody(currentAngVelWorld); // 转为局部坐标系

        // 计算每个轴的扭矩
        double mx = pidPitchRate.step(targetAngVelLocal.x, currentAngVelLocal.x);
        double my = pidYawRate.step(targetAngVelLocal.y, currentAngVelLocal.y);
        double mz = pidRollRate.step(targetAngVelLocal.z, currentAngVelLocal.z);

        Vector3f targetTorque = new Vector3f((float) mx, (float) my, (float) mz);
        float frontDf = targetTorque.x / 0.5f / 2;
        float rightDf = targetTorque.z / 0.5f / 2;
        Vector3f extraYawTorque = new Vector3f(0, (float) my, 0);
        getAgent().getBody().applyTorque(bodyToWorld(extraYawTorque)); // 推进器模型无反扭力矩，故施加一个魔法力矩

        /* 以下两个之中选择一种 */

        // 使用推进器产生推力和力矩 - 最物理，但是控制效果一般，需要好好调参（强化学习？）
//        Vector3f targetForceLocal = worldToBody(targetForce); // 转为局部坐标系，仅使用y投影作为推进器推力
//        thrusters.get(0).setTargetThrust((targetForceLocal.y / 4 + frontDf - rightDf) / maxThrust);
//        thrusters.get(1).setTargetThrust((targetForceLocal.y / 4 + frontDf + rightDf) / maxThrust);
//        thrusters.get(2).setTargetThrust((targetForceLocal.y / 4 + frontDf - rightDf) / maxThrust);
//        thrusters.get(3).setTargetThrust((targetForceLocal.y / 4 + -frontDf + rightDf) / maxThrust);

        // 推进器仅产生力矩，推力是魔法力 - 视觉效果更好，但不物理
        getAgent().getBody().applyCentralForce(targetForce); // 魔法力
        thrusters.get(0).setTargetThrust((frontDf - rightDf) / maxThrust);
        thrusters.get(1).setTargetThrust((frontDf + rightDf) / maxThrust);
        thrusters.get(2).setTargetThrust((frontDf - rightDf) / maxThrust);
        thrusters.get(3).setTargetThrust((-frontDf + rightDf) / maxThrust);
    }

    private Vector3f worldToBody(Vector3f worldVec) {
        Quaternion invRot = agent.getBody().getPhysicsRotation(null).inverse();
        return MyQuaternion.rotate(invRot, worldVec, new Vector3f());
    }

    private Vector3f bodyToWorld(Vector3f bodyVec) {
        return MyQuaternion.rotate(agent.getBody().getPhysicsRotation(null), bodyVec, new Vector3f());
    }
}