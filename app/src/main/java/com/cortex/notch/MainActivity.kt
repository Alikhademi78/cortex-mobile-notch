package com.cortex.notch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cortex.notch.data.CortexPreferences
import com.cortex.notch.data.EyeColorTheme
import com.cortex.notch.data.GeminiChatModel
import com.cortex.notch.data.GeminiVoiceOption
import com.cortex.notch.ui.CuteSmileMouth
import kotlin.math.roundToInt

private enum class MainTab(val titleFa: String) {
    APPEARANCE("ظاهر و ناچ"),
    AI("هوش مصنوعی"),
    KEY_GUIDE("کلید و راهنما")
}

private val BgDeepSpace = Color(0xFF07090E)
private val CardSurface = Color(0xFF0F1420)
private val CardInnerSurface = Color(0xFF0A0E18)
private val TextPrimary = Color(0xFFF3F6FA)
private val TextSecondary = Color(0xFF8C9CB2)
private val BorderSubtle = Color(0x1CFFFFFF)
private val StatusGreen = Color(0xFF4ADE80)
private val StatusAmber = Color(0xFFFFB74D)

class MainActivity : ComponentActivity() {

    private var hasOverlayState by mutableStateOf(false)
    private var hasMicState by mutableStateOf(false)
    private var serviceEnabledState by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissions()
        serviceEnabledState = CortexPreferences.isServiceEnabled(this)

        setContent {
            val context = LocalContext.current

            val micPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasMicState = isGranted
                if (isGranted) {
                    Toast.makeText(context, "دسترسی میکروفون فعال شد ✓", Toast.LENGTH_SHORT).show()
                    if (Settings.canDrawOverlays(this@MainActivity) && CortexPreferences.isServiceEnabled(this@MainActivity)) {
                        NotchOverlayService.start(this@MainActivity)
                    }
                } else {
                    Toast.makeText(context, "برای مکالمه صوتی به مجوز میکروفون نیاز است.", Toast.LENGTH_LONG).show()
                }
            }

            LaunchedEffect(Unit) {
                if (Settings.canDrawOverlays(this@MainActivity) && CortexPreferences.isServiceEnabled(this@MainActivity)) {
                    NotchOverlayService.start(this@MainActivity)
                }
            }

