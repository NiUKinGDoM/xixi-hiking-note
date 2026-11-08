package com.xixi.hiking.nativeapp.ui.records

import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.PhotoEntity
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.ui.GlassButton
import com.xixi.hiking.nativeapp.ui.FieldLabel
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.GlassSegmented
import com.xixi.hiking.nativeapp.ui.GlassTextField
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.photos.PhotoPickerGrid
import com.xixi.hiking.nativeapp.ui.photos.PhotoLightbox
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.xixi.hiking.nativeapp.ui.GlassDateTimePicker
import com.xixi.hiking.nativeapp.ui.MoodWeatherPicker
import com.xixi.hiking.nativeapp.ui.PickerField
import com.xixi.hiking.nativeapp.ui.emojiOf

/** 难度档名（对齐 Web 版 diffLabel） */
fun diffLabel(d: Int): String {
    val n = d.coerceIn(1, 5)
    val names = mapOf(1 to "简单", 2 to "较易", 3 to "中等", 4 to "较难", 5 to "困难")
    return "$n 级 · ${names[n]}"
}

/**
 * 日期输入归一化 —— 能解析就转成统一的 `yyyy-MM-ddTHH:mm:ss`，解析不了就退回原值。
 *
 * ★为什么必须做：日期是可手输的文本框。用户写错一个数（2026-13-45 / 2026-02-30），
 * 原来会原样存进库；之后 `parseRecordDate` 返回 null ⇒ 这条记录在时间轴、热力图、
 * 年结里全部消失，但列表里还在，看起来就是「数据莫名其妙少了」。
 */
private fun normalizeDateInput(raw: String, fallback: String): String {
    val s = raw.trim().replace(' ', 'T').replace('/', '-')
    if (s.isBlank()) return fallback
    for (p in listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")) {
        try {
            val f = SimpleDateFormat(p, Locale.getDefault())
            f.isLenient = false                       // ★false 才会拒绝 13 月 / 45 号
            val d = f.parse(s) ?: continue
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(d)
        } catch (e: Exception) {
            // 换下一个格式
        }
    }
    return fallback
}

/**
 * 记录编辑 —— 全自绘控件（不用 Material 的 OutlinedTextField / Button / Slider）。
 * 字段顺序对齐 Web 版：名称 → 海拔/难度 → 心情/天气/同行人 → 用时/里程 → 日期 → 小日记 → 照片
 */
