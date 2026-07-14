package io.github.sweetzonzi.py_port.common.shop;

import io.github.sweetzonzi.py_port.PyCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class ShopEventHandler {

    private static final String GOLD_OBJECTIVE = "py_gold";

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            getOrCreateObjective(player);
            int gold = getGold(player);
            if (gold == 0 && !hasInitialGold(player)) {
                setInitialGold(player);
            }
        }
    }

    public static Objective getOrCreateObjective(ServerPlayer player) {
        Scoreboard sb = player.getScoreboard();
        Objective obj = sb.getObjective(GOLD_OBJECTIVE);
        if (obj == null) {
            obj = sb.addObjective(
                    GOLD_OBJECTIVE,
                    ObjectiveCriteria.DUMMY,
                    net.minecraft.network.chat.Component.literal("Gold Coins"),
                    ObjectiveCriteria.RenderType.INTEGER,
                    false,
                    null
            );
        }
        return obj;
    }

    public static int getGold(ServerPlayer player) {
        Objective obj = getOrCreateObjective(player);
        return player.getScoreboard()
                .getOrCreatePlayerScore(player, obj)
                .get();
    }

    public static void setGold(ServerPlayer player, int amount) {
        Objective obj = getOrCreateObjective(player);
        ScoreAccess score = player.getScoreboard()
                .getOrCreatePlayerScore(player, obj);
        score.set(Math.max(0, amount));
    }

    public static boolean deductGold(ServerPlayer player, int amount) {
        int current = getGold(player);
        if (current < amount) {
            return false;
        }
        setGold(player, current - amount);
        return true;
    }

    private static boolean hasInitialGold(ServerPlayer player) {
        return player.getPersistentData().getBoolean("py_gold_initialized");
    }

    private static void setInitialGold(ServerPlayer player) {
        player.getPersistentData().putBoolean("py_gold_initialized", true);
        setGold(player, 10000);
    }
}