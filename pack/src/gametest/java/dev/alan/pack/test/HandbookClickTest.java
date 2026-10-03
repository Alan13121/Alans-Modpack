package dev.alan.pack.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** The handbook's chapter list answers a mouse click. */
public final class HandbookClickTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc -> dev.alan.guide.GuideMod.openHandbook.run());
            context.waitTicks(5);
            // Row 3 of the list: x inside the side panel, y = top + 8 + 2 * 22 + 11 (GUI units), see HandbookScreen.
            double[] cursor = context.computeOnClient(mc -> {
                double scale = mc.getWindow().getGuiScale();
                int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
                int panelH = Math.min(h - 40, 250), panelW = Math.min(w - 16, 400);
                int left = (w - panelW) / 2, top = Math.max(4, (h - panelH - 24) / 2);
                return new double[] {(left + 50) * scale, (top + 8 + 2 * 22 + 11) * scale};
            });
            context.takeScreenshot("handbook-before");
            context.getInput().setCursorPos(cursor[0], cursor[1]);
            context.waitTicks(2);
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            context.takeScreenshot("handbook-after");
            int selected = context.computeOnClient(mc -> {
                try {
                    var f = Class.forName("dev.alan.guide.client.HandbookScreen").getDeclaredField("selected");
                    f.setAccessible(true);
                    return f.getInt(null);
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            });
            System.out.println("[handbook-test] selected=" + selected + " screen=" + context.computeOnClient(mc -> String.valueOf(mc.gui.screen())));
            if (selected != 2) throw new AssertionError("FAILED: clicking row 3 selected " + selected);
            // The arrow keys use the game's own key codes, not GLFW's.
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT);
            context.waitTicks(2);
            int next = context.computeOnClient(mc -> {
                try {
                    var f = Class.forName("dev.alan.guide.client.HandbookScreen").getDeclaredField("selected");
                    f.setAccessible(true);
                    return f.getInt(null);
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            });
            if (next != 3) throw new AssertionError("FAILED: the right arrow went to " + next);
        }
    }
}
