package io.github.sweetzonzi.py_port.common.item;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.sweetzonzi.py_port.common.agent.QuatUavAgent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class QuatUavItem extends Item {
    public QuatUavItem() {
        super(new Properties());
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide()) {
            QuatUavAgent agent = new QuatUavAgent(level);
            Transform transform = new Transform(
                    PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                            player.getEyePosition(),
                            player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                    new Quaternion().fromAngles(0, player.getYRot(), 0)
            );
            agent.setPosition(transform.getTranslation());//设置初始位姿
            agent.setRotation(transform.getRotation());
            agent.addToLevel();
            agent.initHover();  // 设置目标为当前位置，开始悬停
            return InteractionResultHolder.consume(stack);
        } else return InteractionResultHolder.success(stack);
    }
}
