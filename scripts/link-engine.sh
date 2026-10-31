#!/usr/bin/env bash
# Generates engine-modules.gradle so the cloned engine repo's modules join this build.
# usage: scripts/link-engine.sh [engine_dir] [engine_module]
set -euo pipefail
DIR="${1:-engine}"; WANT="${2:-Bcore}"; OUT=engine-modules.gradle
S=""; for f in settings.gradle settings.gradle.kts; do [ -f "$DIR/$f" ] && S="$DIR/$f"; done
mods=""
[ -n "$S" ] && mods=$(grep -oE "[\"']:[A-Za-z0-9_.:-]+[\"']" "$S" | tr -d "\"'" | grep -vx ':app' | sort -u || true)
[ -z "$mods" ] && mods=":$WANT"
: > "$OUT"
found=0
for m in $mods; do
  rel="${m#:}"; rel="${rel//://}"
  if [ -d "$DIR/$rel" ]; then
    echo "include '$m'" >> "$OUT"
    echo "project('$m').projectDir = file('$DIR/$rel')" >> "$OUT"
    [ "$m" = ":$WANT" ] && found=1
    echo "linked $m -> $DIR/$rel"
  fi
done
[ -f "$DIR/gradle/libs.versions.toml" ] && mkdir -p gradle && cp "$DIR/gradle/libs.versions.toml" gradle/ && echo "copied version catalog"
if [ -f "$DIR/gradle.properties" ]; then
  grep -E '^(android\.|[A-Za-z_.]+=)' "$DIR/gradle.properties" | grep -vE '^org\.gradle\.(jvmargs|java\.home)' >> gradle.properties || true
fi
if [ "$found" -ne 1 ]; then
  echo "ERROR: engine module ':$WANT' not found in $DIR. Set the 'engine_module' workflow input to the right module name." >&2
  echo "Top-level dirs: $(ls "$DIR")" >&2
  exit 1
fi