            MainScreen(
                hasOverlayPermission = hasOverlayState,
                hasMicPermission = hasMicState,
                isServiceEnabled = serviceEnabledState,
                onRequestOverlayPermission = {
                    requestOverlayPermission()
                },
                onRequestMicPermission = {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onStartService = {
                    CortexPreferences.setServiceEnabled(this, true)
                    serviceEnabledState = true
                    if (Settings.canDrawOverlays(this)) {
                        NotchOverlayService.start(this)
                        Toast.makeText(this, "ناچ هوشمند کورتکس فعال شد!", Toast.LENGTH_SHORT).show()
                    } else {
                        requestOverlayPermission()
                    }
                },
                onStopService = {
                    CortexPreferences.setServiceEnabled(this, false)
                    serviceEnabledState = false
                    NotchOverlayService.stop(this)
                    Toast.makeText(this, "ناچ کورتکس متوقف شد.", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        serviceEnabledState = CortexPreferences.isServiceEnabled(this)
        if (Settings.canDrawOverlays(this) && serviceEnabledState) {
            NotchOverlayService.start(this)
        }
    }

    private fun refreshPermissions() {
        hasOverlayState = Settings.canDrawOverlays(this)
        hasMicState = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}

@Composable
fun MainScreen(
    hasOverlayPermission: Boolean,
    hasMicPermission: Boolean,
    isServiceEnabled: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.APPEARANCE) }

    var apiKeyText by rememberSaveable { mutableStateOf(CortexPreferences.getApiKey(context)) }
    var isApiKeyVisible by rememberSaveable { mutableStateOf(false) }
    var saveFeedback by remember { mutableStateOf<String?>(null) }

    var selectedTheme by remember { mutableStateOf(CortexPreferences.getEyeTheme(context)) }
    var selectedVoice by remember { mutableStateOf(CortexPreferences.getVoiceOption(context)) }
    var selectedChatModel by remember { mutableStateOf(CortexPreferences.getChatModel(context)) }

    var notchWidthDp by remember { mutableIntStateOf(CortexPreferences.getNotchWidthDp(context)) }
    var notchHeightDp by remember { mutableIntStateOf(CortexPreferences.getNotchHeightDp(context)) }
    var notchOffsetXDp by remember { mutableIntStateOf(CortexPreferences.getNotchOffsetXDp(context)) }
    var notchOffsetYDp by remember { mutableIntStateOf(CortexPreferences.getNotchOffsetYDp(context)) }

    var promptText by rememberSaveable { mutableStateOf(CortexPreferences.getSystemPrompt(context)) }
    var promptFeedback by remember { mutableStateOf<String?>(null) }

    val accentColor by animateColorAsState(
        targetValue = Color(selectedTheme.coreHex),
        animationSpec = tween(280),
        label = "accentColor"
    )
    val accentMouthColor by animateColorAsState(
        targetValue = Color(selectedTheme.mouthHex),
        animationSpec = tween(280),
        label = "accentMouthColor"
    )

    fun resetGeometryState() {
        CortexPreferences.resetNotchGeometry(context)
        notchWidthDp = CortexPreferences.DEFAULT_NOTCH_WIDTH_DP
        notchHeightDp = CortexPreferences.DEFAULT_NOTCH_HEIGHT_DP
        notchOffsetXDp = CortexPreferences.DEFAULT_NOTCH_OFFSET_X_DP
        notchOffsetYDp = CortexPreferences.DEFAULT_NOTCH_OFFSET_Y_DP
    }

    fun resetAllSettingsState() {
        CortexPreferences.resetAllToDefaults(context)
        selectedTheme = EyeColorTheme.GOLD
        selectedVoice = GeminiVoiceOption.CHARON
        selectedChatModel = GeminiChatModel.FLASH_35_LITE
        notchWidthDp = CortexPreferences.DEFAULT_NOTCH_WIDTH_DP
        notchHeightDp = CortexPreferences.DEFAULT_NOTCH_HEIGHT_DP
        notchOffsetXDp = CortexPreferences.DEFAULT_NOTCH_OFFSET_X_DP
        notchOffsetYDp = CortexPreferences.DEFAULT_NOTCH_OFFSET_Y_DP
        promptText = CortexPreferences.DEFAULT_SYSTEM_PROMPT
        promptFeedback = null
        focusManager.clearFocus()
        Toast.makeText(context, "تمام تنظیمات به حالت پیش‌فرض کورتکس برگشت ✓", Toast.LENGTH_SHORT).show()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeepSpace)
        ) {
            // Ambient top glow tinted by active eye theme
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accentColor.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Fixed Control Header + Tabs
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HeroHeaderCard(
                        accentColor = accentColor,
                        accentMouthColor = accentMouthColor,
                        isServiceRunning = isServiceEnabled && hasOverlayPermission,
                        hasOverlayPermission = hasOverlayPermission,
                        isServiceEnabled = isServiceEnabled,
                        onToggleService = {
                            if (isServiceEnabled && hasOverlayPermission) {
                                onStopService()
                            } else {
                                onStartService()
                            }
                        }
                    )

                    if (!hasOverlayPermission || !hasMicPermission) {
                        CompactPermissionsBanner(
                            accentColor = accentColor,
                            hasOverlayPermission = hasOverlayPermission,
                            hasMicPermission = hasMicPermission,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onRequestMicPermission = onRequestMicPermission
                        )
                    }

                    SegmentedTabBar(
                        selectedTab = selectedTab,
                        accentColor = accentColor,
                        hasWarningBadge = apiKeyText.isBlank(),
                        onSelectTab = {
                            focusManager.clearFocus()
                            selectedTab = it
                        }
                    )
                }

                // Tab Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                        },
                        label = "tabTransition"
                    ) { tab ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            when (tab) {
                                MainTab.APPEARANCE -> {
                                    EyeThemeSection(
                                        selectedTheme = selectedTheme,
                                        accentColor = accentColor,
                                        onSelectTheme = { theme ->
                                            selectedTheme = theme
                                            CortexPreferences.setEyeTheme(context, theme)
                                        }
                                    )

                                    NotchCalibrationSection(
                                        accentColor = accentColor,
                                        notchWidthDp = notchWidthDp,
                                        notchHeightDp = notchHeightDp,
                                        notchOffsetYDp = notchOffsetYDp,
                                        notchOffsetXDp = notchOffsetXDp,
                                        onWidthChange = {
                                            notchWidthDp = it
                                            CortexPreferences.setNotchWidthDp(context, it)
                                        },
                                        onHeightChange = {
                                            notchHeightDp = it
                                            CortexPreferences.setNotchHeightDp(context, it)
                                        },
                                        onOffsetYChange = {
                                            notchOffsetYDp = it
                                            CortexPreferences.setNotchOffsetYDp(context, it)
                                        },
                                        onOffsetXChange = {
                                            notchOffsetXDp = it
                                            CortexPreferences.setNotchOffsetXDp(context, it)
                                        },
                                        onResetGeometry = {
                                            resetGeometryState()
                                            Toast.makeText(
                                                context,
                                                "ابعاد و مکان ناچ به پیش‌فرض برگشت ✓",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }

                                MainTab.AI -> {
                                    ChatModelSelectorSection(
                                        selectedModel = selectedChatModel,
                                        accentColor = accentColor,
                                        onSelectModel = { model ->
                                            selectedChatModel = model
                                            CortexPreferences.setChatModel(context, model)
                                            Toast.makeText(
                                                context,
                                                "مدل چت روی ${model.titleEn} تنظیم شد ✓",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )

                                    VoiceSelectorSection(
                                        selectedVoice = selectedVoice,
                                        accentColor = accentColor,
                                        onSelectVoice = { option ->
                                            selectedVoice = option
                                            CortexPreferences.setVoiceOption(context, option)
                                            Toast.makeText(
                                                context,
                                                "صدای «${option.titleFa}» انتخاب شد",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )

                                    PersonaSection(
                                        promptText = promptText,
                                        accentColor = accentColor,
                                        feedback = promptFeedback,
                                        onPromptChange = {
                                            promptText = it
                                            promptFeedback = null
                                        },
                                        onSavePrompt = {
                                            CortexPreferences.setSystemPrompt(context, promptText)
                                            promptText = CortexPreferences.getSystemPrompt(context)
                                            focusManager.clearFocus()
                                            promptFeedback = "شخصیت دستیار با موفقیت ذخیره شد ✓"
                                            Toast.makeText(
                                                context,
                                                "پرامپت دستیار بروزرسانی شد!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onResetPrompt = {
                                            CortexPreferences.resetSystemPrompt(context)
                                            promptText = CortexPreferences.DEFAULT_SYSTEM_PROMPT
                                            focusManager.clearFocus()
                                            promptFeedback = "به شخصیت پیش‌فرض کورتکس بازگردانده شد."
                                        }
                                    )
                                }

                                MainTab.KEY_GUIDE -> {
                                    ApiKeySection(
                                        apiKeyText = apiKeyText,
                                        isApiKeyVisible = isApiKeyVisible,
                                        accentColor = accentColor,
                                        saveFeedback = saveFeedback,
                                        onApiKeyChange = {
                                            apiKeyText = it
                                            saveFeedback = null
                                        },
                                        onToggleVisibility = {
                                            isApiKeyVisible = !isApiKeyVisible
                                        },
                                        onSaveKey = {
                                            CortexPreferences.setApiKey(context, apiKeyText)
                                            focusManager.clearFocus()
                                            saveFeedback = "کلید API با موفقیت ذخیره شد ✓"
                                            Toast.makeText(context, "کلید API ذخیره شد!", Toast.LENGTH_SHORT).show()
                                        },
                                        onOpenAiStudio = {
                                            val urlIntent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://aistudio.google.com/app/apikey")
                                            )
                                            try {
                                                context.startActivity(urlIntent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "مرورگر یافت نشد.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onDone = { focusManager.clearFocus() }
                                    )

                                    GesturesGuideSection(accentColor = accentColor)

                                    OutlinedButton(
                                        onClick = { resetAllSettingsState() },
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, Color(0x4DFF5252)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(0x16FF5252),
                                            contentColor = Color(0xFFFF8A80)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Text(
                                            text = "بازگشت همه تنظیمات به حالت پیش‌فرض ↺",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                // Slim Persistent Bottom Brand Strip
                BottomBrandFooter(
                    accentColor = accentColor,
                    onInstagramClick = {
                        val igIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.instagram.com/cortex.fa?mdxt=NGp6czd0dmUweWp5")
                        )
                        try {
                            context.startActivity(igIntent)
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "مرورگر یا اپلیکیشن اینستاگرام یافت نشد.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HeroHeaderCard(
    accentColor: Color,
    accentMouthColor: Color,
    isServiceRunning: Boolean,
    hasOverlayPermission: Boolean,
    isServiceEnabled: Boolean,
    onToggleService: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF131928), Color(0xFF0B0F1A))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(accentColor.copy(alpha = 0.42f), Color(0x14FFFFFF))
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mini Live Face Avatar
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF06080D))
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            listOf(accentColor.copy(alpha = 0.75f), Color(0x1AFFFFFF))
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 6.5.dp, height = 14.dp)
                                .rotate(14f)
                                .clip(RoundedCornerShape(3.5.dp))
                                .background(accentColor)
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 6.5.dp, height = 14.dp)
                                .rotate(-14f)
                                .clip(RoundedCornerShape(3.5.dp))
                                .background(accentColor)
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    CuteSmileMouth(
                        smileMultiplier = 1f,
                        mouthColor = accentMouthColor
                    )
                }
            }

            // Title & Live Status
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "CORTEX NOTCH",
                    color = TextPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "دستیار هوشمند ناچ • صوتی و متنی",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val (statusDotColor, statusLabel) = when {
                    isServiceRunning -> StatusGreen to "ناچ فعال است"
                    isServiceEnabled && !hasOverlayPermission -> StatusAmber to "نیازمند دسترسی ناچ"
                    else -> Color(0xFF64748B) to "متوقف شده"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Text(
                        text = statusLabel,
                        color = statusDotColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Primary Start / Stop Pill Toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isServiceRunning) Color(0x1CFF5252) else accentColor
                    )
                    .border(
                        width = 1.dp,
                        color = if (isServiceRunning) Color(0x55FF5252) else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onToggleService() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isServiceRunning) "توقف ناچ" else "فعال‌سازی",
                    color = if (isServiceRunning) Color(0xFFFF8A80) else Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CompactPermissionsBanner(
    accentColor: Color,
    hasOverlayPermission: Boolean,
    hasMicPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestMicPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A151D))
            .border(1.dp, StatusAmber.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "برای عملکرد کامل ناچ، دسترسی‌های زیر را فعال کنید:",
            color = StatusAmber,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )

        if (!hasOverlayPermission) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("نمایش روی سایر برنامه‌ها (ناچ)", color = TextPrimary, fontSize = 11.5.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(accentColor)
                        .clickable { onRequestOverlayPermission() }
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text("فعال کردن", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!hasMicPermission) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("دسترسی میکروفون (مکالمه صوتی)", color = TextPrimary, fontSize = 11.5.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(accentColor)
                        .clickable { onRequestMicPermission() }
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text("فعال کردن", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabBar(
    selectedTab: MainTab,
    accentColor: Color,
    hasWarningBadge: Boolean,
    onSelectTab: (MainTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0E131E))
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) accentColor else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelectTab(tab) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = tab.titleFa,
                        color = if (isSelected) Color.Black else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                    )
                    if (tab == MainTab.KEY_GUIDE && hasWarningBadge && !isSelected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(StatusAmber)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    accentBorder: Color = BorderSubtle,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .border(1.dp, accentBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun EyeThemeSection(
    selectedTheme: EyeColorTheme,
    accentColor: Color,
    onSelectTheme: (EyeColorTheme) -> Unit
) {
    SectionCard(accentBorder = accentColor.copy(alpha = 0.25f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "رنگ چشم‌ها و تم نوری",
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = selectedTheme.titleFa,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EyeColorTheme.entries.forEach { theme ->
                val isSelected = theme == selectedTheme
                val themeColor = Color(theme.coreHex)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectTheme(theme) }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(themeColor.copy(alpha = if (isSelected) 0.26f else 0.10f))
                            .border(
                                width = if (isSelected) 2.2.dp else 1.dp,
                                color = if (isSelected) themeColor else themeColor.copy(alpha = 0.35f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 18.dp else 14.dp)
                                .clip(CircleShape)
                                .background(themeColor)
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = theme.titleFa.substringBefore(" "),
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun NotchCalibrationSection(
    accentColor: Color,
    notchWidthDp: Int,
    notchHeightDp: Int,
    notchOffsetYDp: Int,
    notchOffsetXDp: Int,
    onWidthChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onOffsetYChange: (Int) -> Unit,
    onOffsetXChange: (Int) -> Unit,
    onResetGeometry: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تنظیم ابعاد و موقعیت ناچ",
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                    .clickable { onResetGeometry() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "پیش‌فرض ↺",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "تغییرات به‌صورت آنی روی ناچ بالای صفحه اعمال می‌شوند تا با دوربین گوشی شما دقیقاً هماهنگ شود.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        CalibrationSliderRow(
            label = "عرض ناچ",
            value = notchWidthDp,
            range = 96f..180f,
            accentColor = accentColor,
            onValueChange = onWidthChange
        )
        CalibrationSliderRow(
            label = "ارتفاع ناچ",
            value = notchHeightDp,
            range = 38f..76f,
            accentColor = accentColor,
            onValueChange = onHeightChange
        )
        CalibrationSliderRow(
            label = "فاصله از بالا (عمودی)",
            value = notchOffsetYDp,
            range = -16f..48f,
            accentColor = accentColor,
            onValueChange = onOffsetYChange
        )
        CalibrationSliderRow(
            label = "تنظیم چپ / راست (افقی)",
            value = notchOffsetXDp,
            range = -120f..120f,
            accentColor = accentColor,
            onValueChange = onOffsetXChange
        )
    }
}

@Composable
private fun CalibrationSliderRow(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(CardInnerSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${value}dp",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color(0xFF1F2839)
            ),
            modifier = Modifier.height(34.dp)
        )
    }
}

@Composable
private fun ChatModelSelectorSection(
    selectedModel: GeminiChatModel,
    accentColor: Color,
    onSelectModel: (GeminiChatModel) -> Unit
) {
    SectionCard(accentBorder = accentColor.copy(alpha = 0.25f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "مدل چت متنی (پنجره شناور)",
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = selectedModel.badgeFa,
                    color = accentColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "مدل Gemini مورد استفاده هنگام چت با دو ضربه روی ناچ را انتخاب کنید:",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            GeminiChatModel.entries.forEach { model ->
                val isSelected = model == selectedModel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.13f)
                            else CardInnerSurface
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accentColor else Color(0xFF1F2838),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectModel(model) }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Radio Circle Indicator
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 1.6.dp,
                                            color = if (isSelected) accentColor else Color(0xFF475569),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(accentColor)
                                        )
                                    }
                                }

                                Text(
                                    text = model.titleEn,
                                    color = if (isSelected) accentColor else TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(
                                        if (isSelected) accentColor.copy(alpha = 0.22f)
                                        else Color(0xFF161D2C)
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = model.badgeFa,
                                    color = if (isSelected) accentColor else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Text(
                            text = model.descriptionFa,
                            color = if (isSelected) Color(0xFFD5DFEC) else TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceSelectorSection(
    selectedVoice: GeminiVoiceOption,
    accentColor: Color,
    onSelectVoice: (GeminiVoiceOption) -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "صدای دستیار صوتی (Gemini Live)",
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = selectedVoice.titleFa,
                color = accentColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GeminiVoiceOption.entries.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { option ->
                        val isSelected = option == selectedVoice
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.14f)
                                    else CardInnerSurface
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color(0xFF1F2838),
                                    shape = RoundedCornerShape(13.dp)
                                )
                                .clickable { onSelectVoice(option) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = option.titleFa,
                                    color = if (isSelected) accentColor else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = option.subtitleFa,
                                    color = TextSecondary,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonaSection(
    promptText: String,
    accentColor: Color,
    feedback: String?,
    onPromptChange: (String) -> Unit,
    onSavePrompt: () -> Unit,
    onResetPrompt: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "شخصیت و پرامپت دستیار",
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "اختیاری",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "لحن و دستورالعمل رفتاری کورتکس در مکالمه صوتی و چت متنی:",
            color = TextSecondary,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = promptText,
            onValueChange = onPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = 160.dp),
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color(0xFF232D3F),
                focusedContainerColor = CardInnerSurface,
                unfocusedContainerColor = CardInnerSurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSavePrompt,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("ذخیره شخصیت", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }

            OutlinedButton(
                onClick = onResetPrompt,
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, Color(0xFF273347)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text("پیش‌فرض", fontSize = 12.sp)
            }
        }

        if (feedback != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = feedback,
                color = StatusGreen,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ApiKeySection(
    apiKeyText: String,
    isApiKeyVisible: Boolean,
    accentColor: Color,
    saveFeedback: String?,
    onApiKeyChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSaveKey: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onDone: () -> Unit
) {
    val hasKey = apiKeyText.isNotBlank()
    SectionCard(
        accentBorder = if (hasKey) StatusGreen.copy(alpha = 0.25f) else StatusAmber.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "کلید رایگان Gemini API",
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (hasKey) StatusGreen.copy(alpha = 0.14f)
                        else StatusAmber.copy(alpha = 0.14f)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (hasKey) "تنظیم شده ✓" else "نیازمند کلید",
                    color = if (hasKey) StatusGreen else StatusAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardInnerSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "۱. روی «دریافت کلید رایگان» بزنید و در Google AI Studio یک API Key بسازید.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
            Text(
                text = "۲. کلید را در کادر زیر وارد و ذخیره کنید (برای اتصال، VPN معتبر روشن باشد).",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = apiKeyText,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("AIzaSy...", color = Color(0xFF556677), fontSize = 13.sp)
                },
                singleLine = true,
                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        text = if (isApiKeyVisible) "مخفی" else "نمایش",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onToggleVisibility() }
                            .padding(8.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color(0xFF232D3F),
                    focusedContainerColor = CardInnerSurface,
                    unfocusedContainerColor = CardInnerSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(13.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSaveKey,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("ذخیره کلید", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }

            OutlinedButton(
                onClick = onOpenAiStudio,
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, Color(0xFF273347)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB5C5D8))
            ) {
                Text("دریافت کلید رایگان ↗", fontSize = 11.5.sp)
            }
        }

        if (saveFeedback != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = saveFeedback,
                color = StatusGreen,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GesturesGuideSection(accentColor: Color) {
    SectionCard {
        Text(
            text = "راهنمای ژست‌های لمسی ناچ",
            color = TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        val gestures = listOf(
            "۱ ضربه کوتاه" to "پلک زدن سریع و واکنش آنی کورتکس",
            "۲ ضربه سریع" to "باز و بسته شدن پنجره چت متنی شناور",
            "لمس و نگه داشتن" to "شروع یا پایان مکالمه صوتی زنده Gemini Live",
            "کشیدن روی ناچ" to "نوازش کردن کورتکس و لبخند زدن"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            gestures.forEach { (action, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardInnerSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = action,
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = desc,
                        color = Color(0xFFC9D4E3),
                        fontSize = 11.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "سیستم ضد اکو: هنگام صحبت کردن دستیار، میکروفون به‌طور موقت بسته می‌شود تا صدای اسپیکر گوشی اکو نشود.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun BottomBrandFooter(
    accentColor: Color,
    onInstagramClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06080D))
    ) {
        HorizontalDivider(color = Color(0x14FFFFFF), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "طراحی توسط کورتکس",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Cortex Mobile Notch v1.0.0",
                    color = Color(0xFF5D6D82),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFF101624))
                    .border(1.dp, accentColor.copy(alpha = 0.42f), RoundedCornerShape(11.dp))
                    .clickable { onInstagramClick() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Cortex.fa",
                    color = accentColor,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "↗",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
