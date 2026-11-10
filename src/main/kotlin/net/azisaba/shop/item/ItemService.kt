package net.azisaba.shop.item

import net.azisaba.shop.item.hook.CrackShotHook
import net.azisaba.shop.item.hook.MMLuckHook
import net.azisaba.shop.item.hook.MythicHook
import net.azisaba.shop.item.hook.StorageBoxHook
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.plugin.Plugin

/** バニラ / MythicMobs / CrackShot のアイテム生成と判定をまとめる */
class ItemService(private val plugin: Plugin) {
    var mythic: MythicHook? = null
        private set
    var crackShot: CrackShotHook? = null
        private set
    var storageBox: StorageBoxHook? = null
        private set
    /** MythicMobs アイテムの購入時の付与に使う */
    var mmLuck: MMLuckHook? = null
        private set

    fun setup() {
        val pm = plugin.server.pluginManager
        mythic = if (pm.isPluginEnabled("MythicMobs")) {
            runCatching { MythicHook() }
                .onFailure { plugin.logger.warning("MythicMobs との連携に失敗しました: $it") }
                .getOrNull()
        } else null
        crackShot = if (pm.isPluginEnabled("CrackShot")) {
            runCatching { CrackShotHook() }
                .onFailure { plugin.logger.warning("CrackShot との連携に失敗しました: $it") }
                .getOrNull()
        } else null
        mmLuck = if (mythic != null && pm.isPluginEnabled("MMLuck")) {
            runCatching { MMLuckHook() }
                .onFailure { plugin.logger.warning("MMLuck との連携に失敗しました: $it") }
                .getOrNull()
        } else null
        storageBox = if (pm.isPluginEnabled("StorageBox")) {
            runCatching { StorageBoxHook() }
                .onFailure { plugin.logger.warning("StorageBox との連携に失敗しました: $it") }
                .getOrNull()
        } else null
        if (mythic != null && mmLuck == null) plugin.logger.warning("MMLuck が無いため、MythicMobs アイテムは直接インベントリに渡します")
        plugin.logger.info("連携: MythicMobs=${mythic != null}, MMLuck=${mmLuck != null}, CrackShot=${crackShot != null}, StorageBox=${storageBox != null}")
    }

    /** 1 個分のアイテムを生成する。存在しない/連携先が無い場合は null */
    fun template(ref: ItemRef): ItemStack? = when (ref) {
        is ItemRef.Vanilla -> ItemStack.of(ref.material)
        is ItemRef.Mythic -> mythic?.create(ref.id)
        is ItemRef.CrackShot -> crackShot?.create(ref.weaponId)
    }?.asOne()

    /** template を最大スタック数ごとに分割して amount 個分作る */
    fun split(template: ItemStack, amount: Int): List<ItemStack> {
        val max = template.maxStackSize.coerceAtLeast(1)
        val result = ArrayList<ItemStack>()
        var remaining = amount
        while (remaining > 0) {
            val size = minOf(max, remaining)
            result += template.asQuantity(size)
            remaining -= size
        }
        return result
    }

    /**
     * 手持ちアイテムが何であるかを判定する。
     * MythicMobs → CrackShot → バニラ の順で判定し、
     * [strictVanilla] が true の場合、説明文/カスタムモデル/PDC/耐久減少のあるバニラ素材は null（売却不可）とする。
     */
    fun identify(stack: ItemStack?, strictVanilla: Boolean): ItemRef? {
        if (stack == null || stack.isEmpty) return null
        // Storage Box は中身ごと別扱い（バニラ素材として誤判定しないよう除外する）
        if (storageBox?.isStorageBox(stack) == true) return null
        mythic?.let { hook -> runCatching { hook.identify(stack) }.getOrNull()?.let { return ItemRef.Mythic(it) } }
        crackShot?.let { hook -> runCatching { hook.identify(stack) }.getOrNull()?.let { return ItemRef.CrackShot(it) } }
        if (strictVanilla && isCustomized(stack)) return null
        return ItemRef.Vanilla(stack.type)
    }

    private fun isCustomized(stack: ItemStack): Boolean {
        if (!stack.hasItemMeta()) return false
        val meta = stack.itemMeta
        return meta.hasLore() ||
            meta.hasCustomModelDataComponent() ||
            meta.hasItemModel() ||
            !meta.persistentDataContainer.isEmpty ||
            (meta is Damageable && meta.hasDamage())
    }
}
