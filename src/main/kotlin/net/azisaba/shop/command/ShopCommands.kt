package net.azisaba.shop.command

import net.azisaba.shop.Migration
import net.azisaba.shop.Shop
import net.azisaba.shop.item.hook.StorageBoxHook
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

/**
 * plugin.yml に記載したコマンド (shop / sell / shopadmin) の処理。
 * Brigadier のライフサイクル登録ではなく plugin.yml 方式にしているのは PlugMan での load/unload/reload に対応するため。
 */
class ShopCommands(private val plugin: Shop) {
    private val messages get() = plugin.messages

    fun register() {
        bind("shop", ShopCommand())
        bind("sell", SellCommand())
        bind("shopadmin", AdminCommand())
    }

    private fun bind(name: String, executor: TabExecutor) {
        val command = plugin.getCommand(name) ?: error("plugin.yml にコマンド $name がありません")
        command.setExecutor(executor)
        command.tabCompleter = executor
    }

    /** プレイヤーかつ無効ワールドでなければ [block] を実行する */
    private inline fun player(sender: CommandSender, block: (Player) -> Unit): Boolean {
        val player = sender as? Player
        if (player == null) {
            messages.send(sender, "players-only")
            return true
        }
        if (player.world.name in plugin.settings.disabledWorlds) {
            messages.send(player, "disabled-world")
            return true
        }
        block(player)
        return true
    }

    private fun complete(input: String, candidates: Iterable<String>): List<String> =
        candidates.filter { it.startsWith(input, ignoreCase = true) }

    private inner class ShopCommand : TabExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean =
            player(sender) { player ->
                val id = args.firstOrNull()
                if (id == null) {
                    plugin.gui.openMenu(player)
                    return@player
                }
                val shop = plugin.registry.shop(id)
                if (shop == null) messages.send(player, "shop-not-found", mapOf("shop" to id))
                else plugin.gui.openShop(player, shop)
            }

        override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> =
            if (args.size == 1) complete(args[0], plugin.registry.shops.values.map { it.id }) else emptyList()
    }

    private inner class SellCommand : TabExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean =
            player(sender) { player ->
                when (args.firstOrNull()?.lowercase()) {
                    null -> plugin.gui.openSell(player)
                    "hand" -> plugin.gui.sellHand(player)
                    "all" -> plugin.gui.sellAll(player)
                    else -> player.sendMessage(command.usage.replace("<command>", label))
                }
            }

        override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> =
            if (args.size == 1) complete(args[0], listOf("hand", "all")) else emptyList()
    }

    private inner class AdminCommand : TabExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
            when (args.firstOrNull()?.lowercase()) {
                "reload" -> {
                    plugin.reload()
                    messages.send(
                        sender, "reloaded",
                        mapOf("shops" to plugin.registry.shops.size.toString(), "prices" to plugin.registry.sellPrices.size.toString()),
                    )
                }
                "import" -> import(sender, force = args.getOrNull(1).equals("force", ignoreCase = true))
                "iteminfo" -> itemInfo(sender)
                else -> sender.sendMessage(command.usage.replace("<command>", label))
            }
            return true
        }

        override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> =
            when {
                args.size == 1 -> complete(args[0], listOf("reload", "import", "iteminfo"))
                args.size == 2 && args[0].equals("import", ignoreCase = true) -> complete(args[1], listOf("force"))
                else -> emptyList()
            }

        private fun itemInfo(sender: CommandSender) {
            val player = sender as? Player ?: return messages.send(sender, "players-only")
            val hand = player.inventory.itemInMainHand
            plugin.items.storageBox?.takeIf { it.isStorageBox(hand) }?.let { boxes ->
                return storageBoxInfo(player, boxes.contents(hand))
            }
            val ref = plugin.items.identify(hand, strictVanilla = false)
                ?: return messages.send(player, "iteminfo-empty")
            val sellable = plugin.items.identify(hand, plugin.registry.sell.vanillaStrict)
            val price = sellable?.let(plugin.registry::sellPrice)
            messages.send(
                player, "iteminfo",
                mapOf("id" to ref.key, "config" to ref.configId, "price" to (price?.let(messages::money) ?: "売却不可")),
            )
        }

        private fun storageBoxInfo(player: Player, contents: StorageBoxHook.Contents?) {
            if (contents == null) return messages.send(player, "iteminfo-empty")
            val ref = plugin.items.identify(contents.item, strictVanilla = false)
                ?: return messages.send(player, "iteminfo-empty")
            val sellable = plugin.items.identify(contents.item, plugin.registry.sell.vanillaStrict)
            val price = sellable?.takeIf { plugin.registry.sell.storageBox.enabled }?.let(plugin.registry::sellPrice)
            val total = price?.let { it * contents.amount * (1 - plugin.registry.sell.storageBox.feePercent / 100.0) }
            messages.send(
                player, "iteminfo-storagebox",
                mapOf(
                    "id" to ref.key,
                    "config" to ref.configId,
                    "amount" to contents.amount.toString(),
                    "price" to (total?.let(messages::money) ?: "売却不可"),
                ),
            )
        }

        private fun import(sender: CommandSender, force: Boolean) {
            if (!force && Migration.hasExistingConfig(plugin.dataFolder)) return messages.send(sender, "import-exists")
            val count = Migration.import(plugin.dataFolder)
            if (count < 0) return messages.send(sender, "import-missing")
            messages.send(sender, "import-done", mapOf("files" to count.toString()))
        }
    }
}
