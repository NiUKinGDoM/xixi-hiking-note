#!/usr/bin/env bash
# 只跑「能点崩人」的那两套：点击冒烟 + 压测（约 40 秒）
#
# 用法：bash tools/stress.sh
set -uo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/jdk-21.0.12}"
GRADLE="${GRADLE_BIN:-C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/gradle-8.11.1/bin/gradle}"

"$GRADLE" :app:testDebugUnitTest \
  --tests "com.xixi.hiking.nativeapp.ClickSmokeTest" \
  --tests "com.xixi.hiking.nativeapp.StressTest" \
  -Dmaven.wagon.http.ssl.insecure=true --console=plain \
  | grep -E "^e:|FAILED|BUILD"

cd app/build/test-results/testDebugUnitTest
for f in TEST-*ClickSmokeTest.xml TEST-*StressTest.xml; do
  [ -f "$f" ] || continue
  t=$(grep -o 'tests="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
  x=$(grep -o 'failures="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
  echo "  $(basename "$f" .xml | sed 's/TEST-com.xixi.hiking.nativeapp.//')：$t 项，失败 $x"
done
