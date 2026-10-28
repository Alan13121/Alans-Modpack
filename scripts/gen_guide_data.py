#!/usr/bin/env python3
"""Generates the quest book (guide mod): advancements, reward loot tables and language files.
The task list lives in docs/mods/guide.md; this script is its machine-readable twin.
Always rewrites everything under the guide mod's data and lang files."""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "mods", "guide", "src", "main", "resources")
DATA = os.path.join(ROOT, "data", "guide")
LANG = os.path.join(ROOT, "assets", "guide", "lang")
BG = "minecraft:gui/advancements/backgrounds/"

TRINKETS = ["feather_charm", "night_vision_charm", "gills_charm", "skill_charm", "fire_ring", "speed_buckle",
            "spring_insole", "regen_charm", "magnet", "blast_ward", "thorns_ring", "hunter_charm", "master_charm"]

def mc(name): return "minecraft:" + name

# --- trigger builders: each returns (criteria dict, requirements list or None)
def tick():
    return {"start": {"trigger": "minecraft:tick"}}, None

def impossible():
    return {"done": {"trigger": "minecraft:impossible"}}, None

def has(*stacks):
    """Obtain every given (item or list of alternatives, count)."""
    criteria = {}
    for i, (item, count) in enumerate(stacks):
        predicate = {"items": item}
        if count > 1: predicate["count"] = {"min": count}
        criteria["has_%d" % i] = {"trigger": "minecraft:inventory_changed", "conditions": {"items": [predicate]}}
    return criteria, None

def placed(*groups):
    """Place a block from every group (a group is a list of alternatives)."""
    criteria, requirements = {}, []
    for gi, group in enumerate(groups):
        names = []
        for bi, block in enumerate(group):
            name = "placed_%d_%d" % (gi, bi)
            criteria[name] = {"trigger": "minecraft:placed_block",
                              "conditions": {"location": {"type": "minecraft:match_block", "blocks": block}}}
            names.append(name)
        requirements.append(names)
    return criteria, requirements

def below_zero():
    return {"deep": {"trigger": "minecraft:location", "conditions": {"player": {
        "type": "minecraft:entity_properties", "entity": "this",
        "predicate": {"minecraft:location": {"position": {"y": {"max": -0.5}}}}}}}}, None

# Every biome of a mine world: the old single biome and the six cave ecosystems.
MINE_WORLD_BIOMES = ["mineworld:mine", "mineworld:crystal_cave", "mineworld:lush_cave", "mineworld:dripstone_cave", "mineworld:ore_nest", "mineworld:sulfur_cave", "mineworld:deep_dark"]


def in_biome(biome):
    return {"enter": {"trigger": "minecraft:location", "conditions": {"player": {
        "type": "minecraft:entity_properties", "entity": "this",
        "predicate": {"minecraft:location": {"biomes": biome}}}}}}, None

