package net.azisaba.shop.shop

import org.bukkit.configuration.ConfigurationSection

/**
 * GUIShop 形式（`pages: Page0: items: '0': {...}`）を、
 * ページ/スロット番号を書かない `items:` リスト形式に変換する。
 *
 * ページ番号 → スロット番号の順に並べ直すだけで、空きスロットは詰める。
 */
object LegacyFormat {
    fun isLegacy(section: ConfigurationSection): Boolean = section.isConfigurationSection("pages")

    /** [section] 直下の `pages` を `items` に置き換える。変換した場合 true */
    fun convert(section: ConfigurationSection): Boolean {
        val pages = section.getConfigurationSection("pages") ?: return false
        val items = ArrayList<Map<String, Any?>>()
        for (pageKey in pages.getKeys(false).sortedBy(::number)) {
            val slots = pages.getConfigurationSection("$pageKey.items") ?: continue
            for (slotKey in slots.getKeys(false).sortedBy(::number)) {
                val item = slots.getConfigurationSection(slotKey) ?: continue
                items += toMap(item)
            }
        }
        section.set("pages", null)
        section.set("items", items)
        return true
    }

    private fun number(key: String): Int = key.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE

    private fun toMap(section: ConfigurationSection): Map<String, Any?> =
        section.getValues(false).mapValues { (_, value) -> if (value is ConfigurationSection) toMap(value) else value }
}
