package io.github.sweetzonzi.py_port.common.agent;

import io.github.sweetzonzi.py_port.PyCraft;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
@EventBusSubscriber(modid = PyCraft.MOD_ID)
public class AgentRegistry {
    private static final Map<String, Function<Level, ? extends AbstractAgent>> registry = new ConcurrentHashMap<>();

    /**
     * Miencraft启动后注册所有智能体类型
     */
    private static void registerAll() {
        // 注册其他智能体类型
        register(QuatUavAgent.TYPE, QuatUavAgent::new);
        register(CarEntity.TYPE, CarEntity::new);
        register(AlgorithmAgent.TYPE, AlgorithmAgent::new);
    }

    private static void register(String type, Function<Level, ? extends AbstractAgent> agent) {
        registry.put(type, agent);
    }

    @SuppressWarnings("unchecked")
    public static <T extends AbstractAgent> T create(String type, Level level) {
        Function<Level, ? extends AbstractAgent> factory = registry.get(type);
        if (factory != null) return (T) factory.apply(level);
        else throw new IllegalArgumentException("No agent registered with name " + type);
    }

    @SubscribeEvent
    public static void onSetup(FMLCommonSetupEvent event) {
        registerAll();
    }
}
