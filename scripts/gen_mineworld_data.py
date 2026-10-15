#!/usr/bin/env python3
"""Generates the worldgen JSON and block textures of the mineworld mod (no libraries needed).
Ore counts are vanilla per-chunk vein counts times ORE_MULTIPLIER, spread over the whole world height.
Cave chambers come from the noise router; each chamber belongs to one of the CAVE_BIOMES ecosystems.
Textures are only written when missing; use --force to regenerate them."""
import json, os, random, shutil, struct, sys, zlib

ORE_MULTIPLIER = 8
ROOT = os.path.join(os.path.dirname(__file__), "..", "mods", "mineworld", "src", "main", "resources")
DATA = os.path.join(ROOT, "data", "mineworld")
ASSETS = os.path.join(ROOT, "assets", "mineworld")

# (name, vanilla configured feature, vanilla veins per chunk)
ORES = [
    ("coal", "minecraft:ore_coal", 30), ("coal_buried", "minecraft:ore_coal_buried", 20),
    ("iron", "minecraft:ore_iron", 20), ("iron_small", "minecraft:ore_iron_small", 10),
    ("copper", "minecraft:ore_copper_small", 16), ("copper_large", "minecraft:ore_copper_large", 4),
    ("gold", "minecraft:ore_gold_buried", 5),
    ("redstone", "minecraft:ore_redstone", 12),
    ("lapis", "minecraft:ore_lapis", 2), ("lapis_buried", "minecraft:ore_lapis_buried", 4),
    ("diamond", "minecraft:ore_diamond_small", 7), ("diamond_medium", "minecraft:ore_diamond_medium", 2),
    ("diamond_buried", "minecraft:ore_diamond_buried", 4), ("diamond_large", "minecraft:ore_diamond_large", 1),
    ("emerald", "minecraft:ore_emerald", 10),
]
# Stone variety, generated before the ores (not multiplied).
BLOBS = [("granite", "minecraft:ore_granite", 20), ("diorite", "minecraft:ore_diorite", 20),
         ("andesite", "minecraft:ore_andesite", 20), ("tuff", "minecraft:ore_tuff", 10)]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


HEIGHT = {"type": "minecraft:uniform", "min_inclusive": {"absolute": -64}, "max_inclusive": {"absolute": 319}}

# Sparse cave chambers: air where the cave_cheese noise (std ~1/3) rises above CAVE_THRESHOLD.
CAVE_THRESHOLD = 0.28
CAVE_XZ_SCALE, CAVE_Y_SCALE = 2.0, 3.2
# Solid sphere around the spawn room so no chamber cuts into it.
SPAWN_POINT, SPAWN_RADIUS = [0, 129, 0], 20

# Cave ecosystems, picked by two climate noises: (temperature, humidity) of each biome.
CAVE_BIOMES = {"crystal_cave": (-0.3, -0.3), "lush_cave": (-0.3, 0.3),
               "dripstone_cave": (0.3, -0.3), "ore_nest": (0.3, 0.3)}

MONSTERS = [("spider", 100, 4), ("zombie", 95, 4), ("zombie_villager", 5, 1), ("skeleton", 100, 4),
            ("creeper", 100, 4), ("enderman", 10, 2), ("witch", 5, 1)]


def placed(feature, count):
    return {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": HEIGHT},
        {"type": "minecraft:biome"}]}


def scanned(feature, count, direction, target, offset):
    """Like vanilla lush/dripstone placements: scan up or down from a random air block to a surface."""
    return {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": HEIGHT},
        {"type": "minecraft:environment_scan", "direction_of_search": direction, "target_condition": target,
         "allowed_search_condition": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}, "max_steps": 12},
        {"type": "minecraft:offset", "x": 0, "y": offset, "z": 0},
        {"type": "minecraft:biome"}]}


SOLID = {"type": "minecraft:solid"}
STURDY_BELOW = {"type": "minecraft:has_sturdy_face", "direction": "down"}