@Composable
fun RecordEditDialog(
    initial: RecordEntity?,
    /** 新建时的稳定草稿 id（由调用方持有）—— 照片、骨架记录、保存都用它 */
    draftId: String,
    photos: List<PhotoEntity>,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onSave: (RecordEntity) -> Unit,
    onAddPhotos: (List<android.net.Uri>) -> Unit,
    onRemovePhoto: (PhotoEntity) -> Unit
) {
    val isNew = initial == null
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()) }
    // ★★base 必须 remember（2026-11-02）：原来这行在每次重组都新建一个 RecordEntity，
    //   id 取「当前毫秒」。保存时 base.id 与打开弹窗时的草稿 id 不是一个 ⇒
    //   ① 照片挂在骨架记录下，正式记录一张图都没有；
    //   ② 列表里多出一条永远删不掉的「（新记录）」。
    val base = remember(initial, draftId) {
        initial ?: RecordEntity(
            id = draftId.ifBlank { "r" + System.currentTimeMillis() },
            name = "", distance = 0.0, duration = 0, elevation = 0, difficulty = 3,
            mood = "", weather = "", companions = "", notes = "",
            createdAt = fmt.format(Date()), updatedAt = fmt.format(Date())
        )
    }

    var name by remember { mutableStateOf(base.name) }
    var elevation by remember { mutableStateOf(if (base.elevation == 0) "" else base.elevation.toString()) }
    var difficulty by remember { mutableStateOf(base.difficulty.coerceIn(1, 5)) }
    var mood by remember { mutableStateOf(base.mood) }
    var weather by remember { mutableStateOf(base.weather) }
    var companions by remember { mutableStateOf(base.companions) }
    var durationH by remember { mutableStateOf(if (base.duration > 0) (base.duration / 60).toString() else "") }
    var durationM by remember { mutableStateOf(if (base.duration > 0) (base.duration % 60).toString() else "") }
    var distance by remember { mutableStateOf(if (base.distance == 0.0) "" else base.distance.toString()) }
    var createdAt by remember { mutableStateOf(base.createdAt.replace("T", " ").take(16)) }
    var notes by remember { mutableStateOf(base.notes) }

    // ★★2026-11-02 三个选择器（用户：「时间选择器也没有，心情选择也没有，天气选择也没有」）
    var showDatePicker by remember { mutableStateOf(false) }
    var showMoodPicker by remember { mutableStateOf(false) }
    var showWeatherPicker by remember { mutableStateOf(false) }

    // ★★2026-11-04 编辑态灯箱（对齐 Web：编辑弹窗里点照片 = openPhotoLightbox(..., editing=true)）
    //   · 点缩略图打开 → 能在灯箱里左右翻、看大图
    //   · 主按钮是「添加」而不是「保存」，并且有「删除」
    var lightboxAt by remember { mutableStateOf<Int?>(null) }
    // 选图 launcher 提到这一层：网格里的「+」和灯箱里的「添加」共用同一个入口，
    // 免得两套 launcher 各算各的上限（Web 也是同一个 pickPhotos）
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(24)
    ) { uris -> if (uris.isNotEmpty()) onAddPhotos(uris) }

    GlassOverlay(
        bottomClose = false,
        hazeState = hazeState, onDismiss = onDismiss,
        maxWidthFraction = 0.92f, corner = 26
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // ★不再自己写死 610dp 限高（2026-11-02）：外层 GlassOverlay 已按屏高 86% 限高，
                //   这层再压一道 → 高屏手机上「照片」那一行正好被切掉半个格子，看着像布局坏了。
                //   现在交给外层限高 + 内部 verticalScroll，矮屏照样能滚。
        ) {
            // ★字段区可滚动，按钮固定在底部 ——
            //   原来整个 Column 都在 verticalScroll 里，小屏上「取消/保存」被滚出屏幕，根本点不到。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            Text(
                if (isNew) "新建记录" else "编辑记录",
                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // ── 名称 ──
            FieldLabel("名称")
            GlassTextField(name, { name = it }, "山名 / 线路名")

            // ── 海拔 + 难度 ──
            // ★★2026-11-05 两次调参（用户先要「海拔缩一半」，看过之后又说「难度/用时太宽」）
            //
            //   ① 原来是 weight(1f):weight(1f) 各一半 —— 海拔/里程只需放「1680」「18.5」这种
            //      短数字，一半宽度纯属浪费；而右边「难度」要排 5 个分段、「用时」要排
            //      数字+时+数字+分 四段，半宽根本不够（时/分小框被压到 50dp 上下，
            //      两位数字都显示不全）。
            //   ② 先试了 1 : 3 —— 数字显示是全了，但难度五个格子被拉得太开、用时也空旷。
            //   ③ 先试了 1 : 3 —— 数字显示是全了，但难度五个格子被拉得太开、用时也空旷。
            //   ④ 最终 2 : 3（左 40% / 右 60%）：
            //      · 左列 ≈ 118dp，扣掉 24dp 内边距还有 94dp，四位海拔「3767」足够；
            //      · 右列 ≈ 180dp，难度每段 ≈ 34dp（最初 30dp，略宽但不散），
            //        用时每个小框 ≈ 68dp，两位数字（约 16dp）宽宽松松。
            //      ⇒ 上下两行左右列必须同权重，否则海拔列和里程列对不齐，看着像表格错位。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(2f)) {
                    FieldLabel("海拔")
                    GlassTextField(
                        elevation, { elevation = it.filter(Char::isDigit) },
                        "米", numeric = true
                    )
                }
                Column(Modifier.weight(3f)) {
                    FieldLabel("难度")
                    GlassSegmented(
                        options = listOf("1", "2", "3", "4", "5"),
                        selectedIndex = difficulty - 1,
                        onSelect = { difficulty = it + 1 },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── 里程 + 用时 ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(2f)) {
                    FieldLabel("里程")
                    GlassTextField(
                        distance, { distance = it.filter { c -> c.isDigit() || c == '.' } },
                        "公里", numeric = true
                    )
                }
                Column(Modifier.weight(3f)) {
                    FieldLabel("用时")
                    // ★★2026-11-05 修「时 / 分 两个字漂移，没在中间」
                    //
                    //   试过两条路：
                    //     ① 把单位画进输入框（suffix）—— 数字和单位能共用一个文本布局，
                    //        但要让「数字+单位」整体居中，得同时约束输入框的最小/最大宽度
                    //        和对齐方式，调了几轮都会出现"数字偏左、单位贴右"，不值当。
                    //     ② 单位留在框外，但**字号和数值保持一致**（都是 bodyMedium 的 14sp）
                    //        —— 同一字体、同一字号 ⇒ em 框和行高完全相同，Row 里垂直居中后
                    //        视觉上就是齐的。之前用的是 11sp，em 框小一圈，居中后看着往下掉。
                    //   ⇒ 用 ②。单位只靠颜色（浅灰）和数值区分，不靠字号。
                    //
                    // ★★2026-11-05 修「用时下面的框连两个数字都显示不全」：
                    //   这一列以前只有半宽，扣掉「时」「分」两个字和 3 段间距后，
                    //   每个小框只剩 50dp 左右，再减掉左右各 12dp 内边距 ⇒ 文本区不到 27dp，
                    //   两位数字直接被裁。现在两件事一起做：
                    //     · 本列权重 1f → 3f（宽度翻倍，见上方注释）
                    //     · 小框内边距 12dp → 8dp（paddingH 参数，只影响这两个框）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) {
                            GlassTextField(
                                durationH, { durationH = it.filter(Char::isDigit) },
                                "0", numeric = true, center = true, paddingH = 8.dp
                            )
                        }
                        Text(
                            "时", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
                        )
                        Box(Modifier.weight(1f)) {
                            GlassTextField(
                                durationM, { durationM = it.filter(Char::isDigit) },
                                "0", numeric = true, center = true, paddingH = 8.dp
                            )
                        }
                        Text(
                            "分", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
                        )
                    }
                }
            }

            // ── 日期时间（点开选择器，不再手打 "YYYY-MM-DD HH:mm"）──
            FieldLabel("日期时间")
            PickerField(
                value = displayDateTime(createdAt),
                placeholder = "点击选择日期时间",
                onClick = { showDatePicker = true }
            )

            // ── 心情 / 天气 / 同行人 ──
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("心情")
                    PickerField(
                        value = mood,
                        placeholder = "选择",
                        emoji = emojiOf(mood, isMood = true),
                        onClick = { showMoodPicker = true }
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("天气")
                    PickerField(
                        value = weather,
                        placeholder = "选择",
                        emoji = emojiOf(weather, isMood = false),
                        onClick = { showWeatherPicker = true }
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("同行人")
                    GlassTextField(companions, { companions = it }, "", center = true)
                }
            }

            // ── 小日记 ──
            FieldLabel("小日记")
            GlassTextField(
                notes, { notes = it },
                "写点这天的见闻、心情、路上故事…",
                singleLine = false, minLines = 3, maxLines = 5
            )

            PhotoPickerGrid(
                photos = photos,
                onPickClick = {
                    pickLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpen = { lightboxAt = it }
            )
            Spacer(Modifier.height(6.dp))
            }   // ← 闭合「可滚动字段区」

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) { GlassButton("取消", com.xixi.hiking.nativeapp.ui.LocalOverlayClose.current) }
                Box(Modifier.weight(1f)) {
                    GlassButton(
                        "保存",
                        onClick = {
                            val h = durationH.toIntOrNull() ?: 0
                            val m = durationM.toIntOrNull() ?: 0
                            onSave(
                                base.copy(
                                    name = name.trim(),
                                    elevation = elevation.toIntOrNull() ?: 0,
                                    difficulty = difficulty,
                                    mood = mood.trim(),
                                    weather = weather.trim(),
                                    companions = companions.trim(),
                                    duration = h * 60 + m,
                                    distance = distance.toDoubleOrNull() ?: 0.0,
                                    // ★日期必须归一化：手输「2026-13-45」原来原样进库，
                                    //   排序、热力图、年结都静默丢掉这条记录（看着像数据丢了）
                                    createdAt = normalizeDateInput(createdAt, base.createdAt),
                                    updatedAt = fmt.format(Date()),
                                    notes = notes
                                )
                            )
                        },
                        primary = true,
                        enabled = name.isNotBlank()
                    )
                }
            }
        }
    }

    // ── 选择器弹窗（叠在编辑弹窗之上）──
    if (showDatePicker) {
        GlassDateTimePicker(
            initial = createdAt,
            withTime = true,
            hazeState = hazeState,
            onDismiss = { showDatePicker = false },
            onConfirm = { iso ->
                createdAt = iso.replace('T', ' ').take(16)
                showDatePicker = false
            }
        )
    }
    if (showMoodPicker) {
        MoodWeatherPicker(
            isMood = true,
            current = mood,
            hazeState = hazeState,
            onDismiss = { showMoodPicker = false },
            onConfirm = { mood = it; showMoodPicker = false }
        )
    }
    if (showWeatherPicker) {
        MoodWeatherPicker(
            isMood = false,
            current = weather,
            hazeState = hazeState,
            onDismiss = { showWeatherPicker = false },
            onConfirm = { weather = it; showWeatherPicker = false }
        )
    }

    // ── 编辑态灯箱（Web：编辑弹窗里点照片 → 灯箱里可翻页 / 添加 / 删除）──
    lightboxAt?.let { at ->
        PhotoLightbox(
            photos = photos,
            startIndex = at,
            onDismiss = { lightboxAt = null },
            editing = true,
            onAdd = {
                pickLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDelete = onRemovePhoto,
            hazeState = hazeState,
            title = name.ifBlank { if (isNew) "新记录" else "照片" }
        )
    }
}

/** "2026-11-02 21:30" → 展示成「2026年11月2日 21:30」；解析不了就原样显示 */
private fun displayDateTime(raw: String): String {
    val s = raw.trim().replace(' ', 'T')
    for (p in listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")) {
        try {
            val f = SimpleDateFormat(p, Locale.getDefault())
            f.isLenient = false
            val d = f.parse(s) ?: continue
            val c = java.util.Calendar.getInstance().apply { time = d }
            val hm = if (p.contains("HH")) String.format(
                Locale.US, " %02d:%02d",
                c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)
            ) else ""
            return "${c.get(java.util.Calendar.YEAR)}年${c.get(java.util.Calendar.MONTH) + 1}月" +
                "${c.get(java.util.Calendar.DAY_OF_MONTH)}日$hm"
        } catch (e: Exception) {
        }
    }
    return raw
}
