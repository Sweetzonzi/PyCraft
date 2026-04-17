package io.github.sweetzonzi.py_port.common.agent;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.common.agent.component.QuatUavCtrlComponent;
import io.github.sweetzonzi.py_port.common.agent.component.ThrusterComponent;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class QuatUavAgent extends AbstractAgent {
    public static final String TYPE = "quat_uav";

    public final ThrusterComponent leftFront;
    public final ThrusterComponent rightFront;
    public final ThrusterComponent leftBack;
    public final ThrusterComponent rightBack;
    public final QuatUavCtrlComponent controller;

    public QuatUavAgent(Level level) {
        super(level);
        leftFront = new ThrusterComponent("left_front", this, new Vector3f(-0.5f, 0f, -0.5f), new Vector3f(0f, -1f, 0f), 10f);
        rightFront = new ThrusterComponent("right_front", this, new Vector3f(0.5f, 0f, -0.5f), new Vector3f(0f, -1f, 0f), 10f);
        leftBack = new ThrusterComponent("left_back", this, new Vector3f(-0.5f, 0f, 0.5f), new Vector3f(0f, -1f, 0f), 10f);
        rightBack = new ThrusterComponent("right_back", this, new Vector3f(0.5f, 0f, 0.5f), new Vector3f(0f, -1f, 0f), 10f);

        controller = new QuatUavCtrlComponent("controller", this, List.of(leftFront, rightFront, leftBack, rightBack));

        addComponent(leftFront);
        addComponent(rightFront);
        addComponent(leftBack);
        addComponent(rightBack);
        addComponent(controller);
    }

    @Override
    protected PhysicsRigidBody createBody() {
        SphereCollisionShape shape = new SphereCollisionShape(0.5f);
        return new PhysicsRigidBody(shape, 1);
    }

    @Override
    public void prePhysicsTick() {
        super.prePhysicsTick();
    }

    public void initHover() {
        controller.hover();
    }

    @Override
    public String getAgentType() {
        return TYPE;
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
        // 留空
    }
}