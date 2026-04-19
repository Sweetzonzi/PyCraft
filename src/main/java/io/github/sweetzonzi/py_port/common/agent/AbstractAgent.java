package io.github.sweetzonzi.py_port.common.agent;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.ManifoldPoint;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.agent.component.AbstractAgentComponent;
import io.github.sweetzonzi.py_port.network.java.payload.AgentCreatePayload;
import io.github.sweetzonzi.py_port.network.java.payload.AgentRemovePayload;
import io.github.sweetzonzi.py_port.network.java.payload.AgentSyncPayload;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
abstract public class AbstractAgent implements IAnimatable<AbstractAgent>, SyncedDataHolder, PhysicsHost {
    /**
     * 实体计数器，用于生成实体 ID
     */
    protected static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
    /**
     * 模型动画信息索引
     */
    public final ModelIndex modelIndex;
    /**
     * 所在世界
     */
    public final Level level;
    /**
     * 动画控制器
     */
    public final AnimController animController;
    /**
     * 模型控制器
     */
    public final ModelController modelController;
    /**
     * 根物理对象
     */
    public final PhysicsRigidBody body;
    private final HashMap<String, PhysicsCollisionObject> allPhysicsBodies = new HashMap<>();
    /**
     * 用于动画等的变量储存
     */
    public final Map<String, Object> variables = new HashMap<>();
    /**
     * 智能体组件
     */
    public final Map<String, AbstractAgentComponent> components = new HashMap<>();
    /**
     * 物理对象 ID
     */
    @Setter
    public int id;
    /**
     * 同步数据存储器
     */
    protected final SynchedEntityData syncedData;
    protected static final EntityDataAccessor<Vector3f> DATA_POS_ID = SynchedEntityData.defineId(AbstractAgent.class, EntityDataSerializers.VECTOR3);
    protected static final EntityDataAccessor<Quaternionf> DATA_ROT_ID = SynchedEntityData.defineId(AbstractAgent.class, EntityDataSerializers.QUATERNION);
    protected static final EntityDataAccessor<org.joml.Vector3f> DATA_VEL_ID = SynchedEntityData.defineId(AbstractAgent.class, EntityDataSerializers.VECTOR3);
    protected static final EntityDataAccessor<org.joml.Vector3f> DATA_ANG_VEL_ID = SynchedEntityData.defineId(AbstractAgent.class, EntityDataSerializers.VECTOR3);
    protected static final EntityDataAccessor<Boolean> IS_ACTIVE_ID = SynchedEntityData.defineId(AbstractAgent.class, EntityDataSerializers.BOOLEAN);
    protected boolean updateLock = false;//是否禁止同步应用位姿数据到刚体
    //运行中
    public int tickCount = 0;
    public int physicsTickCount = 0;
    public volatile boolean isRemoved = false;
    public volatile boolean isInLevel = false;
    public volatile Transform transform = new Transform();//最新位姿数据
    public volatile Transform oldTransform = new Transform();//用于渲染插值
    public Transform syncTransformBuffer = new Transform();//用于缓存同步数据

    /**
     * 创建一个智能体，仅应在服务端被主动调用
     *
     * @param level 智能体将要加入的世界
     */
    protected AbstractAgent(Level level) {
        this.level = level;
        if (!level.isClientSide()) this.id = ENTITY_COUNTER.incrementAndGet();
        this.modelIndex = new ModelIndex("entity", ResourceLocation.fromNamespaceAndPath(PyCraft.MOD_ID, getAgentType()));
        this.animController = new AnimController(this);
        this.modelController = new ModelController(this);
        this.body = createBody();
        this.body.setCollisionGroup(CollisionGroups.PHYSICS_BODY); // 设置碰撞过滤组
//        this.body.setCollideWithGroups(CollisionGroups.PHYSICS_BODY);
        this.body.addCollideWithGroup(CollisionGroups.TERRAIN); // 与地面碰撞
        this.body.addCollideWithGroup(CollisionGroups.PAWN); // 与原版实体碰撞，仅判定，无碰撞响应
        PhysicsBodyExtensionKt.setOwner(body, this);
        if (level.isClientSide()) body.setKinematic(true); // 服务端权威，客户端仅根据服务端数据更新位置，不进行物理计算
        //各类回调
        PhysicsBodyExtensionKt.onCollidePre(this.body, event -> {
            var o1 = event.getO1();
            var o2 = event.getO2();
            var point1 = event.getO1Point();
            var point2 = event.getO2Point();
            long manifoldPointId = point1.getId();
            return this.onPreContact(o1, o2, point1, point2, manifoldPointId);
        });
        PhysicsBodyExtensionKt.onCollideProcessed(this.body, event -> {
            var o1 = event.getO1();
            var o2 = event.getO2();
            var point1 = event.getO1Point();
            var point2 = event.getO2Point();
            long manifoldPointId = point1.getId();
            this.onContactProcessed(o1, o2, point1, point2, manifoldPointId);
            return null;
        });
        // 服务端-客户端之间的同步数据
        SynchedEntityData.Builder syncheddata$builder = new SynchedEntityData.Builder(this);
        syncheddata$builder.define(DATA_POS_ID, new org.joml.Vector3f());
        syncheddata$builder.define(DATA_ROT_ID, new Quaternionf());
        syncheddata$builder.define(DATA_VEL_ID, new org.joml.Vector3f());
        syncheddata$builder.define(DATA_ANG_VEL_ID, new org.joml.Vector3f());
        syncheddata$builder.define(IS_ACTIVE_ID, true);
        this.defineSyncedData(syncheddata$builder);
        this.syncedData = syncheddata$builder.build();
    }

