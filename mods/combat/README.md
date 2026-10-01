# 戰鬥 Combat

Minecraft Java 26.3／Fabric，屬於本模組包的一部分。

## 開發

```sh
./gradlew :mods:combat:build       # 只建置這個模組
./gradlew :mods:combat:runClient   # 只載入這個模組的開發遊戲
```

## 程式入口

- `CombatMod`：伺服器／共用註冊。
- `CombatClient`：客戶端畫面、渲染、提示文字。
- `mixin/`、`client/mixin/`：修改原版行為（登記在 `combat.mixins.json`、`combat.client.mixins.json`）。
