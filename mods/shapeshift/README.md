# 變身 Shapeshift

Minecraft Java 26.3／Fabric，屬於本模組包的一部分。擊殺生物即可解鎖並變成牠。

## 目前進度：階段 1–4 全部完成

- 親手擊殺任何生物（含其他模組的）→ 永久解鎖該形態，死亡不會遺失。
- 按 `V`（可在「控制」設定更改）開啟變身選單：3D 預覽、搜尋、翻頁、「變回人類」；滑鼠移到格子上會列出血量與能力。
- 變身後：
  - 外觀、碰撞箱、視角高度換成該生物；第三人稱鏡頭距離跟著體型調整。
  - 自動繼承該生物的血量、攻擊力、護甲、擊退抗性、跨步高度、安全摔落高度（鐵魔像一拳 15、狼 4）。
  - 會著火免疫的生物（烈焰人、惡魂……）自動防火；水生生物自動水下呼吸、快速游泳。
  - 另外 37 種生物有手調的能力與數值（見下方）。
- 按 `R`（可在「控制」設定更改）使用形態技能：

  | 技能 | 形態 | 說明 |
  | --- | --- | --- |
  | 自爆 | 苦力怕 | 1.5 秒引信（身體會膨脹閃白），再按一次取消；爆炸不傷自己，接著變回人類 |
  | 火球 | 烈焰人 | 小火球 |
  | 爆炸火球 | 惡魂 | |
  | 傳送 | 終界使者 | 傳送到準心指的方塊，最遠 32 格 |
  | 射箭 | 骷髏、流髑、沼骸、掠奪者 | 箭撿不起來 |
  | 風彈 | 旋風人 | |
  | 雪球 | 雪人 | |
  | 吐口水 | 羊駝、商人羊駝 | |
  | 尖牙 | 喚魔者 | 前方一排 12 根 |

  換形態時技能冷卻會重置。
- 弱點：
  - 白天會著火（戴頭盔可擋，頭盔會耗損）：原版會曬傷的生物自動套用（殭屍、骷髏、夜魅……），其他模組的生物只要在 `burn_in_daylight` 標籤裡也會。
  - 碰水、淋雨會受傷：終界使者、烈焰人、雪人、熾足獸。
  - 離開水會窒息（約 15 秒）：鱈魚、鮭魚、熱帶魚、河豚、魷魚、發光魷魚、蝌蚪、深海守衛。
- 生物反應：
  - **同類不打你**：同種生物不會攻擊你；變成任何怪物時，其他怪物都不會攻擊你（殭屍、骷髏、苦力怕……）。
  - **天敵**：鐵魔像、雪人會追打怪物形態的你；狼會追骷髏、羊、兔子、狐狸形態；狐狸、山貓、貓會追雞；羊駝會追狼。
  - **害怕**：變成貓或山貓時苦力怕會逃；變成狼時骷髏、兔子、狐狸會逃；變成殭屍時村民和流浪商人會逃。怕你的生物也不會攻擊你。
  - 你先動手打牠，牠一定會還手。變回人類時，因為形態才追你的生物會放棄。
- 打磨：
  - 受傷、死亡時發出該生物的聲音，平常偶爾會發出牠的叫聲。
  - 第一人稱不顯示人類的手臂（手上的物品照樣顯示）。
  - 畫面左下角顯示目前形態與 `R` 鍵技能。
  - 其他玩家看得到你頭上的名字。
  - 變身的煙霧只給別人看，不會擋住自己的第一人稱視線。
- 空間不夠時拒絕變身（包括變回人類）；死亡後自動變回人類。
- 盔甲保留防禦力，但不顯示；所有形態都能使用物品。

已知限制：鍵位名稱依 macOS 輸入法顯示（例如注音輸入法下 R 顯示為「ㄐ」），這是原版行為，實際按的仍是 R 鍵。

## 形態設定檔（資料包）

每種生物一個檔案：`data/<命名空間>/shapeshift/forms/<生物>.json`，例如 `data/minecraft/shapeshift/forms/horse.json` 就是 `minecraft:horse`。
模組內建的在 `src/main/resources/data/minecraft/shapeshift/forms/`；也可以用世界資料包覆寫，改完輸入 `/reload` 立即生效（變身中的玩家也會即時更新）。

