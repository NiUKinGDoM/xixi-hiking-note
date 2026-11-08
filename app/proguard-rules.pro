# ══════════════════════════════════════════════════════════════════
#  R8 混淆 / 瘦身规则（2026-11-05 首次启用）
#
#  原则：**只保留"运行时要靠名字找"的东西**。
#  别整包 -keep（`-keep class com.xixi.** { *; }` 那种），那样等于没混淆，
#  体积也不会降 —— 混淆的收益大头是"删掉没用的代码 + 缩短名字"。
#
#  这个项目里真正靠名字工作的只有三类：
#    ① Room 生成的 `AppDatabase_Impl`（Room 用反射按名字加载）
#    ② 实体类字段名（Room 生成的 SQL 列名绑定，编译期已固化，但字段不能被删）
#    ③ 崩溃兜底要打印的异常类名（可读性，不是功能）
# ══════════════════════════════════════════════════════════════════

# ── 崩溃堆栈可读性 ─────────────────────────────────────────────
# 不加这两行，线上崩溃只有 `a.a.a(Unknown Source)`，连行号都没有，
# 等于把"能查 bug"这条路自己堵死。配合 mapping.txt 可以还原成真实类名。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 注解、内部类、泛型签名：Room / Compose / Kotlin 都要读
-keepattributes *Annotation*,InnerClasses,Signature,Exceptions,EnclosingMethod

# ── Room ──────────────────────────────────────────────────────
# Room 2.8 自带 consumer rules，这里只做"保险"（多一条不会有害，少一条可能崩）
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# ── 异常类名 ──────────────────────────────────────────────────
# CrashGuard / SafeTap / 分享卡都会把 `e.javaClass.simpleName` 写进诊断，
# 混淆后变成 "a"，诊断报告就没用了。只保留名字，不阻止混淆其它成员。
-keepnames class * extends java.lang.Throwable

# ── 序列化 / JSON ─────────────────────────────────────────────
# 备份走 org.json（运行时字符串 key），不依赖反射，所以 **不需要** keep。
# 如果哪天换成 kotlinx.serialization，这里要加 @Serializable 的 keep。

# ── 第三方库的"检查类是否存在" ────────────────────────────────
# R8 在 full mode 下会因为 optional 依赖缺失报警；这些类我们根本没用到。
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# ── 第三方库：只保名字，不保代码 ──────────────────────────────
# `-keepnames` 只阻止"改名"，不阻止"删除没用的方法" —— 所以体积几乎不受影响，
# 但能挡住一种很难查的故障：库里用 `Class.forName("自己某个类")` 做平台适配，
# 类被改名之后 forName 找不到 → 静默降级或直接抛异常。
#
# OkHttp 有这种逻辑（按平台选 TLS 实现），Coil 有按 API 版本选解码器。
# 它们各自带了 consumer rules，这里再兜一层 —— 6 MB 的包，多这几十 KB 换"不会莫名其妙"，
# 划算。
-keepnames class okhttp3.** { *; }
-keepnames class coil.** { *; }
-keepnames class io.github.kyant0.** { *; }
-keepnames class dev.chrisbanes.haze.** { *; }
