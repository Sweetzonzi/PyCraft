package io.github.sweetzonzi.py_port.common.item;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.sweetzonzi.py_port.common.agent.CarEntity;
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

public class CarItem extends Item {
    public CarItem() {
        super(new Properties());
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide()) {
            // 直接创建 CarEntity，不要用匿名内部类覆盖方法！
            CarEntity car = new CarEntity(level);

            Transform transform = new Transform(
                    PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                            player.getEyePosition(),
                            player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                    new Quaternion().fromAngles(0, player.getYRot(), 0)
            );
            car.setPosition(transform.getTranslation());
            car.setRotation(transform.getRotation());
            car.addToLevel();
            PyCraft.LOGGER.info("CarEntity spawned with ID: {} at ({}, {}, {})",
                    car.getId(),
                    String.format("%.1f", transform.getTranslation().x),
                    String.format("%.1f", transform.getTranslation().y),
                    String.format("%.1f", transform.getTranslation().z));
            return InteractionResultHolder.consume(stack);
        } else {
            return InteractionResultHolder.success(stack);
        }
    }
}