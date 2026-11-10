package net.azisaba.shop.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.configuration.ConfigurationSection
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * GUIShop 互換の `&` カラーコード（`&#RRGGBB` の HEX も可）を Adventure Component に変換する。
 */
object Text {
    private val legacy = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build()

    fun replace(template: String, placeholders: Map<String, String>): String {
        var result = template
        for ((key, value) in placeholders) {
            result = result.replace("%$key%", value)
        }
        return result
    }

    /** チャット/タイトル用 */
    fun component(
        template: String,
        placeholders: Map<String, String> = emptyMap(),
        components: Map<String, Component> = emptyMap(),
    ): Component {
        var result: Component = legacy.deserialize(replace(template, placeholders).replace("\\n", "\n"))
        for ((key, value) in components) {
            result = result.replaceText(
                TextReplacementConfig.builder().matchLiteral("%$key%").replacement(value).build()
            )
        }
        return result
    }

    /** アイテム名/説明文用（デフォルトの斜体を解除する） */
    fun item(template: String, placeholders: Map<String, String> = emptyMap()): Component =
        component(template, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    fun items(templates: List<String>, placeholders: Map<String, String> = emptyMap()): List<Component> =
        templates.map { item(it, placeholders) }

    private val numberFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.ROOT))
    private val units = listOf(1e12 to "T", 1e9 to "B", 1e6 to "M", 1e3 to "K")

    fun number(value: Double, abbreviate: Boolean): String {
        if (abbreviate) {
            for ((threshold, suffix) in units) {
                if (value >= threshold) return DecimalFormat("0.##", DecimalFormatSymbols(Locale.ROOT)).format(value / threshold) + suffix
            }
        }
        return numberFormat.format(value)
    }
}

/** `lore: 'a'` と `lore: [a, b]` のどちらの書き方にも対応する */
fun ConfigurationSection.stringOrList(path: String): List<String> = when {
    isList(path) -> getStringList(path)
    isString(path) -> listOf(getString(path)!!)
    else -> emptyList()
}

/** 未設定なら null、数値ならその値（"1000" のような文字列も許容） */
fun ConfigurationSection.doubleOrNull(path: String): Double? {
    if (!contains(path)) return null
    val value = get(path) ?: return null
    return when (value) {
        is Number -> value.toDouble()
        else -> value.toString().trim().toDoubleOrNull()
    }
}
