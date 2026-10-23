"""The handbook text (dev.alan.guide.client.HandbookScreen), imported by gen_guide_data.py.

One entry per chapter: (icon, zh title, en title, zh body, en body, quest ids). The generator appends a "Quests" section
built from the quest list itself (title, description, reward), so quest text and rewards never drift from the book.
Body markup, one paragraph per line: "# " starts a heading, "- " a bullet, anything else is plain text. The screen wraps
and scrolls, so length is free. Numbers here mirror the mods' code and docs/mods/*.md: change both together."""

# Display names for quest reward items: (zh, en).
REWARD_NAMES = {
    "minecraft:bread": ("麵包", "bread"),
    "minecraft:torch": ("火把", "torches"),
    "minecraft:leather": ("皮革", "leather"),
    "minecraft:chest": ("箱子", "chest"),
    "minecraft:gold_ingot": ("金錠", "gold ingots"),
    "minecraft:diamond": ("鑽石", "diamonds"),
    "minecraft:iron_ingot": ("鐵錠", "iron ingots"),
    "minecraft:lapis_lazuli": ("青金石", "lapis lazuli"),
    "minecraft:iron_pickaxe": ("效率 II 鐵鎬", "Efficiency II iron pickaxe"),
    "minecraft:redstone": ("紅石", "redstone"),
    "minecraft:golden_apple": ("金蘋果", "golden apple"),
    "minecraft:emerald": ("綠寶石", "emeralds"),
    "minecraft:golden_carrot": ("金胡蘿蔔", "golden carrots"),
    "minecraft:arrow": ("箭", "arrows"),
    "minecraft:obsidian": ("黑曜石", "obsidian"),
    "minecraft:diamond_block": ("鑽石方塊", "diamond blocks"),
    "minecraft:coal": ("煤炭", "coal"),
}

