package com.cortex.notch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cortex.notch.data.CortexPreferences
import com.cortex.notch.data.EyeColorTheme
import com.cortex.notch.data.GeminiChatModel
import com.cortex.notch.live.GeminiChatClient
import com.cortex.notch.live.GeminiLiveClient
import com.cortex.notch.live.LiveState
import com.cortex.notch.ui.CortexChatCard
import com.cortex.notch.ui.CortexDropdownCard
import com.cortex.notch.ui.CortexNotchHead

enum class OverlayCardMode {
    NONE,
    LIVE_CARD,
    CHAT_CARD
}

class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun setScreenInteractive(interactive: Boolean) {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.currentState = if (interactive) {
            Lifecycle.State.RESUMED
        } else {
            Lifecycle.State.CREATED
        }
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

class NotchOverlayService : LifecycleService() {

    private var windowManager: WindowManager? = null

    private val liveClient by lazy { GeminiLiveClient(this) }
    private val chatClient by lazy { GeminiChatClient(this) }
    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    private var headComposeView: ComposeView? = null
    private var headLayoutParams: WindowManager.LayoutParams? = null

    private var cardContainerView: FrameLayout? = null
    private var cardComposeView: ComposeView? = null
    private var cardLayoutParams: WindowManager.LayoutParams? = null

    private val cardMode = mutableStateOf(OverlayCardMode.NONE)
    private var isForegroundStarted: Boolean = false
    private val isScreenInteractive = mutableStateOf(true)
    private val currentEyeTheme = mutableStateOf(EyeColorTheme.GOLD)
    private val currentChatModel = mutableStateOf(GeminiChatModel.FLASH_35_LITE)
    private val hasApiKeyState = mutableStateOf(false)
    private var screenReceiverRegistered: Boolean = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            CortexPreferences.KEY_EYE_THEME -> {
                currentEyeTheme.value = CortexPreferences.getEyeTheme(this)
            }
            CortexPreferences.KEY_CHAT_MODEL -> {
                currentChatModel.value = CortexPreferences.getChatModel(this)
            }
            "gemini_api_key" -> {
                hasApiKeyState.value = CortexPreferences.hasApiKey(this)
            }
            CortexPreferences.KEY_NOTCH_WIDTH_DP,
            CortexPreferences.KEY_NOTCH_HEIGHT_DP,
            CortexPreferences.KEY_NOTCH_OFFSET_X_DP,
            CortexPreferences.KEY_NOTCH_OFFSET_Y_DP -> {
                updateNotchWindowGeometry()
            }
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (cardMode.value == OverlayCardMode.CHAT_CARD) {
                        hideCard()
                    }
                    isScreenInteractive.value = false
                    overlayLifecycleOwner.setScreenInteractive(false)
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenInteractive.value = true
                    overlayLifecycleOwner.setScreenInteractive(true)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val interactive = powerManager?.isInteractive ?: true
        isScreenInteractive.value = interactive
        overlayLifecycleOwner.setScreenInteractive(interactive)
        currentEyeTheme.value = CortexPreferences.getEyeTheme(this)
        currentChatModel.value = CortexPreferences.getChatModel(this)
        hasApiKeyState.value = CortexPreferences.hasApiKey(this)