# Extra features in generation order: (step, name, placed feature, biomes). Every biome must list shared
# features in the same order, so one global list is filtered per biome.
EXTRA = [
    (2, "amethyst_geode", placed("minecraft:amethyst_geode", 1), ["crystal_cave"]),
    (2, "large_dripstone", placed("minecraft:large_dripstone", 30), ["dripstone_cave"]),
    (3, "monster_room", placed("minecraft:monster_room", 8), ["ore_nest"]),
    (6, "ore_clay", placed("minecraft:ore_clay", 46), ["lush_cave"]),
    (7, "dripstone_cluster", placed("minecraft:dripstone_cluster", 72), ["dripstone_cave"]),
    (7, "pointed_dripstone", placed("minecraft:pointed_dripstone", 224), ["dripstone_cave"]),
    (9, "glow_lichen", placed("minecraft:glow_lichen", 200), ["crystal_cave"]),
    (9, "moss_ceiling", scanned("minecraft:moss_patch_ceiling", 250, "up", SOLID, -1), ["lush_cave"]),
    (9, "cave_vines", scanned("minecraft:cave_vine", 376, "up", STURDY_BELOW, -1), ["lush_cave"]),
    (9, "lush_clay", scanned("minecraft:lush_caves_clay", 124, "down", SOLID, 1), ["lush_cave"]),
    (9, "moss_floor", scanned("minecraft:moss_patch", 250, "down", SOLID, 1), ["lush_cave"]),
    (9, "rooted_azalea", scanned("minecraft:rooted_azalea_tree", 3, "up", SOLID, -1), ["lush_cave"]),
    (9, "spore_blossom", scanned("minecraft:spore_blossom", 50, "up", SOLID, -1), ["lush_cave"]),
]


def spawns(**categories):
    cats = {c: [] for c in ["ambient", "axolotls", "creature", "misc", "monster",
                            "underground_water_creature", "water_ambient", "water_creature"]}
    for cat, entries in categories.items():
        cats[cat] = [{"type": f"minecraft:{t}", "count": n, "weight": w} for t, w, n in entries]
    return {"argument": {"spawn_costs": {}, "spawns_by_category": cats}, "modifier": "overlay"}


BIOME_SPAWNS = {
    "crystal_cave": spawns(ambient=[("bat", 10, 8)], monster=MONSTERS),
    "lush_cave": spawns(ambient=[("bat", 10, 8)], axolotls=[("axolotl", 10, 4)], monster=MONSTERS),
    "dripstone_cave": spawns(ambient=[("bat", 10, 8)], monster=MONSTERS + [("cave_spider", 60, 2)]),
    "ore_nest": spawns(ambient=[("bat", 10, 8)], monster=MONSTERS + [("cave_spider", 30, 2), ("witch", 20, 1)]),
}


def biome(features, spawn_attr):
    steps = [[] for _ in range(11)]
    for step, name in features:
        steps[step].append(f"mineworld:{name}")
    return {"attributes": {"minecraft:gameplay/natural_mob_spawns": spawn_attr,
                           "minecraft:visual/sky_color": "#000000"},
            "carvers": [], "downfall": 0.0, "effects": {"water_color": "#3f76e4"},
            "features": steps, "has_precipitation": False, "temperature": 0.5}


def noise(name, xz, y):
    return {"type": "minecraft:noise", "noise": name, "xz_scale": xz, "y_scale": y}


