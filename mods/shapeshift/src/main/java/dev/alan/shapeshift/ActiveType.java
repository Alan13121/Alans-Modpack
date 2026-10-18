package dev.alan.shapeshift;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** What pressing the ability key (R) does. Defaults can be changed per form with "cooldown" and "power". */
public enum ActiveType implements StringRepresentable {
    /** Creeper: fuse, then an explosion that never hurts the user, who turns back into a human. power = radius. */
    EXPLODE(0, 3),
    /** Blaze: small fireball. */
    SMALL_FIREBALL(6, 0),
    /** Ghast: exploding fireball. power = explosion size. */
    FIREBALL(20, 1),
    /** Enderman: teleport to the block you are looking at. power = range in blocks. */
    TELEPORT(20, 32),
    /** Llama: spit. */
    SPIT(10, 0),
    /** Skeleton: free arrow (cannot be picked up). power = speed, 3 = fully drawn bow. */
    ARROW(10, 3),
    /** Breeze: wind charge. */
    WIND_CHARGE(10, 0),
    /** Snow golem: snowball. */
    SNOWBALL(4, 0),
    /** Evoker: a line of fangs. power = number of fangs. */
    FANGS(40, 12),
    /** Phantom: a rush along the look direction; everything on the way is hit and thrown aside. power = speed. */
    DIVE(60, 1.6f),
    /** Allay: heals you and the players and animals around you. power = hearts-worth of health (half hearts), radius is 6. */
    HEAL_PULSE(100, 4),
    /** Frog: pulls the creature or item you look at toward you. power = range in blocks. */
    TONGUE(40, 12),
    /** Camel: a flat rush that shoves aside whatever is in the way, without hurting it. power = speed. */
    DASH(60, 1.4f),
    /** Polar bear: a ground slam that hurts, shoves and slows everything around you. power = radius in blocks. */
    SLAM(80, 4),
    /** Turtle: withdraw into the shell, taking much less damage but barely able to move. power = seconds. */
    SHELL(160, 4),
    /** Wither skeleton: a wither skull. */
    WITHER_SKULL(40, 0),
    /** Happy ghast: hostile mobs around you forget about you for a while. power = seconds; range is 16. */
    SOOTHE(200, 10),
    /** Ocelot: a long, low leap forward. power = speed. */
    POUNCE(40, 1.1f),
    /** Parrot: a song that speeds up and hastens you and the players within 8 blocks. power = seconds. */
    SING(200, 10),
    /** Skeleton horse: a burst of speed and jump height for yourself. power = seconds. */
    GALLOP(100, 5),
    /** Zombie horse: stamps everything in a wide cone in front: hurts and throws it back. power = range in blocks. */
    TRAMPLE(60, 6),
    /** Glow squid: an ink cloud that blinds and lights up every creature around. power = radius in blocks. */
    INK_CLOUD(120, 8),
    /** Tadpole: puts out fire around you and lets you and the players near you breathe underwater. power = seconds. */
    SPLASH(100, 8),
    /** Tropical fish: a stream of bubbles that pushes everything in a line away without hurting it. power = length in blocks. */
    BUBBLES(40, 8),
    /** Glow bat: a flash that makes every creature around you glow, and lets you see in the dark. power = radius in blocks. */
    FLASH(120, 16),
    /** Gem slime: a high bounce that knocks back whatever is next to you. power = launch speed. */
    BOUNCE(60, 0.9f),
    /** Ore worm: digs a tunnel two blocks high straight ahead through stone, dirt and ore. power = length in blocks. */
    TUNNEL(30, 6);

    public static final Codec<ActiveType> CODEC = StringRepresentable.fromEnum(ActiveType::values);
    public final int defaultCooldown;
    public final float defaultPower;
    ActiveType(int defaultCooldown, float defaultPower) {
        this.defaultCooldown = defaultCooldown;
        this.defaultPower = defaultPower;
    }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    public String translationKey() { return "shapeshift.active." + getSerializedName(); }
}
