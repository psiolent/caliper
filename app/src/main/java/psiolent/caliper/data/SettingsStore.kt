package psiolent.caliper.data

import android.content.Context
import android.content.SharedPreferences

/** Non-secret and secret settings, backed by SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("caliper.settings", Context.MODE_PRIVATE)

    // Keys are read with all whitespace stripped: a value pasted with a stray
    // newline is a valid key, and whitespace in an HTTP header throws.
    var openRouterApiKey: String
        get() = prefs.getString(KEY_OPENROUTER, "").orEmpty().filterNot { it.isWhitespace() }
        set(value) = prefs.edit().putString(KEY_OPENROUTER, value).apply()

    var usdaApiKey: String
        get() = prefs.getString(KEY_USDA, "").orEmpty().filterNot { it.isWhitespace() }
        set(value) = prefs.edit().putString(KEY_USDA, value).apply()

    var chatModel: String
        get() = prefs.getString(KEY_CHAT_MODEL, DEFAULT_CHAT_MODEL).orEmpty()
        set(value) = prefs.edit().putString(KEY_CHAT_MODEL, value).apply()

    /** Optional override used when a turn includes a photo. Blank falls back to [chatModel]. */
    var visionModel: String
        get() = prefs.getString(KEY_VISION_MODEL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_VISION_MODEL, value).apply()

    /** Running total of OpenRouter spend, in dollars, as reported by the API. */
    var totalCost: Double
        get() = prefs.getFloat(KEY_TOTAL_COST, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_TOTAL_COST, value.toFloat()).apply()

    /**
     * The daily deficit (negative) or surplus (positive), in kilocalories, set against
     * the calories the day will burn. Zero holds. Meaningless without a burn to measure
     * against, so it does nothing while Health Connect is unavailable.
     */
    var dailyAdjustmentKcal: Int
        get() = prefs.getInt(KEY_DAILY_ADJUSTMENT, 0)
        set(value) = prefs.edit().putInt(KEY_DAILY_ADJUSTMENT, value).apply()

    fun recordCost(cost: Double) {
        if (cost > 0) totalCost += cost
    }

    fun modelFor(hasImage: Boolean): String =
        if (hasImage && visionModel.isNotBlank()) visionModel else chatModel

    private companion object {
        const val KEY_OPENROUTER = "openrouter.apiKey"
        const val KEY_USDA = "usda.apiKey"
        const val KEY_CHAT_MODEL = "model.chat"
        const val KEY_VISION_MODEL = "model.vision"
        const val KEY_TOTAL_COST = "usage.totalCost"
        const val KEY_DAILY_ADJUSTMENT = "goal.dailyAdjustmentKcal"

        const val DEFAULT_CHAT_MODEL = "deepseek/deepseek-v4.1-flash"
    }
}
