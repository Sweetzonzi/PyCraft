package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;

public record GetRotationPayload(int entityId) implements PyPayload {
    public static final Codec<GetRotationPayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("entity_id", -1).forGetter(GetRotationPayload::entityId)
            ).apply(instance, GetRotationPayload::new));

    public static final PyPayloadType<GetRotationPayload> TYPE =
            new PyPayloadType<>("get_rotation", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(GetRotationPayload payload, PyContext context) {
        try {
            return context.enqueueWork(() -> {
                var player = context.requirePlayer(payload.entityId());
                JsonObject data = new JsonObject();
                data.addProperty("player_id", player.getId());
                data.addProperty("yaw", player.getYRot());
                data.addProperty("pitch", player.getXRot());
                return PyHandleResult.success(data);
            }).join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return PyHandleResult.fail(cause.getMessage());
        }
    }
}
