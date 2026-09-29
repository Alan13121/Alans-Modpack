#!/usr/bin/env bash
# 用法：scripts/new-mod.sh <mod_id> "<English Name>" "<中文名稱>"
# 例：  scripts/new-mod.sh magic_lantern "Magic Lantern" "魔法燈籠"
# 產生 mods/<mod-id 以 - 連接>/，套件為 dev.alan.<modid 去底線>
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <mod_id> \"<English Name>\" [\"<中文名稱>\"]" >&2
  exit 1
fi

MOD_ID="$1"
NAME_EN="$2"
NAME_ZH="${3:-$2}"
if [[ ! "$MOD_ID" =~ ^[a-z][a-z0-9_]{1,63}$ ]]; then
  echo "mod_id 只能用小寫英數與底線，且以字母開頭" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIR_NAME="${MOD_ID//_/-}"
PKG_SEG="${MOD_ID//_/}"
GROUP="dev.alan"
PKG="$GROUP.$PKG_SEG"
PKG_PATH="${PKG//.//}"
CLASS="$(echo "$NAME_EN" | sed -E 's/[^A-Za-z0-9 ]//g; s/(^| )([a-z])/\1\u\2/g; s/ //g')"
[[ -z "$CLASS" ]] && CLASS="Mod"
DEST="$ROOT/mods/$DIR_NAME"

if [[ -e "$DEST" ]]; then
  echo "$DEST 已存在" >&2
  exit 1
fi

mkdir -p "$DEST/src/main/java/$PKG_PATH" \
         "$DEST/src/client/java/$PKG_PATH/client" \
         "$DEST/src/test/java/$PKG_PATH" \
         "$DEST/src/main/resources/assets/$MOD_ID/lang" \
         "$DEST/src/main/resources/assets/$MOD_ID/items" \
         "$DEST/src/main/resources/assets/$MOD_ID/models/item" \
         "$DEST/src/main/resources/assets/$MOD_ID/textures/item" \
         "$DEST/src/main/resources/data/$MOD_ID/recipe" \
         "$DEST/src/main/java/$PKG_PATH/mixin" \
         "$DEST/src/client/java/$PKG_PATH/client/mixin" \
         "$DEST/src/client/resources"

cat > "$DEST/gradle.properties" <<PROPS
mod_id=$MOD_ID
mod_version=0.1.0
maven_group=$GROUP
PROPS

cp "$ROOT/mods/alchemy-backpack/LICENSE" "$DEST/LICENSE"

cat > "$DEST/src/main/resources/fabric.mod.json" <<JSON
{
  "schemaVersion": 1,
  "id": "$MOD_ID",
  "version": "\${version}",
  "name": "$NAME_EN / $NAME_ZH",
  "description": "",
  "authors": ["Alan"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {"main": ["$PKG.${CLASS}Mod"], "client": ["$PKG.client.${CLASS}Client"]},
  "mixins": ["$MOD_ID.mixins.json", {"config": "$MOD_ID.client.mixins.json", "environment": "client"}],
  "depends": {"fabricloader": ">=\${loader_version}", "minecraft": "\${minecraft_version}", "java": ">=\${java_version}", "fabric-api": "*"}
}
JSON

# Mixin 設定：共用放 main，只在客戶端的放 client。類別名稱加進 "mixins" / "client" 陣列即可。
cat > "$DEST/src/main/resources/$MOD_ID.mixins.json" <<JSON
{
  "required": true,
  "package": "$PKG.mixin",
  "compatibilityLevel": "JAVA_25",
  "mixins": [],
  "injectors": {"defaultRequire": 1}
}
JSON
cat > "$DEST/src/client/resources/$MOD_ID.client.mixins.json" <<JSON
{
  "required": true,
  "package": "$PKG.client.mixin",
  "compatibilityLevel": "JAVA_25",
  "client": [],
  "injectors": {"defaultRequire": 1}
}
JSON

cat > "$DEST/src/main/java/$PKG_PATH/${CLASS}Mod.java" <<JAVA
package $PKG;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ${CLASS}Mod implements ModInitializer {
    public static final String MOD_ID = "$MOD_ID";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(MOD_ID, name); }
    @Override public void onInitialize() {
        LOG.info("$NAME_EN loaded");
    }
}
JAVA

cat > "$DEST/src/client/java/$PKG_PATH/client/${CLASS}Client.java" <<JAVA
package $PKG.client;

import net.fabricmc.api.ClientModInitializer;

public final class ${CLASS}Client implements ClientModInitializer {
    @Override public void onInitializeClient() {
    }
}
JAVA

cat > "$DEST/src/test/java/$PKG_PATH/${CLASS}Test.java" <<JAVA
package $PKG;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ${CLASS}Test {
    @Test void modIdIsValid() {
        assertTrue(${CLASS}Mod.MOD_ID.matches("[a-z][a-z0-9_]*"));
    }
}
JAVA

cat > "$DEST/src/main/resources/assets/$MOD_ID/lang/en_us.json" <<JSON
{
}
JSON
cat > "$DEST/src/main/resources/assets/$MOD_ID/lang/zh_tw.json" <<JSON
{
}
JSON

cat > "$DEST/README.md" <<MD
# $NAME_ZH $NAME_EN

Minecraft Java 26.3／Fabric，屬於本模組包的一部分。

## 開發

\`\`\`sh
./gradlew :mods:$DIR_NAME:build       # 只建置這個模組
./gradlew :mods:$DIR_NAME:runClient   # 只載入這個模組的開發遊戲
\`\`\`

## 程式入口

- \`${CLASS}Mod\`：伺服器／共用註冊。
- \`${CLASS}Client\`：客戶端畫面、渲染、提示文字。
- \`mixin/\`、\`client/mixin/\`：修改原版行為（登記在 \`$MOD_ID.mixins.json\`、\`$MOD_ID.client.mixins.json\`）。
MD

echo "已建立 $DEST"
echo "下一步：./gradlew :mods:$DIR_NAME:build"
