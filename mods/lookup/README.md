# 查詢 Lookup

Minecraft Java 26.3／Fabric，屬於本模組包的一部分。類似 JEI／EMI 的物品與配方查詢：在背包、箱子等畫面右側顯示物品清單，
按 **R** 看合成配方、**U** 看用途（也可以直接左鍵／右鍵點物品）。

## 功能

- 自動讀取**所有模組**的配方，不必逐個模組適配（原版工作台、熔爐類、切石機、鍛造台、釀造）。
- 生物掉落物、方塊掉落物、戰利品表（箱子等），含數量與機率（滑鼠移到物品上）。
- 搜尋：名稱、`@模組`、`#標籤`、`$說明文字`，可組合（空白分隔，全部符合）。
- 書籤：滑鼠指著物品按 **A** 加入／移除，釘在清單最上面一列，存在 `config/lookup_bookmarks.json`。
- 配方填入：開著合成／熔爐類畫面時，配方頁每個配方右上有「+」，點一下把材料放進合成格（Shift＝盡量多）。
  不要求配方已解鎖；材料不夠或放不下時什麼都不會發生。
- 作弊模式：預設關閉。在遊戲中或任何背包／箱子畫面按 **F9**，或點清單上方的「作弊：開／關」字樣切換
  （也可改 `config/lookup.json` 的 `cheat_mode`）。開啟後左鍵拿一組、右鍵拿一個；
  只有創造模式玩家、單人世界擁有者、OP 拿得到。

## 給其他模組：外掛 API

配方本身不需要做任何事。若你的模組有「不是配方」的資料想顯示（自己的轉化、形態卡片……），實作 `LookupPlugin`：

```java
public final class MyLookupPlugin implements LookupPlugin {
    @Override public void register(LookupRegistry registry) {
        registry.views(() -> List.of(new RecipeView(
            "mymod:smelter", Component.literal("My Smelter"), new ItemStack(Items.FURNACE),
            List.of(new RecipeView.Slot(0, 18, List.of(new ItemStack(Items.IRON_ORE)))),   // 輸入
            List.of(new RecipeView.Slot(94, 18, List.of(new ItemStack(Items.IRON_INGOT)))), // 輸出
            RecipeView.Decoration.ARROW, null)));
    }
}
```

再到 `fabric.mod.json` 宣告：

```json
"entrypoints": { "lookup": ["com.example.MyLookupPlugin"] }
```

- **不需要硬相依**：沒裝 lookup 時這個入口不會被載入。編譯時把 lookup 的 client 輸出加進 `clientCompileOnly`
  （本 monorepo 已在根目錄 `build.gradle` 對所有 `mods/*` 設好）。
- 提供者（supplier）會在每次重建索引時重新執行：進入世界、重新載入、開啟物品欄畫面時，所以可以讀取登入後才同步的資料。
- 版面畫布固定 `RecipeView.WIDTH × HEIGHT`（112 × 54）。同一個 `categoryKey` 的視圖共用一個分頁。
- 每個視圖會出現在它所有輸入物品的「用途」與所有輸出物品的「配方」。只是資訊卡（沒有輸出）時，輸出留空即可。
- 需要自訂繪製或額外的提示行時，用 `RecipeView.Painter` 與 `Slot` 的 `extra`。
- **庫存顯示**：`registry.stock(stack -> 數量)`（沒有答案時回傳 -1）。有提供者回答時，配方頁在每個材料格右下角顯示庫存數量（夠是綠色、不夠是紅色），並在右下角標示「×N」可合成次數。物流模組的 `LogisticsLookupPlugin` 用它顯示倉庫存量（開著終端機時才有）。
- 範例：`ShapeshiftLookupPlugin`（形態卡片）、`AlchemyLookupPlugin`（能量值）；lookup 自己的戰利品也走同一個介面。

## 開發

```sh
./gradlew :mods:lookup:build                # 只建置這個模組
./gradlew :mods:lookup:runClient            # 只載入這個模組的開發遊戲
./gradlew :mods:lookup:runClientGameTest    # 自動化遊戲內測試，截圖在 build/run/clientGameTest/screenshots
./gradlew :pack:runClientGameTest           # 全模組一起載入的整合測試
```

## 資料怎麼來

- 填入：`FillRecipe` 封包交給原版的 `RecipeBookMenu.handlePlacement`（處理標籤、堆疊、最大數量），只略過「配方必須已解鎖」的檢查。
- 配方：伺服器把每筆配方的顯示資料（`RecipeDisplayEntry`）分批送給客戶端（原版只送已解鎖的）。
- 釀造：原版沒有顯示資料，另外用 `BrewingSync` 傳原始配方，客戶端展開成具體藥水。
- 戰利品：客戶端拿不到戰利品表，伺服器把每張表轉成 JSON 再解析（所以其他模組的自訂條目也不會壞），用 `LootSync` 送出。

## 程式入口

- `LookupMod`：伺服器端同步（配方、釀造、戰利品）與作弊封包。
- `LookupClient`：按鍵、畫面事件、外掛載入。
- `api/`：公開給其他模組的介面。
- `client/mixin/`：只有一個存取器，讀取容器畫面的位置與滑鼠下的格子（登記在 `lookup.client.mixins.json`）。