    public void addComponent(AbstractAgentComponent component) {
        components.put(component.getName(), component);
    }

    /**
     * Minecraft主线程更新前调用，用于执行主线程特定逻辑如添加粒子、播放声音等
     */
    public void preTick() {
        if (isRemoved) return;
        tickCount++;
        if (!level.isClientSide()) {
            syncToClient(); // 同步数据至客户端
        } else {
            //客户端处理同步插值用位姿数据
            clientSyncPose();
        }
        for (AbstractAgentComponent component : components.values()) {
            component.preTick();
        }
    }

    /**
     * Minecraft主线程更新后调用，用于执行主线程特定逻辑如添加粒子、播放声音等
     */
    public void postTick() {
        if (!level.isClientSide() && body.isInWorld()) {
            if ((body.isActive() || body.isKinematic())) {
                //从刚体同步数据
                oldTransform = transform.clone();
                transform = PhysicsBodyExtensionKt.stateOf(body).getTransform();
                updateLock = true;//锁定刚体数据，仅利用服务端刚体数据更新同步用数据
                setPosition(transform.getTranslation());
                setRotation(transform.getRotation());
                setLinearVelocity(body.getLinearVelocity(null));
                setAngularVelocity(body.getAngularVelocity(null));
                getSyncedData().set(IS_ACTIVE_ID, true);
                updateLock = false;//解锁刚体数据，允许set时应用位姿数据到刚体
            } else if (!body.isActive()) { // 更新休眠状态
                getSyncedData().set(IS_ACTIVE_ID, false);
            }
        }
        for (AbstractAgentComponent component : components.values()) {
            component.postTick();
        }
    }

    /**
     * 每次物理更新前被调用，应当在这里更新控制新号，对刚体施加外力、冲量、扭矩等
     */
    public void prePhysicsTick() {
        if (isRemoved) return;
        physicsTickCount++;
        if (level.isClientSide()) {
            body.setLinearVelocity(getLinearVelocity());
            body.setAngularVelocity(getAngularVelocity());
        }
        for (AbstractAgentComponent component : components.values()) {
            component.prePhysicsTick();
        }
    }

    /**
     * 每次物理更新后被调用
     */
    public void postPhysicsTick() {
        for (AbstractAgentComponent component : components.values()) {
            component.postPhysicsTick();
        }
    }

    /**
     * 碰撞点被创建前调用，可在此判断碰撞情况，并决定是否保留该碰撞点
     *
     * @return 是否保留该碰撞点，true 保留，false 丢弃
     */
    public boolean onPreContact(PhysicsCollisionObject o1, @NotNull PhysicsCollisionObject o2,
                                ManifoldPoint point1, ManifoldPoint point2,
                                long manifoldPointId) {
        return true; // 默认所有碰撞均有效
    }

    /**
     * 碰撞点处理过程中调用，可在此进行高级的修改
     */
    public void onContactProcessed(PhysicsCollisionObject o1, @NotNull PhysicsCollisionObject o2,
                                   ManifoldPoint point1, ManifoldPoint point2,
                                   long manifoldPointId) {
        // 碰撞点处理中，可在此进行高级的修改
    }

