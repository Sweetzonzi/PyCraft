package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;
import net.neoforged.neoforge.network.PacketDistributor;

public record SetRotationPayload(
        int entityId,
        float yaw,
        float pitch
) implements PyPayload {

    public static final Codec<SetRotationPayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("entity_id", -1).forGetter(SetRotationPayload::entityId),
                    Codec.FLOAT.fieldOf("yaw").forGetter(SetRotationPayload::yaw),
                    Codec.FLOAT.fieldOf("pitch").forGetter(SetRotationPayload::pitch)
            ).apply(instance, SetRotationPayload::new));

    public static final PyPayloadType<SetRotationPayload> TYPE =
            new PyPayloadType<>("set_rotation", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(SetRotationPayload payload, PyContext context) {
        if (!Float.isFinite(payload.yaw()) || !Float.isFinite(payload.pitch())) {
            return PyHandleResult.fail("yaw and pitch must be finite numbers");
        }

        try {
            return context.enqueueWork(() -> {
                var player = context.requirePlayer(payload.entityId());
                player.setYRot(payload.yaw());
                player.setXRot(payload.pitch());
                player.setYHeadRot(payload.yaw());
                player.setYBodyRot(payload.yaw());
                PacketDistributor.sendToPlayer(
                        player,
                        new io.github.sweetzonzi.py_port.network.java.payload.SetRotationPayload(
                                payload.yaw(), payload.pitch())
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
