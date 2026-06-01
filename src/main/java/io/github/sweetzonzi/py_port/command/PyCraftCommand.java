package io.github.sweetzonzi.py_port.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.sweetzonzi.py_port.common.agent.AbstractAgent;
import io.github.sweetzonzi.py_port.common.agent.AgentManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class PyCraftCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

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
        );
    }
}
