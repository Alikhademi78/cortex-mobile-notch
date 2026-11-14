package com.cortex.notch.data

import android.content.Context
import android.content.SharedPreferences

enum class EyeColorTheme(
    val id: String,
    val titleFa: String,
    val coreHex: Long,
    val glowInnerHex: Long,
    val glowOuterHex: Long,
    val mouthHex: Long
) {
    GOLD(
        id = "GOLD",
        titleFa = "طلایی کورتکس",
        coreHex = 0xFFFFE082,
        glowInnerHex = 0x66FFD54F,
        glowOuterHex = 0x28FFB300,
        mouthHex = 0xFFFFD54F
    ),
    CYAN(
        id = "CYAN",
        titleFa = "آبی سایبرپانک",
        coreHex = 0xFF80D8FF,
        glowInnerHex = 0x6600E5FF,
        glowOuterHex = 0x2800B0FF,
        mouthHex = 0xFF40C4FF
    ),
    PURPLE(
        id = "PURPLE",
        titleFa = "بنفش نئونی",
        coreHex = 0xFFEA80FC,
        glowInnerHex = 0x66D500F9,
        glowOuterHex = 0x28AA00FF,
        mouthHex = 0xFFE040FB
    ),
    GREEN(
        id = "GREEN",
        titleFa = "سبز هکری",
        coreHex = 0xFFB9F6CA,
        glowInnerHex = 0x6600E676,
        glowOuterHex = 0x2800C853,
        mouthHex = 0xFF69F0AE
    ),
    RED(
        id = "RED",
        titleFa = "قرمز گیمینگ",
        coreHex = 0xFFFF8A80,
        glowInnerHex = 0x66FF5252,
        glowOuterHex = 0x28D50000,
        mouthHex = 0xFFFF5252
    );

    companion object {
        fun fromId(id: String?): EyeColorTheme {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GOLD
        }
    }
}

enum class GeminiVoiceOption(
    val voiceName: String,
    val titleFa: String,
    val subtitleFa: String
) {
    CHARON("Charon", "شارون (مرد)", "صدای گرم و آرام"),
    PUCK("Puck", "پاک (مرد)", "پرانرژی و خودمانی"),
    FENRIR("Fenrir", "فنریر (مرد)", "جدی و عمیق"),
    AOEDE("Aoede", "آئوده (زن)", "روان و خوش‌برخورد"),
    KORE("Kore", "کوره (زن)", "شفاف و ملایم");

    companion object {
        fun fromName(name: String?): GeminiVoiceOption {
            return entries.firstOrNull { it.voiceName.equals(name, ignoreCase = true) } ?: CHARON
        }
    }
}

enum class GeminiChatModel(
    val modelId: String,
    val titleEn: String,
    val badgeFa: String,
    val descriptionFa: String
) {
    FLASH_35_LITE(
        modelId = "gemini-3.5-flash-lite",
        titleEn = "Gemini 3.5 Flash Lite",
        badgeFa = "سریع و اقتصادی",
        descriptionFa = "بهترین گزینه برای چت روزمره با بالاترین سرعت و مصرف کم سهمیه رایگان"
    ),
    FLASH_38(
        modelId = "gemini-3.8-flash",
        titleEn = "Gemini 3.8 Flash",
        badgeFa = "هوشمند و دقیق",
        descriptionFa = "تعادل عالی بین سرعت بالا و پاسخ‌های دقیق‌تر و تحلیلی"
    ),
    HYDROGEN_4(
        modelId = "gemini-4-hydrogen",
        titleEn = "Gemini 4 Hydrogen",
        badgeFa = "نسل جدید",
        descriptionFa = "مدل همه‌کاره نسل ۴ مناسب کدنویسی، ترجمه و راهنمایی پیشرفته"
    ),
    ARGON_4(
        modelId = "gemini-4-argon",
        titleEn = "Gemini 4 Argon",
        badgeFa = "تفکر عمیق",
        descriptionFa = "قدرتمندترین گزینه برای سوالات تخصصی، منطق پیچیده و استدلال عمیق"
    );

    companion object {
        fun fromId(id: String?): GeminiChatModel {
            return entries.firstOrNull { it.modelId.equals(id, ignoreCase = true) } ?: FLASH_35_LITE
        }
    }
}

object CortexPreferences {
    const val PREFS_NAME = "cortex_companion_prefs"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    const val KEY_EYE_THEME = "eye_color_theme"
    const val KEY_VOICE_NAME = "gemini_voice_name"
    const val KEY_CHAT_MODEL = "gemini_chat_model"
    const val KEY_NOTCH_WIDTH_DP = "notch_width_dp"
    const val KEY_NOTCH_HEIGHT_DP = "notch_height_dp"
    const val KEY_NOTCH_OFFSET_X_DP = "notch_offset_x_dp"
    const val KEY_NOTCH_OFFSET_Y_DP = "notch_offset_y_dp"

    const val DEFAULT_NOTCH_WIDTH_DP = 126
    const val DEFAULT_NOTCH_HEIGHT_DP = 50
    const val DEFAULT_NOTCH_OFFSET_X_DP = 0
    const val DEFAULT_NOTCH_OFFSET_Y_DP = 0

