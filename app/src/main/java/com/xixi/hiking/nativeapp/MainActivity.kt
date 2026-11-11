package com.xixi.hiking.nativeapp

import com.xixi.hiking.nativeapp.ui.safeClickable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import com.xixi.hiking.nativeapp.data.findActivity
import com.xixi.hiking.nativeapp.data.PlannedTripEntity
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.ui.AppTab
import com.xixi.hiking.nativeapp.ui.GlassBottomBar
import com.xixi.hiking.nativeapp.ui.TopBarHeight
import com.xixi.hiking.nativeapp.ui.TopGlassBar
import com.xixi.hiking.nativeapp.ui.glassSource
import com.xixi.hiking.nativeapp.ui.records.RecordDetailDialog
import com.xixi.hiking.nativeapp.ui.records.RecordEditDialog
import com.xixi.hiking.nativeapp.ui.overview.OverviewScreen
import com.xixi.hiking.nativeapp.ui.plans.PlanDetailDialog
import com.xixi.hiking.nativeapp.ui.plans.PlansScreen
import com.xixi.hiking.nativeapp.ui.records.RecordListScreen
import com.xixi.hiking.nativeapp.ui.settings.AppDatabaseRef
import com.xixi.hiking.nativeapp.ui.settings.SettingsScreen
import com.xixi.hiking.nativeapp.ui.theme.HikingTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.xixi.hiking.nativeapp.ui.theme.Ink
import androidx.compose.ui.draw.drawBehind

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ★崩溃记录：闪退时把堆栈落盘，下次启动直接显示（我拿不到 logcat 时全靠它）
        com.xixi.hiking.nativeapp.data.CrashGuard.install(this)
        // ★连续崩过就自动降级成静态玻璃（GPU 运行时崩溃 Java 抓不住，只能这样兜）
        com.xixi.hiking.nativeapp.data.CrashGuard.enterSafeModeIfNeeded(this)
        com.xixi.hiking.nativeapp.ui.liquid.LiquidGlass.init(this)
        enableEdgeToEdge()
        setContent {
            // 界面稳定 2.5 秒后清零崩溃计数（写在这里是为了确保真的绘过一帧）
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2500)
                com.xixi.hiking.nativeapp.data.CrashGuard.markStartupOk(this@MainActivity)
            }
            // ★外观：跟随系统 / 强制浅色 / 强制深色（存在 Prefs，重启保留）
            val ctx = androidx.compose.ui.platform.LocalContext.current
            var themeMode by remember {
                mutableStateOf(com.xixi.hiking.nativeapp.data.Prefs.getString(ctx, com.xixi.hiking.nativeapp.data.Prefs.KEY_THEME, "auto"))
            }
            val dark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            HikingTheme(darkTheme = dark) {
                // ★法律前置：没同意隐私政策就先给同意页（应用市场硬性要求）。
                //   条款版本变了会重新征求 —— 用版本号比对，不是只存个 true。
                var agreed by remember {
                    mutableStateOf(
                        com.xixi.hiking.nativeapp.data.Prefs.getString(
                            this@MainActivity, com.xixi.hiking.nativeapp.data.Prefs.KEY_LEGAL_AGREED, ""
                        ) == com.xixi.hiking.nativeapp.ui.LEGAL_VERSION
                    )
                }
                var legalDoc by remember { mutableStateOf<String?>(null) }

                if (!agreed) {
                    com.xixi.hiking.nativeapp.ui.LegalConsentScreen(
                        onAgree = {
                            // ★2026-11-04 对齐 Web `saveLegalAgreement()`：
                            //   除了条款版本，还要记**同意时间**（ISO 8601）——
                            //   "明确同意"要有痕迹，只存版本号说不清什么时候同意的。
                            val prefs = com.xixi.hiking.nativeapp.data.Prefs
                            prefs.setString(
                                this@MainActivity,
                                prefs.KEY_LEGAL_AGREED,
                                com.xixi.hiking.nativeapp.ui.LEGAL_VERSION
                            )
                            prefs.setString(
                                this@MainActivity,
                                prefs.KEY_LEGAL_AGREED_AT,
                                java.text.SimpleDateFormat(
                                    "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US
                                ).format(java.util.Date())
                            )
                            agreed = true
                        },
                        onDecline = { finish() },
                        onReadPolicy = { legalDoc = "privacy" },
                        onReadDisclaimer = { legalDoc = "disclaimer" }
                    )
                    when (legalDoc) {
                        "privacy" -> com.xixi.hiking.nativeapp.ui.settings.PrivacyPolicyDialog(
                            hazeState = remember { HazeState() }, onDismiss = { legalDoc = null }
                        )
                        "disclaimer" -> com.xixi.hiking.nativeapp.ui.settings.DisclaimerDialog(
                            hazeState = remember { HazeState() }, onDismiss = { legalDoc = null }
                        )
                    }
                    return@HikingTheme
                }

                // ★★三层 backdrop（液态玻璃的地基）：
                //   ① bg       = 纯背景（渐变 + 光斑），页面里的卡片/按钮采样它 —— 不会互相串内容
                //   ② content  = 背景 + 全部页面内容，底栏/弹窗采样它 —— 真 backdrop，滚到下面的记录会被弯折
                //   ③ dialog   = 弹窗自己导出的合成结果，在 GlassOverlay 内部创建
                val bgBackdrop = com.kyant.backdrop.backdrops.rememberCanvasBackdrop(
                    onDraw = { drawAppBackground(dark) }
                )
                val contentBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                androidx.compose.runtime.CompositionLocalProvider(
                    // ★页面内玻璃默认采样「纯背景」：卡片在内容层里面，采样内容层就是自采样（递归）
                    com.xixi.hiking.nativeapp.ui.liquid.LocalLiquidBackdrop provides bgBackdrop,
                    // ★浮层（底栏 / 弹窗）采样「整页内容」：这才是真 backdrop，滚到玻璃下面的会被弯折
                    com.xixi.hiking.nativeapp.ui.liquid.LocalContentBackdrop provides contentBackdrop
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        AppRoot(
                            darkTheme = dark,
                            backdrop = contentBackdrop,
                            onThemeChange = { themeMode = it }
                        )
                    }
                }
            }
        }
    }
}

// ★1:1 对齐现有 App 的底栏：4 个 tab，顺序 概览/计划/记录/设置（记录在第 3 位，截图里它被紫色胶囊选中）

