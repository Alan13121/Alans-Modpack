package dev.alan.guide;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuideMod implements ModInitializer {
    public static final String MOD_ID = "guide";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(MOD_ID, name); }
    @Override public void onInitialize() {
        LOG.info("Guide loaded");
    }
}