BOOK = [
    ("minecraft:knowledge_book", "開始之前", "Start here",
     """# 歡迎來到 Alan's Modpack
這本手冊是整合包的使用說明書：每一章講一個主題，從怎麼做、怎麼用到相關數值都寫在裡面，章末列出相關任務和獎勵。
# 怎麼看這本手冊
- 點左邊的章節切換；左右方向鍵切換上一章／下一章。
- 滑鼠在右邊時，滾輪捲動內容；上下鍵、Page Up／Page Down、Home／End 也可以。
- 滑鼠在左邊目錄上時，滾輪捲動目錄。
# 按鍵總表
- L：任務書（原版進度畫面）。
- V：變身選單。R（在世界裡）：使用形態技能。
- K：飾品欄。
- R／U（在背包、箱子等畫面裡指著物品）：看合成配方／用途。A：加入書籤。F9：作弊模式（有權限才能用）。
- 所有按鍵都可以在「選項 → 控制」裡改。注音鍵盤配置下 R 會顯示成「ㄐ」，這是原版的行為。
# 任務書
- 按 L 打開，有六個分頁、33 個任務：起步、煉金、礦世界、物流、變身、終局。
- 每個分頁內前一個任務完成，下一個才會出現；分頁之間互不影響，想先玩哪一塊都可以。
- 完成任務會自動發經驗和物品，不用另外領。任務只是引導，不會鎖住任何內容。
# 建議順序
- 起步：挖礦，湊齊八種礦。
- 煉金：做煉金背包，把多餘的東西換成能量。
- 礦世界：用八種礦開一個礦物世界，取得大量礦物。
- 物流：蓋一座倉庫，整理所有東西，再加自動化與傳送。
- 變身：一路上擊殺生物，收集形態。
- 終局：飾品、鐵砧強化，最後挑戰形態之王。
# 手冊弄丟了？
- 在聊天輸入 /guidebook 就能補一本，所有人都能用。
- 身上已經有手冊時不會再給，所以最多只會有一本。""",
     """# Welcome to Alan's Modpack
This handbook is the manual for the modpack: each chapter covers one topic, from how to make and use things to the numbers behind them. The quests that go with a chapter are listed at its end.
# Reading this book
- Click a chapter on the left; the left and right arrow keys go to the previous or next chapter.
- With the mouse on the right, the wheel scrolls the text; so do Up/Down, Page Up/Page Down and Home/End.
- With the mouse over the chapter list, the wheel scrolls the list.
# Keys
- L: quest book (the vanilla advancements screen).
- V: shapeshift menu. R (in the world): use your form's skill.
- K: trinket slots.
- R / U (in an inventory or chest screen, pointing at an item): recipes / uses. A: bookmark. F9: cheat mode (if allowed).
- Every key can be changed under Options → Controls.
# The quest book
- Press L: six tabs and 33 quests: Getting started, Alchemy, Mine World, Logistics, Shapeshift, Endgame.
- Inside a tab the next quest shows once the one before it is done; tabs do not depend on each other, so start wherever you like.
- Finishing a quest pays experience and items by itself. Quests only guide you and never lock anything.
# Suggested order
- Getting started: mine the eight ores.
- Alchemy: make the alchemy backpack and turn spare items into energy.
- Mine World: use the eight ores to open an ore world full of ores.
- Logistics: build a warehouse for everything, then add automation and teleporters.
- Shapeshift: collect forms by killing mobs along the way.
- Endgame: trinkets, anvil upgrades and finally the Form King.
# Lost this handbook?
- Type /guidebook in chat for a new one. Everyone can use it.
- You never get a second copy while carrying one.""",
     []),

    ("minecraft:iron_pickaxe", "起步", "Getting started",
     """# 第一步：挖礦
整合包的每一條路線都從礦物開始。第一章的目標是湊齊八種礦，各一個就夠。
# 八種礦
- 煤炭、銅、鐵、金、紅石、青金石、鑽石、綠寶石。
- 銅、鐵、金拿粗礦或錠都算。
- 鑽石在深層（Y 小於 0 的深板岩層）比較多；綠寶石只長在山地生態域，也可以和村民交易取得。
- 按 F3 可以看自己的座標，Y 是高度。
# 為什麼要留著
- 這八種礦剛好是礦世界的材料（第三章），丟進一個裝水的鍋釜就能開出一個礦物世界。
- 進了礦世界以後礦物多到用不完，所以前期先留一份，別拿去做別的。
- 八礦任務送的皮革 5 和箱子 1，剛好是煉金背包的材料（再加金錠 1、鑽石 2）。
# 前期小提醒
- 鐵鎬才挖得動金、紅石、鑽石、綠寶石；鑽石鎬才挖得動黑曜石。
- 下礦前帶火把和食物；煉金背包做好後，多餘的圓石、泥土都能換成能量。""",
     """# Step one: mine
Every path in the modpack starts with ores. Chapter 1 asks for one of each of the eight ores.
# The eight ores
- Coal, copper, iron, gold, redstone, lapis lazuli, diamond and emerald.
- Copper, iron and gold count as raw ore or ingots.
- Diamonds are most common deep down (the deepslate layer, below Y 0); emeralds only grow in mountain biomes, and villagers trade them.
- Press F3 to see your coordinates; Y is height.
# Why keep them
- These eight ores are the Mine World ingredients (chapter 3): drop them into a water cauldron and it opens an ore world.
- Inside a mine world ores are endless, so keep one set early and do not spend it.
- The eight-ore quest pays 5 leather and a chest: exactly the alchemy backpack ingredients (plus 1 gold ingot and 2 diamonds).
# Early tips
- An iron pickaxe mines gold, redstone, diamond and emerald; obsidian needs a diamond pickaxe.
- Bring torches and food underground. Once you have the alchemy backpack, spare cobblestone and dirt become energy.""",
     ["ch1/start", "ch1/iron_pickaxe", "ch1/deep", "ch1/eight_ores"]),

    ("alchemy_backpack:backpack", "煉金背包", "Alchemy",
     """# 煉金背包是什麼
把任何物品轉成能量（EMC）存起來，再用能量兌換出背包「學會」過的物品。能量也是物流網路（頻道、傳送）用的能量。
# 合成
- 皮革 5、金錠 1、鑽石 2、箱子 1。
- 上排：皮革、金錠、皮革。中排：鑽石、箱子、鑽石。下排：皮革 ×3。
# 存入（轉化）
- 手持背包按右鍵開啟。
- 把物品放進左上角的轉化格：物品被消耗，能量 = 單價 × 數量，立刻存進背包。
- Shift＋點物品欄裡的物品，可以把整組送進轉化格。
- 第一次轉化某種物品，背包就永久學會它。
- 帶附魔、改過名字或有其他特殊資料的物品不能轉化；煉金背包本身也不能放進去。
# 兌換
- 右邊清單列出所有學會的物品，可以搜尋、翻頁。
- 按 1 兌換一個，按 + 最多兌換一組；能量不夠或物品欄滿了會提示。
# 能量值
- 做得出來的東西，能量是用配方推算的：取最便宜的配方，材料的能量加總後除以產出數量，所以成品不會比材料便宜或貴得離譜。
- 做不出來的原料（礦石、怪物掉落、植物…）各有自己的值，沒特別設定的是 50。
- 例：泥土、圓石 1；木板 8；原木、煤炭 32；鐵錠 256；鐵磚 2304；金錠 2048；鑽石 8192；綠寶石 16384；獄髓錠 65536。
- 空氣、基岩、屏障、指令方塊、生怪磚等是 0，不能轉化。
- 在查詢清單裡指著物品，提示框會顯示它的能量值。
# 其他
- 每個背包各自記能量與學習清單，交給別人也會保留。
- 背包有一格頻道卡槽：放入頻道卡，背包的能量就能供給那個物流頻道（第十章）；只有你在線上、背包在你身上時才算。
- 能量不會流失；單一背包最多存 10 億，超過時拒絕存入。""",
     """# What it is
It turns any item into energy (EMC) and stores it, then turns energy back into any item the backpack has "learned". Energy is also what the logistics network uses for channels and teleporting.
# Recipe
- 5 leather, 1 gold ingot, 2 diamonds, 1 chest.
- Top: leather, gold ingot, leather. Middle: diamond, chest, diamond. Bottom: 3 leather.
# Depositing
- Hold the backpack and right-click to open it.
- Put items in the conversion slot at the top left: they are consumed and value × count is added right away.
- Shift-click an item in your inventory to send the whole stack in.
- The first time you convert an item, the backpack learns it for good.
- Enchanted, renamed or otherwise special items cannot be converted, and neither can the backpack itself.
# Redeeming
- The list on the right shows every learned item, with search and pages.
- Press 1 for one item, + for up to a stack. You get a message if energy or inventory space runs out.
# Energy values
- Anything you can craft is worth what its cheapest recipe costs: the ingredients' energy added up and divided by the number made.
- Raw materials that cannot be crafted (ores, mob drops, plants...) have values of their own, and anything unlisted is worth 50.
- Examples: dirt, cobblestone 1; planks 8; logs, coal 32; iron ingot 256; iron block 2304; gold ingot 2048; diamond 8192; emerald 16384; netherite ingot 65536.
- Air, bedrock, barriers, command blocks, spawners and the like are 0 and cannot be converted.
- Point at an item in the lookup list and its tooltip shows its energy value.
# Also
- Each backpack keeps its own energy and learned list, even when handed to someone else.
- The backpack has a channel card slot: with a card in it, its energy powers that logistics channel (chapter 10), but only while you are online and carry the backpack.
- Energy never drains; one backpack holds up to a billion, and deposits past that are refused.""",
     ["ch2/backpack", "ch2/first_deposit", "ch2/scholar", "ch2/redeem"]),

    ("mineworld:world_cauldron", "礦世界：進出", "Mine World",
     """# 礦世界是什麼
屬於你的礦物世界：幾乎全是石頭和礦物，八種礦的密度都是原版的 8 倍，不分高度。世界高度 -64～320，頂部和底部是基岩，水平方向無限。
# 做出世界鍋釜
- 放一個鍋釜並裝滿水。
- 把煤炭、銅、鐵、金（粗礦或錠）、紅石、青金石、鑽石、綠寶石各一個丟進去（直接丟在鍋釜上）。
- 物品被消耗，八種到齊時鍋釜變成世界鍋釜，水面會有發光粒子。重複的種類不會被吃掉。
- 在查詢清單裡看世界鍋釜的配方，會顯示這個流程。
# 進入與離開
- 站在世界鍋釜裡（或邊緣上）蹲下 4 秒，期間畫面會暈眩，然後傳送進去。
- 你會出現在出生小房間，裡面有發光方塊和回程方塊。
- 站在回程方塊上蹲下 4 秒就回到鍋釜上方。回程方塊打不掉。
- 鍋釜上方被擋住時，會傳送到最近的空位。
# 多個世界
- 每個世界鍋釜是一個獨立的世界，各有自己的種子，數量不限。
- 敲掉世界鍋釜會掉出帶有世界資料的物品，放回去就接回同一個世界，可以搬家。
- 用命名牌（或在鐵砧改名）可以替世界鍋釜取名，提示框會顯示名稱。
# 生存規則
- 沒有天空、全黑、時間固定，記得帶大量火把。
- 黑暗的洞室和礦道會生怪物；有光的地方就不生怪。
- 礦世界裡的床會爆炸，只有怪物城市裡的床能用（第六章）。在其他地方死亡會回主世界重生。
- 寵物不會跟著你傳送。""",
     """# What it is
Your own ore world: almost all stone and ore, with every one of the eight ores at 8 times vanilla density at every height. It spans Y -64 to 320, bedrock top and bottom, endless sideways.
# Making a world cauldron
- Place a cauldron and fill it with water.
- Drop in one each of coal, copper, iron, gold (raw or ingot), redstone, lapis, diamond and emerald (throw them onto the cauldron).
- The items are consumed; once all eight are in, it becomes a world cauldron with glowing particles on the water. Duplicates are not eaten.
- The world cauldron's recipe in the lookup list shows this process.
# Coming and going
- Sneak inside the world cauldron (or on its rim) for 4 seconds; your view swims, then you are moved in.
- You arrive in a small spawn room with a light and a return block.
- Sneak on the return block for 4 seconds to come back above the cauldron. The return block cannot be broken.
- If the space above the cauldron is blocked, you land at the nearest free spot.
# Many worlds
- Every world cauldron is its own world with its own seed; make as many as you like.
- Breaking a world cauldron drops an item that remembers its world. Place it again to reconnect, so you can move it.
- A name tag (or renaming in an anvil) names a world cauldron; the tooltip shows the name.
# Survival rules
- No sky, pitch dark, frozen time: bring lots of torches.
- Monsters spawn in dark caves and tunnels; any light keeps them away.
- Beds explode in a mine world except inside a monster city (chapter 6). Dying anywhere else sends you to the overworld.
- Pets do not follow you through.""",
     ["ch3/cauldron", "ch3/enter", "ch3/harvest", "ch3/second_world"]),

    ("mineworld:gem_slime_spawn_egg", "洞室與生物", "Caves & mobs",
     """# 洞室
礦世界大多是實心的石頭，但挖著挖著會打穿大型洞室，直線挖礦平均每 100～150 格會遇到一個。洞室有六種：
- 晶洞：紫水晶晶洞和發光地衣，有蝙蝠和螢光蝠。
- 苔蘚洞：苔蘚、會結發光漿果的洞穴藤蔓、黏土水池、杜鵑。可以補充食物，也比較亮；有美西螈和寶石史萊姆。
- 滴水石洞：大型滴水石和鐘乳石，小心頭頂掉下來的鐘乳石；有洞穴蜘蛛。
- 富礦巢：深板岩，礦物再多生成一次（等於原版的 16 倍），還有地城（生怪磚）；洞穴蜘蛛、女巫比較多，也有礦石魔像。
- 硫磺洞：硫磺與硃砂的條紋岩壁、硫磺池（池底的烈性硫磺會冒煙，泡進去會噁心）和硫磺尖錐；住著硫磺立方怪。
- 深淵：整片幽匿方塊，幾乎全黑，沒有一般怪物；有幽匿感測體、幽匿尖嘯體和幽匿催生體。**踩到尖嘯體會召喚伏守者**，聽覺極好、一擊致命等級的傷害，能躲就躲。
# 其他地形
- 出生房間周圍 20 格內都是實心的，不會一出門就掉進洞裡。
- 世界底部附近有廢棄礦坑（鐵軌、礦車箱子、蜘蛛網和洞穴蜘蛛生怪磚）。
- 一般怪物和原版一樣：殭屍、骷髏、蜘蛛、苦力怕、終界使者、女巫。
# 礦世界生物
- 螢光蝠：晶洞裡 3～5 隻成群飛，翅膀會發光，全黑也看得到，無害。掉螢石粉 1～2、紫水晶碎片 0～1。
- 寶石史萊姆：苔蘚洞的黑暗處，會主動攻擊，被打會像史萊姆一樣分裂。只有最小隻會掉東西：青金石 60%、綠寶石 30%、鑽石 10%。
- 礦石魔像：富礦巢裡稀有的大塊頭，血量 100、傷害和鐵魔像一樣，但不會先動手。打倒掉 8～16 個粗礦，八種都可能。
- 礦蟲：只在礦世界、生存模式挖礦石時，約 1/30 的機率從那格鑽出來，會攻擊你。打倒掉 2 個你剛挖的那種礦（粗礦或寶石）。
- 有裝變身模組時，擊殺這四種都能解鎖對應的稀有形態（第十一章）。""",
     """# Caves
A mine world is mostly solid rock, but mining breaks into large caves now and then: about one every 100-150 blocks of straight tunnel. There are six kinds:
- Crystal cave: amethyst geodes and glow lichen, with bats and glow bats.
- Lush cave: moss, cave vines with glow berries, clay pools and azaleas. Food and some light; axolotls and gem slimes live here.
- Dripstone cave: big dripstone and stalactites; watch for falling stalactites. Cave spiders live here.
- Ore nest: deepslate with every ore generated twice (16 times vanilla) and dungeons with spawners. More cave spiders and witches, and ore golems.
- Sulfur cave: banded walls of sulfur and cinnabar, sulfur pools (the potent sulfur at the bottom smokes and makes you nauseous) and sulfur spikes; sulfur cubes live here.
- Deep dark: sculk everywhere, nearly pitch black, with no ordinary monsters; sculk sensors, shriekers and catalysts. **A shrieker can summon a warden**, which hears everything and hits extremely hard; avoid it.
# Other terrain
- The rock within 20 blocks of the spawn room is solid, so you never fall into a cave at the door.
- Abandoned mineshafts sit near the bottom of the world (rails, minecart chests, cobwebs and cave spider spawners).
- Ordinary monsters are as in vanilla: zombies, skeletons, spiders, creepers, endermen, witches.
# Mine world creatures
- Glow bat: flocks of 3-5 in crystal caves with glowing wings you can see in the dark. Harmless. Drops 1-2 glowstone dust and 0-1 amethyst shard.
- Gem slime: dark spots in lush caves. Hostile and splits like a slime. Only the smallest drop something: lapis 60%, emerald 30%, diamond 10%.
- Ore golem: a rare giant in ore nests with 100 health and iron golem damage, but it never strikes first. Drops 8-16 raw ores of any of the eight kinds.
- Ore worm: only in a mine world and only in survival, mining an ore block has about a 1 in 30 chance to release one. It attacks you and drops 2 of the ore you just mined.
- With the shapeshift mod, killing each of the four unlocks its rare form (chapter 11).""",
     ["ch3/creatures"]),

    ("minecraft:lantern", "城市與交易", "Cities & trade",
     """# 怪物城市
礦世界裡的安全屋：一座位於巨大圓頂洞窟裡的城市，燈火通明，住著友善的怪物居民。
- 新的礦世界，出生房間有一條點了燈的隧道往南直通第一座城市（約 160 格）。
- 更遠的地方還有其他城市，平均大約每 700 格一座，高度在 Y 60～200 之間。
- 城牆北側是城門，城門內兩側有梯子可以上牆。
# 為什麼安全
- 城裡不會生怪物，而且整座城都很亮。
- 城牆上的骷髏守衛和豬布林守衛（近戰兵拿金劍、遠程兵拿弩）會攻擊追進城的怪物和通緝犯。
- 居民都是無敵的，不會被打死，也不會攻擊你。城市苦力怕是吉祥物，不會爆炸。
# 旅店與回程
- 旅店（城市西南側）的床可以設定重生點，死了就在城裡重生。床不能用來睡覺，時間是固定的。
- 你自己帶床放在城市範圍內也一樣有效；城外的床還是會爆炸。
- 廣場北邊有回程方塊，蹲下 4 秒直接回到世界鍋釜。
# 交易
右鍵居民打開交易畫面。次數沒有上限、價格不會變、居民也不會升等。
- 豬布林礦工（拿鐵鎬，收金錠）：鐵鎬 3、鐵鏟 2、火把 ×16 1、鐵軌 ×16 2。
- 豬布林農夫（拿鋤頭）：麵包 ×6 1、烤馬鈴薯 ×6 1、發光漿果 ×8 1、金胡蘿蔔 ×3 2。
- 豬布林鐵匠（拿鐵砧或鐵劍）：鐵劍、鐵頭盔、鐵靴各 3，鐵護腿 4，鐵胸甲 5，鑽石劍、鑽石鎬各 10，鑽石胸甲 16。
- 豬布林酒保（在旅店）：夜視、速度藥水各 2，治療、抗火、再生藥水各 3。
- 豬布林牧民（拿小麥）：牛、豬、羊、雞、兔子生怪蛋各 4，山羊 5，馬、驢各 6，哞菇 8。
- 空手的豬布林是閒人，不交易。
- 殭屍雜貨商（廣場，收礦物）：終界珍珠 4 鐵錠、火把 ×32 4 煤炭、梯子 ×16 4 銅錠、箭 ×16 3 銅錠、水桶 3 鐵錠、盾牌 4 鐵錠、營火 3 煤炭、紅石火把 ×8 4 紅石。
- 終界使者商人（廣場，收鑽石和綠寶石）：附魔書 3 鑽石；高等附魔書 4 鑽石＋2 青金石；附魔鑽石劍／鎬／斧／頭盔／靴子各 5 鑽石＋2 綠寶石；附魔鑽石護腿 6＋2；附魔鑽石胸甲 7＋2；附魔弓 3 鑽石＋1 綠寶石。
- 黑市商人（廣場南側的小攤位，豬布林外觀、手持獄髓錠，收鑽石和綠寶石）：賣很難拿到的稀有物品，價格很高。獄髓錠 24 鑽石＋8 綠寶石；遠古殘骸 12 鑽石；鞘翅 64 鑽石＋16 綠寶石；不死圖騰、附魔金蘋果各 16 鑽石＋4 綠寶石；三叉戟 24 鑽石；海洋之心 32 綠寶石；界伏殼 8 綠寶石。
- 黑市只出現在新生成的城市，舊的城市不會補。
# 鎖定附魔
- 終界使者的附魔商品每次打開都重新隨機（含經驗修補等寶藏附魔，不會有詛咒）。
- 看到喜歡的附魔，選中那筆交易後按畫面下方的「鎖定交易」，它就固定下來、排在最前面並標上星號，之後可以一直買。
- 鎖定記在你身上，所有城市的終界使者共用，數量沒有上限。再按一次解除鎖定。
- 查詢清單的「城市交易」分類列出每一筆交易，查金錠也看得到所有用途。""",
     """# Monster cities
The safe houses of a mine world: a city in a huge domed cavern, brightly lit and full of friendly monster residents.
- In a new mine world, a lit tunnel runs south from the spawn room straight to the first city (about 160 blocks).
- More cities lie farther out, about one every 700 blocks, between Y 60 and 200.
- The gate is in the north wall; ladders inside the gate lead up onto the wall.
# Why they are safe
- No monsters spawn in a city, and the whole city is lit.
- Skeleton guards and piglin guards (gold swords up close, crossbows on the wall) attack monsters and wanted mobs that follow you in.
- Residents cannot be hurt and never attack you. City creepers are mascots and never explode.
# The inn and the way home
- Beds in the inn (south-west of the city) set your respawn point, so you come back in town. You cannot sleep; time is frozen.
- A bed you bring yourself works too anywhere inside the city; outside it, beds still explode.
- North of the square is a return block: sneak on it for 4 seconds to go straight back to the world cauldron.
# Trading
Right-click a resident to trade. Trades never run out, prices never change and residents never level up.
- Piglin miner (iron pickaxe, takes gold ingots): iron pickaxe 3, iron shovel 2, 16 torches 1, 16 rails 2.
- Piglin farmer (hoe): 6 bread 1, 6 baked potatoes 1, 8 glow berries 1, 3 golden carrots 2.
- Piglin smith (anvil or iron sword): iron sword, helmet and boots 3 each, leggings 4, chestplate 5, diamond sword and pickaxe 10 each, diamond chestplate 16.
- Piglin bartender (in the inn): night vision and swiftness potions 2 each, healing, fire resistance and regeneration 3 each.
- Piglin herder (wheat): cow, pig, sheep, chicken and rabbit spawn eggs 4 each, goat 5, horse and donkey 6 each, mooshroom 8.
- Empty-handed piglins are idlers and do not trade.
- Zombie shopkeeper (the square, takes ores): ender pearl for 4 iron, 32 torches for 4 coal, 16 ladders for 4 copper, 16 arrows for 3 copper, water bucket for 3 iron, shield for 4 iron, campfire for 3 coal, 8 redstone torches for 4 redstone.
- Enderman merchant (the square, takes diamonds and emeralds): enchanted book 3 diamonds; high-level book 4 diamonds + 2 lapis; enchanted diamond sword, pickaxe, axe, helmet or boots 5 diamonds + 2 emeralds each; leggings 6 + 2; chestplate 7 + 2; enchanted bow 3 diamonds + 1 emerald.
- Black market dealer (small stall south of the square, a piglin holding a netherite ingot, takes diamonds and emeralds): rare items that are hard to get, at high prices. Netherite ingot 24 diamonds + 8 emeralds; ancient debris 12 diamonds; elytra 64 diamonds + 16 emeralds; totem of undying or enchanted golden apple 16 diamonds + 4 emeralds each; trident 24 diamonds; heart of the sea 32 emeralds; shulker shell 8 emeralds.
- The black market only appears in newly generated cities; existing cities do not get one.
# Locking an enchantment
- The enderman's enchanted offers re-roll every time you open the screen (treasure enchantments like Mending included, never curses).
- When you like one, select that trade and press "Lock offer" at the bottom: it stays fixed, moves to the top with a star, and you can buy it forever.
- Locks belong to you and work with the enderman in every city; there is no limit. Press again to unlock.
- The lookup list's "City trades" category shows every trade; looking up gold ingots shows everything they buy.""",
     ["ch3/trade"]),

    ("mineworld:bounty_proof", "通緝犯", "Wanted mobs",
     """# 通緝犯
強化過的怪物，名字是「通緝犯・某某［等級］」，打倒可以拿懸賞證明。
- 在礦世界（城市 80 格以外）大約每 3 分鐘會出現一隻，偏好洞室。
- 在主世界大約每 10 分鐘一隻，出現在夜晚的地表或地底黑暗處。
- 會出現在你 24～64 格外的黑暗處，你附近同時最多一隻。和平模式不會出現。
- 任何玩家靠近 32 格內，通緝犯會發光，隔牆也看得到。
# 等級
- C 級（70%）：殭屍或骷髏，血量 2 倍，穿鐵裝。掉 1 張懸賞證明。
- B 級（25%）：衛道士或豬布林蠻兵，血量 3 倍，穿鑽石裝，跑得比較快。掉 3 張。
- A 級（5%）：喚魔者頭目，血量 5 倍，會放尖牙和召喚惱鬼，還帶 3 隻衛道士小弟；32 格內畫面上方有血條。掉 8 張，小弟不掉。
- 喚魔者是變身的稀有形態，打倒 A 級頭目就能解鎖。
# 換獎勵
拿懸賞證明到城門東側的守衛所，找衛兵隊長（拿金斧的豬布林蠻兵）交易：
- 經驗瓶 ×8：1 張。
- 金蘋果：2 張。
- 不死圖騰：6 張。
- 附魔金蘋果：10 張。
# 打法建議
- 把通緝犯引進城市，守衛會幫你打。
- A 級的小弟會圍上來，先拉開距離再處理頭目。""",
     """# Wanted mobs
Strengthened monsters named "Wanted · <name> [tier]". Defeat them for bounty proofs.
- In a mine world (80+ blocks from any city) one appears about every 3 minutes, usually in caves.
- In the overworld about every 10 minutes, at night on the surface or in the dark underground.
- They appear 24-64 blocks away in the dark, at most one near you at a time, and never in peaceful.
- Within 32 blocks of any player a wanted mob glows, visible through walls.
# Tiers
- Tier C (70%): a zombie or skeleton with double health and iron armour. Drops 1 bounty proof.
- Tier B (25%): a vindicator or piglin brute with triple health, diamond armour and a bit more speed. Drops 3.
- Tier A (5%): an evoker boss with 5 times the health, fangs and vexes, and 3 vindicator minions; a boss bar shows within 32 blocks. Drops 8; minions drop none.
- The evoker is a rare shapeshift form, so a tier A boss unlocks it.
# Rewards
Take bounty proofs to the guard post east of the city gate and trade with the guard captain (a piglin brute with a gold axe):
- 8 bottles o' enchanting: 1 proof.
- Golden apple: 2 proofs.
- Totem of undying: 6 proofs.
- Enchanted golden apple: 10 proofs.
# Tips
- Lead a wanted mob into a city and the guards will join in.
- A tier A boss's minions crowd you; back off before you go for the boss.""",
     ["ch3/bounty"]),

    ("logistics:terminal", "物流：倉庫網路", "Warehouse",
     """# 物流是什麼
把基地所有的箱子接成一座倉庫，用一台終端機搜尋、取出、放入全部物品。
# 搭網路
- 倉庫控制器是網路核心，一個網路只能有一個。
- 控制器、物流電纜、終端機、儲存單元等物流方塊互相貼面就連成網路，六個方向都可以。
- 貼著任何網路方塊的箱子、木桶、界伏盒會自動算進倉庫。漏斗、熔爐等機器不算。
- 網路上有兩個控制器、兩個頻道方塊或兩個區塊載入器時，終端機會顯示錯誤，無法使用。
# 主要配方
- 物流電纜 ×4：銅錠、鐵錠、銅錠（橫排一列）。
- 倉庫控制器：鐵錠 4、鑽石 2、紅石 2、箱子 1。上下排：鐵錠、鑽石、鐵錠；中排：紅石、箱子、紅石。
- 倉庫終端機：鐵錠 6、玻璃片、工作台、紅石。上排：鐵、玻璃片、鐵；中排：鐵、工作台、鐵；下排：鐵、紅石、鐵。
- 合成終端機：倉庫終端機＋工作台（隨意擺）。
- 其他配方在查詢清單指著物品按 R 就看得到。
# 倉庫終端機
- 顯示倉庫裡全部物品和總數，可以依數量或名稱排序。
- 搜尋：直接打名稱，@模組名（例如 @minecraft），#標籤（例如 #logs）。
- 游標拿著物品點格子：左鍵全部放進去，右鍵放一個。
- 空手點物品：左鍵取一組，右鍵取半組，Shift＋左鍵直接放進背包。
- 終端機不耗能量。
# 合成終端機
- 多一個 3×3 合成格，材料直接從倉庫拿。
- 合成完成後自動從倉庫補滿材料，可以連續合成。
- 「存回倉庫」按鈕把合成格的東西放回倉庫；關閉畫面時也會自動存回。
- 在查詢的配方頁按 +，會直接從倉庫取材料填進合成格（Shift 盡量多）。查詢配方時，格子右下角會顯示庫存（綠色夠、紅色不夠）和可合成次數。""",
     """# What logistics does
It ties every chest in your base into one warehouse, so one terminal can search, take and store everything.
# Building a network
- The warehouse controller is the core; a network can have only one.
- Controllers, logistics cables, terminals, storage cells and other logistics blocks touching face to face form a network, in all six directions.
- Chests, barrels and shulker boxes touching any network block join the warehouse. Hoppers, furnaces and other machines do not.
- With two controllers, two channel blocks or two chunk loaders on one network the terminal shows an error and stops working.
# Main recipes
- 4 logistics cables: copper ingot, iron ingot, copper ingot in a row.
- Warehouse controller: 4 iron, 2 diamonds, 2 redstone, 1 chest. Top and bottom: iron, diamond, iron; middle: redstone, chest, redstone.
- Warehouse terminal: 6 iron, glass pane, crafting table, redstone. Top: iron, pane, iron; middle: iron, crafting table, iron; bottom: iron, redstone, iron.
- Crafting terminal: warehouse terminal + crafting table (any layout).
- For every other recipe, point at the item in the lookup list and press R.
# The warehouse terminal
- Shows every item in the warehouse with totals, sorted by count or name.
- Search: type a name, @mod (for example @minecraft), or #tag (for example #logs).
- Holding an item, click the grid: left puts it all in, right puts in one.
- Empty-handed on an item: left takes a stack, right half a stack, shift-left straight to your inventory.
- Terminals use no energy.
# The crafting terminal
- Adds a 3×3 crafting grid fed straight from the warehouse.
- After each craft it refills the grid from the warehouse, so you can keep crafting.
- "Store" puts the grid back into the warehouse; closing the screen does the same.
- Press + on a lookup recipe page to fill the grid from the warehouse (shift for as many as possible). Lookup recipes also show stock in each slot's corner (green enough, red short) and how many times you can craft.""",
     ["ch4/controller", "ch4/terminal"]),

    ("logistics:cell", "儲存與自動化", "Automation",
     """# 儲存單元
不用箱子的虛擬容量，接在網路上就能存。
- 配方：鐵錠 7、終界珍珠、木桶。上排：鐵、珍珠、鐵；中排：鐵、木桶、鐵；下排：鐵 ×3。
- I 級：64 種物品，每種最多 10,000 個。
- II 級：128 種，每種 40,000。III 級：256 種，每種 160,000。IV 級：512 種，每種 640,000。
- 升級：四個同級單元在工作台排成 2×2，合成下一級，內容會合併（放不下就不能合成）。
- 拆解：一個 II 級以上的單元單獨放進工作台，拆成四個低一級的；內容全部留在其中一個，所以內容要放得進一個低級單元。
- 單元敲掉後內容會保留在物品上。
# 儲存介面：倉庫的門
- 配方：漏斗、投擲器、物流電纜、鐵錠（隨意擺）。
- 進貨：漏斗或導管送進介面的東西，立刻存進倉庫。
- 備貨：右鍵打開，拿著物品點清單格子設定要備貨的物品（共 9 格），倉庫會把它們備在介面的備貨格裡，讓漏斗或導管取走。
- 每個清單格用滾輪設定備貨量 1～64（Shift 一次 16），0 代表備滿一組。超出清單的東西會自動退回倉庫。
- 四個升級格，每種最多放 8 個：紅石縮短補貨間隔（10 刻 → 1 刻），石英增加每次補貨數量（32 → 256）。
# 導管
- 配方：鐵錠、物流電纜、鐵錠（橫排，得 2 條）。
- 導管是搬運管，連接容器與機器，不屬於倉庫網路。
- 空手右鍵某一面的接頭，切換 關閉 → 輸入（從那個容器取出）→ 輸出（送進那個容器）。
- 拿染料右鍵設定顏色：輸入的東西只會送到同色的輸出，同一條導管可以跑好幾種顏色。
- 每個接頭可以放升級：紅石（最多 16）加快間隔，石英（最多 16）每次多搬 2 個（預設每 10 刻 4 個，最多 36 個）。Shift＋空手右鍵取回升級。
- 能接熔爐（上面放原料、側面放燃料、下面取成品）、釀造台和堆肥桶。
- 導管接上儲存介面，就是機器和倉庫之間的橋：從介面取備貨送進機器，再把成品送回介面。
# 自動合成器
- 配方：原版合成器＋儲存介面（隨意擺）。
- 在 3×3 格放好配方圖樣，它會從倉庫拿材料自動合成，成品放回倉庫。
- 「保持」數量用滾輪調整（預設 64，最多 9999）：倉庫裡少於這個數量才會合成。
- 只用完全一樣的物品當材料，不會用同標籤的替代品（例如指定橡木板就只用橡木板）。
- 升級可以增加每次合成的次數（1～8 次）。
# 農場介面
- 配方：儲存介面＋鐵鋤（隨意擺）。
- 放在農田正上方，管理下方 9×9、三層深的範圍：成熟的作物自動收割並補種，收成直接進倉庫。
- 一般作物（含地獄疣）收割後補種；甘蔗、仙人掌、竹子留底座；西瓜、南瓜只摘果實。
- 可以開啟「使用骨粉」，從倉庫拿骨粉催熟。需要接在可用的網路上才會運作。""",
     """# Storage cells
Virtual space without chests; put one on the network and it stores.
- Recipe: 7 iron, ender pearl, barrel. Top: iron, pearl, iron; middle: iron, barrel, iron; bottom: 3 iron.
- Tier I: 64 item types, up to 10,000 of each.
- Tier II: 128 types, 40,000 each. Tier III: 256 types, 160,000 each. Tier IV: 512 types, 640,000 each.
- Upgrading: four cells of one tier in a 2×2 craft the next tier and merge their contents (refused if it would not fit).
- Splitting: a tier II+ cell alone in the crafting grid splits into four of the tier below; all contents stay in one, so they must fit in one lower cell.
- A broken cell keeps its contents on the item.
# The storage interface: the warehouse's door
- Recipe: hopper, dropper, logistics cable, iron ingot (any layout).
- In: anything a hopper or conduit pushes into the interface goes straight into the warehouse.
- Stock: right-click it, then click the list slots with an item to choose what to stock (9 slots). The warehouse keeps those items in the interface's stock slots for hoppers and conduits to take.
- Scroll on a list slot to set its stock level 1-64 (shift for 16 at a time); 0 means a full stack. Anything off the list goes back to the warehouse.
- Four upgrade slots, up to 8 of each kind: redstone shortens the restock interval (10 ticks → 1), quartz raises the amount per restock (32 → 256).
# Conduits
- Recipe: iron ingot, logistics cable, iron ingot in a row (makes 2).
- Conduits carry items between containers and machines; they are not part of the warehouse network.
- Right-click a connection empty-handed to cycle off → input (take out of that container) → output (put into it).
- Right-click with a dye to set the colour: input only goes to outputs of the same colour, so one line can carry several colours.
- Each connection takes upgrades: redstone (up to 16) for speed, quartz (up to 16) for 2 more items per move (default 4 every 10 ticks, up to 36). Shift-right-click empty-handed to take them back.
- Works with furnaces (top: input, sides: fuel, bottom: output), brewing stands and composters.
- A conduit on a storage interface bridges machines and the warehouse: take stock from the interface into a machine and send the product back.
# The auto crafter
- Recipe: vanilla crafter + storage interface (any layout).
- Set a recipe pattern in its 3×3 grid; it takes ingredients from the warehouse and puts the result back.
- Scroll to set "Keep" (default 64, up to 9999): it only crafts while the warehouse has fewer than that.
- It uses exactly the items you set and never swaps tags (oak planks means only oak planks).
- Upgrades raise crafts per cycle (1-8).
# The farm interface
- Recipe: storage interface + iron hoe (any layout).
- Place it right above farmland. It looks after the 9×9 area below, three layers deep: ripe crops are harvested and replanted, and the harvest goes into the warehouse.
- Ordinary crops (nether wart too) are replanted; sugar cane, cactus and bamboo keep their base; melons and pumpkins are picked, stems stay.
- Turn on "Use bone meal" to take bone meal from the warehouse. It only works on a working network.""",
     ["ch4/cell", "ch4/interfaces", "ch4/automation"]),

    ("logistics:teleporter", "頻道與傳送", "Channels",
     """# 頻道
- 頻道方塊接在網路上，替這個網路開一個頻道。同一個頻道的所有網路會合併成同一座倉庫，在不同的基地也能共用。
- 配方：鐵錠 4、終界珍珠、金錠 2、倉庫控制器、紅石。上排：鐵、珍珠、鐵；中排：金、控制器、金；下排：鐵、紅石、鐵。
- 右鍵打開：從清單選頻道，或輸入名稱新增頻道（1～24 個字），可以設定公開或私有、改名、刪除。
- 公開頻道所有人都看得到；私有頻道只有擁有者看得到，但可以用頻道卡借給朋友。
- 每人最多建立 32 個頻道，只有擁有者或管理員能改名、刪除、改公開設定。
- 頻道的能量存在頻道方塊裡，敲掉頻道方塊會失去存著的能量。
# 頻道卡
- 空白頻道卡 ×4：上排鐵錠、紙、鐵錠；下排紅石、金粒、紅石。
- 在頻道方塊畫面放入空白頻道卡，按「寫入頻道卡」，卡片就記住這個頻道。
- 頻道卡用來綁定無線終端、放進煉金背包，或借給別人使用私有頻道；也可以在頻道方塊按「讀取頻道卡」切換到卡片上的頻道。
# 能量（EMC）
能量和煉金背包是同一種。傳送裝置要用能量，終端機不用。終端機、無線終端、傳送裝置上會顯示頻道目前的能量。
- 太陽能發電機：上排玻璃 ×3，中排青金石、石英、青金石，下排鐵錠、銅錠、鐵錠。白天、正上方看得到天空時每秒 1 EMC。
- 煤炭發電機：鐵錠 7、熔爐、紅石（上排鐵 ×3，中排鐵、熔爐、鐵，下排鐵、紅石、鐵）。放入煤炭或木炭，每秒 4 EMC，一個燒 200 秒（共 800 EMC）；煤炭塊 9 倍。
- 煉金背包放入頻道卡後，背包的能量也算進那個頻道（你在線上、背包在身上時）。
# 無線終端
- 配方：上排終界珍珠；中排鐵錠、倉庫終端機、鐵錠；下排鐵錠、紅石、鐵錠。
- 和寫好的頻道卡一起放進工作台合成，就綁定那個頻道。
- 站在同頻道天線的範圍內，右鍵就能遠端開啟倉庫（只有一般終端機的功能，沒有合成格）。
# 天線
- 配方：上排避雷針；中排紅石、鐵錠、紅石；下排鐵錠 ×3。
- 預設範圍 10 格；天線的格子裡每放一個紅石方塊 +5 格，最多 10 個（60 格）。
# 傳送裝置
- 配方：黑曜石 4、終界珍珠 2、鑽石 2、倉庫控制器。上下排：黑曜石、珍珠、黑曜石；中排：鑽石、控制器、鑽石。
- 接在有頻道的網路上，右鍵選擇同頻道的另一台傳送裝置當目的地。
- 三種狀態：關閉、開啟一次（傳送一次後自動關閉）、常開。開啟時頂端有光柱。
- 站到傳送裝置上方 1～3 格內就會傳送，每次消耗 10 EMC，能量不夠就不能傳。
- 目的地沒載入也能傳，抵達時會自動載入。
# 區塊載入器
- 配方：上排黑曜石、終界之眼、黑曜石；中排終界之眼、倉庫控制器、終界之眼；下排黑曜石、鑽石、黑曜石。
- 接在網路上，讓周圍 3×3 個區塊保持載入，不耗能量。每個網路最多一個。
- 用途：你離開時發電機、自動合成器、農場繼續運作，頻道能量也持續累積。""",
     """# Channels
- A channel block on a network opens a channel for it. Every network on the same channel merges into one warehouse, even across bases.
- Recipe: 4 iron, ender pearl, 2 gold, warehouse controller, redstone. Top: iron, pearl, iron; middle: gold, controller, gold; bottom: iron, redstone, iron.
- Right-click it to pick a channel from the list, or type a name (1-24 characters) to create one; you can set it public or private, rename it or delete it.
- Everyone sees a public channel; a private one only its owner sees, but you can lend it to a friend with a channel card.
- Each player can create up to 32 channels; only the owner or an operator can rename, delete or change visibility.
- A channel's energy is stored in its channel block; breaking the block loses that energy.
# Channel cards
- 4 blank channel cards: top iron, paper, iron; bottom redstone, gold nugget, redstone.
- Put a blank card in the channel block screen and press "Write card"; the card now remembers that channel.
- Cards bind wireless terminals, go in the alchemy backpack, or lend a private channel; "Read card" in a channel block switches to the card's channel.
# Energy (EMC)
The same energy as the alchemy backpack. Teleporters use it; terminals do not. Terminals, wireless terminals and teleporters show the channel's energy.
- Solar generator: top 3 glass; middle lapis, quartz, lapis; bottom iron, copper, iron. 1 EMC per second in daylight with open sky right above it.
- Coal generator: 7 iron, furnace, redstone (top 3 iron, middle iron, furnace, iron, bottom iron, redstone, iron). Coal or charcoal gives 4 EMC per second for 200 seconds (800 EMC each); a coal block lasts 9 times as long.
- An alchemy backpack holding a channel card adds its energy to that channel (while you are online and carry it).
# Wireless terminal
- Recipe: top ender pearl; middle iron, warehouse terminal, iron; bottom iron, redstone, iron.
- Craft it together with a written channel card to bind it to that channel.
- Inside the range of an antenna on the same channel, right-click to open the warehouse from afar (plain terminal only, no crafting grid).
# Antenna
- Recipe: top lightning rod; middle redstone, iron, redstone; bottom 3 iron.
- Range 10 blocks; each redstone block in its slot adds 5, up to 10 blocks (60 range).
# Teleporter
- Recipe: 4 obsidian, 2 ender pearls, 2 diamonds, warehouse controller. Top and bottom: obsidian, pearl, obsidian; middle: diamond, controller, diamond.
- On a network with a channel, right-click it and pick another teleporter on the same channel as the destination.
- Three states: closed, open once (closes after one trip) and always open. An open teleporter shows a beam of light.
- Stand 1-3 blocks above it to teleport. Each trip costs 10 EMC; without energy it will not go.
- The destination does not need to be loaded; it loads when you arrive.
# Chunk loader
- Recipe: top obsidian, ender eye, obsidian; middle ender eye, controller, ender eye; bottom obsidian, diamond, obsidian.
- On a network it keeps the 3×3 chunks around it loaded, free of energy cost. One per network.
- Use it so generators, auto crafters and farms keep running and channel energy keeps building while you are away.""",
     ["ch4/power_and_teleport"]),

    ("minecraft:zombie_spawn_egg", "變身", "Shapeshift",
     """# 解鎖形態
- 親手擊殺一種生物，就永久解鎖牠的形態。每位玩家各自記錄，死亡不會遺失。
- 共有 60 種可收集形態：34 種一般形態，26 種稀有形態（需要特定生態域、結構或特殊條件才遇得到，例如掠奪者、喚魔者、幻翼、悅靈、駱駝、礦世界的四種生物）。
- 終界龍、凋零、監守者、遠古守衛者不能變身。
- 稀有形態也可以用形態印記解鎖（第十五章）。
# 變身選單（V）
- 按 V 打開，有 3D 預覽、搜尋、翻頁。
- 點一個形態就變身；「變回人類」按鈕回到原本的樣子。
- 變身沒有冷卻、沒有消耗，隨時可以換。
# 變身後
- 外觀、體型、碰撞箱、視角高度都變成那種生物；第三人稱的鏡頭距離會跟著體型調整。
- 繼承那種生物的血量、攻擊力、護甲和跨步高度，有些形態還會跑得更快或跳得更高（例如兔子）。
- 照樣可以使用物品、挖方塊、裝飾品；盔甲仍然有防護效果，只是看不到。
- 死亡後自動變回人類。
# 生物怎麼看你
- 同類不會攻擊你，例如變成殭屍時殭屍不理你。
- 變成任何怪物形態時，其他怪物也不會攻擊你。
- 但天敵會找上門：鐵魔像會追打怪物形態，狼會追骷髏形態，狼和狐狸會追兔子形態。
- 有些形態會嚇跑別的生物：貓形態嚇跑苦力怕，殭屍形態嚇跑村民。
- 殭屍、屍殼、溺屍、殭屍村民形態殺死村民時，村民不會死，而是變成殭屍村民（保留職業和交易），可以用虛弱藥水加金蘋果治好。和平難度除外。
- 換形態時，中立生物（例如被你惹毛的豬布林）的仇恨會清除。""",
     """# Unlocking forms
- Kill a kind of mob yourself and you unlock its form for good. Each player has their own list, and death never takes it away.
- There are 60 forms to collect: 34 common and 26 rare (found only in certain biomes, structures or situations, such as the pillager, evoker, phantom, allay, camel and the four mine world creatures).
- The Ender Dragon, the Wither, the Warden and the elder guardian cannot be taken.
- Rare forms can also be unlocked with a form mark (chapter 15).
# The shapeshift menu (V)
- Press V: a 3D preview, search and pages.
- Click a form to become it; "Human" turns you back.
- Changing has no cooldown and no cost; switch whenever you like.
# While transformed
- Your looks, size, hitbox and eye height become the mob's; the third-person camera follows your size.
- You take on its health, attack, armour and step height; some forms also run faster or jump higher (the rabbit, for one).
- You can still use items, mine and wear trinkets; armour still protects you, it just is not drawn.
- When you die you turn back into a human.
# How mobs see you
- Your own kind leaves you alone: as a zombie, zombies ignore you.
- In any monster form, other monsters ignore you too.
- Natural enemies come for you, though: iron golems chase monster forms, wolves chase skeleton forms, wolves and foxes chase rabbit forms.
- Some forms scare other mobs: a cat form scares creepers off, a zombie form scares villagers.
- When a zombie, husk, drowned or zombie villager form kills a villager, it does not die: it turns into a zombie villager that keeps its profession and trades, and a weakness potion plus a golden apple cures it. Not on Peaceful.
- Changing form clears the anger of neutral mobs (such as piglins you upset).""",
     ["ch5/first_form", "ch5/first_transform", "ch5/ten_forms", "ch5/thirty_forms", "ch5/all_forms"]),

    ("minecraft:creeper_head", "形態能力一覽", "Form abilities",
     """# 主動技能（R）
變成有技能的形態時，在世界裡按 R 使用。冷卻以秒計，換形態時冷卻重置。
- 苦力怕：自爆，半徑 3。按下後引信 1.5 秒，期間再按一次可以取消。你不會受傷，爆炸後自動變回人類。預設沒有冷卻，會破壞方塊。
- 烈焰人：小火球，冷卻 0.3 秒，會點燃目標。
- 惡魂：大火球，冷卻 1 秒，會爆炸。
- 終界使者：往視線方向傳送，最遠 32 格，冷卻 1 秒。
- 骷髏、流髑、沼骸：射出一支滿弓的箭，冷卻 0.5 秒；掠奪者的箭威力更高。箭撿不回來。
- 微風使者：風彈，冷卻 0.5 秒，會把東西吹開。
- 雪人：雪球，冷卻 0.2 秒。
- 駱馬、商人駱馬：吐口水，冷卻 0.5 秒。
- 喚魔者：一排 12 隻尖牙，冷卻 2 秒。
- 幻翼：俯衝，冷卻 3 秒；沿視線衝約 8 格，路上的生物受 6 點傷害並被撞開。
- 駱駝：衝刺，冷卻 3 秒；水平衝刺，把路上的生物推開，不傷害。
- 悅靈：治癒脈衝，冷卻 5 秒；回 2 顆心給自己和 6 格內的玩家與動物。
- 青蛙：舌頭，冷卻 2 秒；把準心上的生物或掉落物拉到面前，範圍 12 格。
- 北極熊：重擊，冷卻 4 秒；周圍 4 格的生物受 4 點傷害、被擊退並緩速。
- 海龜：縮殼，冷卻 8 秒；4 秒內幾乎不受傷，但也幾乎不能動。
- 凋零骷髏：射出凋零頭顱，冷卻 2 秒。
- 快樂惡魂：安撫，冷卻 10 秒；10 秒內 16 格內的敵對生物放掉目標，不再選你當目標（打牠們會反擊）。
- 豹貓：撲躍，冷卻 2 秒；向前低跳。
- 鸚鵡：歌唱，冷卻 10 秒；10 秒內自己和 8 格內玩家速度 II、挖掘加速 II。
- 骷髏馬：疾馳，冷卻 5 秒；5 秒內速度 III、跳躍提升 II。
- 殭屍馬：踐踏，冷卻 3 秒；前方 6 格扇形內的生物受 5 點傷害並被撞飛。
- 發光魷魚：墨雲，冷卻 6 秒；8 格內的生物失明 5 秒、發光 8 秒。
- 蝌蚪：水花，冷卻 5 秒；8 秒內 6 格內的玩家不著火、可水下呼吸，附近的火會熄滅。
- 熱帶魚：水泡，冷卻 2 秒；把視線前方 8 格內的生物推走，不傷害。
- 螢光蝠（礦世界）：閃光，冷卻 6 秒；16 格內的生物發光 10 秒，自己夜視 15 秒。
- 寶石史萊姆（礦世界）：彈跳，冷卻 3 秒；跳得很高，起跳時 3 格內的生物被擊退。
- 礦蟲（礦世界）：鑽地，冷卻 1.5 秒；沿面向挖一條 1 寬 2 高、6 格長的隧道（石頭、泥土、礦石）。
- 技能護符、終局護符、綠寶石盔甲強化會影響冷卻和威力（第十三、十四章）。
# 被動能力
- 飛行（連按兩下跳躍起飛）：蝙蝠、蜜蜂、悅靈、鸚鵡、幻翼、惱鬼、烈焰人、惡魂、快樂惡魂。
- 爬牆：蜘蛛、洞穴蜘蛛。
- 夜視：蝙蝠、貓、狐狸、蜘蛛、洞穴蜘蛛、幻翼、發光魷魚、守衛者。
- 水下呼吸：溺屍、青蛙、海龜、守衛者、骷髏馬；魚類和魷魚本來就住在水裡。
- 游得快：溺屍、青蛙、海龜、守衛者、北極熊。
- 緩降：雞。
- 不受摔落傷害：雞、貓、豹貓、兔子、青蛙、史萊姆、岩漿立方怪、微風使者，以及所有會飛的形態。
- 免疫火焰：本身就防火的生物，例如烈焰人、惡魂、岩漿立方怪、熾足獸、凋零骷髏。
# 弱點
- 白天曬到太陽會著火：殭屍、骷髏等不死生物。戴頭盔可以擋，但頭盔會耗損。
- 碰水會受傷（包括下雨）：終界使者、烈焰人、雪人、熾足獸。
- 離開水會窒息（約 15 秒）：鱈魚、鮭魚、河豚、熱帶魚、魷魚、發光魷魚、守衛者、蝌蚪。
- 血量跟著形態走：小型形態血少，大型形態血多但目標也大。""",
     """# Active skills (R)
In a form with a skill, press R in the world. Cooldowns are in seconds and reset when you change form.
- Creeper: explode, radius 3. The fuse is 1.5 seconds; press again to cancel. You take no damage and turn back into a human. No cooldown by default, and it breaks blocks.
- Blaze: small fireball, 0.3 s cooldown, sets targets on fire.
- Ghast: fireball, 1 s cooldown, explodes.
- Enderman: teleport where you look, up to 32 blocks, 1 s cooldown.
- Skeleton, stray, bogged: one fully drawn arrow, 0.5 s cooldown; the pillager's arrow hits harder. Arrows cannot be picked up.
- Breeze: wind charge, 0.5 s cooldown; knocks things back.
- Snow golem: snowball, 0.2 s cooldown.
- Llama, trader llama: spit, 0.5 s cooldown.
- Evoker: a line of 12 fangs, 2 s cooldown.
- Phantom: dive, 3 s cooldown; charges about 8 blocks along your view, hurting (6) and knocking back what it hits.
- Camel: dash, 3 s cooldown; a horizontal charge that shoves creatures aside without hurting them.
- Allay: heal pulse, 5 s cooldown; heals you and the players and animals within 6 blocks by 2 hearts.
- Frog: tongue, 2 s cooldown; pulls the creature or item under your crosshair to you, up to 12 blocks.
- Polar bear: slam, 4 s cooldown; creatures within 4 blocks take 4 damage, are knocked back and slowed.
- Turtle: shell, 8 s cooldown; for 4 seconds you are nearly invulnerable and nearly unable to move.
- Wither skeleton: wither skull, 2 s cooldown.
- Happy ghast: soothe, 10 s cooldown; for 10 seconds hostile mobs within 16 blocks drop their target and stop picking you (hit one and it fights back).
- Ocelot: pounce, 2 s cooldown; a low leap forward.
- Parrot: sing, 10 s cooldown; for 10 seconds you and players within 8 blocks get Speed II and Haste II.
- Skeleton horse: gallop, 5 s cooldown; 5 seconds of Speed III and Jump Boost II.
- Zombie horse: trample, 3 s cooldown; creatures in a 6-block cone take 5 damage and are thrown.
- Glow squid: ink cloud, 6 s cooldown; creatures within 8 blocks are blinded for 5 seconds and glow for 8.
- Tadpole: splash, 5 s cooldown; for 8 seconds players within 6 blocks are put out and can breathe underwater, and fires nearby go out.
- Tropical fish: bubbles, 2 s cooldown; pushes creatures in an 8-block line away without hurting them.
- Glow bat (mine world): flash, 6 s cooldown; creatures within 16 blocks glow for 10 seconds, and you get night vision for 15.
- Gem slime (mine world): bounce, 3 s cooldown; a high jump that knocks back creatures within 3 blocks as you leave the ground.
- Ore worm (mine world): tunnel, 1.5 s cooldown; digs a 1-wide, 2-high, 6-long tunnel in the direction you face (stone, dirt, ores).
- The skill charm, the master's charm and emerald armour upgrades change cooldown and power (chapters 13 and 14).
# Passive abilities
- Flight (double-tap jump to take off): bat, bee, allay, parrot, phantom, vex, blaze, ghast, happy ghast.
- Wall climbing: spider, cave spider.
- Night vision: bat, cat, fox, spider, cave spider, phantom, glow squid, guardian.
- Water breathing: drowned, frog, turtle, guardian, skeleton horse; fish and squid live in water anyway.
- Fast swimming: drowned, frog, turtle, guardian, polar bear.
- Slow falling: chicken.
- No fall damage: chicken, cat, ocelot, rabbit, frog, slime, magma cube, breeze, and every flying form.
- Fire immunity: every mob that is fireproof itself, such as the blaze, ghast, magma cube, strider and wither skeleton.
# Weaknesses
- Burning in daylight: zombies, skeletons and other undead. A helmet protects you but wears down.
- Hurt by water (rain too): enderman, blaze, snow golem, strider.
- Suffocating out of water (about 15 seconds): cod, salmon, pufferfish, tropical fish, squid, glow squid, guardian, tadpole.
- Health follows the form: small forms have little, big ones have lots but are easy to hit.""",
     ["ch5/first_skill"]),

    ("combat:feather_charm", "飾品", "Trinkets",
     """# 飾品欄
- 按 K 打開飾品欄，預設 3 格。
- 飾品背包放在身上任何位置（物品欄、快捷欄、副手都算），每個多 1 格，可以疊加，最多 13 格。
- 飾品背包配方：上排線、鐵錠、線；中排皮革 ×3；下排皮革 ×3。
- 同一種飾品只能裝一個。變身後也有效，死亡不會掉。
- 如果形態本身就有同樣的效果（例如蝙蝠的夜視），以形態為準，不會疊加。
# 十三種飾品
大多數是「中間一個材料、上下左右四個另一種材料」的十字形配方。
- 羽毛護符：免疫摔落傷害。羽毛 ×4＋中間金錠。
- 夜視護符：持續夜視。金錠 ×4＋中間金胡蘿蔔。
- 鰓之護符：水下呼吸。海晶碎片 ×4＋中間鸚鵡螺殼。
- 技能護符：變身技能冷卻 −30%。金錠 ×4＋中間終界之眼。
- 抗火戒指：免疫火焰與岩漿。烈焰粉 ×4＋中間熔岩膏。
- 疾行靴扣：移動速度 +15%。糖 ×4＋中間金錠。
- 彈簧鞋墊：跳躍力 +25%。黏液球 ×4＋中間活塞。
- 再生護符：每 3 秒回 1 點生命。金錠 ×4＋中間惡魂之淚。
- 磁鐵：5 格內的掉落物飛向你。上中排兩側各一個鐵錠（共 4 個，中間空），下排紅石、金錠、紅石。
- 防爆護符：爆炸傷害減半。黑曜石 ×4＋中間火藥。
- 荊棘戒指：被近戰攻擊時反彈 30% 傷害（至少 1 點）。仙人掌 ×4＋中間金錠。
- 狩獵護符：擊殺生物時 50% 機率掉落物多掉一份（對玩家無效）。骨頭 ×4＋中間兔子腳。
- 終局護符：變身技能沒有冷卻、威力 ×1.5，而且變身時生命上限不會低於人類。要打贏真・形態之王拿形態核心（第十五章）。
# 搭配建議
- 礦世界探索：夜視護符＋磁鐵＋羽毛護符。
- 打 Boss：防爆護符、抗火戒指、再生護符、疾行靴扣。
- 變身流派：技能護符，之後換成終局護符。""",
     """# Trinket slots
- Press K for your trinket slots: 3 to start.
- Each trinket bag you carry anywhere (inventory, hotbar or offhand) adds a slot; they stack, up to 13 slots.
- Trinket bag recipe: top string, iron ingot, string; middle 3 leather; bottom 3 leather.
- You can wear only one of each trinket. They work while transformed and stay with you when you die.
- If your form already has the same effect (such as a bat's night vision), the form wins and they do not stack.
# The thirteen trinkets
Most are a cross: one item in the middle with four of another above, below and to the sides.
- Feather charm: no fall damage. 4 feathers + gold ingot in the middle.
- Night vision charm: permanent night vision. 4 gold ingots + golden carrot.
- Gills charm: water breathing. 4 prismarine shards + nautilus shell.
- Skill charm: shapeshift skill cooldown -30%. 4 gold ingots + eye of ender.
- Fire ring: immune to fire and lava. 4 blaze powder + magma cream.
- Swift buckle: +15% speed. 4 sugar + gold ingot.
- Spring insole: +25% jump. 4 slimeballs + piston.
- Regeneration charm: 1 health every 3 seconds. 4 gold ingots + ghast tear.
- Magnet: drops within 5 blocks fly to you. Iron ingots on both sides of the top and middle rows (4, centre empty), bottom redstone, gold ingot, redstone.
- Blast ward: half damage from explosions. 4 obsidian + gunpowder.
- Thorns ring: reflects 30% of melee damage (at least 1). 4 cactus + gold ingot.
- Hunter charm: 50% chance of double loot from kills (not players). 4 bones + rabbit's foot.
- Master's charm: no skill cooldown, skill power ×1.5, and transforming never lowers your max health below a human's. Needs the form core from the True Form King (chapter 15).
# Suggested sets
- Mine world digging: night vision charm, magnet, feather charm.
- Boss fights: blast ward, fire ring, regeneration charm, swift buckle.
- Shapeshifting: the skill charm, later the master's charm.""",
     ["ch6/trinket"]),

    ("minecraft:anvil", "鐵砧強化", "Anvil upgrades",
     """# 盔甲強化
- 在鐵砧左欄放盔甲、右欄放礦物，每個礦物 +1 級，不花經驗。一次放多個就一次升多級，只扣實際用掉的。
- 粗礦、錠、寶石都算；煤炭只認煤，不認木炭。
- 每件盔甲最多 10 級，每種礦物最多 4 級。已經有 6 級以上時，之後每級要 2 個礦物。
- 盔甲有損壞、右欄剛好是修理材料時，會照原版修理，不會強化。
- 屬性寫在盔甲上，提示框會顯示，另有一行「礦物強化：x / 10」。
# 礦物對應的屬性（每級）
- 煤炭：擊退抗性 +0.05。
- 銅：護甲值 +0.5。
- 鐵：護甲韌性 +0.5。
- 金：幸運 +0.5。
- 紅石：移動速度 +2%。
- 青金石：水下氧氣 +0.5。
- 鑽石：最大生命 +1（半顆心）。
- 綠寶石：近戰傷害 +0.5，而且每級讓變身技能威力 +5%（四件全滿最多 +80%）。
- 技能威力影響骷髏的箭速、苦力怕爆炸半徑、惡魂火球、終界使者傳送距離、喚魔者尖牙數量。
# 弓強化
- 一樣在鐵砧：左欄弓、右欄材料，每個 +1 級，不花經驗。每把弓最多 8 級；只支援弓，不支援弩。
- 紅石（最多 3 級）：拉弓速度每級 +25%，3 級是 1.75 倍。
- 終界珍珠（1 級）：箭直線飛行不下墜，5 秒沒打中就消失。
- 烈焰粉（最多 3 級）：命中點燃 3／6／9 秒。
- 黏液球（最多 3 級）：命中給緩速 I～III，3／6／9 秒。
- 火藥（最多 3 級）：命中生物時爆炸（威力 1.5／2.0／2.5），不破壞方塊，但靠太近自己也會被炸到。
- 每支箭的命中效果只觸發一次，穿透箭只對第一個目標有效。
- 變成骷髏按 R 射的箭，會繼承手上強化弓的效果（拉弓速度除外）。""",
     """# Armour upgrades
- Put armour in the anvil's left slot and an ore in the right: each ore adds a level, with no experience cost. Put in several to gain several levels; only what is used is taken.
- Raw ore, ingots and gems all count; coal means coal, not charcoal.
- Each piece takes up to 10 levels in total and up to 4 per ore. From the 7th level on, each level needs 2 ores.
- A damaged piece with its repair material on the right is repaired the vanilla way instead.
- The stats are written on the armour and shown in the tooltip, with a "Mineral upgrades: x / 10" line.
# What each ore gives (per level)
- Coal: knockback resistance +0.05.
- Copper: armour +0.5.
- Iron: armour toughness +0.5.
- Gold: luck +0.5.
- Redstone: speed +2%.
- Lapis: oxygen bonus +0.5.
- Diamond: max health +1 (half a heart).
- Emerald: melee damage +0.5, and +5% shapeshift skill power per level (up to +80% with four full pieces).
- Skill power affects skeleton arrow speed, creeper blast radius, ghast fireballs, enderman teleport range and evoker fang count.
# Bow upgrades
- Also in the anvil: bow on the left, material on the right, one level each, no experience. Up to 8 levels per bow; bows only, not crossbows.
- Redstone (up to 3): +25% draw speed per level, 1.75 times at level 3.
- Ender pearl (1): arrows fly straight without dropping and vanish after 5 seconds without a hit.
- Blaze powder (up to 3): sets targets on fire for 3/6/9 seconds.
- Slimeball (up to 3): Slowness I-III for 3/6/9 seconds.
- Gunpowder (up to 3): explodes on hitting a mob (power 1.5/2.0/2.5). It never breaks blocks but can hit you if you are close.
- Each arrow's on-hit effect fires once; a piercing arrow only affects its first target.
- Arrows from a skeleton form's R skill take the upgrades of the bow in your hand (except draw speed).""",
     ["ch6/armor_upgrade", "ch6/bow_upgrade"]),

    ("combat:form_altar", "形態之王", "The Form King",
     """# 形態之王是什麼
整合包的最終 Boss：牠會依序變成各種形態，用那個形態的技能攻擊你。是「收集形態」的畢業考。
# 準備
- 擊殺凋零和終界龍，取得一顆下界之星和一瓶龍息（龍息要用玻璃瓶在龍的吐息裡裝）。
- 解鎖所有一般形態（34 種）。稀有形態不用收。
- 做形態祭壇：上排黑曜石、終界之眼、黑曜石；中排黑曜石、鑽石、黑曜石；下排黑曜石 ×3。
- 祭壇要放在主世界，周圍半徑 32 格是戰場，選一塊平坦開闊的地方。
# 召喚
- 背包裡帶著下界之星和龍息，右鍵祭壇。材料在 Boss 出現時才會消耗。
- 條件不足時會提示缺什麼，例如目前解鎖了幾種一般形態。
- 打贏後祭壇要冷卻 30 分鐘才能再召喚。
# 戰鬥
- 血量 600，多人時每多一位 +50%（最多算 4 人）。畫面上方的血條會顯示名稱和目前的形態。
- 只有玩家造成的傷害有效（含箭、荊棘反彈、玩家引起的爆炸）；火、岩漿、摔落和牠自己的爆炸傷不到牠。單次傷害最多扣 5%。
- 每次換形態後 3 秒內，牠受到的傷害 +25%，抓準時機輸出。
- 階段一：骷髏（射箭）、烈焰人（小火球）、微風使者（風彈），每 20 秒換一次。多移動、找掩體。
- 階段二：喚魔者（一排尖牙）、終界使者（傳送到你身後，出現前 0.5 秒有粒子預警）、蜘蛛（撲擊），每 15 秒換一次。
- 階段三：惡魂（大火球）、苦力怕、快速烈焰人，每 10 秒換一次。苦力怕形態會發出引信聲並膨脹，爆炸範圍很大；爆炸後牠虛弱 5 秒（受傷 +50%），是最大的輸出機會。
- 牠會走動：骷髏和喚魔者與你保持距離並左右橫移，蜘蛛走近再撲，苦力怕點著引信朝你衝，烈焰人、微風使者和惡魂在你頭上繞圈。
- 階段二、三的形態還會借用另一個形態的技能（例如喚魔者會同時射箭、惡魂會同時召尖牙），不過間隔比本技能長。
- 戰鬥不會破壞方塊，爆炸不傷地形，火球不會點火。
# 輸了怎麼辦
- 戰場內玩家全部倒下超過 3 秒，或所有人離開祭壇 40 格超過 10 秒，就算失敗。
- 失敗時 Boss 消失，下界之星和龍息會掉回祭壇上，可以再試。
- 伺服器關閉時進行中的戰鬥也算失敗並退還材料。
# 獎勵
- 每位打過牠的玩家各得 10～20 個形態碎片和 500 經驗，直接放進背包。
- 形態印記：8 個碎片＋終界之眼（隨意擺）。蹲下使用切換要解鎖的稀有形態（提示框會顯示目標），直接使用就解鎖並消耗印記。
- 形態之種：9 個碎片（隨意擺），用來召喚真・形態之王。
# 真・形態之王
- 解鎖全部 60 種形態（含稀有），帶著形態之種右鍵祭壇。只消耗形態之種，不需要下界之星和龍息。
- 血量 2 倍（1200）、技能和換形態更快、箭與近戰傷害 1.5 倍、苦力怕爆炸 1.5 倍。
- 打贏後除了碎片，還會得到 1 個形態核心和 1500 經驗。
- 終局護符：上排終界之眼；中排金錠、形態核心、金錠；下排終界之眼。
# 推薦裝備
- 防爆護符（惡魂、苦力怕）、抗火戒指（烈焰人、惡魂）、荊棘戒指（終界使者突襲）。
- 疾行靴扣、彈簧鞋墊躲尖牙和火球；再生護符撐長期戰。
- 鑽石強化加生命撐過爆炸；強化弓遠距離輸出（爆裂箭也受單次傷害上限限制）。""",
     """# What it is
The modpack's final boss: it cycles through forms and attacks with each form's skill. It is the graduation exam for collecting forms.
# Getting ready
- Kill the Wither and the Ender Dragon for a nether star and a dragon's breath (bottle the dragon's breath attack with a glass bottle).
- Unlock every common form (34). Rare forms are not needed.
- Craft a form altar: top obsidian, eye of ender, obsidian; middle obsidian, diamond, obsidian; bottom 3 obsidian.
- Place the altar in the overworld; the arena is 32 blocks around it, so pick flat open ground.
# Summoning
- Carry the nether star and the dragon's breath and right-click the altar. They are only used up when the boss appears.
- If you are not ready, it tells you what is missing, such as how many common forms you have.
- After a win the altar needs 30 minutes before the next summon.
# The fight
- 600 health, +50% for each extra player (up to 4 counted). The boss bar shows its name and current form.
- Only damage from players counts (arrows, thorns and explosions you cause included); fire, lava, falling and its own explosions do nothing. One hit takes at most 5%.
- For 3 seconds after each form change it takes 25% more damage: time your attacks.
- Phase one: skeleton (arrows), blaze (small fireballs), breeze (wind charges), changing every 20 seconds. Keep moving and use cover.
- Phase two: evoker (a line of fangs), enderman (teleports behind you, with particles 0.5 seconds before), spider (pounces), every 15 seconds.
- Phase three: ghast (fireballs), creeper, fast blaze, every 10 seconds. The creeper hisses and swells and its blast is huge; afterwards the boss is weakened for 5 seconds (+50% damage taken), the best window of the fight.
- It moves: skeleton and evoker keep their distance and strafe, the spider walks in and pounces, the creeper charges you with its fuse lit, and the blaze, breeze and ghast circle above you.
- In phases two and three each form also borrows another form's skill (the evoker shoots arrows too, the ghast raises fangs), on a longer interval than its own skill.
- The fight never breaks blocks: explosions spare the ground and fireballs light nothing.
# If you lose
- If every player in the arena is down for 3 seconds, or everyone is more than 40 blocks from the altar for 10 seconds, the fight is lost.
- The boss vanishes and the nether star and dragon's breath drop back on the altar, so you can try again.
- A fight running when the server shuts down also counts as lost and refunds the offering.
# Rewards
- Every player who hurt it gets 10-20 form shards and 500 experience, straight into the inventory.
- Form mark: 8 shards + an eye of ender (any layout). Sneak-use it to choose which rare form it unlocks (the tooltip shows the target); use it normally to unlock that form and use up the mark.
- Form seed: 9 shards (any layout), which summons the True Form King.
# The True Form King
- With all 60 forms unlocked (rare ones too), right-click the altar holding a form seed. Only the seed is used: no nether star or dragon's breath.
- Double health (1200), faster skills and form changes, 1.5 times arrow and melee damage, 1.5 times creeper blasts.
- Winning gives the shards plus a form core and 1500 experience.
- Master's charm: top eye of ender; middle gold ingot, form core, gold ingot; bottom eye of ender.
# Recommended gear
- Blast ward (ghast, creeper), fire ring (blaze, ghast), thorns ring (enderman ambushes).
- Swift buckle and spring insole to dodge fangs and fireballs; regeneration charm for the long fight.
- Diamond armour upgrades for health to survive blasts; an upgraded bow for range (explosive arrows are capped per hit too).""",
     ["ch6/vanilla_bosses", "ch6/form_king", "ch6/master_charm"]),

    ("minecraft:spyglass", "查詢", "Lookup",
     """# 物品清單
- 打開背包、箱子或任何容器畫面時，右側會出現物品清單，列出遊戲裡所有物品。
- 上方有搜尋框，下方可以翻頁。
# 看配方與用途
- 指著物品（清單裡或物品欄裡都可以）按 R：看它怎麼做出來。
- 按 U：看它可以拿來做什麼。
- 配方頁包含工作台、熔爐、釀造等原版配方，也有模組的特殊卡片：世界鍋釜、城市交易、通緝犯、礦世界生物、儲存單元升級／拆解、變身形態、生物與方塊的掉落物。
- 煉金背包的能量值會顯示在物品的提示框裡。
# 搜尋語法
- 直接打字：搜尋名稱。
- @模組：例如 @logistics 只看物流模組的物品。
- #標籤：例如 #logs 找所有原木。
- $說明：搜尋提示框裡的文字。
- 用空白隔開可以組合，必須全部符合，例如「@minecraft 劍」。
# 書籤
- 指著物品按 A 加入書籤，書籤會釘在清單最上面一列。再按一次移除。
# 自動填入
- 開著工作台、熔爐等畫面看配方時，配方頁右上角的 + 會把材料從背包填進格子（Shift 盡量多）。
- 在合成終端機上，會直接從倉庫取材料。
- 有開倉庫終端機時，配方格子右下角會顯示倉庫庫存（綠色夠、紅色不夠），以及可以合成幾次。
# 作弊模式
- 創造模式、單人世界的擁有者或管理員，可以按 F9（或點清單上方的「作弊：開／關」）切換。
- 作弊模式下點清單的物品直接拿：左鍵一組，右鍵一個。""",
     """# The item list
- Open your inventory, a chest or any container and an item list appears on the right with every item in the game.
- Search at the top, pages at the bottom.
# Recipes and uses
- Point at an item (in the list or your inventory) and press R to see how it is made.
- Press U to see what it is used for.
- Pages cover crafting, smelting, brewing and other vanilla recipes, plus the mods' own cards: world cauldron, city trades, wanted mobs, mine world creatures, storage cell upgrade/split, shapeshift forms, and mob and block drops.
- Alchemy backpack energy values show in item tooltips.
# Search syntax
- Plain text: names.
- @mod: for example @logistics shows only logistics items.
- #tag: for example #logs finds every log.
- $text: searches tooltip text.
- Separate terms with spaces to combine them; all must match, for example "@minecraft sword".
# Bookmarks
- Point at an item and press A to bookmark it; bookmarks are pinned to the top row of the list. Press again to remove.
# Filling recipes
- With a crafting table, furnace or similar open, the + at the top right of a recipe page fills the slots from your inventory (shift for as many as possible).
- On a crafting terminal it takes ingredients straight from the warehouse.
- With a warehouse terminal open, each recipe slot shows warehouse stock in its corner (green enough, red short) and how many times you can craft.
# Cheat mode
- In creative, as the owner of a single-player world or as an operator, press F9 (or click "Cheat: on/off" above the list).
- In cheat mode, clicking an item in the list gives it to you: left a stack, right one.""",
     []),

    ("minecraft:writable_book", "指令與問答", "Commands & FAQ",
     """# 所有人都能用的指令
- /guidebook：補一本手冊（身上沒有手冊時）。
# 管理員指令
需要遊戲管理員權限（單人世界要開啟作弊）。
- /shapeshift unlock <生物>：解鎖某個形態。
- /shapeshift unlockall：解鎖全部形態。
- /shapeshift reset：清除所有解鎖。
- /shapeshift into <生物>：直接變成某個形態。
- /shapeshift human：變回人類。
- /formking summon：直接召喚形態之王。
- /formking summon_true：直接召喚真・形態之王。
- /formking abort：中止進行中的戰鬥。
# 設定檔
放在遊戲資料夾的 config/ 裡，改完要重新啟動遊戲。
- alchemy-backpack-energy.json：每種物品的能量值（default 是沒列出物品的預設值，0 代表不能轉化）。
- shapeshift.json：換形態冷卻、苦力怕爆炸冷卻、爆炸是否破壞方塊、怪物是否忽略怪物形態、鐵魔像是否追打怪物形態、不能變身的生物清單。
- lookup.json：作弊模式開關。
# 常見問題
- 終端機打不開或顯示錯誤？檢查網路上是不是有兩個控制器、兩個頻道方塊或兩個區塊載入器，電纜是不是有接到控制器。
- 箱子沒有算進倉庫？箱子要直接貼著電纜或其他物流方塊。
- 無線終端打不開？要先和頻道卡合成綁定，並站在同頻道天線的範圍內。
- 傳送裝置不動？確認目的地已選、狀態不是關閉，而且頻道有至少 10 EMC。
- 在礦世界死掉回到主世界？先到城市旅店的床設定重生點。
- 物品放不進煉金背包？帶附魔、改過名字的物品不能轉化。
- 召喚不了形態之王？右鍵祭壇看缺什麼：一般形態、下界之星、龍息，或者祭壇還在冷卻。
- 多人遊戲：伺服器和所有玩家必須使用相同版本的模組。""",
     """# Commands for everyone
- /guidebook: a new handbook (when you carry none).
# Operator commands
These need operator rights (cheats on in single-player).
- /shapeshift unlock <mob>: unlock a form.
- /shapeshift unlockall: unlock every form.
- /shapeshift reset: clear all unlocks.
- /shapeshift into <mob>: become a form right away.
- /shapeshift human: turn back into a human.
- /formking summon: summon the Form King right away.
- /formking summon_true: summon the True Form King right away.
- /formking abort: stop a running fight.
# Config files
In the game folder's config/ directory; restart the game after editing.
- alchemy-backpack-energy.json: every item's energy value ("default" covers unlisted items; 0 means it cannot be converted).
- shapeshift.json: form change cooldown, creeper blast cooldown, whether blasts break blocks, whether monsters ignore monster forms, whether iron golems hunt monster forms, and mobs you cannot become.
- lookup.json: cheat mode on or off.
# FAQ
- The terminal will not open or shows an error? Look for two controllers, two channel blocks or two chunk loaders on the network, and make sure the cables reach the controller.
- A chest is not in the warehouse? It must touch a cable or another logistics block directly.
- The wireless terminal will not open? Bind it by crafting it with a channel card, and stand within range of an antenna on the same channel.
- The teleporter does nothing? Check that a destination is picked, it is not closed, and the channel has at least 10 EMC.
- Dying in a mine world sends you to the overworld? Set your respawn point on a bed in a city inn first.
- An item will not go into the alchemy backpack? Enchanted or renamed items cannot be converted.
- The Form King will not come? Right-click the altar to see what is missing: common forms, the nether star, the dragon's breath, or the altar's cooldown.
- Multiplayer: the server and every player need the same mod versions.""",
     []),
]
