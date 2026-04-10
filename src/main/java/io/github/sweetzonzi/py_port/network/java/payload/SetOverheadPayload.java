package io.github.sweetzonzi.py_port.network.java.payload;

import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.config.CameraConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetOverheadPayload(
        boolean enabled,
        double height
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetOverheadPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PyCraft.MOD_ID, "set_overhead"));

    public static final StreamCodec<FriendlyByteBuf, SetOverheadPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetOverheadPayload::write, SetOverheadPayload::new);

    public SetOverheadPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readDouble());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeDouble(height);
    }

    @Override
    public CustomPacketPayload.Type<SetOverheadPayload> type() {
        return TYPE;
    }

    public static void handle(SetOverheadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                // 设置俯视配置
                CameraConfig.enableOverhead = payload.enabled;
                CameraConfig.overheadHeight = payload.height;

                PyCraft.LOGGER.info("[SetOverhead] enabled={}, height={}", payload.enabled, payload.height);

            } catch (Exception e) {
                PyCraft.LOGGER.error("[SetOverhead] Failed: {}", e.getMessage(), e);
            }
        });
    }
}
