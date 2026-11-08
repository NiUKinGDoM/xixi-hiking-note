package com.xixi.hiking.nativeapp.ui.settings

import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import com.xixi.hiking.nativeapp.ui.FieldLabel
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import com.xixi.hiking.nativeapp.ui.theme.Radii
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.WebDavClient
import com.xixi.hiking.nativeapp.ui.GlassButton
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.GlassTextField
import dev.chrisbanes.haze.HazeState
import com.xixi.hiking.nativeapp.ui.theme.Ink

/** WebDAV 配置（全自绘控件） */
@Composable
fun SyncConfigDialog(
    initial: WebDavClient.Config,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onSave: (WebDavClient.Config) -> Unit,
    onTest: (WebDavClient.Config) -> Unit
) {
    var url by remember { mutableStateOf(initial.url) }
    var user by remember { mutableStateOf(initial.user) }
    var pass by remember { mutableStateOf(initial.pass) }

    // ★★2026-11-05 用户：「导入了网盘信息后编辑不了了」+「连通测试后加一个编辑按钮，
    //   相当于又是两行四个按钮」。
    //
    //   已经绑定过的时候，这三个框是"配置结果"，不是"待填表单" —— 一进来就能改，
    //   很容易手滑把能用的配置改坏，而且看不出哪些值是保存过的。
    //   所以：已配置默认**只读展示**，点「编辑」才解锁；未配置（第一次绑）直接可编辑。
    var editing by remember { mutableStateOf(!initial.configured) }

    GlassOverlay(
        bottomClose = false,
        hazeState = hazeState, onDismiss = onDismiss,
        maxWidthFraction = 0.88f, corner = 24
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    com.xixi.hiking.nativeapp.ui.DialogTitle("云同步设置", icon = Icons.Filled.Cloud)
                }
            }
            Spacer(Modifier.height(10.dp))

            // ★补字段标签 —— 记录、计划的表单都有标签，只有这里是裸 placeholder，
            //   填上值之后就完全看不出哪个格是什么。
            if (editing) {
                FieldLabel("服务器地址")
                GlassTextField(url, { url = it }, "https://dav.jianguoyun.com/dav")
                Spacer(Modifier.height(8.dp))

                FieldLabel("账号（邮箱）")
                GlassTextField(user, { user = it }, "你在坚果云注册的邮箱")
                Spacer(Modifier.height(8.dp))

                FieldLabel("应用密码")
                GlassTextField(pass, { pass = it }, "在安全选项里生成", password = true)
            } else {
                // 只读态：值直接展示（密码打点），一眼能看出"这是保存过的配置"
                ReadOnlyRow("地址", url.ifBlank { "—" })
                Spacer(Modifier.height(6.dp))
                ReadOnlyRow("账号", user.ifBlank { "—" })
                Spacer(Modifier.height(6.dp))
                ReadOnlyRow("密码", if (pass.isBlank()) "—" else "••••••••")
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.INPUT.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f))
                    .padding(10.dp)
            ) {
                Text(
                    // textcheck:ok 这是帮助说明（用户要知道去哪取密码），不是标签
                    "怎么拿：坚果云网页版 → 账户信息 → 安全选项 → 添加应用密码。\n" +
                        "地址填到 dav 就行，目录会自动建",
                    fontSize = 11.sp, lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            // ★「测试」单独一行：它和保存/取消不是一类操作，
            //   三个等宽按钮并排时「测试」看起来像第二个「取消」。
            //   ★2026-11-02：原来这里是**淡紫底 + 紫边框**，是全站最后一处彩色按钮。
            //     用户要求「按钮也别有颜色，都要液态玻璃」，改成同一套玻璃材质。
            // ★2026-11-05 两行四个按钮（用户：「相当于又是两行四个按钮」）
            //   第一行：测试连接 / 编辑（只读时）或 取消编辑（编辑中）
            //   第二行：取消 / 保存（只读时保存禁用 —— 没改东西，按了也没意义）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    GlassButton(
                        if (editing) "测试连接" else "测试连接",
                        onClick = { onTest(WebDavClient.Config(url = url, user = user, pass = pass)) }
                    )
                }
                Box(Modifier.weight(1f)) {
                    // 已配置才有"编辑/取消编辑"这一档；第一次绑定直接就是编辑态
                    if (initial.configured) {
                        GlassButton(
                            if (editing) "取消编辑" else "编辑",
                            onClick = {
                                editing = !editing
                                if (!editing) {
                                    // 退出编辑：把草稿还原回已保存的值，避免"看着改了其实没存"
                                    url = initial.url
                                    user = initial.user
                                    pass = initial.pass
                                }
                            }
                        )
                    } else {
                        GlassButton("清空", onClick = {
                            url = ""
                            user = ""
                            pass = ""
                        })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { GlassButton("取消", com.xixi.hiking.nativeapp.ui.LocalOverlayClose.current) }
                Box(Modifier.weight(1f)) {
                    GlassButton("保存", enabled = editing, onClick = {
                        onSave(WebDavClient.Config(url = url, user = user, pass = pass))
                    }, primary = true)
                }
            }
        }
    }
}

/**
 * 只读的一行配置（标签 + 值）。
 *
 * 为什么不用 disabled 的输入框：灰掉的输入框看起来像"坏了"，
 * 而且点上去还有光标/键盘的残影。直接当文本展示，语义最清楚。
 */
@androidx.compose.runtime.Composable
private fun ReadOnlyRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
