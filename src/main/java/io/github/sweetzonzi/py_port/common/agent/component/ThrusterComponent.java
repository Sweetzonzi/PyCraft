package io.github.sweetzonzi.py_port.common.agent.component;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * 推进器组件，根据设定的推力，每帧为刚体某一位置施加一定推力
 */
@Getter
public class ThrusterComponent extends AbstractAgentComponent {
    private final Vector3f offset;
    private final Vector3f direction;
    private final float maxThrust;
    /**
     * 目标推进力，在服务端与客户端之间同步，范围0~1之间
     */
    protected static final EntityDataAccessor<Float> TARGET_THRUST_ID = SynchedEntityData.defineId(ThrusterComponent.class, EntityDataSerializers.FLOAT);

    /**
     * 推进器组件，根据设定的推力水平对持有者施加一定推力
     *
     * @param name      组件名称
     * @param agent     组件持有者智能体
     * @param offset    相对智能体原点的偏移
     * @param direction 喷射方向，即刚体收到推力的反向
     * @param maxThrust 最大推力(N)
     */
    public ThrusterComponent(String name, AbstractAgent agent, Vector3f offset, Vector3f direction, float maxThrust) {
        super(name, agent);
        this.offset = offset;
        this.direction = direction.normalize(); // 归一化
        this.maxThrust = maxThrust;
    }

    @Override
    public void preTick() {
        super.preTick();
        if (getLevel().isClientSide() && getTargetThrust() * 0.5f > Math.random()) {
            Vector3f force = direction.mult(getTargetThrust()); // 喷流方向乘以推力得到相对刚体的粒子发射矢量
            Quaternion rotation = agent.getRotation(); // 智能体姿态
            MyQuaternion.rotate(rotation, force, force); // 旋转力矢量，得到相对世界的粒子发射矢量
            Vector3f pos = agent.getPosition().add(MyQuaternion.rotate(rotation, offset, null));
            getLevel().addParticle(
                    ParticleTypes.CLOUD,
                    pos.x, pos.y, pos.z,
                    force.x, force.y, force.z
            );
        }
    }

    @Override
    public void prePhysicsTick() {
        super.prePhysicsTick();
        float thrust = getTargetThrust() * maxThrust; // 目标推力乘以最大推力得到实际推力
        Vector3f force = direction.mult(thrust).negate(); // 推力方向乘以推力得到相对刚体的力矢量
        Quaternion rotation = agent.getRotation(); // 智能体姿态
        MyQuaternion.rotate(rotation, force, force); // 旋转力矢量，得到相对世界的力矢量
        agent.getBody().applyForce(force, MyQuaternion.rotate(rotation, offset, null));
    }

    public float getTargetThrust() {
        return this.syncedData.get(TARGET_THRUST_ID);
    }

    /**
     * 设置目标推力，在服务端与客户端之间同步，范围0~1之间
     *
     * @param thrust 目标推力(0~1)
     */
    public void setTargetThrust(float thrust) {
        this.syncedData.set(TARGET_THRUST_ID, Math.clamp(thrust, -1f, 1f));
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET_THRUST_ID, 0f);
    }
}
