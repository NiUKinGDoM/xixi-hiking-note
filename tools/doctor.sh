#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════
#  一键体检（2026-11-05）
#
#  和 gate.sh 的分工：
#    · gate.sh   = 发版门禁。跑 gradle（编译 + 全量测试 + lint），慢，必须全绿。
#    · doctor.sh = 随时体检。只跑静态检查，**几秒出结果**，不编译。
#
#  为什么要单独一个：
#    改完一行代码想知道"有没有捅娄子"，等 gradle 跑两分钟不现实，人就会跳过检查。
#    静态这几项（崩溃点 / 气味 / 设计一致性 / 文档 / 审计）加起来不到 10 秒，
#    覆盖了历史上真出过问题的大半类别，日常用它，发版用 gate。
#
#  用法：bash tools/doctor.sh
#        bash tools/doctor.sh --with-lint    # 额外跑 Android Lint（约 2 分钟）
# ══════════════════════════════════════════════════════════════════
set -uo pipefail
cd "$(dirname "$0")/.."

WITH_LINT=0
[ "${1:-}" = "--with-lint" ] && WITH_LINT=1

FAIL=0
declare -a ROWS

row() {  # row "名称" 状态(ok|warn|fail) "摘要"
  printf '  %-22s %-6s %s\n' "$1" "$2" "$3"
  ROWS+=("$1|$2|$3")
  [ "$2" = "fail" ] && FAIL=$((FAIL + 1))
}

echo "══════════ 一键体检 ══════════"
echo

# ── ① 崩溃点 ──────────────────────────────────────────────
out=$(python tools/crashscan.py --gate 2>&1); rc=$?
high=$(printf '%s' "$out" | grep -oE 'HIGH [0-9]+' | head -1 | grep -oE '[0-9]+')
med=$(printf '%s' "$out" | grep -oE 'MED [0-9]+' | head -1 | grep -oE '[0-9]+')
if [ $rc -eq 0 ]; then
  row "崩溃点" "ok" "HIGH 0 / MED ${med:-0}"
else
  row "崩溃点" "fail" "HIGH ${high:-?} 处需要处理（python tools/crashscan.py 看详情）"
fi

# ── ② 代码气味 ────────────────────────────────────────────
out=$(python tools/smellscan.py --gate 2>&1); rc=$?
err=$(printf '%s' "$out" | grep -oE 'ERROR [0-9]+' | head -1 | grep -oE '[0-9]+')
warn=$(printf '%s' "$out" | grep -oE 'WARN [0-9]+' | head -1 | grep -oE '[0-9]+')
if [ $rc -eq 0 ]; then
  row "代码气味" "ok" "ERROR 0 / WARN ${warn:-0}"
else
  row "代码气味" "fail" "ERROR ${err:-?} 处（python tools/smellscan.py 看详情）"
fi

# ②b 扫描器自检：确保规则真的会报（防止"静默失效 = 假干净"）
st=$(python tools/smellscan.py --selftest 2>&1)
if printf '%s' "$st" | grep -q '全部规则'; then
  n=$(printf '%s' "$st" | grep -c '✓.*：正样本')
  row "扫描器自检" "ok" "${n} 条规则均能命中且不误伤"
else
  row "扫描器自检" "fail" "有规则失效，扫描结果不可信"
fi

# ── ③ 设计一致性（emoji / 字号 / 弹窗档位）────────────────
out=$(python tools/designcheck.py 2>&1); rc=$?
if [ $rc -eq 0 ]; then
  row "设计一致性" "ok" "$(printf '%s' "$out" | tail -1 | cut -c1-60)"
else
  row "设计一致性" "fail" "$(printf '%s' "$out" | grep -E '^✗|ERROR' | head -1 | cut -c1-70)"
fi

# ── ④ 文档 / 版本号一致 ───────────────────────────────────
out=$(python tools/docfresh.py 2>&1); rc=$?
if [ $rc -eq 0 ]; then
  row "版本/文档一致" "ok" "更新日志首条 = 当前版本"
else
  row "版本/文档一致" "fail" "$(printf '%s' "$out" | grep -E '✗|·' | head -2 | tr '\n' ' ' | cut -c1-70)"
fi

# ── ④b 说明文案长度 ──────────────────────────────────────
out=$(python tools/textcheck.py --gate 2>&1); rc=$?
if [ $rc -eq 0 ]; then
  row "说明文案" "ok" "副标题 ≤16 / 正文 ≤48"
else
  n=$(printf '%s' "$out" | grep -oE '合计 [0-9]+ 条' | head -1 | grep -oE '[0-9]+')
  row "说明文案" "fail" "${n:-?} 条超标（副标题 16 / 正文 48）"