    /**
     * <p>创建智能体后必须调用的方法，将其加入物理世界，并注册到 {@link AgentManager}中</p>
     * <p>仅应在服务端主动调用，调用前也可提前设置智能体的位姿速度等属性</p>
     */
    public void addToLevel() {
        getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            if (isInLevel || body.isInWorld()) return null;
            getPhysicsLevel().getWorld().addCollisionObject(body);
            isInLevel = true;
            return null;
        });
        AgentManager.addAgent(this);
        if (!level.isClientSide()) sendCreatePacket();
    }

    protected void sendCreatePacket() {
        PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new AgentCreatePayload(getId(), getAgentType(), getSyncedData().getNonDefaultValues()));
    }

    /**
     * 将智能体从世界中移除，仅应在服务端主动调用
     */
    public void removeFromLevel() {
        this.isRemoved = true;
        getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            getPhysicsLevel().getWorld().removeCollisionObject(body);
            isInLevel = false;
            return null;
        });
        AgentManager.removeAgent(this);
        if (!level.isClientSide()) sendDestroyPacket();
    }

    protected void sendDestroyPacket() {
        PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new AgentRemovePayload(getId()));
    }

    public boolean is(String agentType) {
        return getAgentType().equals(agentType);
    }

    /**
     * <p>创建智能体的刚体对象，应当在此设置质量、转动惯量、摩擦、碰撞体积等属性</p>
     * <p>如有必要，也可在此设置关节约束等</p>
     *
     * @return 刚体对象
     */
    abstract protected PhysicsRigidBody createBody();

    /**
     * 获取智能体的注册类型名称，用于服务端客户端同步，也用于匹配显示的模型和贴图
     *
     * @return 注册类型名称
     */
    abstract public String getAgentType();

    /**
     * <p>立即同步发生变化的数据至客户端</p>
     */
    public void syncToClient() {
        if (!level.isClientSide()) {
            SynchedEntityData synchedentitydata = this.getSyncedData();
            List<SynchedEntityData.DataValue<?>> list = synchedentitydata.packDirty();
            if (list != null) {
                PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new AgentSyncPayload(getId(), list));
            }
        }
    }

    protected void clientSyncPose() {
        oldTransform = transform.clone();
        transform = new Transform(getPosition(), getRotation());
    }

    @Override
    public void onSyncedDataUpdated(@NotNull List<SynchedEntityData.DataValue<?>> dataValues) {
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        if (!level.isClientSide()) return;//服务器在需同步数据变化时不做特殊处理
        if (key.equals(DATA_VEL_ID)) {
            body.setLinearVelocity(PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_VEL_ID)));//应用到刚体
        } else if (key.equals(DATA_ANG_VEL_ID)) {
            body.setAngularVelocity(PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_ANG_VEL_ID)));//应用到刚体
        } else if (key.equals(DATA_POS_ID)) {
            body.setPhysicsLocation(PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_POS_ID)));//应用到刚体
        } else if (key.equals(DATA_ROT_ID)) {
            body.setPhysicsRotation(SparkMathKt.toBQuaternion(getSyncedData().get(DATA_ROT_ID)));//应用到刚体
        }
    }

    protected abstract void defineSyncedData(SynchedEntityData.Builder builder);

    public boolean isActive() {
        if (!level.isClientSide()) return body.isActive();
        return getSyncedData().get(IS_ACTIVE_ID);
    }

    public com.jme3.math.Vector3f getPosition() {
        if (!level.isClientSide()) return body.getPhysicsLocation(null);
        return PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_POS_ID));
    }

    public Quaternion getRotation() {
        if (!level.isClientSide()) return body.getPhysicsRotation(null);
        return SparkMathKt.toBQuaternion(getSyncedData().get(DATA_ROT_ID));
    }

    public Quaternionf getQuaternionf() {
        if (!level.isClientSide()) return SparkMathKt.toQuaternionf(body.getPhysicsRotation(null));
        return getSyncedData().get(DATA_ROT_ID);
    }

    public com.jme3.math.Vector3f getLinearVelocity() {
        if (!level.isClientSide()) return body.getLinearVelocity(null);
        return PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_VEL_ID));
    }

    public com.jme3.math.Vector3f getAngularVelocity() {
        if (!level.isClientSide()) return body.getAngularVelocity(null);
        return PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_ANG_VEL_ID));
    }

    public void setPosition(com.jme3.math.Vector3f position) {
        getSyncedData().set(DATA_POS_ID, SparkMathKt.toVector3f(position));
        if (!updateLock) {
            if (isInLevel)
                getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                    body.setPhysicsLocation(position);
                    return null;
                });
            else {
                body.setPhysicsLocation(position);
                clientSyncPose();
            }
        }
    }

    public void setRotation(Quaternion rotation) {
        getSyncedData().set(DATA_ROT_ID, SparkMathKt.toQuaternionf(rotation));
        if (!updateLock) {
            if (isInLevel)
                getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                    body.setPhysicsRotation(rotation);
                    return null;
                });
            else {
                body.setPhysicsRotation(rotation);
                clientSyncPose();
            }
        }
    }

    public void setLinearVelocity(com.jme3.math.Vector3f linearVelocity) {
        getSyncedData().set(DATA_VEL_ID, SparkMathKt.toVector3f(linearVelocity));
        if (!updateLock) {
            if (isInLevel)
                getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                    body.setLinearVelocity(linearVelocity);
                    return null;
                });
            else body.setLinearVelocity(linearVelocity);
        }
    }

    public void setAngularVelocity(com.jme3.math.Vector3f angularVelocity) {
        getSyncedData().set(DATA_ANG_VEL_ID, SparkMathKt.toVector3f(angularVelocity));
        if (!updateLock) {
            if (isInLevel)
                getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                    body.setAngularVelocity(angularVelocity);
                    return null;
                });
            else body.setAngularVelocity(angularVelocity);
        }
    }

    public com.jme3.math.Vector3f getLinearVelocityLocal() {
        com.jme3.math.Vector3f result = getLinearVelocity();//获取物体质心在世界坐标系下的线速度
        Quaternion worldToLocal = getRotation(); //获取物体相对世界坐标的四元数
        MyQuaternion.rotateInverse(worldToLocal, result, result);//旋转世界坐标系向量到刚体自身坐标系
        return result;
    }

    public com.jme3.math.Vector3f getAngularVelocityLocal() {
        com.jme3.math.Vector3f result = getAngularVelocity();//获取物体在世界坐标系下的角速度
        Quaternion worldToLocal = getRotation(); //获取物体相对世界坐标的四元数
        MyQuaternion.rotateInverse(worldToLocal, result, result);//旋转世界坐标系向量到刚体自身坐标系
        return result;
    }

    public com.jme3.math.Vector3f getFrontVector() {
        return PhysicsHelperKt.toBVector3f(getQuaternionf().transform(new org.joml.Vector3f(0, 0, -1)));
    }

    public com.jme3.math.Vector3f getUpVector() {
        return PhysicsHelperKt.toBVector3f(getQuaternionf().transform(new org.joml.Vector3f(0, 1, 0)));
    }

    public com.jme3.math.Vector3f getRightVector() {
        return PhysicsHelperKt.toBVector3f(getQuaternionf().transform(new org.joml.Vector3f(1, 0, 0)));
    }

    /**
     * <p>基于前向量计算pitch角（俯仰角）</p>
     * <p>pitch角表示物体前后倾斜的角度，范围[-90°, 90°]</p>
     * <p>基于前向量在y轴上的投影计算，避免旋转顺序问题</p>
     *
     * @return pitch角（弧度）
     */
    public float getPitch() {
        com.jme3.math.Vector3f frontVector = getFrontVector();
        // pitch = arcsin(前向量的y分量)
        return (float) Math.asin(frontVector.y);
    }

    /**
     * <p>基于前向量计算yaw角（偏航角）</p>
     * <p>yaw角表示物体左右旋转的角度，范围[-180°, 180°]</p>
     * <p>基于前向量在xz平面上的投影计算，避免旋转顺序问题</p>
     *
     * @return yaw角（弧度）
     */
    public float getYaw() {
        com.jme3.math.Vector3f frontVector = getFrontVector();
        // yaw = atan2(前向量的x分量, 前向量的z分量)
        return (float) Math.atan2(frontVector.x, -frontVector.z);
    }

    /**
     * <p>基于右向量和上向量计算roll角（滚转角）</p>
     * <p>roll角表示物体绕前向轴旋转的角度，范围[-180°, 180°]</p>
     * <p>基于右向量在世界坐标系上向量上的投影计算，避免旋转顺序问题</p>
     *
     * @return roll角（弧度）
     */
    public float getRoll() {
        com.jme3.math.Vector3f rightVector = getRightVector();
        // 计算右向量在世界坐标系上向量(0,1,0)上的投影
        // roll = atan2(右向量的y分量, 右向量在xz平面上的长度)
        float rightVectorLengthXZ = (float) Math.sqrt(rightVector.x * rightVector.x + rightVector.z * rightVector.z);
        return (float) Math.atan2(rightVector.y, rightVectorLengthXZ);
    }


    @NotNull
    public Matrix4f getWorldPositionMatrix(@NotNull Number number) {
        return SparkMathKt.toMatrix4f(SparkMathKt.lerp(oldTransform, transform, number.floatValue()).toTransformMatrix());
    }


    @Override
    public AbstractAgent getAnimatable() {
        return this;
    }

    public @NotNull PhysicsLevel getPhysicsLevel() {
        return SparkLevel.getPhysicsLevel(getLevel());
    }

    @Override
    public @NotNull Level getAnimLevel() {
        return getLevel();
    }

    @Override
    public @NotNull ModelIndex getDefaultModelIndex() {
        return modelIndex;
    }
}
