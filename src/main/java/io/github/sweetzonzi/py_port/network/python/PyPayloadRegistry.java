package io.github.sweetzonzi.py_port.network.python;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.network.python.infrastructure.*;
import io.github.sweetzonzi.py_port.network.python.payload.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 网络包注册，保存网络包类型和处理器的对应关系
 */
public class PyPayloadRegistry {
    private static final Map<String, RegisteredHandler<?>> HANDLERS = new HashMap<>();

    public record RegisteredHandler<T extends PyPayload>(Codec<T> codec, PyPayloadHandler<T> handler) {
    }

    /**
     * Miencraft服务器启动后注册所有网络包及其处理器
     */
    public static void registerAll() {
        // 在此注册所有网络包
        register(ListLevelPayload.TYPE, ListLevelPayload::handle);
        register(GetTimePayload.TYPE, GetTimePayload::handle);
        register(SetBlockPayload.TYPE, SetBlockPayload::handle);
        register(SetBlocksPayload.TYPE, SetBlocksPayload::handle);
        register(GetBlockPayload.TYPE, GetBlockPayload::handle);
        register(GetEntityPosPayload.TYPE, GetEntityPosPayload::handle);
        register(TeleportEntityPayload.TYPE, TeleportEntityPayload::handle);
        register(SetRotationPayload.TYPE, SetRotationPayload::handle);
        register(GetRotationPayload.TYPE, GetRotationPayload::handle);
        register(GetPlayersPayload.TYPE, GetPlayersPayload::handle);
        register(MoveEntityPayload.TYPE, MoveEntityPayload::handle);
        register(SetPerspectivePayload.TYPE, SetPerspectivePayload::handle);
        register(SpawnEntityPayload.TYPE, SpawnEntityPayload::handle);
        register(SpawnParticlePayload.TYPE, SpawnParticlePayload::handle);
        register(DrawPathPayload.TYPE, DrawPathPayload::handle);
        register(GetEntitiesPayload.TYPE, GetEntitiesPayload::handle);
        register(AttackEntityPayload.TYPE, AttackEntityPayload::handle);
        register(RemoveEntityPayload.TYPE, RemoveEntityPayload::handle);
        register(SetOverheadPayload.TYPE, SetOverheadPayload::handle);
        register(SetUavTargetPayload.TYPE, SetUavTargetPayload::handle);
        register(GetUavStatePayload.TYPE, GetUavStatePayload::handle);
        register(GetUavListPayload.TYPE, GetUavListPayload::handle);
        register(SpawnUavPayload.TYPE, SpawnUavPayload::handle);
        register(RemoveUavPayload.TYPE, RemoveUavPayload::handle);
        register(ClearUavPayload.TYPE, ClearUavPayload::handle);
        register(AgentCommandPayload.TYPE, AgentCommandPayload::handle);
        register(SetEntityAttributesPayload.TYPE, SetEntityAttributesPayload::handle);
        register(GetEntityAttributesPayload.TYPE, GetEntityAttributesPayload::handle);
        register(NavigateToEntityPayload.TYPE, NavigateToEntityPayload::handle);
        register(StopNavigationPayload.TYPE, StopNavigationPayload::handle);
        register(SpawnAlgorithmPayload.TYPE, SpawnAlgorithmPayload::handle);
        register(SetContainerItemPayload.TYPE, SetContainerItemPayload::handle);
        register(GetContainerItemsPayload.TYPE, GetContainerItemsPayload::handle);
        register(TakeContainerItemPayload.TYPE, TakeContainerItemPayload::handle);
        PyCraft.LOGGER.info("[PyPayload] Registered {} payload types", HANDLERS.size());
    }

    /**
     * 注册一般网络包
     *
     * @param type    包的注册名与编解码器
     * @param handler 包处理器
     * @param <T>     包类
     */
    public static <T extends PyPayload> void register(PyPayloadType<T> type, PyPayloadHandler<T> handler) {
        if (HANDLERS.containsKey(type.type()))
            throw new IllegalArgumentException("Payload type already registered: " + type.type());
        HANDLERS.put(type.type(), new RegisteredHandler<>(type.codec(), handler));
    }

    public static void handle(PyMessage packet, PyContext ctx) {
        RegisteredHandler<?> reg = HANDLERS.get(packet.type());
        if (reg == null) {
            PyHandleResult fail = PyHandleResult.fail("Unknown message type: " + packet.type());
            ctx.reply(fail);
            return;
        }
        PyHandleResult result = handleWithCodec(reg, packet.data(), ctx);
        ctx.reply(result);
    }

    private static <T extends PyPayload> PyHandleResult handleWithCodec(RegisteredHandler<T> reg, JsonObject data, PyContext ctx) {
        // 使用 Codec 将 JsonObject 解码为具体对象 T
        var result = reg.codec().parse(JsonOps.INSTANCE, data);
        // 处理解码结果
        var optional = result.resultOrPartial(error -> PyCraft.LOGGER.error("Error decoding payload: {}", error));
        if (optional.isPresent()) {
            T payload = optional.get();
            try {
                // 调用用户注册的处理器，获得处理结果
                return reg.handler().handle(payload, ctx);
            } catch (Exception e) {
                // 处理器内部抛出异常，转为失败结果
                return PyHandleResult.fail("Handler error: " + e.getMessage());
            }
        } else {
            // 解码失败，构造错误结果
            var errorMsg = result.error()
                    .map(DataResult.Error::message)
                    .orElse("Unknown decode error");
            return PyHandleResult.fail("Decode failed: " + errorMsg);
        }
    }
}
