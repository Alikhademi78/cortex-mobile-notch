package com.cortex.notch.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.notch.data.EyeColorTheme
import com.cortex.notch.live.ChatMessage
import com.cortex.notch.live.LiveState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.random.Random

val CuteGoldCore = Color(0xFFFFE082)
val CuteGoldGlowInner = Color(0x66FFD54F)
val CuteGoldGlowOuter = Color(0x28FFB300)
val CuteMouthGold = Color(0xFFFFD54F)

val PureOledBlack = Color(0xFF000000)

val CardBackground = Color(0xF510141D)
val CardBorder = Color(0x33FFD54F)
val CardTextMain = Color(0xFFF0F4F8)
val CardTextMuted = Color(0xFF8A94A6)

enum class CompanionEmotion {
    NORMAL,
    HAPPY_PETTED,
    WINKING,
    DIZZY_SHY,
    SLEEPY_NIGHT,
    STARTLED_AWAKE
}

enum class MouthShape {
    SMILE,
    WIDE_GRIN,
    SURPRISED_O,
    YAWN
}

@Composable
fun CortexNotchHead(
    liveState: LiveState = LiveState.IDLE,
    liveAmplitude: Float = 0f,
    eyeTheme: EyeColorTheme = EyeColorTheme.GOLD,
    isScreenInteractive: Boolean = true,
    onHoldAction: () -> Unit = {},
    onDoubleTapAction: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var emotion by remember { mutableStateOf(CompanionEmotion.NORMAL) }
    var mouthShape by remember { mutableStateOf(MouthShape.SMILE) }

    val isLiveActive = liveState != LiveState.IDLE

    // Only animate or vary glow during active states or petting; stay static in IDLE
    // so the overlay consumes 0% CPU when resting.
    val targetGlow = when {
        liveState == LiveState.LISTENING -> (0.85f + liveAmplitude * 0.45f).coerceIn(0.85f, 1.35f)
        liveState == LiveState.SPEAKING -> 1.15f
        liveState == LiveState.THINKING -> 1.05f
        emotion == CompanionEmotion.HAPPY_PETTED -> 1.25f
        else -> 0.95f
    }

    var isBlinking by remember { mutableStateOf(false) }
    var glanceX by remember { mutableFloatStateOf(0f) }
    var glanceY by remember { mutableFloatStateOf(0f) }
    var smileCurve by remember { mutableFloatStateOf(1f) }

    // Pause autonomous habit loop completely when the screen is off
    LaunchedEffect(isScreenInteractive) {
        if (!isScreenInteractive) {
            isBlinking = false
            glanceX = 0f
            glanceY = 0f
            return@LaunchedEffect
        }

        while (true) {
            delay(Random.nextLong(2200, 3800))
            if (emotion == CompanionEmotion.HAPPY_PETTED || emotion == CompanionEmotion.DIZZY_SHY) {
                delay(600)
                continue
            }

            isBlinking = true
            delay(110)
            isBlinking = false

            if (Random.nextFloat() > 0.65f) {
                delay(95)
                isBlinking = true
                delay(85)
                isBlinking = false
            }

            if (liveState != LiveState.SPEAKING) {
                val habitRoll = Random.nextFloat()
                if (habitRoll > 0.65f) {
                    glanceX = -5.0f
                    glanceY = -1.8f
                    delay(1200)
                    glanceX = 0f
                    glanceY = 0f
                } else if (habitRoll > 0.35f) {
                    glanceX = 5.0f
                    glanceY = -1.8f
                    delay(1200)
                    glanceX = 0f
                    glanceY = 0f
                } else if (habitRoll < 0.10f) {
                    mouthShape = MouthShape.SURPRISED_O
                    smileCurve = 1.2f
                    delay(1000)
                    mouthShape = MouthShape.SMILE
                    smileCurve = 1.0f
                }
            }
        }
    }

    suspend fun triggerPetting() {
        if (emotion == CompanionEmotion.HAPPY_PETTED) return
        emotion = CompanionEmotion.HAPPY_PETTED
        mouthShape = MouthShape.WIDE_GRIN
        delay(2200)
        mouthShape = MouthShape.SMILE
        emotion = CompanionEmotion.NORMAL
    }

    suspend fun triggerStartledAwake() {
        emotion = CompanionEmotion.STARTLED_AWAKE
        mouthShape = MouthShape.SURPRISED_O
        delay(220)
        isBlinking = true
        delay(70)
        isBlinking = false
        delay(70)
        isBlinking = true
        delay(70)
        isBlinking = false
        mouthShape = MouthShape.SMILE
        delay(2000)
        emotion = CompanionEmotion.NORMAL
    }

    val targetBlinkScale = when {
        isBlinking -> 0.08f
        emotion == CompanionEmotion.STARTLED_AWAKE -> 1.30f
        else -> 1.0f
    }

    val blinkScaleState = animateFloatAsState(
        targetValue = targetBlinkScale,
        animationSpec = tween(
            durationMillis = 80,
            easing = LinearOutSlowInEasing
        ),
        label = "blinkScale"
    )

    val liveGlanceY = if (liveState == LiveState.THINKING) -3.2f else glanceY
    val liveMouthShape = when {
        liveState == LiveState.THINKING -> MouthShape.SURPRISED_O
        liveState == LiveState.SPEAKING && liveAmplitude > 0.12f -> MouthShape.WIDE_GRIN
        liveState == LiveState.SPEAKING && liveAmplitude > 0.04f -> MouthShape.SURPRISED_O
        else -> mouthShape
    }

    // Use tween instead of low-stiffness spring so eye movements settle quickly (~220ms)
    // instead of running long asymptotic spring tails on the RenderThread.
    val glanceXState = animateFloatAsState(
        targetValue = glanceX,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "glanceX"
    )
    val glanceYState = animateFloatAsState(
        targetValue = liveGlanceY,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "glanceY"
    )

    val leftSquint = emotion == CompanionEmotion.HAPPY_PETTED
    val rightSquint = emotion == CompanionEmotion.HAPPY_PETTED

    val leftDizzyShift = if (emotion == CompanionEmotion.DIZZY_SHY) 4.5f else 0f
    val rightDizzyShift = if (emotion == CompanionEmotion.DIZZY_SHY) -4.5f else 0f

    val coreColor = Color(eyeTheme.coreHex)
    val glowInnerColor = Color(eyeTheme.glowInnerHex)
    val glowOuterColor = Color(eyeTheme.glowOuterHex)
    val mouthColor = Color(eyeTheme.mouthHex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val flareW = 14.dp.toPx()
                val flareH = 12.dp.toPx()
                val bottomR = 24.dp.toPx()

                val islandPath = Path().apply {
                    moveTo(0f, 0f)
                    cubicTo(
                        flareW * 0.45f, 0f,
                        flareW, flareH * 0.50f,
                        flareW, flareH
                    )
                    lineTo(flareW, h - bottomR)
                    quadraticTo(
                        flareW, h,
                        flareW + bottomR, h
                    )
                    lineTo(w - flareW - bottomR, h)
                    quadraticTo(
                        w - flareW, h,
                        w - flareW, h - bottomR
                    )
                    lineTo(w - flareW, flareH)
                    cubicTo(
                        w - flareW, flareH * 0.50f,
                        w - (flareW * 0.45f), 0f,
                        w, 0f
                    )
                    lineTo(0f, 0f)
                    close()
                }

                onDrawBehind {
                    drawPath(
                        path = islandPath,
                        color = PureOledBlack
                    )
                }
            }
            .pointerInput(Unit) {
                var lastTapUpTime = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    var isDrag = false
                    var isLongPressed = false

                    val longPressJob = coroutineScope.launch {
                        delay(420)
                        if (!isDrag && !isLongPressed) {
                            isLongPressed = true
                            lastTapUpTime = 0L
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onHoldAction()
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val current = event.changes.firstOrNull { it.id == down.id }
                        if (current == null || !current.pressed) {
                            longPressJob.cancel()
                            if (!isDrag && !isLongPressed) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapUpTime in 40..330) {
                                    lastTapUpTime = 0L
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDoubleTapAction()
                                } else {
                                    lastTapUpTime = now
                                    coroutineScope.launch {
                                        if (emotion == CompanionEmotion.SLEEPY_NIGHT && !isLiveActive) {
                                            triggerStartledAwake()
                                        } else {
                                            isBlinking = true
                                            delay(90)
                                            isBlinking = false
                                        }
                                    }
                                }
                            }
                            break
                        }

                        val dx = current.position.x - startX
                        if (dx.absoluteValue > 14.dp.toPx() && !isDrag && !isLongPressed) {
                            isDrag = true
                            lastTapUpTime = 0L
                            longPressJob.cancel()
                            current.consume()
                            coroutineScope.launch { triggerPetting() }
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 21.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = (glanceXState.value + leftDizzyShift + 4f).dp.toPx()
                        translationY = (glanceYState.value - 2f).dp.toPx()
                        rotationZ = -14f
                    }
            ) {
                AngledChubbyEye(
                    blinkScaleProvider = { blinkScaleState.value },
                    glowProvider = { targetGlow },
                    coreColor = coreColor,
                    glowInnerColor = glowInnerColor,
                    glowOuterColor = glowOuterColor,
                    isSquinting = leftSquint
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer {
                        translationX = (glanceXState.value + rightDizzyShift - 4f).dp.toPx()
                        translationY = (glanceYState.value - 2f).dp.toPx()
                        rotationZ = 14f
                    }
            ) {
                AngledChubbyEye(
                    blinkScaleProvider = { blinkScaleState.value },
                    glowProvider = { targetGlow },
                    coreColor = coreColor,
                    glowInnerColor = glowInnerColor,
                    glowOuterColor = glowOuterColor,
                    isSquinting = rightSquint
                )
            }

            CuteSmileMouth(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp),
                shape = liveMouthShape,
                smileMultiplier = smileCurve,
                mouthColor = mouthColor
            )
        }
    }
}

