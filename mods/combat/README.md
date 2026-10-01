# 戰鬥 Combat

Minecraft Java 26.3／Fabric，屬於本模組包的一部分。設計與數值見 `docs/mainline-outline.md`、`docs/boss-design.md`。

## 內容

- **飾品**：13 種飾品、飾品欄（預設 3 格，按 K 開啟）、飾品背包（放在身上每個 +1 格，上限為飾品種類數）。
- **鐵砧強化**：用礦物強化盔甲屬性，只消耗礦物；用材料強化弓（拉弓速度、箭矢不下墜、火焰／緩速／爆裂箭）。
- **變身連動**：綠寶石盔甲強化提高變身技能強度，技能護符縮短冷卻，骷髏的 R 鍵箭帶上手上弓的強化。只在安裝變身模組時啟用。
- **形態之王**：自製終局 Boss，由形態祭壇召喚；掉落形態核心，用來合成終局護符。

## 開發

```sh
./gradlew :mods:combat:build                  # 只建置這個模組
./gradlew :mods:combat:runClientGameTest      # 實機測試：飾品、強化、Boss 整場戰鬥
python3 scripts/gen_combat_resources.py --force   # 重新產生材質、配方、語言檔
```

## 程式入口

- `CombatMod`：註冊物品、方塊、附件、事件。
- `Trinkets`／`Trinket`：飾品登錄與效果；`TrinketMenu`：飾品欄。
- `ArmorUpgrades`／`BowUpgrades`／`BowEffects`：鐵砧強化。
- `boss/`：形態之王（`FormKingFight` 一場戰鬥、`BossSkills` 技能、`FormKingFights` 召喚與事件）。
- `ShapeshiftLink`：與變身模組的連動。
- `mixin/`：鐵砧、生物、箭矢、火球。
