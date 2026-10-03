package dev.alan.combat.boss;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Operator commands, mostly for testing: /formking summon|abort. */
public final class FormKingCommands {
    private FormKingCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("formking")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("summon").executes(c -> {
                var player = c.getSource().getPlayerOrException();
                if (!FormKingFights.forceSummon(player.level(), BlockPos.containing(player.position()))) {
                    c.getSource().sendFailure(Component.translatable("combat.boss.busy"));
                    return 0;
                }
                return 1;
            }))
            .then(Commands.literal("abort").executes(c -> {
                int n = FormKingFights.abortAll();
                c.getSource().sendSuccess(() -> Component.translatable("combat.command.aborted", n), false);
                return n;
            })));
    }
}
