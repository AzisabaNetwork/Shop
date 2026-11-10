package net.azisaba.shop.item

import org.bukkit.Material

/**
 * ショップ設定の `id` が指すアイテム。
 *
 * - `DIAMOND` / `minecraft:diamond` … バニラ
 * - `mythic:ItemId` (`mm:` も可) … MythicMobs アイテム
 * - `crackshot:weapon_id` (`cs:` も可) … CrackShot 武器（武器設定のキー = weapon_id。表示名ではない）
 */
sealed interface ItemRef {
    /** 売却価格の照合に使う一意キー */
    val key: String

    /** 設定ファイルに書く形式 */
    val configId: String

    data class Vanilla(val material: Material) : ItemRef {
        override val key: String get() = material.key.toString()
        override val configId: String get() = material.name
    }

    data class Mythic(val id: String) : ItemRef {
        override val key: String get() = "mythic:$id"
        override val configId: String get() = key
    }

    data class CrackShot(val weaponId: String) : ItemRef {
        override val key: String get() = "crackshot:$weaponId"
        override val configId: String get() = key
    }

    companion object {
        fun parse(raw: String?): ItemRef? {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return null
            val separator = value.indexOf(':')
            if (separator > 0) {
                val prefix = value.substring(0, separator).lowercase()
                val id = value.substring(separator + 1).trim()
                if (id.isEmpty()) return null
                return when (prefix) {
                    "mythic", "mm", "mythicmobs" -> Mythic(id)
                    "crackshot", "cs" -> CrackShot(id)
                    "minecraft" -> vanilla(id)
                    else -> null
                }
            }
            return vanilla(value)
        }

        /** GUIShop の設定に残っている旧名 → 1.21.11 の名前 */
        private val ALIASES = mapOf(
            "ENCHANTMENT_TABLE" to "ENCHANTING_TABLE",
            "GRASS" to "SHORT_GRASS",
            "ZOMBIE_PIGMAN_SPAWN_EGG" to "ZOMBIFIED_PIGLIN_SPAWN_EGG",
        )

        private fun vanilla(name: String): Vanilla? {
            val normalized = name.uppercase()
            return Material.matchMaterial(ALIASES[normalized] ?: normalized)?.takeIf { it.isItem && !it.isAir }?.let(::Vanilla)
        }
    }
}
