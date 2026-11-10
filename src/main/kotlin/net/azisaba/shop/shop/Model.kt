package net.azisaba.shop.shop

import net.azisaba.shop.item.ItemRef
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment

/** BLANK は何も置かない空きマス（並びを調整したい時に使う） */
enum class EntryType { SHOP, COMMAND, DUMMY, BLANK }

class ShopEntry(
    val type: EntryType,
    /** 設定に書かれた id（エラー表示用） */
    val rawId: String?,
    val ref: ItemRef?,
    val buyPrice: Double?,
    val sellPrice: Double?,
    /** ショップ GUI 上でのみ使う名前/説明 */
    val shopName: String?,
    val shopLore: List<String>,
    /** 購入したアイテムにも付く名前/説明 */
    val name: String?,
    val lore: List<String>,
    val enchantments: Map<Enchantment, Int>,
    val commands: List<String>,
    val permission: String?,
    val targetShop: String?,
) {
    val buyable: Boolean get() = buyPrice != null
    val sellable: Boolean get() = sellPrice != null && sellPrice > 0 && commands.isEmpty() && ref != null
}

/** ショップ。[entries] は設定ファイルに書かれた順で、ページは GUI 側で自動的に分割する */
class ShopDefinition(val id: String, val title: String, val permission: String?, val entries: List<ShopEntry>)

/** メインメニュー。[entries] は書かれた順に左上から並べ、入りきらない分は自動でページを増やす */
class MenuDefinition(val rows: Int, val entries: List<ShopEntry>)

enum class SlotType { ITEM_DISPLAY, BUY_1, BUY_2, BUY_3, SELL_1, SELL_2, SELL_3, BACK, PLAYER_HEAD, DUMMY }

class LayoutSlot(val type: SlotType, val material: Material?, val name: String?, val lore: List<String>)

class TransactionLayout(
    val title: String,
    val rows: Int,
    val slots: Map<Int, LayoutSlot>,
    val buyName: String?,
    val buyLore: List<String>,
    val sellName: String?,
    val sellLore: List<String>,
    val notBuyableName: String?,
    val notBuyableLore: List<String>,
    val notSellableName: String?,
    val notSellableLore: List<String>,
)

class SellConfig(
    val rows: Int,
    val vanillaStrict: Boolean,
    val useShopPrices: Boolean,
    val mythicEnabled: Boolean,
    val crackShotEnabled: Boolean,
    val vanillaEnabled: Boolean,
    val storageBox: StorageBoxSell,
)

class StorageBoxSell(
    val enabled: Boolean,
    /** true: 中身だけ売って空の Storage Box を返す / false: Storage Box ごと回収する */
    val keepBox: Boolean,
    /** 中身の売却額から差し引く手数料 (%) */
    val feePercent: Double,
    /** /sell all でインベントリ内の Storage Box も売る */
    val includeInSellAll: Boolean,
)