/**
 * ★App 背景画法（渐变 + 三颗光斑）。
 *
 * 抽成 DrawScope 扩展是为了**一份画法两处用**：
 *   ① 屏幕上用 Canvas 画出来
 *   ② 喂给 bgBackdrop（CanvasBackdrop），让页面里的卡片/按钮能采样到同一块背景
 * 两处必须完全一致，否则卡片折射出来的颜色会和真实背景错位。
 *
 * ★2026-11-03 光斑改成 Web `.aurora-bg` 的三颗（蓝/粉/青，深色靛/玫/青）。
 *
 * @param spots 是否画光斑。
 *   屏幕上**不画**（传 false）—— 那三颗由 [com.xixi.hiking.nativeapp.ui.AuroraLayer]
 *   以「只动 graphicsLayer、不重绘」的方式叠在上面，会流动；
 *   而 bgBackdrop 仍然画静态的基准形态（传 true），保证玻璃折射背后有颜色可取。
 *   两边位置/色值同源，肉眼分不出差别（玻璃本身只有 0.05 的白底）。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAppBackground(
    dark: Boolean,
    spots: Boolean = true
) {
    if (dark) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF0B1120), Color(0xFF101A2E), Color(0xFF16203A))
            )
        )
    } else {
        // ★不能是纯白：卡片是白的，背景也白 ⇒ 卡片等于隐形。
        //   压到「极淡灰紫」，白卡片才有浮起来的感觉（iOS 分组列表同款做法）。
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFF7F8FD), Color(0xFFF5F6FC), Color(0xFFF8F4FC)),
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(900f, 1600f)
            )
        )
    }
    if (!spots) return

    // 三颗光斑的**基准形态**（动画 0% 那一帧），位置照 Web：
    //   ① 蓝 左上 (-14vw, -16vh) 70vw   ② 粉 右下 (-16vw, -14vh) 76vw
    //   ③ 青 中上 ( 32vw, -20vh) 54vw
    val w = size.width
    val h = size.height
    // left/top 是元素左上角（像素）。Web 用 vw/vh 混合：直径按宽度算，位置按各自方向算，
    // 所以下面 left/right/bottom 的换算都写成具体表达式，不要试图用一个比例参数概括。
    fun spot(c: Color, d: Float, left: Float, top: Float) {
        val center = androidx.compose.ui.geometry.Offset(left + d / 2f, top + d / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to c, 0.68f to Color.Transparent),
                center = center,
                radius = d / 2f
            ),
            radius = d / 2f,
            center = center
        )
    }
    // ① 蓝 左上：left:-14vw  top:-16vh  直径 70vw
    val d1 = w * 0.70f
    spot(
        if (dark) Color(0xFF6366F1).copy(alpha = 0.26f) else Color(0xFF60A5FA).copy(alpha = 0.30f),
        d1, -0.14f * w, -0.16f * h
    )
    // ② 粉 右下：right:-16vw  bottom:-14vh  直径 76vw
    val d2 = w * 0.76f
    spot(
        if (dark) Color(0xFFBE185D).copy(alpha = 0.24f) else Color(0xFFF472B6).copy(alpha = 0.26f),
        d2, w - d2 + 0.16f * w, h - d2 + 0.14f * h
    )
    // ③ 青 中上：left:32vw  top:-20vh  直径 54vw
    val d3 = w * 0.54f
    spot(
        if (dark) Color(0xFF0F766E).copy(alpha = 0.22f) else Color(0xFF2DD4BF).copy(alpha = 0.22f),
        d3, 0.32f * w, -0.20f * h
    )
}

@Composable
private fun AppRoot(
    darkTheme: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    onThemeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val hazeState = remember { HazeState() }

    val db = remember {
        // ★必须走 buildDatabase()：它带 MIGRATION_2_3。
        //   直接 fallbackToDestructiveMigration() = 每次加字段都清空用户全部数据。
        com.xixi.hiking.nativeapp.data.buildDatabase(context)
    }
    val dao = remember { db.recordDao() }
    val planDao = remember { db.plannedTripDao() }
    val photoDao = remember { db.photoDao() }
    var photoTick by remember { mutableStateOf(0) }
    remember { AppDatabaseRef.set(db); true }

    // ★★2026-11-04 默认页 = **概览**（用户：「默认页是概览页……你没听懂我意思吗」）
    //   上一轮我把「返回时自动跳记录页」理解成了「默认要记录页」，改反了。
    //   现在的口径：启动就是概览；返回键只负责退出（双击确认），不再偷偷切页。
    var tab by remember { mutableStateOf(AppTab.OVERVIEW) }
    var selected by remember { mutableStateOf<RecordEntity?>(null) }
    // ★顶栏标题可自定义（Web 版同功能：点标题改名，最多 20 字，重启保留）
    //
    // ★★2026-11-07 改名迁移：应用正式定名「徒步小记」。
    //   老用户机器上存的很可能还是旧默认值 "XXの徒步小记" ——
    //   那是**用户没改过**、只是早期版本把默认值写进了 Prefs。
    //   这种情况要换掉，否则改名对老用户不生效，标题还挂着旧名字。
    //   判断依据只认这两个旧默认值，用户自己起过的名字（哪怕很像）一律不动。
    var appTitle by remember {
        mutableStateOf(
            run {
                val saved = com.xixi.hiking.nativeapp.data.Prefs.getString(
                    context, com.xixi.hiking.nativeapp.data.Prefs.KEY_APP_TITLE, "徒步小记"
                )
                if (saved == "XXの徒步小记" || saved == "XiXiの徒步小记") "徒步小记" else saved
            }
        )
    }
    // 迁移要**写回磁盘**：否则导出的备份、回忆册标题还会从 Prefs 读到旧名字。
    // 放在 LaunchedEffect 里而不是 remember 里 —— 组合期不该写数据。
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val saved = com.xixi.hiking.nativeapp.data.Prefs.getString(
            context, com.xixi.hiking.nativeapp.data.Prefs.KEY_APP_TITLE, ""
        )
        if (saved == "XXの徒步小记" || saved == "XiXiの徒步小记") {
            com.xixi.hiking.nativeapp.data.Prefs.setString(
                context, com.xixi.hiking.nativeapp.data.Prefs.KEY_APP_TITLE, "徒步小记"
            )
        }
    }
    var showTitleEdit by remember { mutableStateOf(false) }
    // 编辑态：showEdit 控制显隐，editRecord 为 null 表示「新建」
    var showEdit by remember { mutableStateOf(false) }
    var editRecord by remember { mutableStateOf<RecordEntity?>(null) }
    // ★★新建记录的「稳定草稿 id」（2026-11-02 修的核心数据 bug）
    //   原来 RecordEditDialog 内部用 `"r" + System.currentTimeMillis()` 现算 id，
    //   每次重组都是一个新 id ⇒ ① 加照片时挂到骨架记录上，保存时又是另一个 id，
    //   照片丢失 + 列表里多出一条「（新记录）」；② 新建时照片永远不显示。
    //   现在 id 在打开弹窗时定好，弹窗、骨架记录、照片、保存全用同一个。
    var editDraftId by remember { mutableStateOf("") }
    // 新建时为了挂照片落库的骨架记录 id（用来在「取消」时清干净）
    var pendingNewId by remember { mutableStateOf("") }
    // ★协程兜底：scope.launch 里任何异常（Room / 文件 / 网络）原来会直接抛到主线程把 App 崩掉。
    //   「点很多东西都闪退」有一部分就是这里的锅 —— 点删除、导入、同步都走 scope。
    val scope = androidx.compose.runtime.rememberCoroutineScope {
        kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
            android.util.Log.e("hiking", "后台出了点错，已经拦下了", e)
        }
    }

    LaunchedEffect(Unit) {
        // ★★种子数据只能灌一次（2026-11-02）：
        //   原来是「记录数为 0 就灌 3 条示例」—— 用户在设置里点了「抹掉所有足迹」，
        //   下次冷启动记录数又变成 0 ⇒ 三座「假山」原地复活，用户会以为没删掉。
        //   现在用 Prefs 标记「已灌过」，且整段包 try（Room 异常原来会直接崩在启动路径上）。
        try {
            val seeded = com.xixi.hiking.nativeapp.data.Prefs.getBool(
                context, com.xixi.hiking.nativeapp.data.Prefs.KEY_SEEDED, false
            )
            if (!seeded) {
                if (dao.count() == 0) dao.upsertAll(sampleRecords())
                // ★2026-11-05 示例计划（原来只灌记录，计划页是空的，新用户以为没这功能）
                if (planDao.count() == 0) planDao.upsertAll(samplePlans())
                com.xixi.hiking.nativeapp.data.Prefs.setBool(
                    context, com.xixi.hiking.nativeapp.data.Prefs.KEY_SEEDED, true
                )
            }
        } catch (e: Throwable) {
            android.util.Log.w("hiking", "示例数据写入跳过", e)
        }
        // ★自动同步：把 DB 单例交给设置页的导出逻辑复用
        com.xixi.hiking.nativeapp.ui.settings.AppDatabaseRef.set(db)
    }
    val records by dao.observeAll().collectAsState(initial = emptyList())
    val plans by planDao.observeAll().collectAsState(initial = emptyList())

    // ══════════════════════════════════════════════════════════════
    //  ★★2026-11-10 计划闹钟重排（真提醒，不依赖 App 开着）
    //
    //  以前只有"打开 App 时顺手提一句"，App 关着就永远不响 ——
    //  计划、日历、到期处理整套流程的最后一环是空的。
    //  现在：计划列表一变（新增/删除/改日期/完成），立刻按最新数据重排闹钟。
    //  幂等，重复调没有副作用；放在 IO 线程，别让排闹钟卡住首屏。
    // ══════════════════════════════════════════════════════════════
    androidx.compose.runtime.LaunchedEffect(plans) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.xixi.hiking.nativeapp.data.PlanAlarm.reschedule(context, plans)
            } catch (t: Throwable) {
                android.util.Log.w("hiking", "重排计划闹钟失败（已忽略）", t)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ★★2026-11-09 启动自动检查更新（对齐 Web 版 autoCheckUpdateOnLaunch）
    //
    //  Web 版的行为：启动后延迟 1.5 秒查一次 GitHub → 有新版本弹大窗，
    //  无新版**静默**，离线/失败**静默**。原生版之前只能去设置页手动点，
    //  等于"更新功能只对会翻设置的人有效"。
    //
    //  克制原则（照抄 Web）：
    //    · 只在**真有新版本**时弹窗
    //    · 已是最新 / 离线 / 检查失败 → 一个字都不说
    //      （一进 App 就报错是最招人烦的事，而且用户什么也做不了）
    // ══════════════════════════════════════════════════════════════
    var autoUpdateRelease by remember {
        mutableStateOf<com.xixi.hiking.nativeapp.data.Updater.Release?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)      // 让首屏先渲染，别和启动加载抢
        if (!com.xixi.hiking.nativeapp.data.Net.isOnline(context)) return@LaunchedEffect
        val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.xixi.hiking.nativeapp.data.Updater.check(context)
            } catch (t: Throwable) {
                null                        // 检查更新绝不能影响启动
            }
        }
        if (res is com.xixi.hiking.nativeapp.data.Updater.Result.Update) {
            autoUpdateRelease = res.release
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ★★2026-11-11 上次启动崩过 → 提示一次
    //
    //  以前崩溃只写本地日志，界面上一句话都没有：用户只觉得"刚才闪了一下"，
    //  然后什么都没有，诊断也永远不会被看到。这里用轻提示提一句，
    //  不弹窗、不打断，用户想看再进设置导出。
    //  ★只在**没提示过的**那次崩溃上出现（CrashGuard.shouldNotifyLast 用内容 hash 去重）。
    // ══════════════════════════════════════════════════════════════
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2600)      // 排在更新检查之后，别抢注意力
        val notify = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.xixi.hiking.nativeapp.data.CrashGuard.shouldNotifyLast(context)
            } catch (t: Throwable) {
                false
            }
        }
        if (notify) {
            com.xixi.hiking.nativeapp.ui.AppToast.show(
                "上次没正常退出，设置 → 诊断里可导出", "error"
            )
        }
    }

    // ★★2026-11-05 首启「从网页版搬过来」引导
    //
    //   背景：Android 上**不可能**用原生 APK 覆盖安装网页版（PWA 由 Chrome 生成 WebAPK，
    //   包名和签名都不同，数据在浏览器的 IndexedDB 里，原生读不到）。
    //   所以能做的只有把"搬家"这一步做到最短：首次打开、且本机没有任何记录时，
    //   问一句「要在网页版导出、在这里导入吗」——点「去导入」直接落到导入弹窗。
    var showMigrateAsk by remember { mutableStateOf(false) }
    var autoOpenImport by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(records.isEmpty(), plans.isEmpty()) {
        if (records.isEmpty() && plans.isEmpty() &&
            !com.xixi.hiking.nativeapp.data.Prefs.getBool(
                context, "migrate_asked_v1", false
            )
        ) {
            // 等数据加载完再问，避免"库还没读完就弹"
            kotlinx.coroutines.delay(1200)
            com.xixi.hiking.nativeapp.data.Prefs.setBool(context, "migrate_asked_v1", true)
            showMigrateAsk = true
        }
    }

    // ★★2026-11-05 安装包完整性（对应网页版的 ResGuard 资源校验）
    //
    //   网页版校验的是"资源文件有没有被改"；原生版资源都编进 APK，改任何一个字节
    //   都必须重新签名 —— 所以这里校验**签名证书指纹**，等价且更硬。
    //   软校验：只提示，不退出、不禁用（万一以后换签名忘了更新 pin，
    //   硬拦截会把所有老用户锁在门外）。
    //   每个版本只提醒一次：Preps 里记住"已经提醒过哪个版本"。
    var showTamperWarn by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val r = com.xixi.hiking.nativeapp.data.Integrity.check(context)
        if (r.state == com.xixi.hiking.nativeapp.data.Integrity.State.MODIFIED) {
            val warned = com.xixi.hiking.nativeapp.data.Prefs.getString(
                context, "integrity_warned_v", ""
            )
            if (warned != com.xixi.hiking.nativeapp.BuildConfig.VERSION_NAME) {
                com.xixi.hiking.nativeapp.data.Prefs.setString(
                    context, "integrity_warned_v",
                    com.xixi.hiking.nativeapp.BuildConfig.VERSION_NAME
                )
                showTamperWarn = true
            }
        }
    }

    // ★山册要按山聚合「跨记录」的全量照片 —— 订阅全表，不再逐条查（否则「N 张只能翻 M 张」）
    val allPhotos by photoDao.observeAll().collectAsState(initial = emptyList())
    var planTarget by remember { mutableStateOf<PlannedTripEntity?>(null) }
    var showPlan by remember { mutableStateOf(false) }
    // ★计划走完后的庆祝卡（Web 版同款流程：转记录 → 切到记录页 → 庆祝 → 「去补全」进编辑）
    // ★★2026-11-02：日历明细里的「完成」以前是「打开详情弹窗」—— 按了不但没完成，
    //   还弹出一个详情，看着就像坏了。现在先确认（对齐 Web 版的 showConfirmCompleteModal），
    //   确认后直接走「计划 → 记录」的同一条链路。
    var planCompleteTarget by remember { mutableStateOf<com.xixi.hiking.nativeapp.data.PlannedTripEntity?>(null) }
    // 「延期」= 直接以编辑态打开计划弹窗（Web 版 openPlannedDetailModal(id,'edit')）
    var planEdit by remember { mutableStateOf(false) }
    var doneRecordId by remember { mutableStateOf<String?>(null) }
    var donePlanName by remember { mutableStateOf<String?>(null) }
    var confettiPlay by remember { mutableStateOf(false) }
    // 计划刚走完 → 撒一把彩屑（Web 版 showPlanCompleteCelebration 同款）
    LaunchedEffect(doneRecordId) { if (doneRecordId != null) confettiPlay = true }

    // ★★2026-11-05 里程碑达成庆祝（对齐 web 的 showMilestoneCelebration）
    //
    //   为什么用 Prefs 存基线而不是 remember 上一次的值：
    //   首次进入时 records 是异步来的（先空后满），用内存变量会在"空→满"那一下
    //   把所有历史成就全庆祝一遍。存进 Prefs 之后：
    //     · 第一次运行（含灌示例数据）：只写基线，不庆祝
    //     · 之后每次记录变化：只庆祝**新达成**的
    androidx.compose.runtime.LaunchedEffect(records) {
        if (records.isEmpty()) return@LaunchedEffect
        val done = com.xixi.hiking.nativeapp.data.milestoneDone(records).toSet()
        val stored = com.xixi.hiking.nativeapp.data.Prefs.getString(context, "milestone_done", "")
        if (!com.xixi.hiking.nativeapp.data.Prefs.getBool(context, "milestone_init", false)) {
            // 第一次跑：只落基线
            com.xixi.hiking.nativeapp.data.Prefs.setString(context, "milestone_done", done.joinToString(","))
            com.xixi.hiking.nativeapp.data.Prefs.setBool(context, "milestone_init", true)
            return@LaunchedEffect
        }
        val prev = stored.split(",").filter { it.isNotBlank() }.toSet()
        val newly = done - prev
        if (newly.isNotEmpty()) {
            val ms = com.xixi.hiking.nativeapp.data.MILESTONES.firstOrNull { it.id in newly }
            if (ms != null) {
                confettiPlay = true
                com.xixi.hiking.nativeapp.ui.AppToast.show("达成：${ms.title}", "success")
            }
            com.xixi.hiking.nativeapp.data.Prefs.setString(context, "milestone_done", done.joinToString(","))
        }
    }


    // ★启动一次：读界面偏好 + 发当天提醒（对齐 Web 版：计划到期 / 超 30 天没备份）
    LaunchedEffect(Unit) {
        com.xixi.hiking.nativeapp.data.UiPrefs.init(context)
    }
    LaunchedEffect(records.size, plans.size) {
        if (records.isEmpty() && plans.isEmpty()) return@LaunchedEffect
        runCatching {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
            com.xixi.hiking.nativeapp.data.Notifier.dailyReminders(
                context,
                plans.filter { it.plannedDate == today }.map { it.name },
                com.xixi.hiking.nativeapp.data.Prefs.getLong(
                    context, com.xixi.hiking.nativeapp.data.Prefs.KEY_LAST_BACKUP, 0L
                )
            )
            com.xixi.hiking.nativeapp.data.UiPrefs.canNotify =
                com.xixi.hiking.nativeapp.data.Notifier.canPost(context)
        }
    }
    var refreshTick by remember { mutableStateOf(0) }
    var showMilestones by remember { mutableStateOf(false) }
    // ★上次崩溃（有就显示横幅，点开看堆栈）
    var lastCrash by remember { mutableStateOf(com.xixi.hiking.nativeapp.data.CrashGuard.readLast(context)) }
    var showCrashDetail by remember { mutableStateOf(false) }

    // ★★★2026-11-04【真高斯模糊 · 最终做法】模糊交给**弹窗自己**做，页面层完全不动。
    //
    //   上一版是给「背景 + 内容」整层加 Modifier.blur，真机上的结果用户一句话讲清了：
    //   「模糊层在弹窗上方，盖住了所有屏幕」——
    //   根因是**有十几个弹窗是挂在页面 composable 内部的**
    //   （RecordListScreen / PlansScreen / OverviewScreen / SettingsScreen 各有一批），
    //   它们本身就活在要被模糊的那棵树里 ⇒ 被一起糊掉、还被当成"盖在弹窗上面的模糊层"。
    //
    //   现在撤回整层模糊：弹窗自己用 drawBackdrop 采样 contentBackdrop 再 blur
    //   —— 也就是 Web 的 `backdrop-filter: blur()` 的作用域（只糊自己这一块矩形）。
    //   好处：① 弹窗外的页面保持清晰（不再"整屏糊住"）
    //        ② 挂在页面内部的弹窗也自动正常（没人去糊它们了）
    //   见 GlassOverlay 里的 `dialogBlurLayer`。

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ★★2026-11-05「一镜到底」：记录每一次按下的位置，给弹窗当展开原点。
            //   用 Initial pass 只读不消费 ⇒ 不影响任何子节点的点击/滑动。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val down = e.changes.firstOrNull { it.pressed && !it.previousPressed }
                        if (down != null && size.width > 0 && size.height > 0) {
                            com.xixi.hiking.nativeapp.ui.OverlayOrigin.fx = down.position.x / size.width
                            com.xixi.hiking.nativeapp.ui.OverlayOrigin.fy = down.position.y / size.height
                        }
                    }
                }
            }
    ) {
        // ★★★2026-11-04【第八版 · 系统级 Modifier.blur，白块问题根治】
        //
        //   前七版都用 drawBackdrop 去采样"内容层的纹理"再糊，全都倒在同一个坑上：
        //   用户截图里那块**边缘清晰的白色长方块** —— 采样纹理和屏幕坐标错位，
        //   露出一块**没被糊过的原始内容**。调半径、加雾都治不了，因为它不是模糊的产物。
        //
        //   现在直接给内容层上 `Modifier.blur`（系统 RenderEffect，作用于整层，没有采样错位）。
        //   会不会又把弹窗糊掉？不会了：
        //     · 页面内弹窗现在走 Popup（独立窗口），不在这一层里
        //     · 顶层弹窗本来就画在这一层之外
        //   —— 8.3 失败只是因为当时弹窗还长在内容层里。
        val overlayCount = com.xixi.hiking.nativeapp.ui.OverlayOpen.count.intValue
        val blurTarget =
            if (overlayCount > 0 && com.xixi.hiking.nativeapp.ui.liquid.LiquidGlass.gpuEffectsUsable) 16f
            else 0f
        val bgBlur by androidx.compose.animation.core.animateFloatAsState(
            targetValue = blurTarget,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
            label = "overlayBgBlur"
        )

        // ★★「背景 + 内容」整体作为液态玻璃的采样源（离屏录一张纹理）。
        //   底栏与弹窗都是它的**兄弟**（画在它之后），采样它做真实折射。
        //   不要把浮层放进来 —— 会录到自己，形成自我折射 / 递归。
        com.xixi.hiking.nativeapp.ui.liquid.LiquidBackdropHost(
            state = backdrop,
            modifier = Modifier
                .fillMaxSize()
                .let { m -> if (bgBlur > 0.05f) m.blur(bgBlur.dp) else m }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ★背景：渐变（+ 静态光斑给 backdrop 采样）。
                //   ★用 Canvas 直绘（而不是 Box+Brush）是为了让同一段画法能被 bgBackdrop
                //     复用：页面卡片采样 bgBackdrop 时，折射的就是这块背景，不会串到内容。
                //   ★2026-11-03：屏幕上传 spots=false —— 光斑交给下面会流动的 AuroraLayer，
                //     否则静态光斑和动画光斑会叠两层，亮度翻倍。
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawAppBackground(darkTheme, spots = false)
                }
                // ★★会流动的三颗光斑（1:1 搬 Web .aurora-bg）
                com.xixi.hiking.nativeapp.ui.AuroraLayer(Modifier.fillMaxSize())

                // ★内容层：仍标记 haze 源 —— 页面里的卡片用的是「模糊玻璃」（内容层不能自采样）
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .glassSource(hazeState)
                        .statusBarsPadding()
                ) {
                    // ★★标题栏不再占内容的流（2026-11-02）：
                    //   顶栏改成**浮层**（见下面 TopGlassBar），内容从屏顶开始铺，
                    //   滚起来才会从玻璃底下划过 —— 这是"羽化过渡"能成立的前提。
                    //   各页面自己按 TopBarHeight 留顶部内边距，保证第一张卡不被挡住。

                    // ★上次崩溃横幅（只在崩溃后出现一次，点开看堆栈，✕ 清掉）
                    //   ★它现在也得让开顶栏，否则被玻璃压住看不见。
                    val bannerShown = lastCrash != null
                    if (bannerShown) {
                        Spacer(Modifier.height(TopBarHeight))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFDC2626).copy(alpha = 0.10f))
                                .safeClickable { showCrashDetail = true }
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "上次闪退了，点这里看原因",
                                fontSize = 12.sp,
                                color = Ink,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "✕",
                                fontSize = 14.sp,
                                color = Ink.copy(alpha = 0.7f),
                                modifier = Modifier.safeClickable {
                                    com.xixi.hiking.nativeapp.data.CrashGuard.clear(context)
                                    lastCrash = null
                                }.padding(horizontal = 6.dp)
                            )
                        }
                    }

                    // ★★2026-11-02 撤销了原来那条 12dp 的「背景色渐隐」。
                    //   那是盖色块，不是过渡（用户："别切两条横条一弄就完事"）。
                    //   真正的过渡在 TopGlassBar 里：真模糊 + 羽化蒙版。
                    Box(modifier = Modifier.weight(1f)) {
                        // 顶栏是浮层 ⇒ 页面内容要自己让开它（有崩溃横幅时横幅已经让过了）
                        val pageTopInset = if (bannerShown) 10.dp else TopBarHeight

                // ★★★2026-11-04 第二次改左右滑（用户：「所有按钮仍然完全没反应，没弹窗」）
                //
                //   上一版用 HorizontalPager 装四个页面。它在 Robolectric 里点得动，
                //   但真机上**整页的点击都没反应** —— 最可能的原因是 Pager 自己带着
                //   一整套 `scrollable` 手势链：页面里的按钮点击要和它抢同一个
                //   down/up 事件流，只要有一点点位移（手指按下总会抖几个像素），
                //   事件就被 Pager 的拖动判定拿走，点击根本到不了按钮。
                //
                //   现在换成**只监听横向拖动**的手势：
                //     · detectHorizontalDragGestures 必须先越过 touchSlop 才算拖动，
                //       一次正常点击（几乎没有位移）它完全不管，按钮照常收到事件
                //     · 拖动结束后按位移方向切 tab，位移量 < 阈值不切（防误切）
                //   页面切换动画交给 AnimatedContent（左右滑入滑出），
                //   比 Pager 的"跟手位移"简单，但不会干扰任何子节点手势。
                val tabList = AppTab.entries
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onHorizontalDrag = { change, drag ->
                                    total += drag
                                    // 只有真的在横拖时才消费事件（点击不受影响）
                                    if (kotlin.math.abs(total) > viewConfiguration.touchSlop) {
                                        change.consume()
                                    }
                                },
                                onDragEnd = {
                                    val idx = tabList.indexOf(tab)
                                    // 阈值 72dp：小于这个位移当作误触，不切页
                                    val t = 72.dp.toPx()
                                    if (total <= -t && idx < tabList.lastIndex) tab = tabList[idx + 1]
                                    else if (total >= t && idx > 0) tab = tabList[idx - 1]
                                }
                            )
                        }
                ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        // 往右切（序号变大）时新页从右边进、旧页向左边退，反之相反
                        val forward = targetState.ordinal > initialState.ordinal
                        // ★2026-11-05 过渡重调（用户：「优化各种过渡动画」）：
                        //   · 新页只走 86% 位移（走满会像"整块板子拍过来"）
                        //   · 280ms + FastOutSlowIn —— 起步快、收尾缓，跟手感的来源
                        //   · 旧页反向 1/3、240ms，形成前后层次（不是两块同时平移）
                        val ease = androidx.compose.animation.core.FastOutSlowInEasing
                        (
                            androidx.compose.animation.slideInHorizontally(
                                animationSpec = androidx.compose.animation.core.tween(280, easing = ease)
                            ) { w -> ((if (forward) w else -w) * 0.86f).toInt() } +
                                androidx.compose.animation.fadeIn(
                                    androidx.compose.animation.core.tween(200, easing = ease)
                                )
                            ).togetherWith(
                            androidx.compose.animation.slideOutHorizontally(
                                animationSpec = androidx.compose.animation.core.tween(240, easing = ease)
                            ) { w -> if (forward) -w / 3 else w / 3 } +
                                androidx.compose.animation.fadeOut(
                                    androidx.compose.animation.core.tween(160)
                                )
                        )
                    },
                    label = "tabSwitch",
                    modifier = Modifier.fillMaxSize()
                ) { pageTab ->
                when (pageTab) {
                    AppTab.OVERVIEW -> OverviewScreen(
                        records = records,
                        hazeState = hazeState,
                        onOpenRecord = { selected = it },
                        onOpenMilestones = { showMilestones = true },
                        plans = plans,
                        onGoPlans = { tab = AppTab.PLANS },
                        onGoBackup = { tab = AppTab.SETTINGS },
                        // 引导卡主按钮：去记录页写第一条（对齐 Web welcomeGoBtn）
                        onGoRecords = { tab = AppTab.RECORDS },
                        topInset = pageTopInset
                    )
                    // ★山册是记录页里的「视图切换」，不是二级页 —— 底栏始终在
                    AppTab.RECORDS -> RecordListScreen(
                        records = records,
                        photos = allPhotos,
                        hazeState = hazeState,
                        onOpen = { selected = it },
                        topInset = pageTopInset,
                        onNew = { editRecord = null; editDraftId = "r" + System.currentTimeMillis(); pendingNewId = ""; showEdit = true },
                        onDelete = { rec ->
                            scope.launch {
                                val ps = photoDao.byRecord(rec.id)
                                com.xixi.hiking.nativeapp.data.PhotoStore.deleteByRecord(context, ps)
                                photoDao.deleteByRecord(rec.id)
                                dao.deleteById(rec.id)
                                photoTick++
                            }
                        },
                        onDeleteMany = { list ->
                            scope.launch {
                                list.forEach { rec ->
                                    val ps = photoDao.byRecord(rec.id)
                                    com.xixi.hiking.nativeapp.data.PhotoStore.deleteByRecord(context, ps)
                                    photoDao.deleteByRecord(rec.id)
                                }
                                dao.deleteByIds(list.map { it.id })
                                photoTick++
                            }
                        }
                    )
                    AppTab.PLANS -> PlansScreen(
                        plans = plans,
                        hazeState = hazeState,
                        topInset = pageTopInset,
                        onOpen = { planTarget = it; planEdit = false; showPlan = true },
                        onNew = { planTarget = null; planEdit = false; showPlan = true },
                        onDeleteMany = { list -> scope.launch { planDao.deleteByIds(list.map { it.id }) } },
                        // ★日历明细里的「完成」：直接弹确认 → 转记录（不再打开详情）
                        onComplete = { t -> planCompleteTarget = t },
                        onDeleteOne = { t -> scope.launch { planDao.deleteById(t.id) } }
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        recordCount = records.size,
                        planCount = plans.size,
                        // 最早一条记录的日期（徒步年资用；无记录为 null）
                        firstRecordAt = records.minByOrNull { it.createdAt }?.createdAt,
                        // ★导入后必须同时 bump photoTick —— 照片是另一个 Flow，
                        //   只刷 refreshTick 的话记录列表更新了、照片还是旧的（看着像"照片没导进来"）
                        onImported = { refreshTick++; photoTick++ },
                        onThemeChange = onThemeChange,
                        hazeState = hazeState,
                        topInset = pageTopInset,
                        autoOpenImport = autoOpenImport,
                        onAutoOpenConsumed = { autoOpenImport = false }
                    )
                }   // ← 闭合 when(pageTab)
                }   // ← 闭合 AnimatedContent
                }   // ← 闭合手势 Box
            }       // ← 闭合 Box(weight 1f)
            // ★这里**不再**留「给底栏让位」的固定高度 —— 底栏是浮层，
            //   内容本就该能滚到它下面；各页末尾自己有尾巴留白。
            //   两边都留会白出 184dp（实测过一次，屏幕下半截整片空着）。
            }   // ← 闭合「内容 Column」
        }       // ← 闭合「背景 + 内容」Box
        }       // ← 闭合 LiquidBackdropHost（它包住了 背景+内容，作为玻璃采样源）

        // ★★顶栏（浮层 · 真模糊 · 羽化）：
        //   和底栏一样画在采样源**之后**。内容从它底下轧过去，被糊掉 + 渐隐，
        //   这就是"羽化过渡"，不是盖两条横条。
        TopGlassBar(
            title = appTitle,
            onEditTitle = { showTitleEdit = true },
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopCenter),
            trailing = if (com.xixi.hiking.nativeapp.data.UiPrefs.showFps) {
                {
                    // ★帧率胶囊（对齐 Web 版 fpsDisplay）：开关在设置页，默认关
                    //   ★2026-11-05 现在同时显示掉帧数：`52 · 3`（帧率 · 这一秒卡了几次）
                    val st = com.xixi.hiking.nativeapp.ui.rememberFpsStat(true)
                    com.xixi.hiking.nativeapp.ui.FpsPill(st.fps, st.jank)
                }
            } else null
        )

        // ★轻提示（浮层最上层）：底栏之上，任何弹窗/页面都能用
        com.xixi.hiking.nativeapp.ui.AppToastHost()

        // ★★2026-11-09 启动自动检查发现的新版本（见上方 LaunchedEffect）
        autoUpdateRelease?.let { r ->
            com.xixi.hiking.nativeapp.ui.settings.UpdateAvailableDialog(
                hazeState = hazeState,
                release = r,
                onStart = {
                    com.xixi.hiking.nativeapp.data.ApkDownloader.start(context, r)
                },
                onDismiss = { autoUpdateRelease = null }
            )
        }

        // ★底栏（浮层 · 真折射）：画在采样源之后 ⇒ 能折射到页面内容
        GlassBottomBar(
            current = tab,
            backdrop = backdrop,
            onSelect = { t ->
                if (t == tab) {
                    // ★2026-11-03 再点当前 tab = 滚回顶部（与 Web switchTab 同款）
                    com.xixi.hiking.nativeapp.ui.TabReselect.tick.intValue++
                } else {
                    tab = t
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 40.dp, vertical = 10.dp)
        )
    }

    // ★★2026-11-04 返回键：**不再"从别的页跳回记录页"**。
    //   用户：「我返回的时候，页面自动回到记录页而不是概览页」—— 他要的是按返回就退出，
    //   不要这种"自动跳页"的小动作（在概览页按一下返回，突然被扔到记录页，很莫名其妙）。
    //   现在四个主页一视同仁：弹窗打开时由弹窗自己的 BackHandler 关自己；
    //   否则按返回 = 双击退出（见下）。
    //
    // ★★2026-11-04 用户：「还有一个确认退出的双次 toast 弹窗」
    //   对齐 Web：第一次**只弹一条 toast「再按一次退出」**，2 秒内再按一下才真的退出。
    //   以前这里是直接 finish —— 误触就退。
    val lastBackAt = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableLongStateOf(0L)
    }
    val backCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.activity.compose.BackHandler(
        enabled = !showEdit && selected == null &&
            !showPlan && !showMilestones
    ) {
        val now = System.currentTimeMillis()
        if (now - lastBackAt.longValue < 2000L) {
            backCtx.findActivity()?.finish()
        } else {
            lastBackAt.longValue = now
            com.xixi.hiking.nativeapp.ui.AppToast.show("再按一次退出", "info")
        }
    }

    // ★2026-11-05 安装包校验提示（软提示，不影响使用）
    if (showTamperWarn) {
        com.xixi.hiking.nativeapp.ui.GlassOverlay(
            hazeState = hazeState,
            onDismiss = { showTamperWarn = false },
            bottomClose = false
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "安装包已被改动",
                    fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Text(
                    "请到官方渠道重装",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                com.xixi.hiking.nativeapp.ui.GlassButton(
                    "知道了", primary = true,
                    onClick = { showTamperWarn = false }
                )
            }
        }
    }

    // ★2026-11-05 首启迁移引导（只弹一次，见上面的 LaunchedEffect）
    if (showMigrateAsk) {
        com.xixi.hiking.nativeapp.ui.GlassOverlay(
            hazeState = hazeState,
            onDismiss = { showMigrateAsk = false },
            bottomClose = false
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "从网页版搬过来？",
                    fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Text(
                    "安卓版和网页版是两个独立应用，数据不互通。记录可以一次搬完：",
                    fontSize = 12.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                Text(
                    "① 网页版「关于应用 → 导出完整备份」，得到一个 zip\n" +
                        "② 回来点「去导入」，选中那个 zip\n" +
                        "记录、照片、计划都会过来；配过网盘的话，配置也会带上",
                    fontSize = 12.sp, lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                com.xixi.hiking.nativeapp.ui.GlassButton(
                    "去导入", primary = true,
                    onClick = {
                        showMigrateAsk = false
                        autoOpenImport = true
                        tab = AppTab.SETTINGS
                    }
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                com.xixi.hiking.nativeapp.ui.GlassButton(
                    "先不用", onClick = { showMigrateAsk = false }
                )
            }
        }
    }

    // 上次崩溃详情
    if (showCrashDetail && lastCrash != null) {
        com.xixi.hiking.nativeapp.ui.GlassOverlay(
            hazeState = hazeState,
            onDismiss = { showCrashDetail = false },
            maxWidthFraction = 0.92f,
            corner = 20,
            maxHeightFraction = 0.80f
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "上次崩溃的堆栈",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "截图发给我就能定位到具体哪一行",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f))
                        .padding(10.dp)
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = lastCrash ?: "",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.weight(1f)) {
                        com.xixi.hiking.nativeapp.ui.GlassButton("知道了", onClick = { showCrashDetail = false })
                    }
                    Box(Modifier.weight(1f)) {
                        // ★崩了最该做的是「把堆栈发出去」，不是「清掉当没事发生」。
                        //   清除入口还留在横幅的 ✕ 上。
                        com.xixi.hiking.nativeapp.ui.GlassButton(
                            "导出诊断", primary = true,
                            onClick = {
                                com.xixi.hiking.nativeapp.data.CrashGuard.shareDiagnostics(context)
                            }
                        )
                    }
                }
            }
        }
    }

    // 里程碑一览
    if (showMilestones) {
        com.xixi.hiking.nativeapp.ui.milestones.MilestoneListDialog(
            records = records,
            hazeState = hazeState,
            onDismiss = { showMilestones = false }
        )
    }

    // 顶栏标题编辑（最多 20 字，与 Web 版一致）
    if (showTitleEdit) {
        var draft by remember { mutableStateOf(appTitle) }
        com.xixi.hiking.nativeapp.ui.GlassOverlay(
            hazeState = hazeState,
            onDismiss = { showTitleEdit = false },
            bottomClose = false,
            maxWidthFraction = 0.88f,
            corner = 20
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "修改标题", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "给你的日记本起个名字，最多 20 个字符",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                com.xixi.hiking.nativeapp.ui.GlassTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 20) draft = it },
                    placeholder = "徒步小记",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${draft.length} / 20",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.weight(1f)) {
                        com.xixi.hiking.nativeapp.ui.GlassButton("取消", onClick = com.xixi.hiking.nativeapp.ui.LocalOverlayClose.current)
                    }
                    Box(Modifier.weight(1f)) {
                        com.xixi.hiking.nativeapp.ui.GlassButton(
                            "保存",
                            primary = true,
                            onClick = {
                                val v = draft.trim().ifBlank { "徒步小记" }
                                appTitle = v
                                com.xixi.hiking.nativeapp.data.Prefs.setString(
                                    context, com.xixi.hiking.nativeapp.data.Prefs.KEY_APP_TITLE, v
                                )
                                showTitleEdit = false
                            }
                        )
                    }
                }
            }
        }
    }

    // 详情（照片随 photoTick 刷新）
    // ★自动同步：记录/计划一变，30 秒防抖后自动上传（需已配置 WebDAV 且开关打开）
    // ★每天一次的本地自动备份（对齐 Web 版 autoLocalBackupIfDue）。
    //   日记本最怕的不是闪退，是数据没了 —— 而 WebDAV 大多数人不配。
    //   延迟 8 秒：让首页先画出来，别和启动抢 IO。
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(8_000)
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.xixi.hiking.nativeapp.data.LocalBackup.backupIfDue(context) {
                    com.xixi.hiking.nativeapp.ui.settings.buildExportJsonPublic(context)
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w("hiking", "本地自动备份跳过", e)
        }
    }

    // ★★key 必须是「整份数据」而不是「条数 + 第一条的 updatedAt」（2026-11-02）：
    //   原来改第 2、3 条记录、或者改计划名字，key 完全不变 ⇒ 永远不触发上传，
    //   用户以为「开了自动同步」，其实云端一直是旧数据。
    LaunchedEffect(records, plans) {
        if (records.isEmpty() && plans.isEmpty()) return@LaunchedEffect
        if (!com.xixi.hiking.nativeapp.data.Prefs.getBool(context, com.xixi.hiking.nativeapp.data.Prefs.KEY_AUTO_SYNC, false)) return@LaunchedEffect
        if (!com.xixi.hiking.nativeapp.data.WebDavClient.load(context).configured) return@LaunchedEffect
        kotlinx.coroutines.delay(30_000)     // 防抖：连续编辑不反复上传
        // ★2026-11-05 离线时**静默跳过**：自动同步是后台行为，
        //   用户没点任何东西，不该看到"同步失败"这种提示（甚至不该浪费 8 秒等超时）。
        if (!com.xixi.hiking.nativeapp.data.Net.isOnline(context)) return@LaunchedEffect
        // ★必须 try：网络错误 / 401 / 磁盘满都会抛，以前没人接 ⇒ 主线程未捕获 ⇒ 闪退
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // ★2026-11-05 自动同步也改传**整包 zip**（与 Web 版同目录同命名）。
                //   代价是每次上传几十 MB —— 但只有这样网页版才能直接恢复照片；
                //   嫌流量大就在设置里关掉「自动同步」，手动点上传。
                com.xixi.hiking.nativeapp.data.WebDavClient.uploadBackup(context)
            }
        } catch (e: Throwable) {
            android.util.Log.w("hiking", "自动同步失败（已忽略）", e)
        }
    }

    selected?.let { r ->
        val photos by remember(r.id, photoTick) {
            photoDao.observeByRecord(r.id)
        }.collectAsState(initial = emptyList())
        RecordDetailDialog(
            record = r,
            photos = photos,
            hazeState = hazeState,
            // ★2026-11-10 同一座山的其它记录（名称去空格、忽略大小写比对）
            sameName = records.filter {
                it.id != r.id && it.name.trim().equals(r.name.trim(), ignoreCase = true)
            },
            onDismiss = { selected = null },
            onEdit = { editRecord = r; editDraftId = r.id; pendingNewId = ""; showEdit = true; selected = null },
            onDelete = {
                scope.launch {
                    val ps = photoDao.byRecord(r.id)
                    com.xixi.hiking.nativeapp.data.PhotoStore.deleteByRecord(context, ps)
                    photoDao.deleteByRecord(r.id)
                    dao.deleteById(r.id)
                }
                selected = null
            }
        )
    }

    // ★★「计划 → 记录」的唯一实现（2026-11-02 抽出来）：
    //   以前这段内联在详情弹窗的 onComplete 里，导致日历按钮只能"打开详情"。
    //   现在弹窗和日历都走这一条，行为不可能再分叉。
    fun completePlan(t: com.xixi.hiking.nativeapp.data.PlannedTripEntity) {
        scope.launch {
            try {
                val now = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()
                ).format(java.util.Date())
                val rec = RecordEntity(
                    id = "r" + System.currentTimeMillis(),
                    name = t.name,
                    distance = 0.0,          // 计划里没有里程，留给用户之后补
                    duration = 0,
                    elevation = t.elevation, // 计划的目标海拔直接带过来
                    difficulty = t.difficulty,
                    mood = "", weather = "", companions = "",
                    notes = t.note,          // 计划备注当小日记草稿
                    createdAt = now, updatedAt = now
                )
                dao.upsertAll(listOf(rec))
                planDao.deleteById(t.id)
                showPlan = false
                doneRecordId = rec.id
                donePlanName = t.name
                tab = AppTab.RECORDS     // 跟 Web 版一致：自动切到记录页
            } catch (e: Throwable) {
                android.util.Log.e("hiking", "计划转记录失败", e)
            }
        }
    }

    // 计划详情 / 新建
    if (showPlan) {
        PlanDetailDialog(
            initial = planTarget,
            hazeState = hazeState,
            startInEdit = planEdit,
            onDismiss = { showPlan = false; planEdit = false },
            onSave = { p ->
                scope.launch { planDao.upsertAll(listOf(p)); showPlan = false }
            },
            onDelete = planTarget?.let { t -> { scope.launch { planDao.deleteById(t.id) }; showPlan = false } },
            onComplete = planTarget?.let { t -> { showPlan = false; completePlan(t) } }
        )
    }

    // ★★过期关怀（1:1 对齐 Web 版 maybeShowOverdueCare，2026-11-03）
    //   过期没去的计划 → 打开 App 提一次「有 N 个计划过期了」，一天最多一次。
    //   Web 的定稿只有「去处理 / 忽略」两颗按钮（顺延入口已删，改日期在计划页自己做）。
    var overdueCare by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(plans) {
        if (plans.isEmpty()) return@LaunchedEffect
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        if (com.xixi.hiking.nativeapp.data.Prefs
                .getString(context, com.xixi.hiking.nativeapp.data.Prefs.KEY_OVERDUE_CARE_DAY, "") == todayStr
        ) return@LaunchedEffect
        val overdue = plans.filter { it.plannedDate.isNotBlank() && it.plannedDate < todayStr }
        if (overdue.isEmpty()) return@LaunchedEffect
        com.xixi.hiking.nativeapp.data.Prefs.setString(
            context, com.xixi.hiking.nativeapp.data.Prefs.KEY_OVERDUE_CARE_DAY, todayStr
        )
        overdueCare = overdue.map { it.name.ifBlank { "未命名计划" } }
    }

    overdueCare?.let { names ->
        val label = names.take(3).joinToString("、") +
            if (names.size > 3) " 等 ${names.size} 个" else ""
        com.xixi.hiking.nativeapp.ui.GlassConfirm(
            hazeState = hazeState,
            title = "有 ${names.size} 个计划过期了",
            message = "到日子没去的：$label。\n去计划页改个日期或删掉吧。",
            confirmText = "去处理",
            cancelText = "忽略",
            onConfirm = { overdueCare = null; tab = AppTab.PLANS },
            onCancel = { overdueCare = null }
        )
    }

    // ★日历明细里点「完成」→ 完成 / 延期 二选一（1:1 对齐 Web 版 showCompleteOrDelayModal）
    planCompleteTarget?.let { t ->
        com.xixi.hiking.nativeapp.ui.plans.PlanDueDialog(
            planName = t.name,
            hazeState = hazeState,
            onDismiss = { planCompleteTarget = null },
            onComplete = {
                planCompleteTarget = null
                completePlan(t)
            },
            onDelay = {
                planCompleteTarget = null
                planEdit = true
                planTarget = t
                showPlan = true
            }
        )
    }

    // ★计划走完的庆祝卡 —— 必须先「关计划弹窗」再「开这张」，绝不允许两层弹窗叠着
    if (doneRecordId != null) {
        com.xixi.hiking.nativeapp.ui.GlassConfirm(
            hazeState = hazeState,
            title = "走完了 🎉",
            message = "「${donePlanName ?: "这趟"}」已经转成记录。里程、心情、照片要不要现在补上？",
            confirmText = "去补全",
            cancelText = "回头再说",
            onConfirm = {
                val id = doneRecordId
                doneRecordId = null; donePlanName = null
                scope.launch {
                    val r = id?.let { dao.getById(it) }
                    if (r != null) { editRecord = r; editDraftId = r.id; pendingNewId = ""; showEdit = true }
                }
            },
            onCancel = { doneRecordId = null; donePlanName = null }
        )
    }

    // ★彩屑：Canvas 不消费触摸事件，飘过时不影响任何按钮
    com.xixi.hiking.nativeapp.ui.ConfettiOverlay(play = confettiPlay) { confettiPlay = false }

    // 编辑 / 新建
    if (showEdit) {
        // ★照片订阅必须用「同一个 id」：新建时原来用 ""，照片永远订阅不到 ⇒ 加完图看不见
        val editId = editRecord?.id ?: editDraftId
        val editPhotos by remember(editId, photoTick) {
            if (editId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList<com.xixi.hiking.nativeapp.data.PhotoEntity>())
            else photoDao.observeByRecord(editId)
        }.collectAsState(initial = emptyList())

        RecordEditDialog(
            initial = editRecord,
            draftId = editDraftId,
            photos = editPhotos,
            hazeState = hazeState,
            // ★2026-11-10 历史山名：按出现次数排（常去的排在前面），点一下就能填入
            knownNames = remember(records) {
                records
                    .map { it.name.trim() }
                    .filter { it.isNotBlank() }
                    .groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .map { it.key }
            },
            onDismiss = {
                // ★新建时如果已经为了挂照片落过骨架记录，「取消」必须把它连照片一起清掉，
                //   否则列表里会永远留一条「（新记录）」。
                val skeleton = if (editRecord == null) pendingNewId else ""
                if (skeleton.isNotBlank()) {
                    scope.launch {
                        try {
                            val ps = photoDao.byRecord(skeleton)
                            com.xixi.hiking.nativeapp.data.PhotoStore.deleteByRecord(context, ps)
                            photoDao.deleteByRecord(skeleton)
                            dao.deleteById(skeleton)
                        } catch (e: Throwable) {
                            android.util.Log.w("hiking", "清理草稿记录失败", e)
                        }
                    }
                }
                pendingNewId = ""
                showEdit = false
            },
            onSave = { rec ->
                scope.launch {
                    dao.upsertAll(listOf(rec))
                    pendingNewId = ""
                    showEdit = false
                    photoTick++
                    // ★2026-11-05 对齐 web 的 showFirstRecordTip：
                    //   用户**自己**存下第一条时撒一把彩屑 + 一句提示。
                    //   用 Prefs 标记而不是 records.size==1 —— 首次安装会灌一条示例数据，
                    //   按数量判断永远不会触发。示例数据不算"第一笔"，不庆祝才合理。
                    if (!com.xixi.hiking.nativeapp.data.Prefs.getBool(context, "first_record_done", false)) {
                        com.xixi.hiking.nativeapp.data.Prefs.setBool(context, "first_record_done", true)
                        confettiPlay = true
                        com.xixi.hiking.nativeapp.ui.AppToast.show("第一笔已记下", "success")
                    }
                }
            },
            onAddPhotos = { uris ->
                scope.launch {
                    val rid = editRecord?.id ?: pendingNewId.ifBlank {
                        // 新建时先把骨架记录落库，照片才有归属（id 用稳定草稿 id，保存时 upsert 覆盖它）
                        dao.upsertAll(listOf(
                            com.xixi.hiking.nativeapp.data.RecordEntity(
                                id = editDraftId, name = "（新记录）", distance = 0.0, duration = 0,
                                elevation = 0, difficulty = 3, mood = "", weather = "",
                                companions = "", notes = "",
                                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                                updatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            )
                        ))
                        pendingNewId = editDraftId
                        editDraftId
                    }
                    uris.forEach { uri ->
                        val pid = "ph" + System.currentTimeMillis() + "_" + (0..999).random()
                        val saved = com.xixi.hiking.nativeapp.data.PhotoStore.saveFromUri(context, uri, pid)
                        if (saved != null) {
                            photoDao.insertAll(listOf(
                                com.xixi.hiking.nativeapp.data.PhotoEntity(
                                    id = pid, recordId = rid, fileName = saved.first,
                                    width = saved.second, height = saved.third,
                                    createdAt = System.currentTimeMillis()
                                )
                            ))
                        }
                    }
                    photoTick++
                }
            },
            onRemovePhoto = { p ->
                scope.launch {
                    com.xixi.hiking.nativeapp.data.PhotoStore.delete(context, p.fileName)
                    photoDao.deleteById(p.id)
                    photoTick++
                }
            }
        )
    }
}

@Composable
private fun PlaceholderScreen(title: String, desc: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun sampleRecords(): List<RecordEntity> {
    val now = System.currentTimeMillis()
    val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    fun daysAgo(n: Int) = iso.format(Date(now - n * 86_400_000L))
    // ★2026-11-05 示例只留 **1 条记录 + 1 条计划**（用户要求）：
    //   以前灌 3 条，列表一打开像"别人的数据"；一条刚好演示排版，
    //   字段也填满（同行人 / 小日记都有），新用户一眼知道每个位置放什么。
    return listOf(
        RecordEntity(
            id = "seed-1",
            name = "太白山拔仙台",
            distance = 18.5,
            duration = 540,
            elevation = 1680,
            difficulty = 5,
            // ★2026-11-05 必须是 MoodOptions 里真实存在的值（开心/平静/兴奋/疲惫）。
            //   之前这里写了"累但值"—— 选择器里没有这个选项，用户打开编辑会发现
            //   四个心情格全都不亮，等于示例数据自相矛盾。示例的每个字段都必须是
            //   用户自己能选出来的值，否则就是假数据。
            mood = "疲惫",
            // 天气同理：只能是 晴/多云/阴/雨/雪（WeatherOptions）
            weather = "多云",
            companions = "老张、阿May",
            // textcheck:ok 示例数据正文（演示"小日记"字段填满是什么样），不是界面说明
            notes = "凌晨四点从下板寺出发，六点半赶上日出。风大，山顶零下，羽绒服没白带。",
            createdAt = daysAgo(3),
            updatedAt = daysAgo(3)
        )
    )
}

/** ★2026-11-05 示例计划（同样只留一条，字段填满） */
internal fun samplePlans(): List<com.xixi.hiking.nativeapp.data.PlannedTripEntity> {
    val iso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val planned = iso.format(Date(System.currentTimeMillis() + 3 * 86_400_000L))
    val created = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        .format(Date())
    return listOf(
        com.xixi.hiking.nativeapp.data.PlannedTripEntity(
            id = "seed-plan-1",
            name = "华山·西峰看日落",
            plannedDate = planned,
            location = "渭南·华阴",
            // textcheck:ok 示例数据正文，不是界面说明
            note = "下午两点上山，西峰看日落；带厚外套和头灯",
            difficulty = 4,
            createdAt = created,
            elevation = 2154
        )
    )
}
