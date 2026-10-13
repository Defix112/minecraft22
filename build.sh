#!/usr/bin/env bash
# Builds out/agent.jar against the official Mojang-mapped Minecraft client.
# Downloads Minecraft and its mappings into mc/ (never committed).
set -euo pipefail

MC_VERSION="${MC_VERSION:-1.21.11}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
MC="$ROOT/mc/$MC_VERSION"
B="$ROOT/build"
OUT="$ROOT/out"
TOOLCP="$ROOT/lib/asm-9.7.1.jar:$ROOT/lib/asm-commons-9.7.1.jar:$ROOT/lib/asm-tree-9.7.1.jar"

HOOKS=(
  "onTick=net/minecraft/client/Minecraft.tick()V"
  "onHud=net/minecraft/client/gui/Gui.render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"
  "onKey=net/minecraft/client/KeyboardHandler.keyPress(JILnet/minecraft/client/input/KeyEvent;)V"
)

rm -rf "$B" "$OUT"
mkdir -p "$MC/libs" "$B" "$OUT"

if [ ! -f "$MC/client.jar" ] || [ ! -f "$MC/client.txt" ]; then
  echo "== Downloading Minecraft $MC_VERSION"
  vurl=$(curl -fsSL https://piston-meta.mojang.com/mc/game/version_manifest_v2.json \
    | jq -r --arg v "$MC_VERSION" '.versions[] | select(.id == $v) | .url')
  [ -n "$vurl" ] || { echo "Minecraft version $MC_VERSION not found"; exit 1; }
  curl -fsSL "$vurl" -o "$MC/version.json"
  murl=$(jq -r '.downloads.client_mappings.url // empty' "$MC/version.json")
  [ -n "$murl" ] || { echo "Mojang publishes no client mappings for $MC_VERSION"; exit 1; }
  curl -fsSL "$(jq -r '.downloads.client.url' "$MC/version.json")" -o "$MC/client.jar"
  curl -fsSL "$murl" -o "$MC/client.txt"
  jq -r '.libraries[] | select(.downloads.artifact != null) | .downloads.artifact.url' "$MC/version.json" \
    | grep -v 'natives-' | (cd "$MC/libs" && xargs -P 8 -n 1 curl -fsSLO)
  echo "Libraries: $(ls "$MC/libs" | wc -l)"
fi

echo "== Building remap tool"
javac -d "$B/tools" -cp "$TOOLCP" "$ROOT/tools/Remap.java"
remap() { java -cp "$B/tools:$TOOLCP" Remap "$@"; }

if [ ! -f "$MC/client-named.jar" ]; then
  echo "== Deobfuscating client with Mojang mappings"
  remap deobf "$MC/client.txt" "$MC/client.jar" "$MC/client-named.jar"
fi

echo "== Resolving hook targets"
remap genobf "$MC/client.txt" "$B/gen/com/modloader/ObfNames.java" "${HOOKS[@]}"

echo "== Compiling mod against Minecraft $MC_VERSION"
mapfile -t SOURCES < <(find "$ROOT/src" -name '*.java')
javac --release 21 --add-modules jdk.attach -encoding UTF-8 -Xlint:-options -d "$B/named" \
  -cp "$MC/client-named.jar:$MC/libs/*:$ROOT/lib/asm-9.7.1.jar" \
  "${SOURCES[@]}" "$B/gen/com/modloader/ObfNames.java"

remap verify "$B/named/com/modloader/Hooks.class" "${HOOKS[@]}"

echo "== Mapping mod to the game's obfuscated names"
remap obf "$MC/client.txt" "$MC/client-named.jar" "$B/named" "$B/obf"
(cd "$B/obf" && unzip -q -o "$ROOT/lib/asm-9.7.1.jar" 'org/*')

jar cfm "$OUT/agent.jar" "$ROOT/META-INF/MANIFEST.MF" -C "$B/obf" .
cp "$ROOT/test.cmd" "$OUT/test.cmd"
echo "== Built out/agent.jar for Minecraft $MC_VERSION ($(du -k "$OUT/agent.jar" | cut -f1) KB)"
