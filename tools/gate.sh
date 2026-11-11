#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════
#  原生版唯一门禁（2026-11-02，对齐 Web 版「六项优化」⑤⑥）
#
#  Web 版的教训：门禁有两份实现（checkall.sh / checkall.js），
#  新加的检查只进了一份 ⇒ 「发版时根本不跑」，名义有门禁等于没有。
#
#  所以这里只留**这一个入口**，打包前必须跑它：
#    ① 版本号 / 文档一致性（README + 原生版更新日志）
#    ② 全量单元测试（含点击冒烟、压测、截图）
#  任何一步失败就非 0 退出，不许跳步。
#
#  用法：  bash tools/gate.sh
# ══════════════════════════════════════════════════════════════════
set -euo pipefail

cd "$(dirname "$0")/.."

# JDK / Gradle：允许用环境变量覆盖，否则用本机固定路径
export JAVA_HOME="${JAVA_HOME:-C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/jdk-21.0.12}"
GRADLE="${GRADLE_BIN:-C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/gradle-8.11.1/bin/gradle}"

echo "════ ① 版本号 / 文档一致性 ════"
python tools/docfresh.py

echo
echo "════ ①b 门禁套件一致性 ════"
python tools/suiteguard.py

echo
echo "════ ①c 设计一致性（emoji / 字号 / 弹窗档位）════"
python tools/designcheck.py

echo
echo "════ ①d 系统级 / 软件层审计 ════"
# 提示性质（存在已知的"超大文件"这类不阻塞项），不作为发版门槛
python tools/audit.py | tail -6 || true

echo
echo "════ ①d2 说明文案长度（副标题 ≤16 / 正文 ≤48）════"
python tools/textcheck.py --selftest
python tools/textcheck.py --gate

echo
echo "════ ①d3 数据库升级路径（缺迁移 = 老用户升级崩）════"
python tools/dbaudit.py --gate

echo
echo "════ ①d4 删除入口二次确认 ════"
# 每一个删除入口都必须先弹确认（点错了不会直接丢数据）。
# 判定按「同函数有 GlassConfirm」→「点了置 pending/confirm 状态」→「沿回调链追到父组件」三层走。
python tools/delguard.py --selftest
python tools/delguard.py --gate

echo
echo "════ ①d5 弹窗嵌套 / 自绘弹窗 ════"
# 内层弹窗必须能浮在外层之上（2026-11-05「照片/灯箱删除点了没弹窗」的根因）
python tools/overlayscan.py --selftest
python tools/overlayscan.py --gate

echo
echo "════ ①e 崩溃点扫描（HIGH 必须清零）════"
# 抓的是「数据不可控处用了会抛异常的写法」：!! / first() / 下标 / 字符串直转数字 /
# 日期解析。有 HIGH 就不许发版 —— 这些不是理论风险，每一条都对应过真机崩溃。
python tools/crashscan.py --gate

echo
echo "════ ①f 代码气味扫描（ERROR 必须清零）════"
# 先自检：规则失效会让扫描"安静地通过"，那比不扫更危险
python tools/smellscan.py --selftest
python tools/smellscan.py --gate

echo
echo "════ ①g 单文件行数（防止拆开的代码又堆回去）════"
# 2026-11-11 加：SettingsScreen 曾长到 1746 行一个函数 1260 行。
# 阈值 1500 只拦"又出现巨型文件"，800 是提醒该拆了 —— 不逼着立刻重构，
# 否则门禁一红就发不了版，最后只能整个关掉。
python tools/sizeguard.py --gate

echo
echo "════ ①g Android Lint（error 必须清零）════"
# 真静态分析：API 级别（minSdk 24 上才炸的那种）、权限、资源类型、
# Compose 的 SuspiciousIndentation 等。这一项抓到过「分享卡在 Android 7~9 必崩」。
"$GRADLE" :app:lintDebug --console=plain | tail -20

echo
echo "════ ② 全量单元测试（点击冒烟 + 压测 + 截图）════"
"$GRADLE" :app:testDebugUnitTest -Dmaven.wagon.http.ssl.insecure=true --console=plain \
  | grep -E "^e:|FAILED|BUILD"

echo
echo "════ ③ 汇总 ════"
cd app/build/test-results/testDebugUnitTest
total=0
fail=0
for f in *.xml; do
  t=$(grep -o 'tests="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
  x=$(grep -o 'failures="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
  e=$(grep -o 'errors="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
  total=$((total + t))
  fail=$((fail + x + e))
  echo "  $(basename "$f" .xml | sed 's/TEST-com.xixi.hiking.nativeapp.//')：$t 项，失败 $((x + e))"
done
echo "  ─────────────"
echo "  合计 $total 项，失败 $fail 项"
if [ "$fail" -ne 0 ]; then
  echo "✗ 门禁不通过"
  exit 1
fi
echo "✓ 门禁通过"