        CortexPreferences.getPrefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        registerScreenStateReceiver()
        startAsForeground()
        setupTwoIndependentWindows()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isForegroundStarted) {
            startAsForeground()
        }
        currentEyeTheme.value = CortexPreferences.getEyeTheme(this)
        currentChatModel.value = CortexPreferences.getChatModel(this)
        hasApiKeyState.value = CortexPreferences.hasApiKey(this)
        updateNotchWindowGeometry()
        return START_STICKY
    }

    private fun updateNotchWindowGeometry() {
        val density = resources.displayMetrics.density
        val widthDp = CortexPreferences.getNotchWidthDp(this)
        val heightDp = CortexPreferences.getNotchHeightDp(this)
        val offsetXDp = CortexPreferences.getNotchOffsetXDp(this)
        val offsetYDp = CortexPreferences.getNotchOffsetYDp(this)

        headLayoutParams?.let { lp ->
            val newW = (widthDp * density).toInt()
            val newH = (heightDp * density).toInt()
            val newX = (offsetXDp * density).toInt()
            val newY = (offsetYDp * density).toInt()
            if (lp.width != newW || lp.height != newH || lp.x != newX || lp.y != newY) {
                lp.width = newW
                lp.height = newH
                lp.x = newX
                lp.y = newY
                headComposeView?.let { view ->
                    try {
                        windowManager?.updateViewLayout(view, lp)
                    } catch (_: Exception) {}
                }
            }
        }

        cardLayoutParams?.let { lp ->
            val newCardY = ((heightDp + offsetYDp.coerceAtLeast(0)) * density).toInt()
            if (lp.y != newCardY) {
                lp.y = newCardY
                cardContainerView?.let { view ->
                    try {
                        windowManager?.updateViewLayout(view, lp)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenStateReceiver, filter)
            }
            screenReceiverRegistered = true
        } catch (e: Exception) {
            Log.e("NotchOverlayService", "Failed to register screen state receiver", e)
        }
    }

    private fun unregisterScreenStateReceiver() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {
            // Ignore
        } finally {
            screenReceiverRegistered = false
        }
    }

    private fun startAsForeground() {
        val channelId = "cortex_notch_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Cortex Notch Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the dynamic notch companion active"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("دستیار ناچ کورتکس فعال است")
            .setContentText("نگه داشتن ناچ: مکالمه صوتی • دو ضربه: چت متنی")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var fgsType = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    if (hasMicPermission) {
                        fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                } else if (hasMicPermission) {
                    fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                if (fgsType != 0) {
                    startForeground(1001, notification, fgsType)
                } else {
                    startForeground(1001, notification)
                }
            } else {
                startForeground(1001, notification)
            }
            isForegroundStarted = true
        } catch (e: Exception) {
            Log.e("NotchOverlayService", "Failed to startForeground", e)
        }
    }

    private fun setupTwoIndependentWindows() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density

        val widthDp = CortexPreferences.getNotchWidthDp(this)
        val heightDp = CortexPreferences.getNotchHeightDp(this)
        val offsetXDp = CortexPreferences.getNotchOffsetXDp(this)
        val offsetYDp = CortexPreferences.getNotchOffsetYDp(this)

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        headLayoutParams = WindowManager.LayoutParams(
            (widthDp * density).toInt(),
            (heightDp * density).toInt(),
            windowType,
            baseFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = (offsetXDp * density).toInt()
            y = (offsetYDp * density).toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        headComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(overlayLifecycleOwner))
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

            setContent {
                CortexNotchHead(
                    liveState = liveClient.state.value,
                    liveAmplitude = liveClient.audioAmplitude.floatValue,
                    eyeTheme = currentEyeTheme.value,
                    isScreenInteractive = isScreenInteractive.value,
                    onHoldAction = {
                        handleHoldLiveTrigger()
                    },
                    onDoubleTapAction = {
                        handleDoubleTapChatTrigger()
                    }
                )
            }
        }

        val cardFlags = baseFlags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        cardLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            cardFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = ((heightDp + offsetYDp.coerceAtLeast(0)) * density).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }

        cardComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(overlayLifecycleOwner))
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

            setContent {
                when (cardMode.value) {
                    OverlayCardMode.CHAT_CARD -> {
                        CortexChatCard(
                            messages = chatClient.messages,
                            isLoading = chatClient.isLoading.value,
                            eyeTheme = currentEyeTheme.value,
                            hasApiKey = hasApiKeyState.value,
                            activeModelLabel = currentChatModel.value.titleEn,
                            onSendMessage = { msgText ->
                                chatClient.sendMessage(msgText, CortexPreferences.getApiKey(this@NotchOverlayService))
                            },
                            onClearHistory = {
                                chatClient.clearHistory()
                            },
                            onClose = {
                                hideCard()
                            },
                            onOpenSettings = {
                                openSettings()
                            }
                        )
                    }
                    OverlayCardMode.LIVE_CARD, OverlayCardMode.NONE -> {
                        CortexDropdownCard(
                            liveState = liveClient.state.value,
                            liveAmplitude = liveClient.audioAmplitude.floatValue,
                            eyeTheme = currentEyeTheme.value,
                            errorMessage = liveClient.errorMessage.value,
                            hasApiKey = hasApiKeyState.value,
                            onDisconnectLive = {
                                handleDisconnectLive()
                            },
                            onRetry = {
                                handleRetryLive()
                            },
                            onOpenSettings = {
                                openSettings()
                            }
                        )
                    }
                }
            }
        }

        cardContainerView = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    if (cardMode.value == OverlayCardMode.CHAT_CARD) {
                        hideCard()
                        return true
                    }
                }
                return super.dispatchKeyEvent(event)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE && cardMode.value != OverlayCardMode.NONE) {
                    hideCard()
                    return true
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            visibility = View.GONE
            alpha = 0f
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            addView(
                cardComposeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        try {
            windowManager?.addView(headComposeView, headLayoutParams)
            windowManager?.addView(cardContainerView, cardLayoutParams)
        } catch (e: Exception) {
            Log.e("NotchOverlayService", "Failed to add overlay windows", e)
            stopSelf()
        }
    }

    private fun handleHoldLiveTrigger() {
        if (liveClient.isConnected() || liveClient.state.value == LiveState.CONNECTING) {
            liveClient.disconnect()
            if (cardMode.value == OverlayCardMode.LIVE_CARD) {
                hideCard()
            }
        } else {
            startLiveSession()
        }
    }

    private fun handleDoubleTapChatTrigger() {
        hasApiKeyState.value = CortexPreferences.hasApiKey(this)
        currentChatModel.value = CortexPreferences.getChatModel(this)
        if (cardMode.value == OverlayCardMode.CHAT_CARD) {
            hideCard()
            return
        }
        if (liveClient.isConnected() || liveClient.state.value == LiveState.CONNECTING) {
            liveClient.disconnect()
        }
        showCard(OverlayCardMode.CHAT_CARD)
    }

    private fun handleRetryLive() {
        startLiveSession()
    }

    private fun startLiveSession() {
        hasApiKeyState.value = CortexPreferences.hasApiKey(this)
        val key = CortexPreferences.getApiKey(this)
        if (key.isBlank()) {
            showCard(OverlayCardMode.LIVE_CARD)
            return
        }
        liveClient.connect(key)
        showCard(OverlayCardMode.LIVE_CARD)
    }

    private fun handleDisconnectLive() {
        liveClient.disconnect()
        if (cardMode.value == OverlayCardMode.LIVE_CARD) {
            hideCard()
        }
    }

    private fun openSettings() {
        hideCard()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun showCard(targetMode: OverlayCardMode) {
        if (targetMode == OverlayCardMode.NONE) return
        val wasVisible = cardMode.value != OverlayCardMode.NONE
        cardMode.value = targetMode

        cardContainerView?.let { view ->
            view.animate().cancel()
            cardLayoutParams?.let { lp ->
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                if (targetMode == OverlayCardMode.CHAT_CARD) {
                    lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                } else {
                    lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    hideSoftKeyboard(view)
                }
                try {
                    windowManager?.updateViewLayout(view, lp)
                } catch (_: Exception) {}
            }

            if (targetMode == OverlayCardMode.CHAT_CARD) {
                view.requestFocus()
            }

            if (!wasVisible) {
                view.alpha = 0f
                view.translationY = -10f
                view.scaleX = 0.96f
                view.scaleY = 0.96f
                view.visibility = View.VISIBLE
                view.post {
                    if (cardMode.value == OverlayCardMode.NONE) return@post
                    view.pivotX = view.width / 2f
                    view.pivotY = 0f
                    view.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .withLayer()
                        .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                        .setDuration(180)
                        .start()
                }
            }
        }
    }

    private fun hideCard() {
        if (cardMode.value == OverlayCardMode.NONE) return
        cardMode.value = OverlayCardMode.NONE
        cardContainerView?.let { view ->
            hideSoftKeyboard(view)
            view.animate().cancel()
            cardLayoutParams?.let { lp ->
                lp.flags = lp.flags or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                try {
                    windowManager?.updateViewLayout(view, lp)
                } catch (_: Exception) {}
            }
            view.animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .translationY(-8f)
                .withLayer()
                .setInterpolator(android.view.animation.AccelerateInterpolator(1.4f))
                .setDuration(130)
                .withEndAction {
                    if (cardMode.value == OverlayCardMode.NONE) {
                        view.visibility = View.GONE
                    }
                }
                .start()
        }
    }

    private fun hideSoftKeyboard(view: View) {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        CortexPreferences.getPrefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        unregisterScreenStateReceiver()
        try {
            liveClient.disconnect()
            if (headComposeView != null) windowManager?.removeView(headComposeView)
            if (cardContainerView != null) windowManager?.removeView(cardContainerView)
        } catch (_: Exception) {
            // Ignore
        }
        overlayLifecycleOwner.destroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, NotchOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NotchOverlayService::class.java)
            context.stopService(intent)
        }
    }
}
