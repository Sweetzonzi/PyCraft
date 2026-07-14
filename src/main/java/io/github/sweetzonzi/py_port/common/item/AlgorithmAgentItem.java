package io.github.sweetzonzi.py_port.common.item;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.sweetzonzi.py_port.common.agent.AlgorithmAgent;
import io.github.sweetzonzi.py_port.PyCraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class AlgorithmAgentItem extends Item {
    public AlgorithmAgentItem() {
        super(new Properties());
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide()) {
            AlgorithmAgent agent = new AlgorithmAgent(level);

            Transform transform = new Transform(
                    PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                            player.getEyePosition(),
                            player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                    new Quaternion().fromAngles(0, player.getYRot(), 0)
            );
            agent.setPosition(transform.getTranslation());
            agent.setRotation(transform.getRotation());
            agent.addToLevel();
            PyCraft.LOGGER.info("AlgorithmAgent spawned with ID: {} at ({}, {}, {})",
                    agent.getId(),
                    String.format("%.1f", transform.getTranslation().x),
                    String.format("%.1f", transform.getTranslation().y),
                    String.format("%.1f", transform.getTranslation().z));
            return InteractionResultHolder.consume(stack);
        } else {
            return InteractionResultHolder.success(stack);
        }
    }
}