def killed(*types, any_of=False):
    # By default every type must be killed; any_of=True is satisfied by any one of them.
    criteria = {}
    for t in types:
        criteria["killed_" + t.split(":")[1]] = {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": {
            "type": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": t}}}}
    return criteria, [list(criteria)] if any_of else None

# --- the task list. (id, parent, frame, icon, trigger, exp, rewards, announce, zh title, zh desc, en title, en desc)
# rewards: list of (item, count) or (item, count, enchantments dict)
BACKGROUND = {"ch1": "stone", "ch2": "husbandry", "ch3": "adventure", "ch4": "nether", "ch5": "husbandry", "ch6": "end"}
EIGHT_ORES = [mc("coal"), [mc("copper_ingot"), mc("raw_copper")], [mc("iron_ingot"), mc("raw_iron")], [mc("gold_ingot"), mc("raw_gold")],
              mc("redstone"), mc("lapis_lazuli"), mc("diamond"), mc("emerald")]
CELLS = ["logistics:cell", "logistics:cell_2", "logistics:cell_3", "logistics:cell_4"]

TASKS = [
    # Chapter 1: start
    ("ch1/start", None, "task", mc("grass_block"), tick(), 5, [(mc("bread"), 8)], False,
     "歡迎來到 Alan's Modpack", "這是一份整合包任務書，隨時可以回來看。先挖礦吧。",
     "Welcome to Alan's Modpack", "This book guides you through the modpack. Start by mining."),
    ("ch1/iron_pickaxe", "ch1/start", "task", mc("iron_pickaxe"), has((mc("iron_pickaxe"), 1)), 20, [], False,
     "第一把鐵鎬", "做一把鐵鎬。", "First Iron Pickaxe", "Craft an iron pickaxe."),
    ("ch1/deep", "ch1/iron_pickaxe", "task", mc("deepslate"), below_zero(), 30, [(mc("torch"), 16)], False,
     "深入地底", "挖到 Y 小於 0 的深度。", "Into the Deep", "Dig below Y 0."),
    ("ch1/eight_ores", "ch1/deep", "goal", mc("diamond"), has(*[(o, 1) for o in EIGHT_ORES]), 100,
     [(mc("leather"), 5), (mc("chest"), 1)], False,
     "八礦齊備", "收集煤炭、銅、鐵、金、紅石、青金石、鑽石、綠寶石各一個，這剛好是礦世界的材料。",
     "All Eight Ores", "Collect coal, copper, iron, gold, redstone, lapis, diamond and emerald. They are what a mine world is made of."),

    # Chapter 2: alchemy
    ("ch2/backpack", None, "task", "alchemy_backpack:backpack", has(("alchemy_backpack:backpack", 1)), 50, [(mc("gold_ingot"), 4)], False,
     "煉金背包", "做出煉金背包。把物品放進去，會轉成能量。", "Alchemy Backpack", "Craft the alchemy backpack. Items put into it become energy."),
    ("ch2/first_deposit", "ch2/backpack", "task", mc("iron_ingot"), impossible(), 30, [], False,
     "第一次存入", "把一個物品放進背包轉成能量。", "First Deposit", "Put an item into the backpack to turn it into energy."),
    ("ch2/scholar", "ch2/first_deposit", "goal", mc("enchanted_book"), impossible(), 80, [(mc("diamond"), 2)], False,
     "博學", "讓背包學會 20 種物品。", "Scholar", "Let the backpack learn 20 different items."),
    ("ch2/redeem", "ch2/scholar", "task", mc("emerald"), impossible(), 50, [(mc("iron_ingot"), 16)], False,
     "兌換", "用能量從背包兌換出物品。", "Redeem", "Take an item out of the backpack with its energy."),

    # Chapter 3: mine world
    ("ch3/cauldron", None, "task", "mineworld:world_cauldron", impossible(), 100, [(mc("lapis_lazuli"), 16)], False,
     "世界鍋釜", "把八種礦物各一個丟進裝水的鍋釜，做出世界鍋釜。", "World Cauldron",
     "Throw one of each of the eight ores into a water cauldron to make a world cauldron."),
    ("ch3/enter", "ch3/cauldron", "task", mc("deepslate_diamond_ore"), in_biome(MINE_WORLD_BIOMES), 50, [(mc("torch"), 32)], False,
     "踏入礦世界", "站在世界鍋釜上蹲下，進入你的礦世界。", "Into the Mine World", "Stand on a world cauldron and sneak to enter your mine world."),
    ("ch3/harvest", "ch3/enter", "goal", mc("diamond_block"), has((mc("diamond"), 16), (mc("emerald"), 16), (mc("redstone"), 64)), 150,
     [(mc("iron_pickaxe"), 1, {"minecraft:efficiency": 2})], False,
     "豐收", "同時持有鑽石 16、綠寶石 16、紅石 64。", "Bumper Harvest", "Hold 16 diamonds, 16 emeralds and 64 redstone at once."),
    ("ch3/second_world", "ch3/harvest", "task", "mineworld:world_cauldron", impossible(), 100, [], False,
     "第二個世界", "做出第二個世界鍋釜，每一個都是不同的世界。", "A Second World", "Make a second world cauldron. Every one is a different world."),
    ("ch3/trade", "ch3/enter", "task", mc("gold_ingot"), impossible(), 50, [(mc("gold_ingot"), 8)], False,
     "第一筆城市交易", "在怪物城市和居民交易一次。", "First City Trade", "Trade once with a resident of a monster city."),
    ("ch3/bounty", "ch3/enter", "task", "mineworld:bounty_proof", has(("mineworld:bounty_proof", 1)), 100, [(mc("golden_apple"), 1)], False,
     "賞金獵人", "打倒通緝犯，取得第一張懸賞證明。", "Bounty Hunter", "Defeat a wanted mob and get your first bounty proof."),
    ("ch3/creatures", "ch3/enter", "task", "mineworld:gem_slime_spawn_egg",
     killed("mineworld:glow_bat", "mineworld:gem_slime", "mineworld:ore_golem", "mineworld:ore_worm", any_of=True), 80, [(mc("emerald"), 4)], False,
     "礦世界的住民", "打倒任一種礦世界生物：螢光蝠、寶石史萊姆、礦石魔像或礦蟲。",
     "Mine World Natives", "Defeat any mine world creature: a glow bat, gem slime, ore golem or ore worm."),

    # Chapter 4: logistics
    ("ch4/controller", None, "task", "logistics:controller", placed(["logistics:controller"]), 50, [(mc("redstone"), 16)], False,
     "倉庫的心臟", "放下倉庫控制器，一個網路只能有一個。", "The Heart of the Warehouse", "Place a warehouse controller. A network has exactly one."),
    ("ch4/terminal", "ch4/controller", "task", "logistics:terminal", placed(["logistics:terminal"]), 50, [(mc("iron_ingot"), 16)], False,
     "看見倉庫", "放下倉庫終端機，用物流電纜接到控制器。", "See the Warehouse", "Place a terminal and connect it to the controller with cable."),
    ("ch4/cell", "ch4/terminal", "task", "logistics:cell", has((CELLS, 1)), 80, [(mc("gold_ingot"), 8)], False,
     "第一個儲存單元", "取得儲存單元，它是倉庫的虛擬容量。", "First Storage Cell", "Get a storage cell. It holds the warehouse's virtual stock."),
    ("ch4/interfaces", "ch4/cell", "task", "logistics:storage_interface", placed(["logistics:storage_interface"], ["logistics:conduit"]), 100,
     [(mc("redstone"), 32)], False,
     "進出口", "放下儲存介面與導管，讓物品自動進出倉庫。", "In and Out", "Place a storage interface and a conduit to move items in and out automatically."),
    ("ch4/automation", "ch4/interfaces", "task", "logistics:autocrafter", placed(["logistics:crafting_terminal"], ["logistics:autocrafter"]), 150,
     [(mc("diamond"), 4)], False,
     "自動化", "放下合成終端機與自動合成器。", "Automation", "Place a crafting terminal and an auto crafter."),
    ("ch4/power_and_teleport", "ch4/automation", "goal", "logistics:teleporter",
     placed(["logistics:solar_generator", "logistics:coal_generator"], ["logistics:teleporter"]), 200, [(mc("coal"), 32)], False,
     "能源與傳送", "放下一台發電機，並放下傳送裝置。", "Power and Teleport", "Place a generator and a teleporter."),

    # Chapter 5: shapeshift
    ("ch5/first_form", None, "task", mc("zombie_spawn_egg"), impossible(), 30, [], False,
     "第一個形態", "擊殺一隻生物，解鎖牠的形態。", "First Form", "Kill a mob to unlock its form."),
    ("ch5/first_transform", "ch5/first_form", "task", mc("pig_spawn_egg"), impossible(), 50, [(mc("golden_carrot"), 8)], False,
     "第一次變身", "按 V 打開變身選單，變成一種生物。", "First Transformation", "Press V to open the form menu and become a mob."),
    ("ch5/first_skill", "ch5/first_transform", "task", mc("fire_charge"), impossible(), 50, [], False,
     "技能", "變成有技能的形態，按 R 使用。", "Skill", "Become a form with a skill and press R to use it."),
    ("ch5/ten_forms", "ch5/first_skill", "task", mc("sculk_catalyst"), impossible(), 150, [(mc("diamond"), 2)], False,
     "收集家", "解鎖 10 種形態。", "Collector", "Unlock 10 forms."),
    ("ch5/thirty_forms", "ch5/ten_forms", "goal", mc("lectern"), impossible(), 400, [(mc("emerald"), 16)], False,
     "博物館", "解鎖 30 種形態。", "Museum", "Unlock 30 forms."),
    ("ch5/all_forms", "ch5/thirty_forms", "challenge", mc("dragon_head"), impossible(), 1000, [(mc("diamond"), 8)], True,
     "集齊所有形態", "解鎖全部可收集的形態。", "Every Form", "Unlock every collectable form."),

    # Chapter 6: the endgame
    ("ch6/trinket", None, "task", "combat:feather_charm", has((["combat:" + t for t in TRINKETS], 1)), 80, [(mc("leather"), 4)], False,
     "第一個飾品", "取得任一種飾品，按 K 打開飾品欄裝上。", "First Trinket", "Get any trinket and press K to wear it."),
    ("ch6/armor_upgrade", "ch6/trinket", "task", mc("iron_chestplate"), impossible(), 100, [], False,
     "鐵砧淬煉", "在鐵砧用礦物強化一件盔甲。", "Anvil Tempering", "Strengthen a piece of armor with an ore at an anvil."),
    ("ch6/bow_upgrade", "ch6/armor_upgrade", "task", mc("bow"), impossible(), 100, [(mc("arrow"), 64)], False,
     "弓手的手藝", "在鐵砧強化一把弓。", "Bowyer's Craft", "Upgrade a bow at an anvil."),
    ("ch6/vanilla_bosses", "ch6/bow_upgrade", "challenge", mc("nether_star"), killed(mc("wither"), mc("ender_dragon")), 500,
     [(mc("obsidian"), 16)], True,
     "兩大原版 Boss", "擊殺凋零與終界龍。形態祭壇需要一顆下界之星與一瓶龍息。",
     "The Two Bosses", "Defeat the Wither and the Ender Dragon. The form altar asks for a nether star and a dragon's breath."),
    ("ch6/form_king", "ch6/vanilla_bosses", "challenge", "combat:form_core", impossible(), 2000, [(mc("diamond_block"), 3)], True,
     "形態之王", "集齊所有一般形態，在形態祭壇擊敗形態之王，拿到形態碎片。", "The Form King", "Collect every common form (rare ones are not needed) and defeat the Form King at the form altar for form shards."),
    ("ch6/master_charm", "ch6/form_king", "challenge", "combat:master_charm", has(("combat:master_charm", 1)), 500, [], True,
     "終局護符", "用形態碎片做出形態之種，召喚並擊敗真・形態之王，用掉落的形態核心做出終局護符。", "Master's Charm", "Make a form seed from shards, defeat the True Form King, and craft the master's charm from its form core."),
]

# A tab only shows once its root is done (or an ancestor within two steps is), and the real first task of
# chapters 2-6 needs the player to actually do something. So each of those chapters gets a header that
# completes on the first tick, silently, and the old first task hangs under it.
HEADERS = {
    "ch2": ("alchemy_backpack:backpack", "第二章：煉金", "煉金背包與物品兌換。", "Chapter 2: Alchemy", "The alchemy backpack and item redemption."),
    "ch3": ("mineworld:world_cauldron", "第三章：礦世界", "用鍋釜開出專屬礦物維度。", "Chapter 3: Mine World", "Open ore dimensions with the cauldron."),
    "ch4": ("logistics:controller", "第四章：物流", "倉庫、終端與自動化。", "Chapter 4: Logistics", "Warehouses, terminals and automation."),
    "ch5": (mc("zombie_spawn_egg"), "第五章：變身", "收集形態並變身。", "Chapter 5: Shapeshift", "Collect forms and transform."),
    "ch6": ("combat:feather_charm", "第六章：終局", "飾品、鐵砧強化與 Boss。", "Chapter 6: Endgame", "Trinkets, anvil upgrades and bosses."),
}
HEADER_IDS = {c + "/intro" for c in HEADERS}
TASKS = [t for c, h in sorted(HEADERS.items()) for t in [(c + "/intro", None, "goal", h[0], tick(), 0, [], False, h[1], h[2], h[3], h[4])]] + [
    (t[0], c + "/intro", *t[2:]) if t[1] is None and (c := t[0].split("/")[0]) in HEADERS else t for t in TASKS]

# --- the handbook (dev.alan.guide.client.HandbookScreen): the chapter text lives in guide_handbook.py; each chapter ends
# with a quest section generated from TASKS, so titles, descriptions and rewards always match the quest book.
import guide_handbook
from guide_handbook import REWARD_NAMES

TASK_BY_ID = {t[0]: t for t in TASKS}

def quest_section(ids):
    if not ids: return "", ""
    zh_lines, en_lines = ["# 相關任務"], ["# Quests"]
    for tid in ids:
        _, _, _, _, _, exp, items, _, zt, zd, et, ed = TASK_BY_ID[tid]
        zr, er = ["經驗 %d" % exp], ["%d XP" % exp]
        for entry in items:
            zn, enn = REWARD_NAMES[entry[0]]
            zr.append(zn if entry[1] == 1 else "%s ×%d" % (zn, entry[1]))
            er.append(enn if entry[1] == 1 else "%d %s" % (entry[1], enn))
        zh_lines.append("- %s：%s獎勵：%s。" % (zt, zd, "、".join(zr)))
        en_lines.append("- %s: %s Reward: %s." % (et, ed, ", ".join(er)))
    return "\n" + "\n".join(zh_lines), "\n" + "\n".join(en_lines)

BOOK = [(icon, zt, et, zb + quest_section(ids)[0], eb + quest_section(ids)[1]) for icon, zt, et, zb, eb, ids in guide_handbook.BOOK]
assert sorted(tid for entry in guide_handbook.BOOK for tid in entry[5]) == sorted(
    t[0] for t in TASKS if t[0] not in HEADER_IDS), "every quest appears in exactly one handbook chapter"
BOOK_ENTRIES = len(BOOK)

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8").write(text)

def reward_loot(items):
    pools = []
    for entry in items:
        item, count = entry[0], entry[1]
        # One modifier per entry: a count for stacks, or the enchantments (never both in the current task list).
        modifier = None
        if len(entry) > 2:
            modifier = {"type": "minecraft:set_enchantments", "enchantments": entry[2]}
        elif count > 1:
            modifier = {"type": "minecraft:set_count", "count": count}
        element = {"type": "minecraft:item", "name": item}
        if modifier: element["modifier"] = modifier
        pools.append({"rolls": 1, "entries": [element]})
    return {"type": "minecraft:advancement_reward", "pools": pools}

zh, en = {}, {}
for (tid, parent, frame, icon, trigger, exp, items, announce, zt, zd, et, ed) in TASKS:
    criteria, requirements = trigger
    key = "advancement.guide." + tid.replace("/", ".")
    chapter = tid.split("/")[0]
    display = {"icon": {"id": icon}, "title": {"translate": key + ".title"}, "description": {"translate": key + ".desc"},
               "frame": frame, "show_toast": tid not in HEADER_IDS, "announce_to_chat": announce, "hidden": False}
    if parent is None: display["background"] = BG + BACKGROUND[chapter]
    advancement = {"display": display, "criteria": criteria}
    if parent is not None: advancement["parent"] = "guide:" + parent
    if requirements is not None: advancement["requirements"] = requirements
    rewards = {"experience": exp}
    if items:
        rewards["loot"] = ["guide:rewards/" + tid]
        write(os.path.join(DATA, "loot_table", "rewards", tid + ".json"), json.dumps(reward_loot(items)) + "\n")
    advancement["rewards"] = rewards
    write(os.path.join(DATA, "advancement", tid + ".json"), json.dumps(advancement) + "\n")
    zh[key + ".title"], zh[key + ".desc"] = zt, zd
    en[key + ".title"], en[key + ".desc"] = et, ed

for i, (icon, zt, et, zb, eb) in enumerate(BOOK, 1):
    zh["handbook.guide.entry.%d.title" % i], en["handbook.guide.entry.%d.title" % i] = zt, et
    zh["handbook.guide.entry.%d.body" % i], en["handbook.guide.entry.%d.body" % i] = zb, eb
zh["item.guide.handbook"], en["item.guide.handbook"] = "Alan's Modpack 手冊", "Alan's Modpack Handbook"
zh["handbook.guide.close"], en["handbook.guide.close"] = "關閉", "Close"
zh["guide.handbook.button"], en["guide.handbook.button"] = "手冊", "Handbook"
zh["guide.handbook.hint"], en["guide.handbook.hint"] = "按背包左上角的「手冊」按鈕可以打開使用手冊（也可以輸入 /guidebook）。", "Open your inventory and click the Handbook button in the top-left corner for the manual (or type /guidebook)."
write(os.path.join(ROOT, "assets", "guide", "handbook.json"), json.dumps([{"icon": e[0]} for e in BOOK]) + "\n")
write(os.path.join(ROOT, "assets", "guide", "items", "handbook.json"), json.dumps({"model": {"type": "minecraft:model", "model": "guide:item/handbook"}}) + "\n")
write(os.path.join(ROOT, "assets", "guide", "models", "item", "handbook.json"),
      json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "guide:item/handbook"}}) + "\n")  # art/guide/item/handbook.png
write(os.path.join(LANG, "zh_tw.json"), json.dumps(zh, ensure_ascii=False, indent=2) + "\n")
write(os.path.join(LANG, "en_us.json"), json.dumps(en, ensure_ascii=False, indent=2) + "\n")
print(len(TASKS), "advancements,", BOOK_ENTRIES, "handbook entries")
