package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;
import net.neoforged.neoforge.network.PacketDistributor;

public record SetPerspectivePayload(
        int entityId,
        int mode // 0: 第一人称, 1: 第三人称背面, 2: 第三人称正面
) implements PyPayload {

    public static final Codec<SetPerspectivePayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("entity_id", -1).forGetter(SetPerspectivePayload::entityId),
                    Codec.INT.fieldOf("mode").forGetter(SetPerspectivePayload::mode)
            ).apply(instance, SetPerspectivePayload::new));

    public static final PyPayloadType<SetPerspectivePayload> TYPE =
            new PyPayloadType<>("set_perspective", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(SetPerspectivePayload payload, PyContext context) {
        if (payload.mode() < 0 || payload.mode() > 2) {
            return PyHandleResult.fail("Invalid perspective mode: " + payload.mode());
        }

        try {
            return context.enqueueWork(() -> {
                var player = context.requirePlayer(payload.entityId());
                PacketDistributor.sendToPlayer(
                        player,
                        new io.github.sweetzonzi.py_port.network.java.payload.SetPerspectivePayload(payload.mode())
                );
                JsonObject data = new JsonObject();
                data.addProperty("player_id", player.getId());
                data.addProperty("status", "queued");
                return PyHandleResult.success(data);
            }).join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return PyHandleResult.fail(cause.getMessage());
        }
    }
}
