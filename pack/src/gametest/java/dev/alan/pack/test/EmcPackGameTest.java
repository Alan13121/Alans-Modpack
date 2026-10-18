package dev.alan.pack.test;

import dev.alchemy.EnergyValues;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * With every mod loaded, every item still has an EMC value and the explicit values leave no recipe loop or energy-making recipe.
 * Items that fell back to the default are printed so a new item without a value is noticed.
 */
public final class EmcPackGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            var base = server.computeOnServer(s -> EnergyValues.baseItems().stream().sorted().toList());
            var loops = server.computeOnServer(s -> EnergyValues.loopItems().stream().sorted().toList());
            var profitable = server.computeOnServer(s -> EnergyValues.profitable());
            System.out.println("EMC PACK: " + base.size() + " items fell back to the default value: " + base);
            if (!loops.isEmpty()) throw new AssertionError("EMC recipe loops with no listed value: " + loops);
            if (!profitable.isEmpty()) throw new AssertionError("Recipes that make energy: " + profitable);
        }
    }
}
