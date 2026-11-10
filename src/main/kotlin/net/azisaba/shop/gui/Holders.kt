package net.azisaba.shop.gui

import net.azisaba.shop.shop.ShopDefinition
import net.azisaba.shop.shop.ShopEntry
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/** このプラグインの GUI であることを示す InventoryHolder */
sealed class GuiHolder : InventoryHolder {
    lateinit var backing: Inventory
    val actions = HashMap<Int, (Player, ClickType) -> Unit>()

    override fun getInventory(): Inventory = backing
}

class MenuHolder(val page: Int) : GuiHolder()

class ShopHolder(val shop: ShopDefinition, val page: Int) : GuiHolder()

class TransactionHolder(val shop: ShopDefinition, val page: Int, val entry: ShopEntry) : GuiHolder() {
    var headSlot: Int = -1
}

class SellHolder : GuiHolder() {
    var processed = false
}
