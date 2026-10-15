package dev.alan.mineworld.city;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The fixed plan of a monster city, in coordinates relative to the city centre (x/z) and the floor (y = 0 is the floor
 * layer). Pure so it can be unit tested; the structure pieces turn it into blocks and entities.
 */
public final class CityLayout {
    /** The city (wall included) covers -WALL_OUTER..WALL_OUTER on x and z; HALF bounds the structure box. */
    public static final int HALF = 56;
    /** Outer wall ring: |d| from WALL_INNER to WALL_OUTER. */
    public static final int WALL_OUTER = 55, WALL_INNER = 53, WALL_HEIGHT = 6;
    /** Gate on the north (-z) side: x in -GATE_HALF..GATE_HALF, y 1..GATE_HEIGHT. */
    public static final int GATE_HALF = 3, GATE_HEIGHT = 5;
    /** Dome: ceiling height above the floor is EDGE_HEIGHT at the edge and up to DOME_HEIGHT in the middle. */
    public static final int DOME_HEIGHT = 32, EDGE_HEIGHT = 8;
    private static final double DOME_RADIUS = 64.0;
    /** How deep the gate passage is dug into the rock outside the wall. */
    public static final int PASSAGE_LENGTH = 8;
    public static final int PLOT_SIZE = 16;
    private static final int[] PLOT_CENTRES = {-38, -18, 18, 38};
    public static final int HOUSE_VARIANTS = 6;

    private CityLayout() {}

    /** Ceiling height (blocks of air above the floor) at a column. */
    public static int ceiling(int dx, int dz) {
        double r = Math.sqrt((double) dx * dx + (double) dz * dz) / DOME_RADIUS;
        return EDGE_HEIGHT + (int) Math.round((DOME_HEIGHT - EDGE_HEIGHT) * Math.sqrt(Math.max(0.0, 1.0 - r * r)));
    }

    public static boolean inside(int dx, int dz) {
        return Math.max(Math.abs(dx), Math.abs(dz)) <= WALL_OUTER;
    }

    public static boolean isWall(int dx, int dz) {
        int d = Math.max(Math.abs(dx), Math.abs(dz));
        return d >= WALL_INNER && d <= WALL_OUTER;
    }

    public static boolean isGate(int dx, int y, int dz) {
        return dz <= -WALL_INNER && Math.abs(dx) <= GATE_HALF && y >= 1 && y <= GATE_HEIGHT;
    }

    /** Main streets: the north-south and east-west axes. */
    public static boolean isStreet(int dx, int dz) {
        return Math.abs(dx) <= GATE_HALF || Math.abs(dz) <= GATE_HALF;
    }

    /** A building placed with its minimum corner at (x, z) relative to the centre, on the floor. */
    public record Building(String template, int x, int z) {}

    /** The buildings of a city; houses and gardens vary with the random, the rest is fixed. */
    public static List<Building> buildings(Random random) {
        List<Building> out = new ArrayList<>();
        out.add(new Building("plaza", -9, -9));
        out.add(new Building("inn", -46, 10));
        out.add(new Building("guard_post", 10, -46));
        for (int px : PLOT_CENTRES) {
            for (int pz : PLOT_CENTRES) {
                if (pz == 18 && (px == -38 || px == -18)) continue; // inn
                if (px == 18 && pz == -38) continue; // guard post
                String template = random.nextInt(4) == 0 ? "garden" : "house_" + (1 + random.nextInt(HOUSE_VARIANTS));
                out.add(new Building(template, px - PLOT_SIZE / 2, pz - PLOT_SIZE / 2));
            }
        }
        return out;
    }

    public enum GuardKind { SKELETON, PIGLIN_MELEE, PIGLIN_RANGED }

    /** A guard post: where the guard spawns (y relative to the floor) and how far it may wander from there. */
    public record Guard(GuardKind kind, int x, int y, int z, int radius) {}

    /** Skeletons and ranged piglins on the wall top, melee and ranged piglins at the gate and along the inside of the wall. */
    public static List<Guard> guards() {
        List<Guard> out = new ArrayList<>();
        int top = WALL_HEIGHT + 1;
        int edge = WALL_INNER + 1;
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                out.add(new Guard(GuardKind.SKELETON, sx * edge, top, sz * edge, 12));
            }
        }
        out.add(new Guard(GuardKind.PIGLIN_RANGED, 0, top, edge, 12));
        out.add(new Guard(GuardKind.PIGLIN_RANGED, edge, top, 0, 12));
        out.add(new Guard(GuardKind.PIGLIN_RANGED, -edge, top, 0, 12));
        out.add(new Guard(GuardKind.PIGLIN_RANGED, -8, top, -edge, 6));
        out.add(new Guard(GuardKind.PIGLIN_RANGED, 8, top, -edge, 6));
        int inner = WALL_INNER - 3;
        out.add(new Guard(GuardKind.PIGLIN_MELEE, -4, 1, -inner, 6));
        out.add(new Guard(GuardKind.PIGLIN_MELEE, 4, 1, -inner, 6));
        out.add(new Guard(GuardKind.PIGLIN_MELEE, -inner, 1, 0, 16));
        out.add(new Guard(GuardKind.PIGLIN_MELEE, inner, 1, 0, 16));
        out.add(new Guard(GuardKind.PIGLIN_MELEE, 0, 1, inner, 16));
        return out;
    }
}