```json
{
  "max_health": 22,
  "flying_speed": 0.06,
  "abilities": ["flight", "night_vision", "no_fall_damage"],
  "active": {"type": "small_fireball", "cooldown": 6},
  "weaknesses": ["water_damage"],
  "attributes": {
    "movement_speed": {"amount": 1.0, "operation": "add_multiplied_base"},
    "jump_strength": {"amount": 0.3}
  }
}
```

| 欄位 | 說明 |
| --- | --- |
| `max_health` | 覆寫最大血量（不寫就用該生物原本的） |
| `flying_speed` | 有 `flight` 時的飛行速度，預設 0.05（創造模式同速） |
| `abilities` | `flight` 飛行、`climb` 爬牆、`night_vision` 夜視、`fire_immune` 防火、`water_breathing` 水下呼吸、`swim_fast` 快速游泳、`slow_falling` 緩降、`no_fall_damage` 免摔傷 |
| `active` | R 鍵技能：`type` 為 `explode`、`small_fireball`、`fireball`、`teleport`、`spit`、`arrow`、`wind_charge`、`snowball`、`fangs`；可選 `cooldown`（tick，20 = 1 秒）、`power`（爆炸半徑／傳送距離／箭速／尖牙數量）、`fuse`（自爆引信 tick，0 = 立刻爆） |
| `weaknesses` | `burns_in_daylight` 白天著火、`water_damage` 碰水受傷、`needs_water` 離水窒息 |
| `scares` | 看到你會逃跑的生物（ID 或 `#標籤`，例如 `"minecraft:creeper"`、`"#minecraft:skeletons"`） |
| `hunted_by` | 看到你會攻擊的生物（同上） |
| `attributes` | 任何原版屬性（可省略 `minecraft:`）。`operation`：`add_value`（加數值，預設）、`add_multiplied_base`（`1.0` = 基礎值 ×2）、`add_multiplied_total` |

常用屬性：`movement_speed`、`jump_strength`、`step_height`、`gravity`、`safe_fall_distance`、`attack_damage`、`armor`、`knockback_resistance`、`block_interaction_range`、`entity_interaction_range`、`water_movement_efficiency`、`oxygen_bonus`。

內建手調的形態：蝙蝠、鸚鵡、蜜蜂、悅靈、惱鬼、夜魅、烈焰人、惡魂、快樂惡魂（飛行）；雞（緩降）；馬、驢、騾、骷髏馬、殭屍馬、駱駝、羊駝、商人羊駝（跑速、跳躍）；蜘蛛、洞穴蜘蛛（爬牆、夜視）；狼、貓、山貓、狐狸、兔子、山羊、青蛙、史萊姆、岩漿立方怪、旋風人、北極熊；鐵魔像（慢、爆炸不擊退）；終界使者（手長）；發光魷魚、海龜、深海守衛、沉屍。

## 設定檔 `config/shapeshift.json`

```json
{
  "cooldown_seconds": 0,
  "explode_cooldown_seconds": 0,
  "explosions_break_blocks": true,
  "monsters_ignore_monster_forms": true,
  "golems_hunt_monster_forms": true,
  "blocked_entities": ["minecraft:ender_dragon", "minecraft:wither", "minecraft:warden",
                       "minecraft:elder_guardian", "minecraft:armor_stand", "minecraft:mannequin"]
}
```

| 欄位 | 說明 |
| --- | --- |
| `cooldown_seconds` | 每次變身之間的冷卻 |
| `explode_cooldown_seconds` | 苦力怕自爆後多久才能再變身（想要自爆冷卻就改這個） |
| `explosions_break_blocks` | 自爆會不會炸壞方塊（也受原版 `mob_griefing` 規則影響） |
| `monsters_ignore_monster_forms` | 變成怪物時，其他怪物不攻擊你 |
| `golems_hunt_monster_forms` | 變成怪物時，鐵魔像和雪人會攻擊你（玩家自己造的鐵魔像依原版規則不會攻擊玩家） |
| `blocked_entities` | 不能解鎖、不能變的生物 |

