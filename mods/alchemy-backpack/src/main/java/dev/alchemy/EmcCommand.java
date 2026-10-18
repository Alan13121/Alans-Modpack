package dev.alchemy;

import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Operator command: /emc dump writes every item's value, and the base items that fell back to the default, to a text file. */
final class EmcCommand {
    private EmcCommand() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("emc")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("dump").executes(c -> {
                Path file = FabricLoader.getInstance().getGameDir().resolve("emc-dump.txt");
                try {
                    Files.write(file, lines());
                } catch (IOException e) {
                    c.getSource().sendFailure(Component.translatable("alchemy.dump.failed", e.getMessage()));
                    return 0;
                }
                int base = EnergyValues.baseItems().size();
                c.getSource().sendSuccess(() -> Component.translatable("alchemy.dump.done", base, file.toString()), false);
                return base;
            })));
    }

    private static List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("# Base items: nothing can craft them and the config does not list them; each is worth " + EnergyValues.defaultValue() + " EMC.");
        out.add("# List an item in config/alchemy-backpack-energy.json to give it its own value, then restart.");
        out.addAll(EnergyValues.baseItems().stream().sorted().toList());
        out.add("");
        out.add("# Loop items: their only recipes need themselves (ingot <-> block); they got the default value, so list them in the config.");
        out.addAll(EnergyValues.loopItems().stream().sorted().toList());
        out.add("");
        out.add("# Profitable recipes: crafting makes energy (the result is worth more than its ingredients); lower the result's value or raise an ingredient's.");
        out.addAll(EnergyValues.profitable());
        out.add("");
        out.add("# All values (id = EMC; 0 cannot be converted)");
        EnergyValues.values().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
            .forEach(e -> out.add(e.getKey() + " = " + e.getValue()));
        return out;
    }
}