fi

# ── ④c Room 升级风险 ─────────────────────────────────────
out=$(python tools/dbaudit.py --gate 2>&1); rc=$?
if [ $rc -eq 0 ]; then
  row "数据库迁移" "ok" "$(printf '%s' "$out" | grep -oE 'version [0-9]+' | head -1)"
else
  row "数据库迁移" "fail" "缺迁移或出现破坏性回退（python tools/dbaudit.py）"
fi

# ── ④c2 删除入口二次确认 ─────────────────────────────────
out=$(python tools/delguard.py --gate 2>&1); rc=$?
n=$(printf '%s' "$out" | grep -oE '（[0-9]+ 个入口）' | grep -oE '[0-9]+')
if [ $rc -eq 0 ]; then
  row "删除确认" "ok" "${n:-?} 个删除入口都有二次确认"
else
  row "删除确认" "fail" "有删除入口直接执行，python tools/delguard.py"
fi

# ── ④c3 弹窗嵌套 ─────────────────────────────────────────
out=$(python tools/overlayscan.py --gate 2>&1); rc=$?
if [ $rc -eq 0 ]; then
  row "弹窗嵌套" "ok" "内层走独立窗口，已接弹窗体系"
else
  row "弹窗嵌套" "fail" "有自绘 Dialog/Popup 或超 2 层，python tools/overlayscan.py"
fi

# ── ④d 未测模块 ──────────────────────────────────────────
out=$(python tools/covscan.py --gate 2>&1); rc=$?
hi=$(printf '%s' "$out" | grep -oE '高风险 [0-9]+' | head -1 | grep -oE '[0-9]+')
if [ $rc -eq 0 ]; then
  row "未测模块" "ok" "高风险 ${hi:-0} 个（阈值 6）"
else
  row "未测模块" "fail" "高风险 ${hi:-?} 个，先补测试"
fi

# ── ⑤ 系统级 / 软件层审计（提示性质）──────────────────────
out=$(python tools/audit.py 2>&1)
e=$(printf '%s' "$out" | grep -oE '错误 [0-9]+' | head -1 | grep -oE '[0-9]+')
w=$(printf '%s' "$out" | grep -oE '警告 [0-9]+' | head -1 | grep -oE '[0-9]+')
if [ "${e:-0}" -gt 0 ]; then
  row "系统审计" "fail" "错误 ${e} / 警告 ${w:-0}"
else
  row "系统审计" "warn" "错误 0 / 警告 ${w:-0}（提示性质，见 python tools/audit.py）"
fi

# ── ⑥ 上次单测结果（不重跑，读 test-results）─────────────
resdir="app/build/test-results/testDebugUnitTest"
if ls "$resdir"/*.xml >/dev/null 2>&1; then
  total=0; failed=0; classes=0
  for f in "$resdir"/*.xml; do
    t=$(grep -o 'tests="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
    x=$(grep -o 'failures="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
    er=$(grep -o 'errors="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
    total=$((total + ${t:-0})); failed=$((failed + ${x:-0} + ${er:-0})); classes=$((classes + 1))
  done
  if [ "$failed" -eq 0 ]; then
    row "单元测试（上次）" "ok" "${classes} 个类 / ${total} 项，全过"
  else
    row "单元测试（上次）" "fail" "${total} 项里有 ${failed} 项失败"
  fi
else
  row "单元测试（上次）" "warn" "还没跑过（gradle :app:testDebugUnitTest）"
fi

# ── ⑦ 可选：Android Lint ──────────────────────────────────
if [ "$WITH_LINT" = "1" ]; then
  echo
  echo "  正在跑 Android Lint（约 2 分钟）…"
  export JAVA_HOME="${JAVA_HOME:-C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/jdk-21.0.12}"
  GRADLE="${GRADLE_BIN:-C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/gradle-8.11.1/bin/gradle}"
  if "$GRADLE" :app:lintDebug --console=plain >/tmp/lint.log 2>&1; then
    row "Android Lint" "ok" "0 error"
  else
    reps=$(find app/build -name "lint-results-debug.txt" 2>/dev/null | head -1)
    n=0
    [ -n "$reps" ] && n=$(grep -cE ": Error:" "$reps" 2>/dev/null || echo 0)
    row "Android Lint" "fail" "${n} 个 error"
  fi
fi

echo
echo "──────────────────────────────"
if [ "$FAIL" -eq 0 ]; then
  echo "✓ 体检通过（${#ROWS[@]} 项）"
  echo "  发版前还要跑：bash tools/gate.sh（编译 + 全量测试 + lint）"
  exit 0
else
  echo "✗ ${FAIL} 项不通过"
  exit 1
fi
