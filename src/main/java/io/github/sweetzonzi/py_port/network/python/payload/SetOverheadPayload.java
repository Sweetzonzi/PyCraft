package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.config.CameraConfig;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;

public record SetOverheadPayload(
        boolean enabled,
        double height
) implements PyPayload {

    public static final Codec<SetOverheadPayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
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
        // 直接修改配置，Mixin 会读取
        CameraConfig.enableOverhead = payload.enabled();
        CameraConfig.overheadHeight = payload.height();

        JsonObject data = new JsonObject();
        data.addProperty("enabled", payload.enabled());
        data.addProperty("height", payload.height());
        data.addProperty("success", true);

        return PyHandleResult.success(data);
    }
}