    const val OFFICIAL_LIVE_ENDPOINT =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    const val DEFAULT_SYSTEM_PROMPT =
        "تو «کورتکس» (CORTEX) هستی؛ دستیار هوش مصنوعی صوتی، پرانرژی، مهربان و رفیق کاربر که روی ناچ بالای نمایشگر گوشی همراهش زندگی می‌کنی. همیشه خیلی کوتاه و خلاصه (حداکثر ۱ یا ۲ جمله روان)، گرم، پرانرژی و به زبان فارسی عامیانه و خودمانی صحبت کن."

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_GEMINI_API_KEY, "")?.trim() ?: ""
    }

    fun setApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }

    fun hasApiKey(context: Context): Boolean {
        return getApiKey(context).isNotBlank()
    }

    fun isServiceEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SERVICE_ENABLED, true)
    }

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun getSystemPrompt(context: Context): String {
        val saved = getPrefs(context).getString(KEY_SYSTEM_PROMPT, null)?.trim()
        return if (!saved.isNullOrBlank()) saved else DEFAULT_SYSTEM_PROMPT
    }

    fun setSystemPrompt(context: Context, prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) {
            resetSystemPrompt(context)
        } else {
            getPrefs(context).edit().putString(KEY_SYSTEM_PROMPT, trimmed).apply()
        }
    }

    fun resetSystemPrompt(context: Context) {
        getPrefs(context).edit().remove(KEY_SYSTEM_PROMPT).apply()
    }

    fun getEyeTheme(context: Context): EyeColorTheme {
        return EyeColorTheme.fromId(getPrefs(context).getString(KEY_EYE_THEME, EyeColorTheme.GOLD.id))
    }

    fun setEyeTheme(context: Context, theme: EyeColorTheme) {
        getPrefs(context).edit().putString(KEY_EYE_THEME, theme.id).apply()
    }

    fun getVoiceOption(context: Context): GeminiVoiceOption {
        return GeminiVoiceOption.fromName(getPrefs(context).getString(KEY_VOICE_NAME, GeminiVoiceOption.CHARON.voiceName))
    }

    fun setVoiceOption(context: Context, option: GeminiVoiceOption) {
        getPrefs(context).edit().putString(KEY_VOICE_NAME, option.voiceName).apply()
    }

    fun getChatModel(context: Context): GeminiChatModel {
        return GeminiChatModel.fromId(getPrefs(context).getString(KEY_CHAT_MODEL, GeminiChatModel.FLASH_35_LITE.modelId))
    }

    fun setChatModel(context: Context, model: GeminiChatModel) {
        getPrefs(context).edit().putString(KEY_CHAT_MODEL, model.modelId).apply()
    }

    fun getNotchWidthDp(context: Context): Int {
        return getPrefs(context).getInt(KEY_NOTCH_WIDTH_DP, DEFAULT_NOTCH_WIDTH_DP).coerceIn(96, 180)
    }

    fun setNotchWidthDp(context: Context, widthDp: Int) {
        getPrefs(context).edit().putInt(KEY_NOTCH_WIDTH_DP, widthDp.coerceIn(96, 180)).apply()
    }

    fun getNotchHeightDp(context: Context): Int {
        return getPrefs(context).getInt(KEY_NOTCH_HEIGHT_DP, DEFAULT_NOTCH_HEIGHT_DP).coerceIn(38, 76)
    }

    fun setNotchHeightDp(context: Context, heightDp: Int) {
        getPrefs(context).edit().putInt(KEY_NOTCH_HEIGHT_DP, heightDp.coerceIn(38, 76)).apply()
    }

    fun getNotchOffsetXDp(context: Context): Int {
        return getPrefs(context).getInt(KEY_NOTCH_OFFSET_X_DP, DEFAULT_NOTCH_OFFSET_X_DP).coerceIn(-120, 120)
    }

    fun setNotchOffsetXDp(context: Context, offsetXDp: Int) {
        getPrefs(context).edit().putInt(KEY_NOTCH_OFFSET_X_DP, offsetXDp.coerceIn(-120, 120)).apply()
    }

    fun getNotchOffsetYDp(context: Context): Int {
        return getPrefs(context).getInt(KEY_NOTCH_OFFSET_Y_DP, DEFAULT_NOTCH_OFFSET_Y_DP).coerceIn(-16, 48)
    }

    fun setNotchOffsetYDp(context: Context, offsetYDp: Int) {
        getPrefs(context).edit().putInt(KEY_NOTCH_OFFSET_Y_DP, offsetYDp.coerceIn(-16, 48)).apply()
    }

    fun resetNotchGeometry(context: Context) {
        getPrefs(context).edit()
            .putInt(KEY_NOTCH_WIDTH_DP, DEFAULT_NOTCH_WIDTH_DP)
            .putInt(KEY_NOTCH_HEIGHT_DP, DEFAULT_NOTCH_HEIGHT_DP)
            .putInt(KEY_NOTCH_OFFSET_X_DP, DEFAULT_NOTCH_OFFSET_X_DP)
            .putInt(KEY_NOTCH_OFFSET_Y_DP, DEFAULT_NOTCH_OFFSET_Y_DP)
            .apply()
    }

    fun resetAllToDefaults(context: Context) {
        getPrefs(context).edit()
            .putString(KEY_EYE_THEME, EyeColorTheme.GOLD.id)
            .putString(KEY_VOICE_NAME, GeminiVoiceOption.CHARON.voiceName)
            .putString(KEY_CHAT_MODEL, GeminiChatModel.FLASH_35_LITE.modelId)
            .putInt(KEY_NOTCH_WIDTH_DP, DEFAULT_NOTCH_WIDTH_DP)
            .putInt(KEY_NOTCH_HEIGHT_DP, DEFAULT_NOTCH_HEIGHT_DP)
            .putInt(KEY_NOTCH_OFFSET_X_DP, DEFAULT_NOTCH_OFFSET_X_DP)
            .putInt(KEY_NOTCH_OFFSET_Y_DP, DEFAULT_NOTCH_OFFSET_Y_DP)
            .remove(KEY_SYSTEM_PROMPT)
            .apply()
    }
}
