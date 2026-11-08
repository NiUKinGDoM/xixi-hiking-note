#!/usr/bin/env bash
# 只跑「截图」这一套，并把图拷到 build/shots/（按文件名排序，方便一张张看）
#
# 用法：bash tools/shots.sh [过滤关键字]
#   例：bash tools/shots.sh overview   # 只跑与 overview 相关的（gradle --tests 过滤）
set -uo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/jdk-21.0.12}"
GRADLE="${GRADLE_BIN:-C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/gradle-8.11.1/bin/gradle}"

FILTER="${1:-}"
if [ -n "$FILTER" ]; then
  "$GRADLE" :app:testDebugUnitTest --tests "*${FILTER}*" -Dmaven.wagon.http.ssl.insecure=true --console=plain
else
  "$GRADLE" :app:testDebugUnitTest --tests "com.xixi.hiking.nativeapp.UiSnapshotTest" \
    -Dmaven.wagon.http.ssl.insecure=true --console=plain
fi

OUT="build/shots"
rm -rf "$OUT" && mkdir -p "$OUT"
i=0
for f in $(ls app/build/ui-shots/*.png 2>/dev/null | sort); do
  i=$((i + 1))
  cp "$f" "$OUT/$(printf '%02d' $i)-$(basename "$f")"
done
echo
echo "✓ 共 $i 张，已拷到 $OUT/（按名字排序 = 页面顺序）"
ls "$OUT" | sed 's/^/   /'
