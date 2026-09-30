#!/usr/bin/env python3
"""Generates the worldgen JSON and block textures of the mineworld mod (no libraries needed).
Ore counts are vanilla per-chunk vein counts times ORE_MULTIPLIER, spread over the whole world height."""
import json, os, random, shutil, struct, zlib

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


def placed(feature, count):
    return {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
            "min_inclusive": {"absolute": -64}, "max_inclusive": {"absolute": 319}}},
        {"type": "minecraft:biome"}]}


def worldgen():
    step = []
    for name, feature, count in BLOBS:
        write(f"{DATA}/worldgen/placed_feature/{name}.json", placed(feature, count))
        step.append(f"mineworld:{name}")
    for name, feature, count in ORES:
        write(f"{DATA}/worldgen/placed_feature/ore_{name}.json", placed(feature, count * ORE_MULTIPLIER))
        step.append(f"mineworld:ore_{name}")
    features = [[] for _ in range(11)]
    features[6] = step
    write(f"{DATA}/worldgen/biome/mine.json", {
        "attributes": {
            "minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": {
                c: [] for c in ["ambient", "axolotls", "creature", "misc", "monster",
                                "underground_water_creature", "water_ambient", "water_creature"]}},
                "modifier": "overlay"},
            "minecraft:visual/sky_color": "#000000"},
        "carvers": [], "downfall": 0.0, "effects": {"water_color": "#3f76e4"},
        "features": features, "has_precipitation": False, "temperature": 0.5})
    zero = 0.0
    write(f"{DATA}/worldgen/noise_settings/mine.json", {
        "default_block": "minecraft:stone", "default_fluid": "minecraft:water",
        "disable_mob_generation": True, "legacy_random_source": False,
        "material_rule": {"type": "minecraft:sequence", "sequence": [
            "minecraft:bedrock_floor", "minecraft:bedrock_roof",
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:vertical_gradient",
                "false_at_and_above": {"absolute": 8}, "random_name": "minecraft:deepslate",
                "true_at_and_below": {"absolute": 0}},
             "then_run": {"type": "minecraft:block", "result_state": "minecraft:deepslate"}}]},
        "noise": {"height": 384, "min_y": -64},
        "noise_router": {"chunk_surface_level": zero, "continents": zero, "depth": zero, "erosion": zero,
                         "final_density": 1.0, "ridges": zero, "temperature": zero, "vegetation": zero},
        "sea_level": -64, "spawn_target": []})
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