修改後重啟生效。

## 指令（需要管理員權限）

| 指令 | 作用 |
| --- | --- |
| `/shapeshift unlock <生物>` | 解鎖一種形態 |
| `/shapeshift unlockall` | 解鎖所有形態 |
| `/shapeshift into <生物>` | 直接變身（忽略解鎖、冷卻、空間） |
| `/shapeshift human` | 變回人類 |
| `/shapeshift reset` | 變回人類並清空解鎖 |

## 開發

```sh
./gradlew :mods:shapeshift:build              # 編譯 + 單元測試
./gradlew :mods:shapeshift:runClient          # 只載入這個模組的開發遊戲
./gradlew :mods:shapeshift:runClientGameTest  # 自動化遊戲內測試（會開遊戲視窗）
```

`runClientGameTest` 會建立世界，自動完成：擊殺豬解鎖、鎖定形態被拒、開選單、變成 9 種生物並檢查碰撞箱與血量；
被動能力（蝙蝠飛行與夜視、清除效果後自動恢復、馬的速度／跳躍／跨步、鐵魔像攻擊力與鏡頭距離、烈焰人防火、雞緩降、魷魚水下呼吸、蜘蛛爬牆而豬不行、變回後全部還原）；
主動技能（苦力怕引信同步、爆炸不傷自己且變回人類、炸壞方塊並傷害附近的豬、再按取消引信、7 種投射物、終界使者傳送、沒技能的形態不受影響）；
弱點（殭屍中午著火且身體畫出火焰、終界使者泡水受傷、鱈魚上岸空氣下降）；
生物反應（殭屍會追人類但不理殭屍形態、被打會還手、鐵魔像追怪物形態且變回人類後放棄、狼追骷髏形態、苦力怕逃離貓、村民逃離殭屍）；
打磨（豬形態的受傷／死亡音效、第一人稱無手臂、HUD）；
血量比例、天花板太低時拒絕變成鐵魔像、死亡後變回人類且解鎖保留。
截圖在 `build/run/clientGameTest/screenshots/`。

## 程式入口

| 檔案 | 作用 |
| --- | --- |
| `ShapeshiftMod` | 註冊玩家資料（解鎖清單、目前形態）、封包、擊殺／重生／登入事件、指令 |
| `Shapeshifter` | 伺服器端變身：檢查、血量、碰撞箱、特效 |
| `Forms` | 查詢哪些生物可以變、牠們的體型 |
| `FormDefinitions` | 讀取形態資料包、同步給客戶端、算出每個形態最終的能力與屬性 |
| `Passives` | 伺服器端套用／移除屬性、效果、飛行 |
| `Actives` | R 鍵技能（自爆、投射物、傳送、尖牙） |
| `Weaknesses` | 日曬、碰水、離水 |
| `Reactions`、`FleeFormGoal` | 生物對變身玩家的反應（不攻擊、追殺、逃跑） |
| `FormSounds` | 受傷、死亡、平常叫聲 |
| `Ability`、`ActiveType`、`ActiveAbility`、`Weakness`、`FormDefinition`、`ModifierSpec` | 形態 JSON 的格式 |
| `Unlocks`、`FormStats`、`FormPlan` | 純邏輯（單元測試） |
| `ShapeshiftConfig` | 設定檔 |
| `mixin/AvatarMixin` | 改玩家碰撞箱與視角高度 |
| `mixin/LivingEntityMixin` | 爬牆 |
| `mixin/MobMixin` | 讓生物不攻擊友好的變身玩家 |
| `mixin/VillagerHostilesSensorMixin` | 村民把殭屍形態當成殭屍而逃跑 |
| `mixin/PlayerSoundMixin` | 換成生物的受傷／死亡音效 |
| `client/FormScreen` | 變身選單 |
| `client/FormBodies` | 替每個變身玩家準備一隻「假生物」並同步動作、著火、苦力怕膨脹 |
| `client/mixin/LevelExtractorMixin` | 在世界中把玩家換成假生物來畫 |
| `client/mixin/FirstPersonHandsMixin` | 第一人稱隱藏人類手臂 |
| `client/FormHud` | 左下角形態與技能提示 |
