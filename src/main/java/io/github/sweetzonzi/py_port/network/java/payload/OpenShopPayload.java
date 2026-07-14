package io.github.sweetzonzi.py_port.network.java.payload;

import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.client.gui.screen.ShopScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record OpenShopPayload() implements CustomPacketPayload {
    public static final Type<OpenShopPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PyCraft.MOD_ID, "open_shop")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenShopPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenShopPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final OpenShopPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                Minecraft.getInstance().setScreen(new ShopScreen())
        );
    }
}