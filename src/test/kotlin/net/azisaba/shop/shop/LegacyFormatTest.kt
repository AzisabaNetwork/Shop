package net.azisaba.shop.shop

import org.bukkit.configuration.MemoryConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyFormatTest {
    private val legacy = """
        title: '&5Test'
        pages:
          Page1:
            items:
              '0':
                id: EMERALD
          Page0:
            items:
              '10':
                id: GOLD_INGOT
                buy-price: 20
              '2':
                id: DIAMOND
                buy-price: 10
                lore:
                  - a
                  - b
    """.trimIndent()

    @Test
    fun `pages are flattened in page then slot order`() {
        val yaml = YamlConfiguration().apply { loadFromString(legacy) }
        assertTrue(LegacyFormat.isLegacy(yaml))
        assertTrue(LegacyFormat.convert(yaml))
        assertFalse(yaml.contains("pages"))

        // 保存→再読込しても items リストとして読めること
        val reloaded = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }
        val items = reloaded.getMapList("items")
        assertEquals(listOf("DIAMOND", "GOLD_INGOT", "EMERALD"), items.map { it["id"] })

        val first = MemoryConfiguration().createSection("item", items[0])
        assertEquals(10.0, first.getDouble("buy-price"))
        assertEquals(listOf("a", "b"), first.getStringList("lore"))
        assertEquals("&5Test", reloaded.getString("title"))
    }

    @Test
    fun `new format is left untouched`() {
        val yaml = YamlConfiguration().apply { loadFromString("items:\n  - id: DIAMOND\n") }
        assertFalse(LegacyFormat.isLegacy(yaml))
        assertFalse(LegacyFormat.convert(yaml))
    }
}
