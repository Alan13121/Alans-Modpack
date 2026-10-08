package dev.alan.shapeshift.api;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Lets other mods (the combat mod, for one) tune a form's R-key ability without shapeshift knowing about them.
 * Register from your mod initialiser; every hook runs on the server thread.
 */
public final class SkillHooks {
    /** How strong and how slow-to-recharge an ability is for one player. 1 means unchanged. */
    public record Scale(float power, float cooldown) {
        public static final Scale NONE = new Scale(1f, 1f);
        public Scale times(Scale other) { return new Scale(power * other.power, cooldown * other.cooldown); }
    }

    @FunctionalInterface public interface Scaler { Scale scale(ServerPlayer player); }
    /** Called for every projectile an ability launches, just before it enters the world. */
    @FunctionalInterface public interface Launch { void beforeLaunch(ServerPlayer player, Entity projectile); }

    /** Decides whether one attribute modifier of a form is applied to this player; return false to skip it. */
    @FunctionalInterface public interface ModifierFilter {
        boolean keep(ServerPlayer player, String attributeId, double amount, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation);
    }

    private static final List<ModifierFilter> FILTERS = new ArrayList<>();
    private static final List<Scaler> SCALERS = new ArrayList<>();
    private static final List<Launch> LAUNCHES = new ArrayList<>();

    private SkillHooks() {}

    public static void registerScaler(Scaler scaler) { SCALERS.add(scaler); }
    public static void registerLaunch(Launch launch) { LAUNCHES.add(launch); }
    public static void registerModifierFilter(ModifierFilter filter) { FILTERS.add(filter); }

    /** False when any registered filter drops this modifier. */
    public static boolean keepModifier(ServerPlayer player, String attributeId, double amount,
                                       net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation) {
        for (ModifierFilter filter : FILTERS) if (!filter.keep(player, attributeId, amount, operation)) return false;
        return true;
    }

    /** Product of all registered scalers for this player. */
    public static Scale scaleFor(ServerPlayer player) {
        Scale total = Scale.NONE;
        for (Scaler scaler : SCALERS) total = total.times(scaler.scale(player));
        return total;
    }

    public static void launched(ServerPlayer player, Entity projectile) {
        for (Launch launch : LAUNCHES) launch.beforeLaunch(player, projectile);
    }

    /** Cooldown in ticks after scaling; a scaled cooldown never rounds down to nothing unless it was nothing or the scale is zero. */
    public static int scaledCooldown(int ticks, Scale scale) {
        if (ticks <= 0 || scale.cooldown() <= 0f) return 0;
        return Math.max(1, Math.round(ticks * Math.max(0f, scale.cooldown())));
    }
}
