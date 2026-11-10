package net.azisaba.shop.item.hook

import org.bukkit.inventory.ItemStack
import xyz.acrylicstyle.storageBox.utils.StorageBox

/**
 * StorageBox プラグインの Storage Box を読む。
 *
 * StorageBox が有効な場合のみインスタンス化すること
 */
class StorageBoxHook {
    /** Storage Box の中身。[item] は 1 個分（説明文や PDC を含む） */
    class Contents(val item: ItemStack, val amount: Long)

    fun isStorageBox(stack: ItemStack): Boolean = box(stack) != null

    /** 中身。Storage Box でない、または空なら null */
    fun contents(stack: ItemStack): Contents? {
        val box = box(stack) ?: return null
        if (box.isEmpty || box.type == null) return null
        val item = box.componentItemStack?.takeUnless { it.isEmpty } ?: return null
        return Contents(item.asOne(), box.amount)
    }

    /** 中身を空にした同じ Storage Box */
    fun emptied(stack: ItemStack): ItemStack? {
        val box = box(stack) ?: return null
        box.amount = 0
        return box.itemStack
    }

    private fun box(stack: ItemStack): StorageBox? =
        if (stack.isEmpty) null else runCatching { StorageBox.getStorageBox(stack) }.getOrNull()
}
