package io.github.sweetzonzi.py_port.common.agent;

import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.common.agent.component.QuatUavCtrlComponent;
import io.github.sweetzonzi.py_port.common.agent.component.ThrusterComponent;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;

import java.util.List;

public class QuatUavAgent extends AbstractAgent {
    /**
     * 智能体类型名称，用于读取模型贴图，也用于在{@link AgentRegistry}中注册
     */
    public static final String TYPE = "quat_uav"; // 需要与resources/spark_modules/py_port.builtin/py_port中的模型和贴图名称对应

    public final ThrusterComponent leftFront;
    public final ThrusterComponent rightFront;
    public final ThrusterComponent leftBack;
    public final ThrusterComponent rightBack;
    public final QuatUavCtrlComponent controller;

    public QuatUavAgent(Level level) {
        super(level);
        leftFront = new ThrusterComponent(
                "left_front",
                this,
                new Vector3f(-0.5f, 0f, -0.5f),
                new Vector3f(0f, -1f, 0f),
                10f
        );
        rightFront = new ThrusterComponent(
                "right_front",
                this,
                new Vector3f(0.5f, 0f, -0.5f),
                new Vector3f(0f, -1f, 0f),
                10f
        );
        leftBack = new ThrusterComponent(
                "left_back",
                this,
                new Vector3f(-0.5f, 0f, 0.5f),
                new Vector3f(0f, -1f, 0f),
                10f
        );
        rightBack = new ThrusterComponent(
                "right_back",
                this,
                new Vector3f(0.5f, 0f, 0.5f),
                new Vector3f(0f, -1f, 0f),
                10f
        );
        controller = new QuatUavCtrlComponent(
                "controller",
                this,
                List.of(leftFront, rightFront, leftBack, rightBack)
        );
        addComponent(leftFront);
        addComponent(rightFront);
        addComponent(leftBack);
        addComponent(rightBack);
        addComponent(controller);
    }

    /**
     * <p>创建智能体的刚体对象，应当在此设置质量、转动惯量、摩擦、碰撞体积等属性</p>
     * <p>如有必要，也可在此设置关节约束等</p>
     *
     * @return 刚体对象
     */
    @Override
    protected PhysicsRigidBody createBody() {
        SphereCollisionShape shape = new SphereCollisionShape(0.5f);
        return new PhysicsRigidBody(shape, 1);
    }

    @Override
    public void prePhysicsTick() {
        super.prePhysicsTick();
        // 直接设置推力 TODO: 改为在无人机控制器里设置
    }

    public void initHover() {
        // 设置目标为当前位置，实现悬停
        controller.hover();
    }

    /**
     * 设置目标位置（绝对坐标）
     */
    public void setTargetPosition(Vector3f pos) {
        controller.setTarget(pos);
    }

    /**
     * 设置目标位置（Minecraft坐标）
     */
    public void setTargetPosition(net.minecraft.world.phys.Vec3 pos) {
        controller.setTarget(pos);
    }

    /**
     * 向前飞N格（相对当前位置）
     */
    public void moveForward(float blocks) {
        Vector3f current = getPosition();
        Vector3f target = current.add(0, 0, -blocks);  // Z-是前方
        setTargetPosition(target);
    }

    /**
     * 向上飞N格
     */
    public void moveUp(float blocks) {
        Vector3f current = getPosition();
        setTargetPosition(current.add(0, blocks, 0));
    }

    /**
     * 获取智能体的注册类型名称，用于服务端客户端同步，也用于匹配显示的模型和贴图
     *
     * @return 注册类型名称
     */
    @Override
    public String getAgentType() {
        return TYPE;
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
        // 无额外所需同步数据，留空即可
    }
}
