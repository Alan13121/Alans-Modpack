# 任務書 Guide

Minecraft Java 26.3／Fabric，屬於本模組包的一部分。純資料模組：六章、30 個任務（外加 5 個章首進度，共 35 個），加上獎勵戰利品表與語言檔。清單與設計見 `docs/mods/guide.md`。

## 修改任務

進度、獎勵與文字全部由腳本產生，不要手改 JSON：

```sh
python3 scripts/gen_guide_data.py
```

腳本會整份重寫 `src/main/resources/data/guide` 與 `assets/guide/lang`。

## 注意

- 這個遊戲版本的進度與戰利品表格式與舊版不同：觸發條件用單一物件加 `type`，戰利品用 `modifier`（不是 `functions`）。舊寫法不會報錯，只會被默默忽略。
- 需要程式授予的進度（觸發器為 `impossible`，條件名稱為 `done`）由各模組的 `Guide`／`QuestBook` 小工具授予，找不到進度時什麼都不做。
- 測試在 `pack/src/gametest/.../GuideGameTest`：`./gradlew :pack:runClientGameTest`。

## 手冊

- 物品 `guide:handbook`（Alan's Modpack 手冊），右鍵開啟自製的手冊畫面：左邊章節、右邊內文，內文依視窗寬度自動換行、可用滾輪或方向鍵捲動，所以長度不受限。
- 玩家第一次進入世界時自動拿到一本；用玩家身上的實體標籤 `guide_handbook_given` 記住，之後登入不會再給。
- 書丟了輸入 `/guidebook`（所有玩家可用）補一本；身上已有手冊時不會再給，所以最多一本。
- 內容在 `scripts/gen_guide_data.py` 的 `BOOK`：每章一列，內文每行一段，`# ` 開頭是標題、`- ` 開頭是項目。畫面在 `src/client/.../HandbookScreen`。
