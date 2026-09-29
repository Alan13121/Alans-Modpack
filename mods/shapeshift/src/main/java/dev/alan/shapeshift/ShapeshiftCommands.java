package dev.alan.shapeshift;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

/** Operator commands, mostly for testing: /shapeshift unlock|unlockall|reset|into. */
final class ShapeshiftCommands {
    private ShapeshiftCommands() {}
    static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("shapeshift")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("unlock")
                .then(Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                    .suggests(SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES))
                    .executes(c -> {
                        var type = ResourceArgument.getSummonableEntityType(c, "entity").value();
                        var player = c.getSource().getPlayerOrException();
                        if (!Shapeshifter.unlock(player, type)) {
                            c.getSource().sendFailure(Component.translatable("shapeshift.command.unlock_failed", type.getDescription()));
                            return 0;
                        }
                        c.getSource().sendSuccess(() -> Component.translatable("shapeshift.unlocked", type.getDescription()), false);
                        return 1;
                    })))
            .then(Commands.literal("unlockall").executes(c -> {
                var player = c.getSource().getPlayerOrException();
                int count = 0;
                for (var type : BuiltInRegistries.ENTITY_TYPE) if (Shapeshifter.unlock(player, type)) count++;
                int added = count;
                c.getSource().sendSuccess(() -> Component.translatable("shapeshift.command.unlocked_all", added), false);
                return count;
            }))
            .then(Commands.literal("reset").executes(c -> {
                var player = c.getSource().getPlayerOrException();
                Shapeshifter.clearStats(player);
                player.setAttached(ShapeshiftMod.UNLOCKS, Unlocks.EMPTY);
                c.getSource().sendSuccess(() -> Component.translatable("shapeshift.command.reset"), false);
                return 1;
            }))
            .then(Commands.literal("into")
                .then(Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                    .suggests(SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES))
                    .executes(c -> {
                        var type = ResourceArgument.getSummonableEntityType(c, "entity").value();
                        if (!Forms.isLivingForm(type)) {
                            c.getSource().sendFailure(Component.translatable("shapeshift.locked"));
                            return 0;
                        }
                        return Shapeshifter.apply(c.getSource().getPlayerOrException(), type, false) ? 1 : 0;
                    })))
            .then(Commands.literal("human").executes(c ->
                Shapeshifter.apply(c.getSource().getPlayerOrException(), null, false) ? 1 : 0)));
    }
}
