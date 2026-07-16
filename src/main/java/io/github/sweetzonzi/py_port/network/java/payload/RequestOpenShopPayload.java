package io.github.sweetzonzi.py_port.network.java.payload;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import io.github.sweetzonzi.py_port.common.agent.AlgorithmAgent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端→服务端：请求打开 AlgorithmAgent 商店界面。
 * 服务端 handler 会验证玩家附近 5 格内是否存在 AlgorithmAgent，
 * 若存在则发送 {@link OpenShopPayload} 打开商店。
 */
public record RequestOpenShopPayload() implements CustomPacketPayload {
    public static final Type<RequestOpenShopPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PyCraft.MOD_ID, "request_open_shop")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestOpenShopPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestOpenShopPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final RequestOpenShopPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            // 检查附近 5 格内是否有 AlgorithmAgent（同 /pycraft shop 指令逻辑）
            Vec3 playerPos = serverPlayer.position();
            for (AbstractAgent agent : AgentManager.getLevelAgents(serverPlayer.serverLevel())) {
                if (agent instanceof AlgorithmAgent) {
                    Vector3f agentPos = agent.getTransform().getTranslation();
                    double dx = agentPos.x - playerPos.x();
                    double dy = agentPos.y - playerPos.y();
                    double dz = agentPos.z - playerPos.z();
                    if (Math.sqrt(dx * dx + dy * dy + dz * dz) < 5.0) {
                        PacketDistributor.sendToPlayer(serverPlayer, new OpenShopPayload());
                        return;
                    }
                }
            }
        });
    }
}