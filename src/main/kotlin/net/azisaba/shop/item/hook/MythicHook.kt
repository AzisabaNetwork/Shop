package net.azisaba.shop.item.hook

import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.inventory.ItemStack

/** MythicMobs が有効な場合のみインスタンス化すること（クラス参照で NoClassDefFoundError になるため） */
class MythicHook {
    private val items get() = MythicBukkit.inst().itemManager

    fun create(id: String): ItemStack? {
        if (!items.getItem(id).isPresent) return null
        return items.getItemStack(id)?.takeUnless { it.isEmpty }
    }

    fun identify(stack: ItemStack): String? =
        if (items.isMythicItem(stack)) items.getMythicTypeFromItem(stack) else null

    fun ids(): Collection<String> = items.itemNames
}
