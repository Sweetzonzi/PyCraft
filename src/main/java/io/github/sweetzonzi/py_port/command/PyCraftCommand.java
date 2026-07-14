package io.github.sweetzonzi.py_port.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.common.agent.AlgorithmAgent;
import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import io.github.sweetzonzi.py_port.common.shop.ShopEventHandler;
import io.github.sweetzonzi.py_port.network.java.payload.OpenShopPayload;
import io.github.sweetzonzi.py_port.network.java.payload.OpenTeachScreenPayload;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class PyCraftCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();

        dispatcher.register(Commands.literal("pycraft")
                // /pycraft list — 列出所有 agent
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            var source = ctx.getSource();
                            var server = source.getServer();
                            boolean found = false;
                            for (ServerLevel level : server.getAllLevels()) {
                                for (AbstractAgent a : AgentManager.getLevelAgents(level)) {
                                    source.sendSuccess(() ->
                                                    Component.literal(" §7[" + a.getId() + "] §f" + a.getAgentType()),
                                            false);
                                    found = true;
                                }
                            }
                            if (!found) {
                                source.sendSuccess(() -> Component.literal("§e没有活跃的 agent"), false);
                            }
                            return 1;
                        }))
                // /pycraft remove <id> — 删除指定 agent
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                    var source = ctx.getSource();
                                    var server = source.getServer();

                                    for (ServerLevel level : server.getAllLevels()) {
                                        AbstractAgent a = AgentManager.getAgent(level, id);
                                        if (a != null) {
                                            a.removeFromLevel();
                                            source.sendSuccess(() ->
                                                            Component.literal("§a已删除 agent #" + id + " (" + a.getAgentType() + ")"),
                                                    true);
                                            return 1;
                                        }
                                    }
                                    source.sendFailure(Component.literal("§c未找到 agent #" + id));
                                    return 0;
                                })))
                // /pycraft teach — 打开算法教学界面
                .then(Commands.literal("teach")
                        .executes(ctx -> {
                            var source = ctx.getSource();
                            var player = source.getPlayer();
                            if (player == null) {
                                source.sendFailure(Component.literal("§c该命令只能由玩家执行"));
                                return 0;
                            }

                            boolean found = false;
                            Vec3 playerPos = player.position();
                            for (AbstractAgent agent : AgentManager.getLevelAgents(player.serverLevel())) {
                                if (agent instanceof AlgorithmAgent) {
                                    com.jme3.math.Vector3f agentPos = agent.getTransform().getTranslation();
                                    double dx = agentPos.x - playerPos.x();
                                    double dy = agentPos.y - playerPos.y();
                                    double dz = agentPos.z - playerPos.z();
                                    if (Math.sqrt(dx * dx + dy * dy + dz * dz) < 5.0) {
                                        found = true;
                                        break;
                                    }
                                }
                            }

                            if (!found) {
                                source.sendFailure(Component.literal("§c附近没有算法教学智能体"));
                                return 0;
                            }

                            PacketDistributor.sendToPlayer(player, new OpenTeachScreenPayload());
                            source.sendSuccess(() -> Component.literal("§a打开算法百科..."), false);
                            return 1;
                        }))
                // /pycraft shop — 打开武器商店
                .then(Commands.literal("shop")
                        .executes(PyCraftCommand::executeShop)
                        .then(Commands.literal("balance")
                                .executes(PyCraftCommand::executeShopBalance))
                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 99999))
                                                .executes(PyCraftCommand::executeShopSet)))))

                // /pycraft find <block> [radius]: 让最近的 AlgorithmAgent 开始搜寻采集
                .then(Commands.literal("find")
                        .then(Commands.argument("block", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                .executes(ctx -> executeFind(ctx, 20)) // 默认半径 20
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                                        .executes(ctx -> executeFind(ctx,
                                                IntegerArgumentType.getInteger(ctx, "radius"))))))
                // /pycraft ai stop — 停止当前任务
                .then(Commands.literal("ai")
                        .then(Commands.literal("stop")
                                .executes(PyCraftCommand::executeAiStop))
                        // /pycraft ai status — 查看状态
                        .then(Commands.literal("status")
                                .executes(PyCraftCommand::executeAiStatus))
                        // /pycraft ai value <block> <weight> — 设置方块价值权重
                        .then(Commands.literal("value")
                                .then(Commands.argument("block", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                        .then(Commands.argument("weight", IntegerArgumentType.integer(1, 1000))
                                                .executes(PyCraftCommand::executeAiValue)))))
        );
    }

    // 命令执行

    /** /pycraft shop — 打开武器商店 */
    private static int executeShop(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令只能由玩家执行"));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, new OpenShopPayload());
        source.sendSuccess(() -> Component.literal("§a打开武器商店..."), false);
        return 1;
    }

    /** /pycraft shop balance — 查看金币余额 */
    private static int executeShopBalance(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令只能由玩家执行"));
            return 0;
        }
        int gold = ShopEventHandler.getGold(player);
        source.sendSuccess(() -> Component.literal("§6当前金币: §e" + gold + " G"), false);
        return 1;
    }

    /** /pycraft shop set <player> <amount> — 设置玩家金币（OP） */
    private static int executeShopSet(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "target");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        var server = source.getServer();
        var targetPlayer = server.getPlayerList().getPlayerByName(targetName);
        if (targetPlayer == null) {
            source.sendFailure(Component.literal("§c玩家 " + targetName + " 不在线"));
            return 0;
        }

        ShopEventHandler.setGold(targetPlayer, amount);
        source.sendSuccess(() -> Component.literal("§a已设置 " + targetName + " 的金币为 " + amount + " G"), true);
        targetPlayer.sendSystemMessage(Component.literal("§e你的金币已被设置为 " + amount + " G"));
        return 1;
    }

    /** /pycraft find <block> [radius] */
    private static int executeFind(CommandContext<CommandSourceStack> ctx, int radius) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令只能由玩家执行"));
            return 0;
        }

        Holder<Block> blockHolder = ctx.getArgument("block", Holder.class);
        Block targetBlock = blockHolder.value();

        // 找最近的 AlgorithmAgent
        AlgorithmAgent nearest = null;
        double nearestDist = Double.MAX_VALUE;
        Vec3 playerPos = player.position();

        for (AbstractAgent agent : AgentManager.getLevelAgents(player.serverLevel())) {
            if (agent instanceof AlgorithmAgent algo) {
                com.jme3.math.Vector3f agentPos = algo.getTransform().getTranslation();
                double dx = agentPos.x - playerPos.x();
                double dy = agentPos.y - playerPos.y();
                double dz = agentPos.z - playerPos.z();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = algo;
                }
            }
        }

        if (nearest == null) {
            source.sendFailure(Component.literal("§c当前世界没有 AlgorithmAgent"));
            return 0;
        }

        if (nearestDist > 30) {
            source.sendFailure(Component.literal("§c最近的 AlgorithmAgent 太远了（距离 " +
                    String.format("%.1f", nearestDist) + " 格）"));
            return 0;
        }

        AlgorithmAgent finalNearest = nearest;
        String blockName = Component.translatable(targetBlock.getDescriptionId()).getString();
        finalNearest.startFinding(targetBlock, radius);
        source.sendSuccess(() -> Component.literal("§aAgent #" + finalNearest.getId()
                + " 开始采集 " + blockName + "（半径 " + radius + " 格）"), true);
        return 1;
    }

    /** /pycraft ai stop */
    private static int executeAiStop(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令只能由玩家执行"));
            return 0;
        }

        AlgorithmAgent target = findNearestAlgo(player);
        if (target == null) {
            source.sendFailure(Component.literal("§c附近没有 AlgorithmAgent"));
            return 0;
        }

        target.stopTask();
        source.sendSuccess(() -> Component.literal("§aAgent #" + target.getId() + " 已停止任务"), true);
        return 1;
    }

    /** /pycraft ai status */
    private static int executeAiStatus(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令只能由玩家执行"));
            return 0;
        }

        AlgorithmAgent target = findNearestAlgo(player);
        if (target == null) {
            source.sendFailure(Component.literal("§c附近没有 AlgorithmAgent"));
            return 0;
        }

        String status = target.getTaskStatus();
        List<ItemStack> inventory = target.getInventory();
        int totalItems = inventory.stream().mapToInt(ItemStack::getCount).sum();

        source.sendSuccess(() -> Component.literal("§6=== Agent #" + target.getId() + " 状态 ==="), false);
        source.sendSuccess(() -> Component.literal(" §7状态: §f" + status), false);
        source.sendSuccess(() -> Component.literal(" §7背包: §f" + totalItems + " 个物品"), false);

        if (!inventory.isEmpty()) {
            for (ItemStack stack : inventory) {
                source.sendSuccess(() -> Component.literal("   §8- §7" + stack.getHoverName().getString()
                        + " x" + stack.getCount()), false);
            }
        }
        return 1;
    }

    /** /pycraft ai value <block> <weight> */
    private static int executeAiValue(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令只能由玩家执行"));
            return 0;
        }

        Holder<Block> blockHolder = ctx.getArgument("block", Holder.class);
        Block block = blockHolder.value();
        int weight = IntegerArgumentType.getInteger(ctx, "weight");

        AlgorithmAgent target = findNearestAlgo(player);
        if (target == null) {
            source.sendFailure(Component.literal("§c附近没有 AlgorithmAgent"));
            return 0;
        }

        target.setBlockValue(block, weight);
        source.sendSuccess(() -> Component.literal("§a已设置 " + Component.translatable(block.getDescriptionId()).getString()
                + " 价值权重为 " + weight), true);
        return 1;
    }

    // ========== 辅助方法 ==========

    /** 找到最近的 AlgorithmAgent（30 格内） */
    private static AlgorithmAgent findNearestAlgo(ServerPlayer player) {
        AlgorithmAgent nearest = null;
        double nearestDist = 30.0; // 最大搜索距离
        Vec3 playerPos = player.position();

        for (AbstractAgent agent : AgentManager.getLevelAgents(player.serverLevel())) {
            if (agent instanceof AlgorithmAgent algo) {
                com.jme3.math.Vector3f agentPos = algo.getTransform().getTranslation();
                double dx = agentPos.x - playerPos.x();
                double dy = agentPos.y - playerPos.y();
                double dz = agentPos.z - playerPos.z();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = algo;
                }
            }
        }
        return nearest;
    }
}