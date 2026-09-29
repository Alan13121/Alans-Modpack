package dev.alan.shapeshift;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;

/** Looks up which entity types can be used as forms and their base stats. Works on both sides. */
public final class Forms {
    private Forms() {}
    public static String id(EntityType<?> type) { return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(); }
    public static Optional<EntityType<?>> type(String id) {
        var parsed = Identifier.tryParse(id);
        return parsed == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(parsed);
    }
    /** Living, non-player entities with attributes. The config blocklist is checked separately on the server. */
    public static boolean isLivingForm(EntityType<?> type) {
        return type != EntityTypes.PLAYER && DefaultAttributes.hasSupplier(type);
    }
    public static EntityDimensions dimensions(EntityType<?> type) { return type.getDimensions(); }
    /** The form a player currently has, or empty when human. */
    public static Optional<EntityType<?>> current(Player player) {
        String id = player.getAttached(ShapeshiftMod.FORM);
        return id == null ? Optional.empty() : type(id).filter(Forms::isLivingForm);
    }
}
