package net.azisaba.shop.config

import net.azisaba.shop.util.stringOrList
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration

/** GUIShop の config.yml と同じキー構成を読み込む */
class Settings(config: FileConfiguration) {
    val disableEscapeBack = config.getBoolean("disable-escape-back", false)
    val disableEscapeBackQuantity = config.getBoolean("disable-escape-back-quantity", false)
    val enableSound = config.getBoolean("enable-sound", true)
    val hideNonBuyable = config.getBoolean("hide-non-buyable", false)
    val abbreviatePrices = config.getBoolean("abbreviate-prices", false)
    val purchaseSound: String = config.getString("purchase-sound", "ENTITY_PLAYER_LEVELUP")!!
    val transactionLog = config.getBoolean("transaction-log", false)
    val disabledWorlds: Set<String> = config.getStringList("disabled-worlds").toSet()

    val forward = Button.read(config.getConfigurationSection("buttons.forward"), Material.ARROW, "&b» 次へ", 52)
    val backward = Button.read(config.getConfigurationSection("buttons.backward"), Material.ARROW, "&b« 戻る", 48)
    val pageIndicator = Button.read(config.getConfigurationSection("buttons.page-indicator"), Material.NETHER_STAR, "&fページ %page% / %maxpage%", 50)
    val back = Button.read(config.getConfigurationSection("buttons.back"), Material.BARRIER, "&cメニューに戻る", 45)

    private val titles = config.getConfigurationSection("titles")
    fun title(key: String, default: String): String = titles?.getString(key) ?: default

    private val lores = config.getConfigurationSection("lores")
    fun lore(key: String, default: String): String = lores?.getString(key) ?: default

    val buyMaterial = material(config.getString("transaction-gui.buy-material"), Material.LIME_STAINED_GLASS_PANE)
    val sellMaterial = material(config.getString("transaction-gui.sell-material"), Material.RED_STAINED_GLASS_PANE)
    val notBuyableMaterial = material(config.getString("transaction-gui.not-buyable-material"), Material.GRAY_STAINED_GLASS_PANE)
    val notSellableMaterial = material(config.getString("transaction-gui.not-sellable-material"), Material.GRAY_STAINED_GLASS_PANE)
    val quantities = listOf(
        config.getInt("transaction-gui.quantity-1", 1),
        config.getInt("transaction-gui.quantity-2", 32),
        config.getInt("transaction-gui.quantity-3", 64),
    ).map { it.coerceAtLeast(1) }

    class Button(
        val material: Material,
        val name: String,
        val lore: List<String>,
        val shopName: String,
        val shopLore: List<String>,
        val slot: Int,
    ) {
        companion object {
            fun read(section: ConfigurationSection?, material: Material, name: String, slot: Int): Button {
                if (section == null) return Button(material, name, emptyList(), name, emptyList(), slot)
                val n = section.getString("name") ?: name
                val lore = section.stringOrList("lore")
                return Button(
                    material(section.getString("id"), material),
                    n,
                    lore,
                    section.getString("shop-name") ?: n,
                    section.stringOrList("shop-lore").ifEmpty { lore },
                    // ショップ画面の最下段 (45-53) のみ。商品と重ならないようにする
                    section.getInt("slot", slot).takeIf { it in 45 until 54 } ?: slot,
                )
            }
        }
    }

    companion object {
        fun material(name: String?, fallback: Material): Material =
            name?.let { Material.matchMaterial(it) }?.takeIf { it.isItem && !it.isAir } ?: fallback
    }
}
