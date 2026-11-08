package com.xixi.hiking.nativeapp.ui.photos

import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.xixi.hiking.nativeapp.data.PhotoEntity
import com.xixi.hiking.nativeapp.data.PhotoStore
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.TextMuted
import com.xixi.hiking.nativeapp.ui.theme.Ink

/** 单张缩略图（本地文件） */
@Composable
fun PhotoThumb(photo: PhotoEntity, modifier: Modifier = Modifier, corner: Int = Radii.SUB) {
    val context = LocalContext.current
    AsyncImage(
        model = PhotoStore.file(context, photo.fileName),
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(corner.dp)),
        contentScale = ContentScale.Crop
    )
}

/**
 * 照片墙（详情页用）：横向一排缩略图 + 点击开灯箱。
 */
@Composable
fun PhotoStrip(
    photos: List<PhotoEntity>,
    onOpen: (Int) -> Unit
) {
    if (photos.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // ★2026-11-05 崩溃点扫描：原来是 `items(photos.size) { i -> photos[i] }`，
        //   索引合法但依赖"这两处读的是同一份列表"。itemsIndexed 直接拿到元素本身，
        //   不存在"按下标去取"这一步，天然没有越界。
        itemsIndexed(photos, key = { _, p -> p.id }) { i, p ->
            Box(
                Modifier
                    .size(76.dp)
                    .safeClickable { onOpen(i) }
            ) {
                PhotoThumb(photo = p, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * 照片网格（编辑页用）：已有照片 + 末尾「+」选图按钮。
 *
 * ★2026-11-04 两处对齐 Web：
 *   ① 缩略图可点开 → **编辑态灯箱**（Web：编辑弹窗里点照片同样是 openPhotoLightbox(..., true)，
 *      能在灯箱里左右翻、添加、删除）。以前这里点了没反应，只能靠右上角小叉删。
 *   ② 选图 launcher 交给调用方持有 —— 灯箱里的「添加」按钮和这里的「+」必须用**同一个**
 *      launcher（Web 是同一个 pickPhotos()），否则会出现两套选图入口、上限各算各的。
 */
@Composable
fun PhotoPickerGrid(
    photos: List<PhotoEntity>,
    maxCount: Int = 24,
    onPickClick: () -> Unit,
    onOpen: (Int) -> Unit
) {
    // ★★2026-11-05 用户：「小图右上角的✕去掉吧，不在那里删除」
    //
    //   这个 18dp 的小叉是缩略图角上的"快捷删除"，问题有三：
    //     · 太小、贴着图角，误触率比列表里的垃圾桶高得多
    //     · 绕过了灯箱那一套（灯箱里删除是两段式确认）
    //     · 同一个动作两个入口、两套标准，用户还得记哪个有确认
    //   现在统一：**只在灯箱里删**（点缩略图进灯箱 → 点「删除」→ 再点「确认删除？」）。
    //   和 Web 版一致：编辑中的照片删除统一走灯箱。
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("照片", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            Text("${photos.size}/$maxCount", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(photos, key = { _, p -> p.id }) { i, p ->
                Box(
                    Modifier
                        .size(72.dp)
                        .safeClickable { onOpen(i) }
                ) {
                    PhotoThumb(photo = p, modifier = Modifier.fillMaxSize())
                }
            }
            if (photos.size < maxCount) {
                item {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(Radii.SUB.dp))
                            .background(AccentIndigo.copy(alpha = 0.08f))
                            .safeClickable { onPickClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, "添加照片", tint = Ink, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }


}

