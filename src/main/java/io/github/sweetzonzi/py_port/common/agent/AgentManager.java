package io.github.sweetzonzi.py_port.common.agent;

import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent;
import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.network.java.payload.AgentComponentSyncPayload;
import io.github.sweetzonzi.py_port.network.java.payload.AgentCreatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = PyCraft.MOD_ID)
public class AgentManager {
    public static final Map<Level, Map<Integer, AbstractAgent>> levelAgents = new ConcurrentHashMap<>();

    public static void addAgent(AbstractAgent abstractAgent) {
        levelAgents.computeIfAbsent(
                abstractAgent.getLevel(), k -> new ConcurrentHashMap<>()).put(
                abstractAgent.getId(), abstractAgent);
    }

    public static void removeAgent(AbstractAgent abstractAgent) {
        if (levelAgents.containsKey(abstractAgent.getLevel())) {
            levelAgents.get(abstractAgent.getLevel()).remove(abstractAgent.getId());
        }
    }

    @Nullable
    public static AbstractAgent getAgent(Level level, int id) {
        if (levelAgents.containsKey(level)) {
            return levelAgents.get(level).get(id);
        }
        return null;
    }

    public static Set<AbstractAgent> getLevelAgents(Level level) {
        return new HashSet<>(levelAgents.computeIfAbsent(level, k -> new ConcurrentHashMap<>()).values());
    }

    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent event) {
        levelAgents.clear();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncFullStateTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncFullStateTo(player);
        }
    }

    /** Sends existing non-vanilla agents to players who join after agent creation. */
    public static void syncFullStateTo(ServerPlayer player) {
        for (AbstractAgent agent : getLevelAgents(player.serverLevel())) {
            var agentData = agent.getSyncedData().getNonDefaultValues();
            PacketDistributor.sendToPlayer(player, new AgentCreatePayload(
                    agent.getId(),
                    agent.getAgentType(),
                    agentData == null ? List.of() : agentData
            ));
            agent.getComponents().values().forEach(component -> {
                var componentData = component.getSyncedData().getNonDefaultValues();
                if (componentData != null && !componentData.isEmpty()) {
                    PacketDistributor.sendToPlayer(player, new AgentComponentSyncPayload(
                            agent.getId(), component.getName(), componentData));
                }
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPreTick(LevelTickEvent.Pre event) {
        levelAgents.computeIfAbsent(event.getLevel(), k -> new ConcurrentHashMap<>()).values().forEach(AbstractAgent::preTick);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPostTick(LevelTickEvent.Post event) {
        levelAgents.computeIfAbsent(event.getLevel(), k -> new ConcurrentHashMap<>()).values().forEach(AbstractAgent::postTick);
    }

    @SubscribeEvent
    public static void onPrePhysicsTick(PhysicsLevelTickEvent.Pre event) {
        levelAgents.computeIfAbsent(event.getLevel().getMcLevel(), k -> new ConcurrentHashMap<>()).values().forEach(AbstractAgent::prePhysicsTick);
    }

    @SubscribeEvent
    public static void onPostPhysicsTick(PhysicsLevelTickEvent.Post event) {
        levelAgents.computeIfAbsent(event.getLevel().getMcLevel(), k -> new ConcurrentHashMap<>()).values().forEach(AbstractAgent::postPhysicsTick);
    }
}
