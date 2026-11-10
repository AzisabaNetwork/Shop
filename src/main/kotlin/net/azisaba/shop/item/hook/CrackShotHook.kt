package net.azisaba.shop.item.hook

import net.azisaba.crackshot.CSUtility
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * CrackShot 武器を weapon_id（武器設定のキー = PDC `crackshot:weapon_id`）で扱う。
 * 表示名による判定は行わない。
 *
 * CrackShot が有効な場合のみインスタンス化すること
 */
class CrackShotHook {
    private val utility = CSUtility()
    private val weaponIdKey: NamespacedKey =
        utility.handle.weaponIdKey as? NamespacedKey ?: NamespacedKey(utility.handle, "weapon_id")

    /** weapon_id の武器を生成する。存在しない weapon_id なら null */
    fun create(weaponId: String): ItemStack? =
        runCatching { utility.generateWeapon(weaponId) }.getOrNull()
            ?.takeUnless { it.isEmpty }
            ?.takeIf { identify(it) == weaponId }

    /** アイテムに付いている weapon_id。CrackShot 武器でなければ null */
    fun identify(stack: ItemStack): String? =
        stack.persistentDataContainer.get(weaponIdKey, PersistentDataType.STRING)?.takeIf { it.isNotEmpty() }
}
