package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.concurrent.CompletableFuture;

/**
 * 停止 Mob 当前的原生路径导航。
 */
public record StopNavigationPayload(
        int entity_id
) implements PyPayload {

    public static final Codec<StopNavigationPayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("entity_id")
                            .forGetter(
                                    StopNavigationPayload::entity_id
                            )
            ).apply(instance, StopNavigationPayload::new));

    public static final PyPayloadType<StopNavigationPayload> TYPE =
            new PyPayloadType<>(
                    "stop_navigation",
                    CODEC
            );

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    private static Entity findEntity(
            Iterable<ServerLevel> levels,
            int entityId
    ) {
        for (ServerLevel level : levels) {
            Entity entity = level.getEntity(entityId);

            if (entity != null) {
                return entity;
            }
        }

        return null;
    }

    public static PyHandleResult handle(
            StopNavigationPayload payload,
            PyContext context
    ) {
        var server = context.getServer();

        if (server == null) {
            return PyHandleResult.fail(
                    "Server not running"
            );
        }

        CompletableFuture<JsonObject> future =
                new CompletableFuture<>();

        server.execute(() -> {
            try {
                Entity found = findEntity(
                        server.getAllLevels(),
                        payload.entity_id()
                );

                if (found == null) {
                    throw new IllegalArgumentException(
                            "Entity not found: "
                                    + payload.entity_id()
                    );
                }

                if (!(found instanceof Mob mob)) {
                    throw new IllegalArgumentException(
                            "Entity is not a Mob: "
                                    + payload.entity_id()
                    );
                }

                boolean wasDone =
                        mob.getNavigation().isDone();

                boolean hadPath =
                        mob.getNavigation().getPath() != null;

                mob.getNavigation().stop();

                JsonObject result = new JsonObject();

                result.addProperty(
                        "entity_id",
                        mob.getId()
                );

                result.addProperty(
                        "stopped",
                        true
                );

                result.addProperty(
                        "previously_done",
                        wasDone
                );

                result.addProperty(
                        "previously_had_path",
                        hadPath
                );

                result.addProperty(
                        "navigation_done",
                        mob.getNavigation().isDone()
                );

                future.complete(result);

            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });

        try {
            return PyHandleResult.success(
                    future.get()
            );
        } catch (Exception exception) {
            Throwable cause = exception.getCause();

            String message;

            if (cause != null && cause.getMessage() != null) {
                message = cause.getMessage();
            } else if (exception.getMessage() != null) {
                message = exception.getMessage();
            } else {
                message = exception.getClass()
                        .getSimpleName();
            }

            return PyHandleResult.fail(
                    "Stop navigation failed: " + message
            );
        }
    }
}