package io.github.sweetzonzi.py_port.common.agent;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.agent.component.CarCtrlComponent;
import io.github.sweetzonzi.py_port.common.agent.component.LineFollowComponent;
import io.github.sweetzonzi.py_port.common.agent.component.TurtleCtrlComponent;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;

public class CarEntity extends QuatUavAgent {
    public static final String TYPE = "car_entity";

    private CarCtrlComponent controller;
    private LineFollowComponent lineFollower;
    private TurtleCtrlComponent turtleController;

    public CarEntity(Level level) {
        super(level);

        // 替换掉无人机的控制器
        this.components.remove("controller");

        // 添加汽车控制器
        this.controller = new CarCtrlComponent("car_controller", this);
        addComponent(controller);

        // 添加巡线组件（默认禁用，由 Python 脚本激活）
        this.lineFollower = new LineFollowComponent("line_follower", this);
        addComponent(lineFollower);

        // 添加海龟控制组件（步进控制）
        this.turtleController = new TurtleCtrlComponent("turtle_controller", this);
        addComponent(turtleController);

        PyCraft.LOGGER.info("CarEntity created with car controller, line follower and turtle controller");
    }

    @Override
    protected PhysicsRigidBody createBody() {
        // 汽车的碰撞箱（长、高、宽）
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(1.0f, 0.6f, 1.0f));
        PhysicsRigidBody body = new PhysicsRigidBody(shape, 15f);
        body.setRestitution(0.3f);
        body.setFriction(0.3f);
        return body;
    }

    @Override
    public String getAgentType() {
        return "car_entity";
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
        // 留空
    }

    // Python 调用接口
    public void drive(float throttle, float steering, boolean brake) {
        if (controller != null) {
            controller.setControl(throttle, steering, brake);
        }
    }

    public void handbrake() {
        if (controller != null) {
            controller.handbrake();
        }
    }

    public void releaseHandbrake() {
        if (controller != null) {
            controller.releaseHandbrake();
        }
    }

    public float getSpeed() {
        return getLinearVelocity().length();
    }

    public PhysicsRigidBody getBody() {
        return body;
    }

    public LineFollowComponent getLineFollower() {
        return lineFollower;
    }

    public TurtleCtrlComponent getTurtleController() {
        return turtleController;
    }
}
