package com.example.qrstudio

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.qrstudio.ui.theme.QRStudioTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.EnumMap
import kotlin.math.roundToInt

private const val QR_SIZE = 1024
private const val DEFAULT_CONTENT = "https://qr.studio/demo"
private const val DEFAULT_FOREGROUND = "111827"
private const val DEFAULT_BACKGROUND = "FFFFFF"

private enum class AppTab(val title: String) {
    CONTENT("内容"),
    STYLE("样式"),
    EXPORT("导出"),
}

private data class CorrectionOption(
    val key: String,
    val label: String,
    val detail: String,
    val level: ErrorCorrectionLevel,
)

private val correctionOptions = listOf(
    CorrectionOption("L", "L", "7%", ErrorCorrectionLevel.L),
    CorrectionOption("M", "M", "15%", ErrorCorrectionLevel.M),
    CorrectionOption("Q", "Q", "25%", ErrorCorrectionLevel.Q),
    CorrectionOption("H", "H", "30%", ErrorCorrectionLevel.H),
)

private data class QrConfig(
    val content: String,
    val correction: ErrorCorrectionLevel,
    val margin: Int,
    val foreground: Int,
    val background: Int,
    val centerImage: Bitmap?,
)

private data class GeneratedQr(val bitmap: Bitmap, val canvasSize: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QRStudioTheme {
                QRStudioApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QRStudioApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.CONTENT) }
    var draftContent by rememberSaveable { mutableStateOf(DEFAULT_CONTENT) }
    var generatedContent by rememberSaveable { mutableStateOf(DEFAULT_CONTENT) }
    var correctionKey by rememberSaveable { mutableStateOf("H") }
    var margin by rememberSaveable { mutableIntStateOf(4) }
    var foregroundHex by rememberSaveable { mutableStateOf(DEFAULT_FOREGROUND) }
    var backgroundHex by rememberSaveable { mutableStateOf(DEFAULT_BACKGROUND) }
    var centerImageString by rememberSaveable { mutableStateOf<String?>(null) }

    val correction = correctionOptions.firstOrNull { it.key == correctionKey } ?: correctionOptions.last()
    val foreground = parseUiColor(foregroundHex, Color(0xFF111827))
    val background = parseUiColor(backgroundHex, Color.White)
    val centerImageUri = centerImageString?.let(Uri::parse)
    val centerImageBitmap by produceState<Bitmap?>(initialValue = null, centerImageString) {
        value = withContext(Dispatchers.IO) {
            centerImageUri?.let { decodeBitmap(appContext, it) }
        }
    }

    val generatedQr by produceState<GeneratedQr?>(
        initialValue = null,
        generatedContent,
        correctionKey,
        margin,
        foregroundHex,
        backgroundHex,
        centerImageString,
        centerImageBitmap,
    ) {
        val config = QrConfig(
            content = generatedContent,
            correction = correction.level,
            margin = margin,
            foreground = parseColorInt(foregroundHex, android.graphics.Color.DKGRAY),
            background = parseColorInt(backgroundHex, android.graphics.Color.WHITE),
            centerImage = centerImageBitmap,
        )
        value = if (config.content.isBlank()) {
            null
        } else {
            withContext(Dispatchers.Default) { generateQrBitmap(config) }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                val localUri = withContext(Dispatchers.IO) {
                    cacheCenterImage(appContext, selectedUri)
                }
                if (localUri == null) {
                    snackbarHostState.showSnackbar("无法读取所选图像，请重新选择")
                } else {
                    centerImageString = localUri.toString()
                    selectedTab = AppTab.STYLE
                }
            }
        }
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun performSave() {
        val bitmap = generatedQr?.bitmap ?: return
        scope.launch {
            val saved = withContext(Dispatchers.IO) { saveQrToGallery(appContext, bitmap) }
            showMessage(if (saved != null) "二维码已保存到系统图库" else "保存失败，请检查存储权限")
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) performSave() else showMessage("未获得存储权限，二维码没有保存")
    }

    fun handleSave() {
        if (generatedQr == null) return
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            performSave()
        }
    }

    fun handleShare() {
        val bitmap = generatedQr?.bitmap ?: return
        scope.launch {
            val shared = withContext(Dispatchers.IO) { shareQr(appContext, bitmap) }
            if (!shared) showMessage("暂时无法打开分享面板")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("QR Studio", fontWeight = FontWeight.Bold)
                        Text(
                            "二维码生成器 · arm64-v8a",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier.padding(start = 16.dp).size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { selectedTab = AppTab.EXPORT }) {
                        Icon(Icons.Default.Upload, contentDescription = "打开导出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.CONTENT -> Icons.AutoMirrored.Filled.TextSnippet
                                    AppTab.STYLE -> Icons.Default.Tune
                                    AppTab.EXPORT -> Icons.Default.SaveAlt
                                },
                                contentDescription = tab.title,
                            )
                        },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.CONTENT -> ContentTab(
                modifier = Modifier.padding(innerPadding),
                draftContent = draftContent,
                onDraftChange = { draftContent = it },
                generatedQr = generatedQr,
                centerImageBitmap = centerImageBitmap,
                onQuickContent = { draftContent = it },
                onGenerate = {
                    generatedContent = draftContent.trim()
                    if (draftContent.isBlank()) {
                        showMessage("请输入文本或链接")
                    } else {
                        showMessage("二维码已更新")
                    }
                },
            )

            AppTab.STYLE -> StyleTab(
                modifier = Modifier.padding(innerPadding),
                selectedCorrection = correctionKey,
                onCorrectionChange = { correctionKey = it },
                margin = margin,
                onMarginChange = { margin = it },
                foregroundHex = foregroundHex,
                onForegroundChange = { foregroundHex = it.uppercase().filter { char -> char in "0123456789ABCDEF" }.take(6) },
                backgroundHex = backgroundHex,
                onBackgroundChange = { backgroundHex = it.uppercase().filter { char -> char in "0123456789ABCDEF" }.take(6) },
                centerImageUri = centerImageUri,
                onPickImage = { imagePicker.launch("image/*") },
                onRemoveImage = {
                    centerImageString?.let { deleteCachedCenterImage(appContext, Uri.parse(it)) }
                    centerImageString = null
                },
            )

            AppTab.EXPORT -> ExportTab(
                modifier = Modifier.padding(innerPadding),
                generatedQr = generatedQr,
                centerImageBitmap = centerImageBitmap,
                correction = correction,
                margin = margin,
                hasCenterImage = centerImageUri != null,
                onSave = ::handleSave,
                onShare = ::handleShare,
            )
        }
    }
}

