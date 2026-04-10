package io.github.sweetzonzi.py_port.network.java.payload;

import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.config.CameraConfig;  // 导入配置
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetPerspectivePayload(int mode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetPerspectivePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PyCraft.MOD_ID, "set_perspective"));

    public static final StreamCodec<FriendlyByteBuf, SetPerspectivePayload> STREAM_CODEC =
            StreamCodec.ofMember(SetPerspectivePayload::write, SetPerspectivePayload::new);

    public SetPerspectivePayload(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(mode);
    }

    @Override
    public CustomPacketPayload.Type<SetPerspectivePayload> type() {
        return TYPE;
    }

    public static void handle(SetPerspectivePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options == null) {
                    PyCraft.LOGGER.error("[SetPerspective] mc.options is null");
                    return;
                }

                // 切换到第一人称时，关闭俯视强制模式
                if (payload.mode == 0) {
                    CameraConfig.enableOverhead = false;
                    PyCraft.LOGGER.info("[SetPerspective] Disabled overhead mode");
                }

                CameraType type = switch (payload.mode) {
                    case 0 -> CameraType.FIRST_PERSON;
                    case 1 -> CameraType.THIRD_PERSON_BACK;
                    case 2 -> CameraType.THIRD_PERSON_FRONT;
                    default -> throw new IllegalArgumentException("Invalid mode: " + payload.mode);
                };

                mc.options.setCameraType(type);
                PyCraft.LOGGER.info("[SetPerspective] mode={}", payload.mode);

            } catch (Exception e) {
                PyCraft.LOGGER.error("[SetPerspective] Failed: {}", e.getMessage(), e);
            }
        });
    }
}