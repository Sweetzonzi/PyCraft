package io.github.sweetzonzi.py_port.client.gui.screen;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import io.github.sweetzonzi.py_port.common.agent.AlgorithmAgent;
import io.github.sweetzonzi.py_port.network.java.payload.RequestOpenShopPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端事件处理器，检测鼠标右击 AlgorithmAgent 以打开商店。
 * <p>在 {@link ClientTickEvent.Pre} 中检测右键边沿触发，
 * 不做射线-AABB 相交检测，确认玩家点击了 AlgorithmAgent 后，
 * 向服务端发送 {@link RequestOpenShopPayload}。</p>
 */
@EventBusSubscriber(modid = PyCraft.MOD_ID, value = Dist.CLIENT)
public class ClientInteractionHandler {

    /** 上一 tick 的右键按下状态，用于边沿检测 */
    private static boolean wasRightClickDown = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 边沿检测：右键刚按下（而非按住不放）
        boolean isDown = mc.options.keyUse.isDown();
        boolean justPressed = isDown && !wasRightClickDown;
        wasRightClickDown = isDown;

        if (!justPressed) return;

        // 从玩家眼睛位置发射射线
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getLookAngle();
        double range = 5.0;
        Vec3 endPos = eyePos.add(lookVec.scale(range));

        // 遍历所有 AlgorithmAgent，检测射线是否命中其碰撞箱
        for (AbstractAgent agent : AgentManager.getLevelAgents(mc.level)) {
            if (!(agent instanceof AlgorithmAgent)) continue;

            Vector3f pos = agent.getPosition();
            // 碰撞箱大小来自 AlgorithmAgent.createBody() 的 BoxCollisionShape(0.35, 0.6, 0.35)（半长宽高）
            double halfWidth = 0.35;
            double halfHeight = 0.6;
            AABB aabb = new AABB(
                    pos.x - halfWidth, pos.y - halfHeight, pos.z - halfWidth,
                    pos.x + halfWidth, pos.y + halfHeight, pos.z + halfWidth
            );

            if (aabb.clip(eyePos, endPos).isPresent()) {
                // 命中 → 通知服务端打开商店
                PacketDistributor.sendToServer(new RequestOpenShopPayload());
                break;
            }
        }
    }
}