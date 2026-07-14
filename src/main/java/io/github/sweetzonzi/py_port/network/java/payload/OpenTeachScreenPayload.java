package io.github.sweetzonzi.py_port.network.java.payload;

import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.client.gui.screen.AlgorithmListScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record OpenTeachScreenPayload() implements CustomPacketPayload {
    public static final Type<OpenTeachScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PyCraft.MOD_ID, "open_teach_screen")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTeachScreenPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenTeachScreenPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final OpenTeachScreenPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                Minecraft.getInstance().setScreen(new AlgorithmListScreen())
        );
    }
}