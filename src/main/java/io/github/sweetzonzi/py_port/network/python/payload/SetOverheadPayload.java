package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;
import net.neoforged.neoforge.network.PacketDistributor;

public record SetOverheadPayload(
        int entityId,
        boolean enabled,
        double height
) implements PyPayload {

    public static final Codec<SetOverheadPayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("entity_id", -1).forGetter(SetOverheadPayload::entityId),
                    Codec.BOOL.fieldOf("enabled").forGetter(SetOverheadPayload::enabled),
                    Codec.DOUBLE.fieldOf("height").forGetter(SetOverheadPayload::height)
            ).apply(instance, SetOverheadPayload::new));

    public static final PyPayloadType<SetOverheadPayload> TYPE =
            new PyPayloadType<>("set_overhead", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(SetOverheadPayload payload, PyContext context) {
        if (!Double.isFinite(payload.height()) || payload.height() < 0.0) {
            return PyHandleResult.fail("height must be a finite non-negative number");
        }

        try {
            return context.enqueueWork(() -> {
                var player = context.requirePlayer(payload.entityId());
                PacketDistributor.sendToPlayer(
                        player,
                        new io.github.sweetzonzi.py_port.network.java.payload.SetOverheadPayload(
                                payload.enabled(), payload.height())
                );
                JsonObject data = new JsonObject();
                data.addProperty("player_id", player.getId());
                data.addProperty("enabled", payload.enabled());
                data.addProperty("height", payload.height());
                return PyHandleResult.success(data);
            }).join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return PyHandleResult.fail(cause.getMessage());
        }
    }
}
