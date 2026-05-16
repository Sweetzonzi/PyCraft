package io.github.sweetzonzi.py_port.network.java.payload;

import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record AgentRemovePayload(
        int id
) implements CustomPacketPayload {
    public static final Type<AgentRemovePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PyCraft.MOD_ID, "agent_remove_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AgentRemovePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull AgentRemovePayload decode(RegistryFriendlyByteBuf buffer) {
            int id = buffer.readInt();
            return new AgentRemovePayload(id);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, AgentRemovePayload value) {
            buffer.writeInt(value.id());
            //buffer.writeByte(255);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final AgentRemovePayload payload, final IPayloadContext context) {
        AbstractAgent agent = AgentManager.getAgent(context.player().level(), payload.id());
        if (agent instanceof AbstractAgent) {
            context.enqueueWork(agent::removeFromLevel);
        } else
            PyCraft.LOGGER.error("尝试从维度{}中移除不存在的智能体: {}", context.player().level().dimension().location(), payload.id);
    }
}
