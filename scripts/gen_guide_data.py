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

def in_biome(biome):
    return {"enter": {"trigger": "minecraft:location", "conditions": {"player": {
        "type": "minecraft:entity_properties", "entity": "this",
        "predicate": {"minecraft:location": {"biomes": biome}}}}}}, None

def killed(*types):
    criteria = {}
    for t in types:
        criteria["killed_" + t.split(":")[1]] = {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": {
            "type": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": t}}}}
    return criteria, None

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
    ("ch3/enter", "ch3/cauldron", "task", mc("deepslate_diamond_ore"), in_biome("mineworld:mine"), 50, [(mc("torch"), 32)], False,
     "踏入礦世界", "站在世界鍋釜上蹲下，進入你的礦世界。", "Into the Mine World", "Stand on a world cauldron and sneak to enter your mine world."),
    ("ch3/harvest", "ch3/enter", "goal", mc("diamond_block"), has((mc("diamond"), 16), (mc("emerald"), 16), (mc("redstone"), 64)), 150,
     [(mc("iron_pickaxe"), 1, {"minecraft:efficiency": 2})], False,
     "豐收", "同時持有鑽石 16、綠寶石 16、紅石 64。", "Bumper Harvest", "Hold 16 diamonds, 16 emeralds and 64 redstone at once."),
    ("ch3/second_world", "ch3/harvest", "task", "mineworld:world_cauldron", impossible(), 100, [], False,
     "第二個世界", "做出第二個世界鍋釜，每一個都是不同的世界。", "A Second World", "Make a second world cauldron. Every one is a different world."),

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

# --- the handbook (dev.alan.guide.client.HandbookScreen). One entry per row: (icon, zh title, en title, zh body, en body).
# Body markup, one paragraph per line: "# " starts a heading, "- " a bullet, anything else is plain text. The screen wraps
# and scrolls, so length is free.
BOOK = [
    (mc("knowledge_book"), "歡迎", "Welcome",
     """# 歡迎來到 Alan's Modpack
這本手冊介紹整合包裡有什麼，以及每個任務該怎麼完成。左邊選章節，滾輪或方向鍵捲動內容。
# 任務書
- 按 L 打開任務書（原版的進度畫面），有六個分頁、30 個任務。
- 完成任務會自動發經驗和物品，不用另外領。
- 每章的第一個任務在分頁最左邊，往右依序完成。
# 手冊弄丟了？
- 在聊天輸入 /guidebook 就能補一本。
- 身上已經有手冊時不會再給，所以最多只會有一本。""",
     """# Welcome to Alan's Modpack
This handbook covers what the modpack adds and how to finish each quest. Pick a chapter on the left; scroll with the wheel or the arrow keys.
# The quest book
- Press L to open the quest book (the vanilla advancements screen): six tabs, 30 quests.
- Finishing a quest pays experience and items by itself.
- Each chapter starts at the left of its tab; work to the right.
# Lost this handbook?
- Type /guidebook in chat for a new one.
- You never get a second copy while carrying one."""),
    (mc("iron_pickaxe"), "第一章 起步", "1 Getting started",
     """# 第一章 起步
先挖礦，湊齊礦世界的材料。
# 任務
- 歡迎：進入遊戲就完成，送麵包。
- 第一把鐵鎬：做一把鐵鎬。
- 深入地底：挖到 Y 小於 0 的深度（按 F3 看座標），送火把。
- 八礦齊備：煤炭、銅、鐵、金、紅石、青金石、鑽石、綠寶石各一個，粗礦或錠都算，送皮革和箱子。
# 提示
這八種礦剛好是第三章礦世界的材料，不要用掉。皮革和箱子是煉金背包的材料。""",
     """# Chapter 1: Getting started
Mine first and gather what the Mine World needs.
# Quests
- Welcome: done on joining, pays bread.
- First Iron Pickaxe: craft an iron pickaxe.
- Into the Deep: dig below Y 0 (F3 shows coordinates), pays torches.
- Eight Ores: coal, copper, iron, gold, redstone, lapis, diamond and emerald, one each. Raw ore or ingots count. Pays leather and a chest.
# Tip
These eight are the chapter 3 Mine World ingredients, so do not use them up. The leather and chest go into the alchemy backpack."""),
    ("alchemy_backpack:backpack", "第二章 煉金", "2 Alchemy",
     """# 第二章 煉金
煉金背包把物品轉成能量，之後用能量兌換任何學過的物品。
# 合成
- 皮革 5、金錠 1、鑽石 2、箱子 1，在工作台排成上面皮革／金錠／皮革，中間鑽石／箱子／鑽石，下面全是皮革。
# 用法
- 手持背包按右鍵開啟。
- 把物品放進左上角的轉化格，物品被消耗，能量立刻存進背包。
- Shift＋點物品欄裡的物品可以快速轉化整組。
- 轉化過的物品，背包就永久學會它；右邊的清單可搜尋、翻頁。
- 按 1 兌換一個，按 + 最多兌換一組。
# 任務
- 煉金背包：做出背包。
- 第一次存入：放一個物品進去。
- 博學：學會 20 種物品。
- 兌換：用能量兌換出一個物品。
每個背包各自記能量與學習清單，交給別人也會保留。""",
     """# Chapter 2: Alchemy
The alchemy backpack turns items into energy, then trades energy for any item it has learned.
# Recipe
- 5 leather, 1 gold ingot, 2 diamonds, 1 chest: leather / gold / leather on top, diamond / chest / diamond in the middle, leather all along the bottom.
# Use
- Hold it and right-click to open.
- Drop an item in the top-left slot: it is consumed and its energy is stored at once.
- Shift-click an inventory stack to convert it quickly.
- Converting an item teaches the backpack that item for good; the list on the right can be searched and paged.
- Press 1 to redeem one, + for up to a stack.
# Quests
- Alchemy Backpack: craft it.
- First Deposit: put one item in.
- Scholar: learn 20 items.
- Redeem: take one item out with energy.
Each backpack keeps its own energy and list, even if you give it away."""),
    ("mineworld:world_cauldron", "第三章 礦世界", "3 Mine World",
     """# 第三章 礦世界
專屬的礦物世界：全是石頭和礦物，礦密度是原版的 8 倍。
# 做出世界鍋釜
- 準備一個裝了水的鍋釜。
- 把煤、銅、鐵、金（粗礦或錠）、紅石、青金石、鑽石、綠寶石各一個丟進去。
- 物品被消耗，鍋釜變成世界鍋釜；重複的種類不吃。
# 進出
- 站在鍋釜裡（或邊緣上）蹲下 4 秒進入。
- 出生小房間裡有回程方塊，站在上面蹲下 4 秒回來。
- 世界沒有天空、全黑、不會自然生成生物，記得帶火把。
# 其他
- 每個世界鍋釜有自己的世界；敲掉再放回會接回同一個世界。
- 用命名牌可以替世界鍋釜取名。
# 任務
- 世界鍋釜：做出一個。
- 踏入礦世界。
- 豐收：同時持有鑽石 16、綠寶石 16、紅石 64。
- 第二個世界：做出第二個世界鍋釜。""",
     """# Chapter 3: Mine World
Your own ore world: all stone and ore, with ore density 8 times vanilla.
# Making a world cauldron
- Take a cauldron filled with water.
- Drop in one each of coal, copper, iron, gold (raw or ingot), redstone, lapis, diamond and emerald.
- The items are consumed and the cauldron becomes a world cauldron. Duplicates are not eaten.
# Coming and going
- Sneak inside the cauldron (or on its rim) for 4 seconds to enter.
- The spawn room has a return block: sneak on it for 4 seconds to leave.
- There is no sky, it is pitch dark and nothing spawns naturally, so bring torches.
# Also
- Every world cauldron is its own world; breaking and replacing it reconnects to the same one.
- A name tag names a world cauldron.
# Quests
- World Cauldron: make one.
- Step into the Mine World.
- Harvest: hold 16 diamonds, 16 emeralds and 64 redstone at once.
- Second World: make another world cauldron."""),
    ("logistics:controller", "第四章 物流", "4 Logistics",
     """# 第四章 物流
用一個終端機操作基地裡所有的箱子。
# 搭網路
- 倉庫控制器是網路核心，一個網路只能有一個。
- 控制器、物流電纜、終端機、儲存單元互相貼面就連成網路，六個方向都可以。
- 貼著網路的箱子、木桶、界伏盒自動算進倉庫；漏斗、熔爐不算。
# 終端機
- 倉庫終端機：顯示全部物品，可搜尋（名稱、@模組、#標籤）與排序。
- 游標拿著物品點格子：左鍵全放、右鍵放一個。空手點：左鍵取一組、右鍵取半組、Shift＋左鍵放進背包。
- 合成終端機：加一個 3×3 合成格，材料直接從倉庫取，合成後自動補滿。
# 儲存單元
- 虛擬容量，I 到 IV 四級，每升一級種類 ×2、數量上限 ×4。
- 四個同級單元排成 2×2 可合成下一級；一個高級單元可拆回四個低一級的。
# 進出與自動化
- 儲存介面：漏斗、導管送進來的物品直接存入倉庫；右鍵設定備貨清單，清單上的物品會備在介面裡，讓漏斗或導管取走；每格可設備貨量（最多 64）。
- 儲存介面有四個升級格：紅石加快、石英增加每次數量。
- 導管：連接容器與機器，不屬於倉庫網路。空手右鍵接頭切換 關閉／輸入／輸出，染料設定顏色，紅石與石英可加速與加量（Shift＋空手取回）。同色的輸入會送到同色的輸出。
- 自動合成器：放好 3×3 配方，從倉庫取材料自動合成。
- 農場介面：放在農田正上方，自動收割並補種下方 9×9。
# 任務
- 倉庫的心臟：放控制器。
- 看見倉庫：放終端機並接上電纜。
- 第一個儲存單元：取得儲存單元。
- 進出口：放儲存介面與導管。
- 自動化：放合成終端機與自動合成器。
- 能源與傳送：放一台發電機，並放傳送裝置。""",
     """# Chapter 4: Logistics
One terminal operates every chest in your base.
# Building a network
- The warehouse controller is the core; a network may have only one.
- Controller, logistics cables, terminals and storage cells join when they touch faces, in all six directions.
- Chests, barrels and shulker boxes touching the network count as warehouse. Hoppers and furnaces do not.
# Terminals
- Warehouse terminal: shows every item; search by name, @mod or #tag, and sort.
- Click a grid slot while holding an item: left stores all, right stores one. With an empty cursor: left takes a stack, right takes half, Shift-left sends it to your inventory.
- Crafting terminal: adds a 3x3 grid that takes materials from the warehouse and refills after each craft.
# Storage cells
- Virtual capacity in four tiers, I to IV; each tier doubles the types and quadruples the amount per type.
- Four cells of one tier in a 2x2 merge into the next tier; one high-tier cell splits back into four of the tier below.
# Moving items and automation
- Storage interface: whatever hoppers or conduits put in goes straight into the warehouse. Right-click to set a stock list; listed items are kept ready in the interface for hoppers or conduits to take, and each slot can set how many (up to 64).
- The storage interface has four upgrade slots: redstone speeds it up, quartz raises items per pulse.
- Conduit: links containers and machines, and is not part of the warehouse network. Click a connector with an empty hand to cycle off / input / output, use dye for colour, redstone and quartz for speed and amount (Shift + empty hand takes them back). Inputs feed outputs of the same colour.
- Auto crafter: set a 3x3 pattern and it crafts from the warehouse by itself.
- Farm interface: place it right above farmland; it harvests and replants the 9x9 area below.
# Quests
- Heart of the Warehouse: place a controller.
- See the Warehouse: place a terminal and link it with cable.
- First Storage Cell: get a cell.
- In and Out: place a storage interface and a conduit.
- Automation: place a crafting terminal and an auto crafter.
- Power and Teleport: place a generator and a teleporter."""),
    (mc("zombie_spawn_egg"), "第五章 變身", "5 Shapeshift",
     """# 第五章 變身
親手擊殺生物，就永久解鎖牠的形態，之後隨時可以變成牠。
# 操作
- V：打開變身選單，3D 預覽、搜尋、翻頁，也有「變回人類」。
- R：使用形態技能，例如苦力怕自爆、烈焰人火球、終界使者傳送、骷髏射箭。
- 死亡後自動變回人類，解鎖的形態不會遺失。
# 好處
- 繼承該生物的血量、攻擊力、護甲、跨步高度。
- 水生生物水下呼吸，防火的生物免疫火焰。
- 同類不會攻擊你；變成怪物時，其他怪物也不會攻擊你。
# 弱點
- 殭屍、骷髏等白天會著火，戴頭盔可擋。
- 終界使者、烈焰人、雪人碰水會受傷。
- 魚類離開水約 15 秒會窒息。
- 天敵會追你，例如鐵魔像追打怪物形態、狼追骷髏形態。
# 任務
- 第一個形態：擊殺一隻生物。
- 第一次變身：按 V 選一個形態。
- 技能：變成有技能的形態並按 R。
- 收集家：解鎖 10 種。博物館：解鎖 30 種。集齊所有形態。""",
     """# Chapter 5: Shapeshift
Kill a mob yourself to unlock its form for good, then become it whenever you like.
# Controls
- V: open the form menu with 3D previews, search, paging and "back to human".
- R: use the form's skill, such as creeper self-destruct, blaze fireball, enderman teleport or skeleton arrows.
- Dying turns you back into a human; unlocked forms are never lost.
# Perks
- You inherit the mob's health, attack, armor and step height.
- Water mobs breathe underwater; fire-proof mobs are immune to fire.
- Mobs of your kind do not attack you; as a monster, other monsters leave you alone.
# Weaknesses
- Zombies, skeletons and the like burn in daylight; a helmet blocks it.
- Endermen, blazes and snow golems are hurt by water.
- Fish suffocate about 15 seconds out of water.
- Natural enemies chase you, such as iron golems against monster forms and wolves against skeleton forms.
# Quests
- First Form: kill a mob.
- First Transformation: pick a form with V.
- Skill: become a form with a skill and press R.
- Collector: unlock 10. Museum: unlock 30. Then unlock every form."""),
    ("combat:feather_charm", "第六章 終局", "6 Endgame",
     """# 第六章 終局
飾品、鐵砧強化，以及最後的 Boss。
# 飾品
- 共 13 種，按 K 打開飾品欄，預設 3 格。
- 飾品背包放在身上，每個多 1 格，上限為飾品種類數。
# 鐵砧強化
- 盔甲：在鐵砧用礦物強化屬性，只消耗礦物；綠寶石盔甲強化會提高變身技能強度。
- 弓：用材料強化拉弓速度、箭矢不下墜、火焰／緩速／爆裂箭。
# 形態之王
- 先擊殺凋零與終界龍，取得下界之星與龍息。
- 集齊所有形態。
- 在形態祭壇召喚形態之王並擊敗牠，掉落形態碎片。
- 用 9 個碎片做出形態之種；解鎖全部形態後用它召喚真・形態之王，擊敗牠會掉落形態核心。
- 用形態核心做出終局護符。
# 任務
- 第一個飾品：取得任一飾品。
- 鐵砧淬煉：強化一件盔甲。弓手的手藝：強化一把弓。
- 兩大原版 Boss：擊殺凋零與終界龍。
- 形態之王：在祭壇擊敗牠。
- 終局護符：做出終局護符，通關！""",
     """# Chapter 6: Endgame
Trinkets, anvil upgrades and the final boss.
# Trinkets
- There are 13; press K to open the trinket screen, 3 slots by default.
- A trinket bag in your inventory adds 1 slot each, up to the number of trinkets.
# Anvil upgrades
- Armor: strengthen stats with ore at an anvil, consuming only the ore; emerald armor upgrades raise shapeshift skill power.
- Bow: upgrade draw speed, arrow drop, and fire / slowness / explosive arrows with materials.
# The Form King
- First defeat the Wither and the Ender Dragon for a nether star and dragon's breath.
- Unlock every form.
- Summon the Form King at the form altar and beat it for a form core.
- Craft the master's charm from the form core.
# Quests
- First Trinket: get any trinket.
- Anvil Tempering: upgrade armor. Bowyer's Craft: upgrade a bow.
- The Two Bosses: defeat the Wither and the Ender Dragon.
- The Form King: beat it at the altar.
- Master's Charm: craft it and you have won!"""),
    (mc("spyglass"), "查詢與小技巧", "Lookup & tips",
     """# 查詢（物品與配方）
開背包或箱子時，右側有物品清單：
- 指著物品按 R 看合成配方，按 U 看用途，按 A 加入書籤。
- 搜尋可用名稱、@模組、#標籤、$說明文字。
- 開著工作台或熔爐時，配方旁的 + 會把材料填進格子（Shift 盡量多）；在合成終端機上會直接從倉庫取材料。
- 配方頁也看得到生物與方塊的掉落物，以及倉庫裡的庫存。
# 按鍵總表
- L：任務書　V：變身選單　R：形態技能　K：飾品欄
# 小技巧
- 八種礦先留著，別拿去做別的。
- 帶火把和食物再進礦世界。
- 倉庫網路建好後，其他章節的材料都能從終端機直接取。""",
     """# Lookup (items and recipes)
Opening your inventory or a chest shows an item list on the right:
- Point at an item: R for recipes, U for uses, A to bookmark.
- Search by name, @mod, #tag or $tooltip text.
- With a crafting table or furnace open, the + beside a recipe fills in the materials (Shift for as many as possible); at a crafting terminal it pulls from the warehouse.
- Recipe pages also show mob and block drops, and your warehouse stock.
# Key summary
- L: quest book   V: form menu   R: form skill   K: trinkets
# Tips
- Keep the eight ores; do not spend them on anything else.
- Bring torches and food into the Mine World.
- Once the warehouse network is up, materials for every other chapter are one terminal away."""),
]
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
zh["guide.handbook.have"], en["guide.handbook.have"] = "你身上已經有一本手冊了。", "You already carry a handbook."
zh["guide.handbook.given"], en["guide.handbook.given"] = "已給你一本手冊。", "Here is a handbook."
write(os.path.join(ROOT, "assets", "guide", "handbook.json"), json.dumps([{"icon": e[0]} for e in BOOK]) + "\n")
write(os.path.join(ROOT, "assets", "guide", "items", "handbook.json"), json.dumps({"model": {"type": "minecraft:model", "model": "guide:item/handbook"}}) + "\n")
write(os.path.join(ROOT, "assets", "guide", "models", "item", "handbook.json"),
      json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/knowledge_book"}}) + "\n")
write(os.path.join(LANG, "zh_tw.json"), json.dumps(zh, ensure_ascii=False, indent=2) + "\n")
write(os.path.join(LANG, "en_us.json"), json.dumps(en, ensure_ascii=False, indent=2) + "\n")
print(len(TASKS), "advancements,", BOOK_ENTRIES, "handbook entries")