def worldgen():
    ores = []
    for name, feature, count in BLOBS:
        write(f"{DATA}/worldgen/placed_feature/{name}.json", placed(feature, count))
        ores.append((6, name))
    for name, feature, count in ORES:
        write(f"{DATA}/worldgen/placed_feature/ore_{name}.json", placed(feature, count * ORE_MULTIPLIER))
        ores.append((6, f"ore_{name}"))
    # Ore nests get every ore a second time.
    rich = []
    for name, feature, count in ORES:
        write(f"{DATA}/worldgen/placed_feature/ore_rich_{name}.json", placed(feature, count * ORE_MULTIPLIER))
        rich.append((6, f"ore_rich_{name}"))
    for _, name, feature, _ in EXTRA:
        write(f"{DATA}/worldgen/placed_feature/{name}.json", feature)

    # Old worlds keep their already generated chunks in the single "mine" biome.
    write(f"{DATA}/worldgen/biome/mine.json", biome(ores, spawns()))
    for b in CAVE_BIOMES:
        features = ores + (rich if b == "ore_nest" else []) + [(s, n) for s, n, _, bs in EXTRA if b in bs]
        features.sort(key=lambda f: f[0])  # stable: keeps the global order inside each step
        write(f"{DATA}/worldgen/biome/{b}.json", biome(features, BIOME_SPAWNS[b]))

    for n in ("cave_kind_a", "cave_kind_b"):
        write(f"{DATA}/worldgen/noise/{n}.json", {"base_octave": -7, "octave_count": 2})
    caves = {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 4, "input": {
        "type": "minecraft:sub", "left": CAVE_THRESHOLD,
        "right": noise("minecraft:cave_cheese", CAVE_XZ_SCALE, CAVE_Y_SCALE)}}
    spawn_shield = {"type": "minecraft:sub", "left": 1.0, "right": {"type": "minecraft:mul",
        "left": {"type": "minecraft:distance_to_point", "point": SPAWN_POINT, "metric": "euclidean"},
        "right": 1.0 / SPAWN_RADIUS}}
    # Solid bands at the bottom and top so chambers never break through the bedrock floor and roof.
    floor = {"type": "minecraft:gradient", "axis": "y", "from_coordinate": -64, "to_coordinate": -56, "from_value": 1.0, "to_value": -1.0}
    roof = {"type": "minecraft:gradient", "axis": "y", "from_coordinate": 311, "to_coordinate": 319, "from_value": -1.0, "to_value": 1.0}
    density = caves
    for part in (spawn_shield, floor, roof):
        density = {"type": "minecraft:max", "left": density, "right": part}
    zero = 0.0
    write(f"{DATA}/worldgen/noise_settings/mine.json", {
        "default_block": "minecraft:stone", "default_fluid": "minecraft:water",
        "disable_mob_generation": True, "legacy_random_source": False,
        "material_rule": {"type": "minecraft:sequence", "sequence": [
            "minecraft:bedrock_floor", "minecraft:bedrock_roof",
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": ["mineworld:ore_nest"]},
             "then_run": {"type": "minecraft:block", "result_state": "minecraft:deepslate"}},
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:vertical_gradient",
                "false_at_and_above": {"absolute": 8}, "random_name": "minecraft:deepslate",
                "true_at_and_below": {"absolute": 0}},
             "then_run": {"type": "minecraft:block", "result_state": "minecraft:deepslate"}}]},
        "noise": {"height": 384, "min_y": -64},
        "noise_router": {"chunk_surface_level": zero, "continents": zero, "depth": zero, "erosion": zero,
                         "final_density": density,
                         "ridges": zero,
                         "temperature": noise("mineworld:cave_kind_a", 1.0, 1.0),
                         "vegetation": noise("mineworld:cave_kind_b", 1.0, 1.0)},
        "sea_level": -64, "spawn_target": []})
    # Vanilla mineshafts (placed near the world bottom because the sea level is the bottom).
    write(f"{ROOT}/data/minecraft/tags/worldgen/biome/has_structure/mineshaft.json",
          {"replace": False, "values": [f"mineworld:{b}" for b in CAVE_BIOMES]})
    write(f"{DATA}/dimension_type/mine.json", {
        "ambient_light": 0.0,
        "attributes": {
            "minecraft:audio/ambient_sounds": {"mood": {"block_search_extent": 8, "offset": 2.0,
                "sound": "minecraft:ambient.cave", "tick_delay": 6000}},
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True},
            "minecraft:gameplay/straw_bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True},
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:visual/ambient_light_color": "#000000",
            "minecraft:visual/fog_color": "#000000",
            "minecraft:visual/sky_color": "#000000",
            "minecraft:visual/fog_start_distance": 0.0,
            "minecraft:visual/fog_end_distance": 64.0,
            "minecraft:visual/sky_light_factor": 0.0},
        "coordinate_scale": 1.0, "has_ceiling": False, "has_ender_dragon_fight": False, "has_fixed_time": True,
        "has_skylight": False, "height": 384, "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 384, "min_y": -64, "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0, "skybox": "none"})


def png(path, px):
    # Never overwrite a texture that already exists (it may have been replaced by hand); pass --force to regenerate.
    if os.path.exists(path) and "--force" not in sys.argv:
        return
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)


def canvas(base, seed, noise=10):
    rnd = random.Random(seed)
    return [[[max(0, min(255, base[k] + rnd.randint(-noise, noise))) for k in range(3)] + [255] for _ in range(16)] for _ in range(16)]


def textures():
    tex = f"{ASSETS}/textures/block"
    # glowing teal "world water"
    px = canvas((40, 190, 200), 11, 18)
    for y in range(16):
        for x in range(16):
            if (x * 3 + y * 5) % 11 == 0:
                px[y][x] = [180, 255, 250, 255]
    png(f"{tex}/world_water.png", px)
    # unbreakable return block: dark stone with a glowing rune frame
    side = canvas((30, 34, 44), 12, 6)
    for i in range(16):
        for a, b in ((0, i), (15, i), (i, 0), (i, 15)):
            side[a][b] = [16, 18, 24, 255]
    for i in range(3, 13):
        for a, b in ((3, i), (12, i), (i, 3), (i, 12)):
            side[a][b] = [60, 200, 210, 255]
    png(f"{tex}/return_block_side.png", side)
    top = canvas((26, 30, 40), 13, 6)
    for i in range(2, 14):
        for a, b in ((2, i), (13, i), (i, 2), (i, 13)):
            top[a][b] = [60, 200, 210, 255]
    for y in range(6, 10):
        for x in range(6, 10):
            top[y][x] = [190, 255, 250, 255]
    png(f"{tex}/return_block_top.png", top)


if __name__ == "__main__":
    shutil.rmtree(f"{DATA}/worldgen", ignore_errors=True)
    worldgen()
    textures()
    print("done")