@Composable
fun CortexDropdownCard(
    liveState: LiveState = LiveState.IDLE,
    liveAmplitude: Float = 0f,
    eyeTheme: EyeColorTheme = EyeColorTheme.GOLD,
    errorMessage: String? = null,
    hasApiKey: Boolean = true,
    onDisconnectLive: () -> Unit,
    onRetry: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val isLiveActive = liveState != LiveState.IDLE
    val accentColor = Color(eyeTheme.coreHex)
    val borderAccent = Color(eyeTheme.glowInnerHex)

    Box(
        modifier = Modifier.wrapContentSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Note: Do NOT use animateContentSize here on a WRAP_CONTENT WindowManager overlay,
        // because resizing the OS surface frame-by-frame causes flicker and lag.
        Column(
            modifier = Modifier
                .padding(top = 2.dp)
                .widthIn(min = 210.dp, max = 310.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBackground)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(borderAccent, Color(0x18FFFFFF))
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (liveState == LiveState.ERROR) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF5252))
                            )
                            Text(
                                text = "خطای اتصال",
                                color = Color(0xFFFF5252),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = errorMessage ?: "مشکل از اینترنت است! لطفاً VPN با آی‌پی خوب روشن کنید و دوباره تلاش کنید.",
                            color = Color(0xFFFFD0C9),
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تلاش مجدد ↻",
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onRetry() }
                            )

                            Text(
                                text = "بستن ✕",
                                color = CardTextMuted,
                                fontSize = 11.5.sp,
                                modifier = Modifier.clickable { onDisconnectLive() }
                            )
                        }
                    }
                }
            } else if (isLiveActive) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.5.dp)
                                .clip(CircleShape)
                                .background(
                                    when (liveState) {
                                        LiveState.CONNECTING -> Color(0xFFFFB74D)
                                        LiveState.THINKING -> accentColor
                                        LiveState.SPEAKING -> Color(0xFF4ADE80)
                                        else -> Color(0xFFFF5252)
                                    }
                                )
                        )
                        Text(
                            text = when (liveState) {
                                LiveState.CONNECTING -> "در حال اتصال..."
                                LiveState.LISTENING -> "گوش می‌دهم..."
                                LiveState.THINKING -> "در حال فکر..."
                                LiveState.SPEAKING -> "در حال صحبت"
                                LiveState.ERROR -> "خطا"
                                else -> "کورتکس لایو"
                            },
                            color = CardTextMain,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(16.dp)
                    ) {
                        val bar1 = (4 + liveAmplitude * 11).dp
                        val bar2 = (6 + liveAmplitude * 16).dp
                        val bar3 = (5 + liveAmplitude * 14).dp
                        val bar4 = (3 + liveAmplitude * 9).dp

                        listOf(bar1, bar2, bar3, bar4).forEach { barHeight ->
                            Box(
                                modifier = Modifier
                                    .width(2.8.dp)
                                    .height(barHeight)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0x30FF5252))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onDisconnectLive()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = Color(0xFFFF7272),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (!hasApiKey) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "کلید API تنظیم نشده",
                            color = Color(0xFFFFB74D),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "برای مکالمه صوتی، ابتدا کلید رایگان Gemini را در تنظیمات برنامه وارد کنید.",
                            color = CardTextMuted,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ورود به تنظیمات ←",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onOpenSettings() }
                        )
                    }
                }
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Text(
                                text = "دستیار کورتکس آماده است",
                                color = CardTextMain,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "آنلاین",
                            color = Color(0xFF4ADE80),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "نگه داشتن: مکالمه صوتی • دو ضربه: چت متنی",
                        color = CardTextMuted,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CortexChatCard(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    eyeTheme: EyeColorTheme = EyeColorTheme.GOLD,
    hasApiKey: Boolean = true,
    activeModelLabel: String = "",
    onSendMessage: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val accentColor = Color(eyeTheme.coreHex)
    val borderAccent = Color(eyeTheme.glowInnerHex)
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var copiedMessageId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isLoading) {
        val targetIndex = (messages.size + if (isLoading) 1 else 0) - 1
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    fun submitCurrentInput() {
        val trimmed = inputText.trim()
        if (trimmed.isNotEmpty() && !isLoading) {
            inputText = ""
            onSendMessage(trimmed)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(334.dp)
                .height(if (hasApiKey) 364.dp else 175.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFA0F1420), Color(0xFA090D15))
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(borderAccent, Color(0x1EFFFFFF))
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isLoading) accentColor else Color(0xFF4ADE80))
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "چت هوشمند کورتکس",
                                    color = CardTextMain,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isLoading) {
                                    Text(
                                        text = "در حال تایپ...",
                                        color = accentColor,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            if (activeModelLabel.isNotBlank() && hasApiKey) {
                                Text(
                                    text = activeModelLabel,
                                    color = accentColor.copy(alpha = 0.85f),
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (messages.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x1AFFFFFF))
                                    .clickable { onClearHistory() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "پاک کردن",
                                    color = Color(0xFFA9B7C9),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0x28FF5252))
                                .clickable { onClose() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                color = Color(0xFFFF7D7D),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x18FFFFFF))
                )

                if (!hasApiKey) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "کلید API تنظیم نشده است",
                            color = Color(0xFFFFB74D),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "برای گفتگو با چت‌بات کورتکس، ابتدا کلید رایگان Gemini API را در تنظیمات وارد کنید.",
                            color = CardTextMuted,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor)
                                .clickable { onOpenSettings() }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "ورود به تنظیمات ←",
                                color = Color.Black,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Messages Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        if (messages.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "سلام! من کورتکس هستم ✨",
                                    color = accentColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "هر سوالی داری همین‌جا بنویس و ارسال کن.",
                                    color = Color(0xFF93A3B8),
                                    fontSize = 11.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = if (msg.isUser) Alignment.Start else Alignment.End
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .widthIn(max = 258.dp)
                                                .clip(
                                                    RoundedCornerShape(
                                                        topStart = 15.dp,
                                                        topEnd = 15.dp,
                                                        bottomStart = if (msg.isUser) 4.dp else 15.dp,
                                                        bottomEnd = if (msg.isUser) 15.dp else 4.dp
                                                    )
                                                )
                                                .background(
                                                    when {
                                                        msg.isError -> Color(0x28FF5252)
                                                        msg.isUser -> accentColor.copy(alpha = 0.18f)
                                                        else -> Color(0xFF161E2E)
                                                    }
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = when {
                                                        msg.isError -> Color(0x66FF5252)
                                                        msg.isUser -> accentColor.copy(alpha = 0.42f)
                                                        else -> Color(0x22FFFFFF)
                                                    },
                                                    shape = RoundedCornerShape(
                                                        topStart = 15.dp,
                                                        topEnd = 15.dp,
                                                        bottomStart = if (msg.isUser) 4.dp else 15.dp,
                                                        bottomEnd = if (msg.isUser) 15.dp else 4.dp
                                                    )
                                                )
                                                .padding(horizontal = 11.dp, vertical = 8.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = msg.text,
                                                    color = if (msg.isError) Color(0xFFFFC9C9) else CardTextMain,
                                                    fontSize = 12.sp,
                                                    lineHeight = 19.sp
                                                )

                                                if (!msg.isUser && !msg.isError) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        Text(
                                                            text = if (copiedMessageId == msg.id) "کپی شد ✓" else "کپی متن",
                                                            color = if (copiedMessageId == msg.id) Color(0xFF4ADE80) else accentColor.copy(alpha = 0.85f),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .clickable {
                                                                    clipboard.setText(AnnotatedString(msg.text))
                                                                    copiedMessageId = msg.id
                                                                    coroutineScope.launch {
                                                                        delay(1500)
                                                                        if (copiedMessageId == msg.id) {
                                                                            copiedMessageId = null
                                                                        }
                                                                    }
                                                                }
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (isLoading) {
                                    item(key = "typing_indicator") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color(0xFF161E2E))
                                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = "کورتکس در حال نوشتن...",
                                                    color = accentColor,
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Detached Input Area: Independent Send Button (Right in RTL) + Independent Text Box
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val canSend = inputText.isNotBlank() && !isLoading

                        // Standalone Circular Send Button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) accentColor else Color(0xFF141B29)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (canSend) accentColor.copy(alpha = 0.7f) else Color(0x28FFFFFF),
                                    shape = CircleShape
                                )
                                .clickable(enabled = canSend) {
                                    submitCurrentInput()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↑",
                                color = if (canSend) Color.Black else Color(0xFF66758A),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // Standalone Text Input Pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp, max = 88.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF141B29))
                                .border(
                                    width = 1.dp,
                                    color = if (inputText.isNotEmpty()) accentColor.copy(alpha = 0.45f) else Color(0x28FFFFFF),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "پیامت رو بنویس...",
                                    color = Color(0xFF6B7B90),
                                    fontSize = 12.sp
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp
                                ),
                                cursorBrush = SolidColor(accentColor),
                                maxLines = 3,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submitCurrentInput() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AngledChubbyEye(
    blinkScaleProvider: () -> Float,
    glowProvider: () -> Float,
    coreColor: Color = CuteGoldCore,
    glowInnerColor: Color = CuteGoldGlowInner,
    glowOuterColor: Color = CuteGoldGlowOuter,
    isSquinting: Boolean = false,
    eyeWidth: Dp = 14.dp,
    eyeHeight: Dp = 25.dp
) {
    Canvas(modifier = Modifier.size(width = eyeWidth, height = eyeHeight)) {
        val w = size.width
        val glow = glowProvider()

        if (isSquinting) {
            val arcPath = Path().apply {
                moveTo(1f, size.height * 0.62f)
                quadraticTo(
                    w / 2f, size.height * 0.22f,
                    w - 1f, size.height * 0.62f
                )
            }
            drawPath(
                path = arcPath,
                color = glowOuterColor.copy(alpha = 0.55f * glow),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = arcPath,
                color = glowInnerColor.copy(alpha = 0.75f * glow),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = arcPath,
                color = coreColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        } else {
            val blinkScale = blinkScaleProvider()
            val currentH = (size.height * blinkScale).coerceAtLeast(2f)
            val cY = size.height / 2f
            val topY = cY - (currentH / 2f)

            drawRoundRect(
                color = glowOuterColor.copy(alpha = 0.38f * glow),
                topLeft = Offset(-5f, topY - 3.5f),
                size = Size(w + 10f, currentH + 7f),
                cornerRadius = CornerRadius((w + 10f) / 2f, (w + 10f) / 2f)
            )

            drawRoundRect(
                color = glowInnerColor.copy(alpha = 0.52f * glow),
                topLeft = Offset(-2f, topY - 1.5f),
                size = Size(w + 4f, currentH + 3f),
                cornerRadius = CornerRadius((w + 4f) / 2f, (w + 4f) / 2f)
            )

            drawRoundRect(
                color = coreColor,
                topLeft = Offset(0f, topY),
                size = Size(w, currentH),
                cornerRadius = CornerRadius(w / 2f, w / 2f)
            )
        }
    }
}

@Composable
fun CuteSmileMouth(
    modifier: Modifier = Modifier,
    shape: MouthShape = MouthShape.SMILE,
    smileMultiplier: Float = 1.0f,
    mouthColor: Color = CuteMouthGold
) {
    Canvas(modifier = modifier.size(width = 17.dp, height = 7.dp)) {
        val w = size.width
        val h = size.height

        when (shape) {
            MouthShape.SURPRISED_O -> {
                val r = 2.6.dp.toPx()
                drawCircle(
                    color = mouthColor,
                    radius = r,
                    center = Offset(w / 2f, h / 2f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            MouthShape.YAWN -> {
                drawRoundRect(
                    color = mouthColor,
                    topLeft = Offset((w / 2f) - 3.dp.toPx(), 0.5f),
                    size = Size(6.dp.toPx(), h - 1f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            MouthShape.WIDE_GRIN -> {
                val grinH = h * 1.35f
                val grinPath = Path().apply {
                    moveTo(0.5f, 0.5f)
                    quadraticTo(w / 2f, grinH, w - 0.5f, 0.5f)
                }
                drawPath(
                    path = grinPath,
                    color = mouthColor,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            MouthShape.SMILE -> {
                val smileH = h * smileMultiplier
                val smilePath = Path().apply {
                    moveTo(1f, 1f)
                    quadraticTo(w / 2f, smileH + 1f, w - 1f, 1f)
                }
                drawPath(
                    path = smilePath,
                    color = mouthColor,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