@Composable
private fun ContentTab(
    modifier: Modifier,
    draftContent: String,
    onDraftChange: (String) -> Unit,
    generatedQr: GeneratedQr?,
    centerImageBitmap: Bitmap?,
    onQuickContent: (String) -> Unit,
    onGenerate: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(
            eyebrow = "CONTENT",
            title = "生成二维码",
            subtitle = "输入一段文本或链接，实时查看可分享的二维码。",
        )

        PreviewPanel(
            generatedQr = generatedQr,
            centerImage = centerImageBitmap,
            title = "实时预览",
        )

        SectionLabel("输入内容")
        OutlinedTextField(
            value = draftContent,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("文本或链接") },
            placeholder = { Text("例如：https://example.com") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            minLines = 3,
            maxLines = 5,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(16.dp),
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = false,
                onClick = { onQuickContent("https://qr.studio/demo") },
                label = { Text("网址示例") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = false,
                onClick = { onQuickContent("会议室 A · 14:30") },
                label = { Text("文本示例") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.TextSnippet, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }

        Button(
            onClick = onGenerate,
            enabled = draftContent.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Default.QrCode2, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("生成二维码")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StyleTab(
    modifier: Modifier,
    selectedCorrection: String,
    onCorrectionChange: (String) -> Unit,
    margin: Int,
    onMarginChange: (Int) -> Unit,
    foregroundHex: String,
    onForegroundChange: (String) -> Unit,
    backgroundHex: String,
    onBackgroundChange: (String) -> Unit,
    centerImageUri: Uri?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val inkSwatches = listOf("111827", "00695C", "3F3F46", "7C2D12")
    val paperSwatches = listOf("FFFFFF", "F5F5F4", "FFF7ED", "ECFEFF")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(
            eyebrow = "STYLE",
            title = "二维码样式",
            subtitle = "自定义冗余率、留白、颜色和中心图像。",
        )

        SettingGroup(title = "纠错等级", supporting = "中心图像越大，越建议使用 H 级冗余率。") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                correctionOptions.forEach { option ->
                    CorrectionChoice(
                        option = option,
                        selected = selectedCorrection == option.key,
                        onClick = { onCorrectionChange(option.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SettingGroup(title = "二维码留白", supporting = "留白单位为模块，建议保留 4 个模块以上。") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Quiet zone", style = MaterialTheme.typography.bodyMedium)
                Text("$margin modules", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = margin.toFloat(),
                onValueChange = { onMarginChange(it.roundToInt()) },
                valueRange = 0f..12f,
                steps = 11,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SettingGroup(title = "颜色", supporting = "使用高对比度颜色，确保手机摄像头容易识别。") {
            ColorEditor(
                title = "前景色",
                value = foregroundHex,
                fallback = Color(0xFF111827),
                swatches = inkSwatches,
                onValueChange = onForegroundChange,
            )
            Spacer(Modifier.height(12.dp))
            ColorEditor(
                title = "背景色",
                value = backgroundHex,
                fallback = Color.White,
                swatches = paperSwatches,
                onValueChange = onBackgroundChange,
            )
        }

        SettingGroup(title = "中心图像", supporting = "推荐使用正方形 PNG，二维码中心会自动保留安全区。") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectedImagePreview(uri = centerImageUri, context = context)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (centerImageUri == null) "尚未添加图像" else "已添加中心图像",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (centerImageUri == null) "添加品牌标志或头像" else "导出时会嵌入二维码中心",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickImage, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (centerImageUri == null) "选择图像" else "更换图像")
                }
                if (centerImageUri != null) {
                    TextButton(onClick = onRemoveImage) { Text("移除") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ExportTab(
    modifier: Modifier,
    generatedQr: GeneratedQr?,
    centerImageBitmap: Bitmap?,
    correction: CorrectionOption,
    margin: Int,
    hasCenterImage: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(
            eyebrow = "EXPORT",
            title = "导出二维码",
            subtitle = "保存高清 PNG，或直接发送给其他应用。",
        )

        PreviewPanel(
            generatedQr = generatedQr,
            centerImage = centerImageBitmap,
            title = "导出预览",
        )

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("文件信息", fontWeight = FontWeight.Bold)
                ExportInfoRow("格式", "PNG")
                ExportInfoRow("尺寸", "1024 × 1024 px")
                ExportInfoRow("纠错等级", "${correction.label} · ${correction.detail}")
                ExportInfoRow("留白", "$margin modules")
                ExportInfoRow("中心图像", if (hasCenterImage) "已嵌入" else "无")
            }
        }

        Button(
            onClick = onSave,
            enabled = generatedQr != null && (!hasCenterImage || centerImageBitmap != null),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Default.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("保存到系统图库")
        }
        OutlinedButton(
            onClick = onShare,
            enabled = generatedQr != null && (!hasCenterImage || centerImageBitmap != null),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("分享二维码")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PageHeader(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            eyebrow,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingGroup(title: String, supporting: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            content()
        })
    }
}

@Composable
private fun CorrectionChoice(
    option: CorrectionOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier
            .height(68.dp)
            .semantics { role = Role.RadioButton }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(option.label, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            Text(option.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ColorEditor(
    title: String,
    value: String,
    fallback: Color,
    swatches: List<String>,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            swatches.forEach { hex ->
                ColorSwatch(
                    hex = hex,
                    selected = value == hex,
                    onClick = { onValueChange(hex) },
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("HEX") },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(parseUiColor(value, fallback)),
                )
            },
            prefix = { Text("#") },
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )
    }
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(parseUiColor(hex, Color.Gray))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "已选择", tint = if (hex == "FFFFFF" || hex == "F5F5F4") Color.Black else Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PreviewPanel(generatedQr: GeneratedQr?, centerImage: Bitmap?, title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("PNG", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (generatedQr == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color(0xFFCBD5D1))
                        Text("输入内容后生成", color = Color(0xFF64746F))
                    }
                } else {
                    Image(
                        bitmap = generatedQr.bitmap.asImageBitmap(),
                        contentDescription = "二维码预览",
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                    )
                    if (centerImage != null) {
                        Surface(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(9.dp)),
                            shape = RoundedCornerShape(9.dp),
                            color = Color.White,
                        ) {
                            Image(
                                bitmap = centerImage.asImageBitmap(),
                                contentDescription = "二维码中心图像预览",
                                modifier = Modifier.padding(4.dp).clip(RoundedCornerShape(6.dp)),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PreviewStat(label = "分辨率", value = "1024 px")
                PreviewStat(label = "画布", value = generatedQr?.canvasSize?.let { "$it px" } ?: "--")
                PreviewStat(label = "状态", value = if (generatedQr == null) "待生成" else "就绪")
            }
        }
    }
}

@Composable
private fun PreviewStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExportInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SelectedImagePreview(uri: Uri?, context: Context) {
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { uri?.let { decodeBitmap(context, it) } }
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "中心图像", modifier = Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(10.dp)))
        }
    }
}

private fun parseColorInt(value: String, fallback: Int): Int {
    val clean = value.trim().removePrefix("#")
    return if (clean.length == 6) {
        runCatching { android.graphics.Color.parseColor("#$clean") }.getOrDefault(fallback)
    } else {
        fallback
    }
}

private fun parseUiColor(value: String, fallback: Color): Color = Color(parseColorInt(value, fallback.toArgbInt()))

private fun Color.toArgbInt(): Int {
    val alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
    val red = (red * 255).roundToInt().coerceIn(0, 255)
    val green = (green * 255).roundToInt().coerceIn(0, 255)
    val blue = (blue * 255).roundToInt().coerceIn(0, 255)
    return android.graphics.Color.argb(alpha, red, green, blue)
}

private fun generateQrBitmap(config: QrConfig): GeneratedQr? {
    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.ERROR_CORRECTION, config.correction)
        put(EncodeHintType.MARGIN, config.margin)
        put(EncodeHintType.CHARACTER_SET, "UTF-8")
    }
    val matrix = runCatching {
        MultiFormatWriter().encode(config.content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints)
    }.getOrNull() ?: return null

    val bitmap = Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(QR_SIZE * QR_SIZE)
    for (y in 0 until QR_SIZE) {
        for (x in 0 until QR_SIZE) {
            pixels[y * QR_SIZE + x] = if (matrix[x, y]) config.foreground else config.background
        }
    }
    bitmap.setPixels(pixels, 0, QR_SIZE, 0, 0, QR_SIZE, QR_SIZE)

    config.centerImage?.let { centerBitmap ->
        drawCenterImage(bitmap, centerBitmap, config.background)
    }
    return GeneratedQr(bitmap = bitmap, canvasSize = QR_SIZE)
}

private fun drawCenterImage(bitmap: Bitmap, centerBitmap: Bitmap, background: Int) {
    val canvas = Canvas(bitmap)
    val center = QR_SIZE / 2f
    val imageSize = QR_SIZE * 0.18f
    val safeSize = imageSize * 1.24f
    val safeRect = RectF(center - safeSize / 2f, center - safeSize / 2f, center + safeSize / 2f, center + safeSize / 2f)
    val safePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
    canvas.drawRoundRect(safeRect, safeSize * 0.15f, safeSize * 0.15f, safePaint)

    val imageRect = RectF(center - imageSize / 2f, center - imageSize / 2f, center + imageSize / 2f, center + imageSize / 2f)
    val source = centerCropRect(centerBitmap)
    val path = Path().apply { addRoundRect(imageRect, imageSize * 0.14f, imageSize * 0.14f, Path.Direction.CW) }
    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val saveCount = canvas.save()
    canvas.clipPath(path)
    canvas.drawBitmap(centerBitmap, source, imageRect, imagePaint)
    canvas.restoreToCount(saveCount)
}

private fun centerCropRect(bitmap: Bitmap): Rect {
    val side = minOf(bitmap.width, bitmap.height)
    val left = (bitmap.width - side) / 2
    val top = (bitmap.height - side) / 2
    return Rect(left, top, left + side, top + side)
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun cacheCenterImage(context: Context, sourceUri: Uri): Uri? {
    val imageDir = File(context.filesDir, "qr_images").apply { mkdirs() }
    val imageFile = File(imageDir, "center_image_${System.currentTimeMillis()}.img")
    return try {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            imageFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        imageDir.listFiles()
            ?.filter { it != imageFile }
            ?.forEach { it.delete() }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    } catch (_: Exception) {
        imageFile.delete()
        null
    }
}

private fun deleteCachedCenterImage(context: Context, uri: Uri) {
    if (uri.authority != "${context.packageName}.fileprovider") return
    File(context.filesDir, "qr_images").listFiles()
        ?.filter { it.name.startsWith("center_image_") }
        ?.forEach { it.delete() }
}

private fun saveQrToGallery(context: Context, bitmap: Bitmap): Uri? {
    val resolver = context.contentResolver
    val name = "qr_${System.currentTimeMillis()}.png"
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QR Studio")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(collection, values) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        uri
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        null
    }
}

private fun shareQr(context: Context, bitmap: Bitmap): Boolean {
    return runCatching {
        val imageDir = File(context.cacheDir, "images").apply { mkdirs() }
        val imageFile = File(imageDir, "qr_share.png")
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val imageUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享二维码").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
