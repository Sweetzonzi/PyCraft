package io.github.sweetzonzi.py_port.common.agent.component;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import java.util.List;
import static com.jme3.math.FastMath.clamp;

public class QuatUavCtrlComponent extends AbstractAgentComponent {
    private final List<ThrusterComponent> thrusters;

    protected static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(QuatUavCtrlComponent.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(QuatUavCtrlComponent.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(QuatUavCtrlComponent.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> TARGET_YAW = SynchedEntityData.defineId(QuatUavCtrlComponent.class, EntityDataSerializers.FLOAT);

    private float mass;
    private final float gravity = 9.81f;
    private float armLength;
    private float maxThrust;

    // 外环PID
    private float kpPosX = 1.0f;
    private float kpPosY = 1.0f;
    private float kpPosZ = 1.5f;

    private float kiPosX = 0.001f;
    private float kiPosY = 0.01f;
    private float kiPosZ = 0.04f;

    private float kdPosX = 1.5f;
    private float kdPosY = 1.5f;
    private float kdPosZ = 2.0f;

    // 内环PID
    private float kpAttRoll = 6.0f;
    private float kpAttPitch = 6.0f;
    private float kpAttYaw = 3.0f;

    private float kdAttRoll = 5.0f;
    private float kdAttPitch = 5.0f;
    private float kdAttYaw = 2.0f;

    private float integralX = 0, integralY = 0, integralZ = 0;
    private float integralRoll = 0, integralPitch = 0, integralYaw = 0;

    private static final float INTEGRAL_LIMIT_POS = 1.0f;
    private static final float INTEGRAL_LIMIT_ATT = 1.0f;
    private static final float Dt = 0.02f;

    public QuatUavCtrlComponent(String name, AbstractAgent agent, List<ThrusterComponent> thrusters) {
        super(name, agent);
        this.thrusters = thrusters;
        this.mass = agent.getBody().getMass();
        this.armLength = thrusters.get(0).getOffset().length();
        this.maxThrust = thrusters.get(0).getMaxThrust();
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
        resetIntegrals();
    }

    public void resetIntegrals() {
        integralX = integralY = integralZ = 0;
        integralRoll = integralPitch = integralYaw = 0;
    }

    private Quaternion multiply(Quaternion a, Quaternion b) {
        return new Quaternion(
                a.getW()*b.getX() + a.getX()*b.getW() + a.getY()*b.getZ() - a.getZ()*b.getY(),
                a.getW()*b.getY() - a.getX()*b.getZ() + a.getY()*b.getW() + a.getZ()*b.getX(),
                a.getW()*b.getZ() + a.getX()*b.getY() - a.getY()*b.getX() + a.getZ()*b.getW(),
                a.getW()*b.getW() - a.getX()*b.getX() - a.getY()*b.getY() - a.getZ()*b.getZ()
        );
    }

    @Override
    public void prePhysicsTick() {
        super.prePhysicsTick();
        if (getLevel().isClientSide()) return;

        Vector3f pos = agent.getPosition();
        Vector3f vel = new Vector3f();
        agent.getBody().getLinearVelocity(vel);
        Vector3f angularVelLocal = agent.getAngularVelocityLocal();

        Vector3f target = getTarget();

        float ex = target.x - pos.x;
        float ey = target.y - pos.y;
        float ez = target.z - pos.z;

        float deadband = 0.05f; // 误差死区

        if (Math.abs(ex) < deadband) ex = 0;
        if (Math.abs(ey) < deadband) ey = 0;
        if (Math.abs(ez) < deadband) ez = 0;

        // 积分
        integralX = clamp(integralX + ex * Dt, -INTEGRAL_LIMIT_POS, INTEGRAL_LIMIT_POS);
        integralY = clamp(integralY + ey * Dt, -INTEGRAL_LIMIT_POS, INTEGRAL_LIMIT_POS);
        integralZ = clamp(integralZ + ez * Dt, -INTEGRAL_LIMIT_POS, INTEGRAL_LIMIT_POS);

        // 外环输出加速度
        float axDes = kpPosX * ex + kiPosX * integralX - kdPosX * vel.x;
        float ayDes = kpPosY * ey + kiPosY * integralY - kdPosY * vel.y;
        float azDes = kpPosZ * ez + kiPosZ * integralZ - kdPosZ * vel.z;

        float totalThrust = mass * (gravity + ayDes);
        totalThrust = clamp(totalThrust, 0, 4 * maxThrust * 0.9f);
        float baseThrust = totalThrust / 4f;

        // 世界转换为机体坐标
        Quaternion rot = agent.getRotation();
        Vector3f accWorld = new Vector3f(axDes, 0, azDes);
        Quaternion quat = rot;
        Quaternion qInv = new Quaternion(-quat.getX(), -quat.getY(), -quat.getZ(), quat.getW());

        // 把向量当四元数
        Quaternion vQuat = new Quaternion(accWorld.x, accWorld.y, accWorld.z, 0);
        Quaternion temp = multiply(qInv, vQuat);
        Quaternion result = multiply(temp, quat);
        Vector3f accBody = new Vector3f(result.getX(), result.getY(), result.getZ());

        float phiDes = FastMath.atan2(accBody.z, gravity);
        float thetaDes = FastMath.atan2(-accBody.x, gravity);

        float maxTilt = 20 * FastMath.DEG_TO_RAD;
        phiDes = clamp(phiDes, -maxTilt, maxTilt);
        thetaDes = clamp(thetaDes, -maxTilt, maxTilt);

        float psiDes = getTargetYaw();

        float phi = agent.getRoll();
        float theta = agent.getPitch();
        float psi = agent.getYaw();

        // 角速度轴
        //float p = angularVelLocal.x; // roll
        //float r = angularVelLocal.y; // yaw
        //float q = angularVelLocal.z; // pitch
        float p = angularVelLocal.x; // roll
        float q = angularVelLocal.y; // pitch
        float r = angularVelLocal.z; // yaw

        float ePhi = phiDes - phi;
        float eTheta = thetaDes - theta;
        float ePsi = psiDes - psi;

        float attDeadband = 1 * FastMath.DEG_TO_RAD;

        if (Math.abs(ePhi) < attDeadband) ePhi = 0;
        if (Math.abs(eTheta) < attDeadband) eTheta = 0;
        if(Math.abs((ePsi)) < attDeadband) ePsi = 0;

        while (ePsi > FastMath.PI) ePsi -= 2 * FastMath.PI;
        while (ePsi < -FastMath.PI) ePsi += 2 * FastMath.PI;

        float pDes = kpAttRoll * ePhi - kdAttRoll * p;
        float qDes = kpAttPitch * eTheta - kdAttPitch * q;
        float rDes = kpAttYaw * ePsi - kdAttYaw * r;

        float torqueRoll = (pDes - p) * 0.05f;
        float torquePitch = (qDes - q) * 0.05f;
        float torqueYaw = (rDes - r) * 0.1f;
        agent.getBody().applyTorque(new Vector3f(0, torqueYaw, 0));

        float rollDiff = torqueRoll / (2 * armLength);
        float pitchDiff = torquePitch / (2 * armLength);
        float tLF = baseThrust - rollDiff - pitchDiff;
        float tRF = baseThrust + rollDiff - pitchDiff;
        float tLB = baseThrust - rollDiff + pitchDiff;
        float tRB = baseThrust + rollDiff + pitchDiff;

        // 推力非负保护
        float minT = Math.min(Math.min(tLF, tRF), Math.min(tLB, tRB));
        if (minT < 0) {
            tLF -= minT;
            tRF -= minT;
            tLB -= minT;
            tRB -= minT;
        }

        thrusters.get(0).setTargetThrust(clamp(tLF / maxThrust, 0f, 1f));
        thrusters.get(1).setTargetThrust(clamp(tRF / maxThrust, 0f, 1f));
        thrusters.get(2).setTargetThrust(clamp(tLB / maxThrust, 0f, 1f));
        thrusters.get(3).setTargetThrust(clamp(tRB / maxThrust, 0f, 1f));

        // 角阻尼
        Vector3f angVel = new Vector3f();
        agent.getBody().getAngularVelocity(angVel);
        // 阻尼系数
        float damping = 0.3f;
        // 力矩
        Vector3f dampingTorque = angVel.mult(-damping, new Vector3f());
        // 施加到刚体
        agent.getBody().applyTorque(dampingTorque);

        // 角速度限制
        float maxRate = 2.0f;
        Vector3f clamped = new Vector3f(
                clamp(angVel.x, -maxRate, maxRate),
                clamp(angVel.y, -maxRate, maxRate),
                clamp(angVel.z, -maxRate, maxRate)
        );
        agent.getBody().setAngularVelocity(clamped);

    }
}