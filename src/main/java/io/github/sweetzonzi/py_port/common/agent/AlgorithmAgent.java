package io.github.sweetzonzi.py_port.common.agent;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.agent.component.AlgorithmBrainComponent;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class AlgorithmAgent extends AbstractAgent {
    public static final String TYPE = "algorithm_ai";

    private AlgorithmBrainComponent brain;

    public AlgorithmAgent(Level level) {
        super(level);

        this.brain = new AlgorithmBrainComponent("algorithm_brain", this);
        addComponent(brain);

        PyCraft.LOGGER.info("AlgorithmAgent created with AlgorithmBrainComponent");
    }

    @Override
    protected PhysicsRigidBody createBody() {
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(0.35f, 0.6f, 0.35f));
        PhysicsRigidBody body = new PhysicsRigidBody(shape, 10f);
        body.setRestitution(0.0f);
        body.setFriction(0.5f);
        body.setLinearDamping(0.99f);
        body.setAngularDamping(0.99f);
        return body;
    }

    @Override
    public String getAgentType() {
        return TYPE;
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
    }

    public void startFinding(Block target, int radius) {
        if (brain != null) brain.startFinding(target, radius);
    }

    public void stopTask() {
        if (brain != null) brain.stop();
    }

    public String getTaskStatus() {
        if (brain == null) return "无大脑组件";
        return brain.getStatus();
    }

    public List<ItemStack> getInventory() {
        if (brain == null) return List.of();
        return brain.getInventory();
    }

    public void setBlockValue(Block block, int value) {
        if (brain != null) brain.setBlockValue(block, value);
    }
}