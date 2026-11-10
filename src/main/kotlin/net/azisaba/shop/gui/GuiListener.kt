package net.azisaba.shop.gui

import net.azisaba.shop.Shop
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent

class GuiListener(private val plugin: Shop) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.getHolder(false) as? GuiHolder ?: return
        if (holder is SellHolder) return // 売却 GUI は自由にアイテムを出し入れできる
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory != event.view.topInventory) return
        holder.actions[event.rawSlot]?.invoke(player, event.click)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDrag(event: InventoryDragEvent) {
        val holder = event.inventory.getHolder(false) as? GuiHolder ?: return
        if (holder !is SellHolder) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onOpen(event: InventoryOpenEvent) {
        if (!plugin.settings.debug) return
        val holder = event.inventory.getHolder(false) as? GuiHolder ?: return
        plugin.logger.info("[debug] open ${event.player.name} ${describe(holder)} cancelled=${event.isCancelled}")
    }

    private fun describe(holder: GuiHolder): String = when (holder) {
        is MenuHolder -> "Menu(page=${holder.page})"
        is ShopHolder -> "Shop(${holder.shop.title}, page=${holder.page})"
        is TransactionHolder -> "Transaction(page=${holder.page})"
        is SellHolder -> "Sell"
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.getHolder(false) as? GuiHolder ?: return
        val player = event.player as? Player ?: return
        val settings = plugin.settings
        val byPlayer = event.reason == InventoryCloseEvent.Reason.PLAYER
        if (settings.debug) {
            plugin.logger.info(
                "[debug] close ${player.name} ${describe(holder)} reason=${event.reason} " +
                    "disable-escape-back=${settings.disableEscapeBack} menuEntries=${plugin.registry.menu.entries.size}",
            )
        }
        when (holder) {
            is SellHolder -> plugin.gui.processSell(player, holder)
            // ESC: 2 ページ目以降なら 1 つ前のページへ、1 ページ目ならメニューへ
            is ShopHolder -> if (byPlayer && !settings.disableEscapeBack) {
                if (holder.page > 0) plugin.gui.openShop(player, holder.shop, holder.page - 1)
                else if (plugin.registry.menu.entries.isNotEmpty()) plugin.gui.openMenu(player)
            }
            is TransactionHolder -> if (byPlayer && !settings.disableEscapeBackQuantity) {
                plugin.gui.openShop(player, holder.shop, holder.page)
            }
            is MenuHolder -> if (byPlayer && !settings.disableEscapeBack && holder.page > 0) {
                plugin.gui.openMenu(player, holder.page - 1)
            }
        }
    }
}
