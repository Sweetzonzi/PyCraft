package io.github.sweetzonzi.py_port.network.java.payload;

import io.github.sweetzonzi.py_port.PyCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetRotationPayload(float yaw, float pitch) implements CustomPacketPayload {
    public static final Type<SetRotationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PyCraft.MOD_ID, "set_rotation")
    );

    public static final StreamCodec<FriendlyByteBuf, SetRotationPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetRotationPayload::write, SetRotationPayload::new);

    public SetRotationPayload(FriendlyByteBuf buffer) {
        this(buffer.readFloat(), buffer.readFloat());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
    }

    @Override
    public Type<SetRotationPayload> type() {
        return TYPE;
    }

    public static void handle(SetRotationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                PyCraft.LOGGER.warn("[SetRotation] Local player is not available");
                return;
            }
            player.setYRot(payload.yaw());
            player.setXRot(payload.pitch());
            player.yHeadRot = payload.yaw();
            player.yBodyRot = payload.yaw();
        });
    }
}
