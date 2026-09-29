# Alan's Pack（模組包開發 monorepo）

Minecraft Java **26.3**／Fabric Loader **0.19.5**／Fabric API **0.160.6+26.3**／Java **25**。

所有自製模組放在同一個 Gradle 專案裡，共用版本與建置設定，最後由 `pack/` 打包成一個模組包。

## 目錄結構

```text
game2/
├── build.gradle          # 所有 mods/* 共用的 Loom 建置慣例
├── settings.gradle       # 自動納入 mods/ 底下每個含 gradle.properties 的資料夾
├── gradle.properties     # ★ 唯一的版本來源：MC / Loader / Fabric API / Java / 模組包名稱
├── mods/
│   └── alchemy-backpack/ # 每個模組一個資料夾
│       ├── gradle.properties   # mod_id、mod_version、maven_group
│       └── src/
│           ├── main/     # 伺服器＋共用：物品、選單、資料、網路封包、設定
│           ├── client/   # 只在客戶端：Screen、提示文字、渲染
│           └── test/     # JUnit：純邏輯（不需啟動遊戲）
├── pack/
│   ├── build.gradle      # runClient（全模組同時載入）與 .mrpack 輸出
│   └── overrides/        # 會原樣放進玩家遊戲資料夾，例如 config/ 預設設定
└── scripts/new-mod.sh    # 建立新模組骨架
```

## 常用指令

```sh
./gradlew build                          # 建置＋測試所有模組，並輸出模組包
./gradlew :mods:alchemy-backpack:build   # 只建置單一模組
./gradlew :mods:alchemy-backpack:runClient  # 只載入單一模組測試
./gradlew :pack:runClient                # 載入「全部」模組測試（檢查衝突）
./gradlew :pack:mrpack                   # → pack/build/distributions/alan-pack-<ver>.mrpack
./gradlew :pack:packMods                 # → pack/build/pack-mods/（直接丟進 mods 資料夾用）
```

Windows 使用 `gradlew.bat`。

## 新增模組

```sh
scripts/new-mod.sh magic_lantern "Magic Lantern" "魔法燈籠"
./gradlew :mods:magic-lantern:build
```

會產生 `mods/magic-lantern/`，套件 `dev.alan.magiclantern`，含 main / client 入口、中英文語系檔、一個單元測試。
不用修改任何根目錄檔案，下一次建置就會自動納入模組包。

## 沿用自煉金背包的設計慣例

| 慣例 | 煉金背包中的例子 |
| --- | --- |
| `XxxMod` 集中註冊物品、資料組件、選單、封包；提供 `id(name)` | `AlchemyMod` |
| 狀態用**不可變 record + Codec**，存在物品的 DataComponent 上，邏輯寫成純函式方便單元測試 | `BagData` + `BagDataTest` |
| 所有會改變狀態的操作只在伺服器執行，客戶端只送按鈕編號 | `BagMenu.clickMenuButton` |
| 伺服器設定放 `config/<mod-id>-*.json`，登入時用 payload 同步給客戶端 | `EnergyValues` + `EnergySync` |
| 客戶端程式只放 `src/client`，避免專用伺服器載入客戶端類別而崩潰 | `AlchemyClient`、`BagScreen` |
| 所有顯示文字走語系鍵，同時提供 `en_us` 與 `zh_tw` | `lang/*.json` |

## 模組包發佈

- `.mrpack` 可直接匯入 Modrinth App 或 Prism Launcher；會安裝指定的 Minecraft 與 Fabric Loader。
- 目前自製模組與 Fabric API 都以 `overrides/mods/` 內嵌。若要上架 Modrinth，應改把 Fabric API 等第三方模組寫進 `modrinth.index.json` 的 `files`（含下載網址與雜湊），而非內嵌。
- 模組包的預設設定檔放 `pack/overrides/config/`，例如整包統一的 `alchemy-backpack-energy.json`。
- 多人遊戲：伺服器與所有玩家都需要相同的模組 jar。

## 版本升級

只改根目錄 `gradle.properties` 的 `minecraft_version` / `loader_version` / `loom_version` / `fabric_api_version`，
各模組的 `fabric.mod.json` 會在建置時自動帶入。
