package net.azisaba.shop.gui

import net.azisaba.shop.Shop
import net.azisaba.shop.item.ItemRef
import net.azisaba.shop.shop.EntryType
import net.azisaba.shop.shop.LayoutSlot
import net.azisaba.shop.shop.ShopDefinition
import net.azisaba.shop.shop.ShopEntry
import net.azisaba.shop.shop.SlotType
import net.azisaba.shop.util.Text
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.inventory.meta.SkullMeta

class GuiManager(private val plugin: Shop) {
    private companion object {
        /** ショップ 1 ページに並べる数（6 段のうち上 5 段） */
        const val SHOP_PAGE_SIZE = 45
    }

    private val settings get() = plugin.settings
    private val messages get() = plugin.messages
    private val registry get() = plugin.registry
    private val items get() = plugin.items
    private val economy get() = plugin.economy

    // ───────────────────────────── 共通 ─────────────────────────────

    private fun <H : GuiHolder> create(holder: H, rows: Int, title: Component): H {
        holder.backing = Bukkit.createInventory(holder, rows * 9, title)
        return holder
    }

    /** クリックイベント中に直接開かないよう、次の tick でプレイヤーのスケジューラから開く */
    private fun show(player: Player, holder: GuiHolder) {
        player.scheduler.run(plugin, { player.openInventory(holder.inventory) }, null)
    }

    private fun button(material: Material, name: String, lore: List<String>, placeholders: Map<String, String> = emptyMap()): ItemStack {
        val stack = ItemStack.of(material)
        stack.editMeta { meta ->
            meta.displayName(Text.item(name, placeholders))
            if (lore.isNotEmpty()) meta.lore(Text.items(lore, placeholders))
        }
        return stack
    }

    private fun money(value: Double) = messages.money(value)

    // ───────────────────────────── メニュー ─────────────────────────────

    fun openMenu(player: Player, page: Int = 0) {
        val menu = registry.menu
        if (menu.entries.isEmpty()) {
            messages.send(player, "something-wrong")
            return
        }
        // 全部入るなら全マスを使い、入らなければ最下段をページ送り用に空けて自動でページを分ける
        val fitsOnePage = menu.entries.size <= menu.rows * 9
        val rows = if (fitsOnePage) menu.rows else menu.rows.coerceAtLeast(2)
        val pages = paginate(menu.entries, if (fitsOnePage) rows * 9 else (rows - 1) * 9)
        val index = page.coerceIn(0, pages.lastIndex)
        val pageNumber = if (pages.size > 1) {
            Text.replace(settings.title("menu-shop-pagenumber", " &7%number%"), mapOf("number" to (index + 1).toString()))
        } else ""
        val title = Text.component(settings.title("menu", "&8Shop %page-number%"), mapOf("page-number" to pageNumber))
        val holder = create(MenuHolder(index), rows, title)
        val size = rows * 9

        for ((slot, entry) in pages[index].withIndex()) {
            if (entry.type == EntryType.BLANK) continue
            holder.backing.setItem(slot, menuIcon(entry))
            holder.actions[slot] = { p, _ -> clickEntry(p, null, 0, entry) }
        }
        if (pages.size > 1) {
            // ボタン位置はショップ画面と同じ（最下段内の列）に揃える
            val bottom = size - 9
            val placeholders = mapOf("page" to (index + 1).toString(), "maxpage" to pages.size.toString())
            if (index > 0) {
                val b = settings.backward
                val slot = bottom + (b.slot - 45)
                holder.backing.setItem(slot, button(b.material, b.name, b.lore, placeholders))
                holder.actions[slot] = { p, _ -> openMenu(p, index - 1) }
            }
            if (index < pages.lastIndex) {
                val b = settings.forward
                val slot = bottom + (b.slot - 45)
                holder.backing.setItem(slot, button(b.material, b.name, b.lore, placeholders))
                holder.actions[slot] = { p, _ -> openMenu(p, index + 1) }
            }
            val b = settings.pageIndicator
            holder.backing.setItem(bottom + (b.slot - 45), button(b.material, b.name, b.lore, placeholders))
        }
        show(player, holder)
    }

    /** 書かれた順のリストを 1 ページ [perPage] 個ずつに分ける（空でも 1 ページ） */
    private fun <T> paginate(list: List<T>, perPage: Int): List<List<T>> =
        list.chunked(perPage.coerceAtLeast(1)).ifEmpty { listOf(emptyList()) }

    private fun menuIcon(entry: ShopEntry): ItemStack {
        val stack = entry.ref?.let(items::template) ?: ItemStack.of(Material.BARRIER)
        stack.editMeta { meta ->
            (entry.shopName ?: entry.name)?.let { meta.displayName(Text.item(it)) }
            val lore = entry.shopLore.ifEmpty { entry.lore }
            if (lore.isNotEmpty()) meta.lore(Text.items(lore))
        }
        return stack
    }

    // ───────────────────────────── ショップ ─────────────────────────────

    fun openShop(player: Player, shop: ShopDefinition, page: Int = 0) {
        if (shop.permission != null && !player.hasPermission(shop.permission)) {
            messages.send(player, "no-permission")
            return
        }
        // 上 5 段 (45 マス) に書かれた順で並べ、溢れたら自動で次のページへ。最下段はボタン用
        val visible = shop.entries.filterNot { settings.hideNonBuyable && it.type == EntryType.SHOP && !it.buyable }
        val pages = paginate(visible, SHOP_PAGE_SIZE)
        val index = page.coerceIn(0, pages.lastIndex)
        val title = Text.component(settings.title("shop", "&8Shop > %shopname%"), mapOf("shopname" to shop.title))
        val holder = create(ShopHolder(shop, index), 6, title)

        for ((slot, entry) in pages[index].withIndex()) {
            if (entry.type == EntryType.BLANK) continue
            holder.backing.setItem(slot, shopIcon(entry))
            holder.actions[slot] = { p, _ -> clickEntry(p, shop, index, entry) }
        }

        val placeholders = mapOf("page" to (index + 1).toString(), "maxpage" to pages.size.toString())
        if (index > 0) {
            val b = settings.backward
            holder.backing.setItem(b.slot, button(b.material, b.shopName, b.shopLore, placeholders))
            holder.actions[b.slot] = { p, _ -> openShop(p, shop, index - 1) }
        }
        if (index < pages.lastIndex) {
            val b = settings.forward
            holder.backing.setItem(b.slot, button(b.material, b.shopName, b.shopLore, placeholders))
            holder.actions[b.slot] = { p, _ -> openShop(p, shop, index + 1) }
        }
        if (pages.size > 1) {
            val b = settings.pageIndicator
            holder.backing.setItem(b.slot, button(b.material, b.name, b.lore, placeholders))
            holder.actions.remove(b.slot)
        }
        if (registry.menu.entries.isNotEmpty()) {
            val b = settings.back
            holder.backing.setItem(b.slot, button(b.material, b.shopName, b.shopLore))
            holder.actions[b.slot] = { p, _ -> openMenu(p) }
        }
        show(player, holder)
    }

    /** ショップ GUI に並べるアイテム（名前/説明＋価格行） */
    private fun shopIcon(entry: ShopEntry): ItemStack {
        val stack = displayItem(entry, forShop = true)
        if (entry.type != EntryType.SHOP && entry.type != EntryType.COMMAND) return stack
        stack.editMeta { meta ->
            val lore = (meta.lore() ?: emptyList()).toMutableList()
            lore += priceLines(entry)
            meta.lore(lore)
        }
        return stack
    }

    private fun priceLines(entry: ShopEntry): List<Component> {
        val lines = ArrayList<Component>()
        lines += when (val buy = entry.buyPrice) {
            null -> Text.item(settings.lore("cannot-buy", "&c購入できません"))
            0.0 -> Text.item(settings.lore("free", "&f購入: &aFREE"))
            else -> Text.item(settings.lore("buy", "&f購入: &c%amount%"), mapOf("amount" to money(buy)))
        }
        if (entry.type == EntryType.SHOP) {
            lines += if (entry.sellable) {
                Text.item(settings.lore("sell", "&f売却: &c%amount%"), mapOf("amount" to money(entry.sellPrice!!)))
            } else {
                Text.item(settings.lore("cannot-sell", "&c売却できません"))
            }
        }
        return lines
    }

    /**
     * 表示用アイテム。id のアイテムを生成し、shop-name/name・shop-lore/lore・エンチャントを反映する。
     * MythicMobs/CrackShot アイテムで説明が未指定の場合は元の説明文を残す。
     */
    private fun displayItem(entry: ShopEntry, forShop: Boolean): ItemStack {
        val ref = entry.ref
        val template = ref?.let(items::template)
        if (template == null) {
            if (ref == null && entry.rawId == null) return ItemStack.of(Material.STONE)
            return button(Material.BARRIER, "&cアイテムが見つかりません", listOf("&7id: ${entry.rawId}"))
        }
        applyCustomization(template, entry, forShop)
        return template
    }

    private fun applyCustomization(stack: ItemStack, entry: ShopEntry, forShop: Boolean) {
        stack.editMeta { meta ->
            val name = if (forShop) entry.shopName ?: entry.name else entry.name
            name?.let { meta.displayName(Text.item(it)) }
            val lore = if (forShop) entry.shopLore.ifEmpty { entry.lore } else entry.lore
            if (lore.isNotEmpty()) meta.lore(Text.items(lore))
            for ((enchantment, level) in entry.enchantments) {
                if (meta is EnchantmentStorageMeta) meta.addStoredEnchant(enchantment, level, true)
                else meta.addEnchant(enchantment, level, true)
            }
        }
    }

    private fun clickEntry(player: Player, shop: ShopDefinition?, page: Int, entry: ShopEntry) {
        entry.targetShop?.let { target ->
            val targetShop = registry.shop(target)
            if (targetShop == null) messages.send(player, "shop-not-found", mapOf("shop" to target))
            else openShop(player, targetShop, 0)
            return
        }
        when (entry.type) {
            EntryType.COMMAND -> buyCommand(player, entry)
            EntryType.SHOP -> when {
                entry.commands.isNotEmpty() -> buyCommand(player, entry)
                shop != null -> openTransaction(player, shop, page, entry)
            }
            else -> {}
        }
    }

    // ───────────────────────────── 取引画面 ─────────────────────────────

    fun openTransaction(player: Player, shop: ShopDefinition, page: Int, entry: ShopEntry) {
        val layout = registry.transaction
        val display = displayItem(entry, forShop = true)
        val itemName = entry.shopName?.let { Text.component(it) } ?: entry.name?.let { Text.component(it) } ?: display.effectiveName()
        val title = Text.component(layout.title, components = mapOf("item" to itemName))
        val holder = create(TransactionHolder(shop, page, entry), layout.rows, title)

        for ((slot, def) in layout.slots) {
            val stack: ItemStack = when (def.type) {
                SlotType.ITEM_DISPLAY -> shopIcon(entry)
                SlotType.BUY_1, SlotType.BUY_2, SlotType.BUY_3 -> {
                    val amount = settings.quantities[def.type.ordinal - SlotType.BUY_1.ordinal]
                    holder.actions[slot] = { p, _ -> buy(p, entry, amount); refreshHead(p, holder) }
                    buyButton(entry, amount)
                }
                SlotType.SELL_1, SlotType.SELL_2, SlotType.SELL_3 -> {
                    val amount = settings.quantities[def.type.ordinal - SlotType.SELL_1.ordinal]
                    holder.actions[slot] = { p, _ -> sellFromShop(p, entry, amount); refreshHead(p, holder) }
                    sellButton(entry, amount)
                }
                SlotType.BACK -> {
                    holder.actions[slot] = { p, _ -> openShop(p, shop, page) }
                    val b = settings.backward
                    button(b.material, b.shopName, b.shopLore)
                }
                SlotType.PLAYER_HEAD -> {
                    holder.headSlot = slot
                    playerHead(player)
                }
                SlotType.DUMMY -> dummy(def)
            }
            holder.backing.setItem(slot, stack)
        }
        show(player, holder)
    }

    private fun dummy(def: LayoutSlot): ItemStack =
        button(def.material ?: Material.BLACK_STAINED_GLASS_PANE, def.name ?: " ", def.lore)

    private fun buyButton(entry: ShopEntry, amount: Int): ItemStack {
        val layout = registry.transaction
        val price = entry.buyPrice
        if (price == null || (entry.ref?.let(items::template) == null)) {
            return button(
                settings.notBuyableMaterial,
                layout.notBuyableName ?: settings.title("transaction-not-buyable", "&c&l購入不可"),
                layout.notBuyableLore,
            )
        }
        val ph = mapOf("amount" to amount.toString(), "price" to money(price * amount))
        return button(
            settings.buyMaterial,
            layout.buyName ?: settings.title("transaction-buy-button", "&a&l購入: %amount%個"),
            layout.buyLore.ifEmpty { listOf(settings.title("transaction-buy-lore", "&7クリックで %amount% 個を %price% で購入")) },
            ph,
        ).asQuantity(amount.coerceIn(1, 64))
    }

    private fun sellButton(entry: ShopEntry, amount: Int): ItemStack {
        val layout = registry.transaction
        if (!entry.sellable) {
            return button(
                settings.notSellableMaterial,
                layout.notSellableName ?: settings.title("transaction-not-sellable", "&c&l売却不可"),
                layout.notSellableLore,
            )
        }
        val ph = mapOf("amount" to amount.toString(), "price" to money(entry.sellPrice!! * amount))
        return button(
            settings.sellMaterial,
            layout.sellName ?: settings.title("transaction-sell-button", "&c&l売却: %amount%個"),
            layout.sellLore.ifEmpty { listOf(settings.title("transaction-sell-lore", "&7クリックで %amount% 個を %price% で売却")) },
            ph,
        ).asQuantity(amount.coerceIn(1, 64))
    }

    private fun playerHead(player: Player): ItemStack {
        val ph = mapOf("player" to player.name, "balance" to money(economy.balance(player)))
        val stack = button(
            Material.PLAYER_HEAD,
            settings.title("transaction-balance-title", "&6&l%player%"),
            listOf(settings.title("transaction-balance-lore", "&7所持金: &a%balance%")),
            ph,
        )
        stack.editMeta(SkullMeta::class.java) { it.owningPlayer = player }
        return stack
    }

    private fun refreshHead(player: Player, holder: TransactionHolder) {
        if (holder.headSlot >= 0) holder.backing.setItem(holder.headSlot, playerHead(player))
    }

    // ───────────────────────────── 購入・売却処理 ─────────────────────────────

    private fun checkBuyPermission(player: Player, entry: ShopEntry): Boolean {
        if (entry.permission != null && !player.hasPermission(entry.permission)) {
            messages.send(player, "no-permission")
            return false
        }
        if (!economy.available) {
            messages.send(player, "economy-missing")
            return false
        }
        return true
    }

    fun buy(player: Player, entry: ShopEntry, amount: Int) {
        val price = entry.buyPrice
        val ref = entry.ref
        if (price == null || ref == null) {
            messages.send(player, "cant-buy")
            return
        }
        if (!checkBuyPermission(player, entry)) return
        val template = items.template(ref)
        if (template == null) {
            messages.send(player, "went-wrong", mapOf("error" to "${entry.rawId} を生成できません（連携プラグイン未導入、またはIDの誤り）"))
            return
        }
        applyCustomization(template, entry, forShop = false)

        val total = price * amount
        if (!economy.has(player, total)) {
            messages.send(player, "not-enough-money", mapOf("needed" to money(total)))
            return
        }
        if (!economy.withdraw(player, total)) {
            messages.send(player, "something-wrong")
            return
        }
        var delivered = amount
        val mmLuck = items.mmLuck
        if (ref is ItemRef.Mythic && mmLuck != null) {
            // MMLuck 経由で付与（StorageBox / Stash への振り分けは MMLuck が行う）
            delivered = 0
            try {
                for (stack in items.split(template, amount)) {
                    mmLuck.give(player, ref.id, stack.amount)
                    delivered += stack.amount
                }
            } catch (e: Exception) {
                plugin.logger.log(java.util.logging.Level.SEVERE, "MMLuck での付与に失敗しました: ${ref.id} x${amount - delivered} (${player.name})", e)
                val refund = price * (amount - delivered)
                if (refund > 0) economy.deposit(player, refund)
                messages.send(player, "went-wrong", mapOf("error" to "MMLuck での付与に失敗しました"))
                if (delivered == 0) return
            }
        } else {
            val leftovers = player.inventory.addItem(*items.split(template, amount).toTypedArray()).values
            if (leftovers.isNotEmpty()) {
                leftovers.forEach { player.world.dropItem(player.location, it) }
                messages.send(player, "partial-purchase", mapOf("amount" to leftovers.sumOf { it.amount }.toString()))
            }
        }
        val charged = price * delivered
        messages.send(player, "purchase", mapOf("taken" to money(charged)))
        playPurchaseSound(player)
        log("BUY", player, ref, delivered, charged)
    }

    private fun buyCommand(player: Player, entry: ShopEntry) {
        if (entry.commands.isEmpty()) return
        if (!checkBuyPermission(player, entry)) return
        val price = entry.buyPrice ?: 0.0
        if (!economy.has(player, price)) {
            messages.send(player, "not-enough-money", mapOf("needed" to money(price)))
            return
        }
        if (!economy.withdraw(player, price)) {
            messages.send(player, "something-wrong")
            return
        }
        val lines = entry.commands.map {
            Text.replace(it, mapOf("player" to player.name, "uuid" to player.uniqueId.toString())).removePrefix("/")
        }
        // コンソールコマンドはグローバルリージョンで実行する（Folia 対応。Paper では次 tick のメインスレッド）
        Bukkit.getGlobalRegionScheduler().execute(plugin) {
            val console = Bukkit.getConsoleSender()
            for (line in lines) {
                if (!runCatching { Bukkit.dispatchCommand(console, line) }.getOrDefault(false)) {
                    player.scheduler.run(plugin, { messages.send(player, "command-error") }, null)
                    plugin.logger.warning("コマンドの実行に失敗しました: $line")
                }
            }
        }
        if (price > 0) messages.send(player, "purchase", mapOf("taken" to money(price)))
        playPurchaseSound(player)
    }

    /** 取引画面の売却ボタン。インベントリ内の一致アイテムを最大 amount 個売る */
    private fun sellFromShop(player: Player, entry: ShopEntry, amount: Int) {
        if (!entry.sellable) return
        if (!economy.available) {
            messages.send(player, "economy-missing")
            return
        }
        val key = entry.ref!!.key
        val strict = registry.sell.vanillaStrict
        val inventory = player.inventory
        val contents = inventory.storageContents
        var remaining = amount
        for ((index, stack) in contents.withIndex()) {
            if (remaining <= 0) break
            if (items.identify(stack, strict)?.key != key) continue
            val take = minOf(remaining, stack!!.amount)
            remaining -= take
            contents[index] = if (take >= stack.amount) null else stack.asQuantity(stack.amount - take)
        }
        val sold = amount - remaining
        if (sold == 0) {
            messages.send(player, "nothing-to-sell")
            return
        }
        val total = entry.sellPrice!! * sold
        inventory.storageContents = contents
        if (!economy.deposit(player, total)) {
            messages.send(player, "something-wrong")
            return
        }
        messages.send(player, "sell", mapOf("money" to money(total)))
        playPurchaseSound(player)
        log("SELL", player, entry.ref, sold, total)
    }

    // ───────────────────────────── /sell ─────────────────────────────

    fun openSell(player: Player) {
        val title = Text.component(settings.title("sell", "&8Shop > 売却"))
        show(player, create(SellHolder(), registry.sell.rows, title))
    }

    /** /sell GUI を閉じた時。売れるものを売り、売れないものは返却する */
    fun processSell(player: Player, holder: SellHolder) {
        if (holder.processed) return
        holder.processed = true
        val contents = holder.backing.contents.filterNotNull().filterNot { it.isEmpty }
        holder.backing.clear()
        sellStacks(player, contents, returnUnsold = true)
    }

    /**
     * 売却 1 件分の査定。
     * Storage Box は中身 × 個数で査定して手数料を引く。[returned] は売却後に返す物（keep-box 時の空の Storage Box）
     */
    private class Appraisal(
        val ref: ItemRef,
        val count: Long,
        val money: Double,
        val fee: Double,
        val storageBox: Boolean,
        val returned: ItemStack?,
    )

    private fun appraise(stack: ItemStack?): Appraisal? {
        if (stack == null || stack.isEmpty) return null
        val strict = registry.sell.vanillaStrict
        val boxes = items.storageBox
        if (boxes != null && boxes.isStorageBox(stack)) {
            val config = registry.sell.storageBox
            if (!config.enabled) return null
            val contents = boxes.contents(stack) ?: return null
            val ref = items.identify(contents.item, strict) ?: return null
            val price = registry.sellPrice(ref) ?: return null
            val count = contents.amount * stack.amount
            val gross = price * count
            val fee = gross * config.feePercent / 100.0
            val returned = if (config.keepBox) boxes.emptied(stack)?.asQuantity(stack.amount) else null
            return Appraisal(ref, count, gross - fee, fee, true, returned)
        }
        val ref = items.identify(stack, strict) ?: return null
        val price = registry.sellPrice(ref) ?: return null
        return Appraisal(ref, stack.amount.toLong(), price * stack.amount, 0.0, false, null)
    }

    private fun isStorageBox(stack: ItemStack?): Boolean =
        stack != null && items.storageBox?.isStorageBox(stack) == true

    private fun sendStorageBoxSold(player: Player, appraisal: Appraisal) {
        val key = if (appraisal.returned != null) "sell-storagebox-kept" else "sell-storagebox"
        messages.send(
            player, key,
            mapOf(
                "item" to appraisal.ref.configId,
                "amount" to Text.number(appraisal.count.toDouble(), false),
                "money" to money(appraisal.money),
                "fee" to money(appraisal.fee),
            ),
        )
    }

    private fun giveBack(player: Player, stacks: Collection<ItemStack>) {
        if (stacks.isEmpty()) return
        player.inventory.addItem(*stacks.toTypedArray()).values.forEach { player.world.dropItem(player.location, it) }
    }

    /** /sell hand */
    fun sellHand(player: Player) {
        val hand = player.inventory.itemInMainHand
        val appraisal = appraise(hand)
        if (appraisal == null) {
            messages.send(player, "not-sellable-hand")
            return
        }
        if (!economy.available) {
            messages.send(player, "economy-missing")
            return
        }
        player.inventory.setItemInMainHand(appraisal.returned)
        if (!economy.deposit(player, appraisal.money)) {
            player.inventory.setItemInMainHand(hand)
            messages.send(player, "something-wrong")
            return
        }
        if (appraisal.storageBox) sendStorageBoxSold(player, appraisal)
        messages.send(player, "sell", mapOf("money" to money(appraisal.money)))
        playPurchaseSound(player)
        log("SELL", player, appraisal.ref, appraisal.count, appraisal.money)
    }

    /** /sell all （メインインベントリ 36 枠のみ。防具・オフハンドは対象外。Storage Box は設定で有効な時のみ） */
    fun sellAll(player: Player) {
        val inventory = player.inventory
        val contents = inventory.storageContents
        val includeBoxes = registry.sell.storageBox.includeInSellAll
        val sellable = ArrayList<ItemStack>()
        for ((index, stack) in contents.withIndex()) {
            if (!includeBoxes && isStorageBox(stack)) continue
            if (appraise(stack) == null) continue
            sellable += stack!!
            contents[index] = null
        }
        if (sellable.isEmpty()) {
            messages.send(player, "nothing-to-sell")
            return
        }
        if (!economy.available) {
            messages.send(player, "economy-missing")
            return
        }
        inventory.storageContents = contents
        sellStacks(player, sellable, returnUnsold = true)
    }

    private fun sellStacks(player: Player, stacks: List<ItemStack>, returnUnsold: Boolean) {
        if (stacks.isEmpty()) return
        val unsold = ArrayList<ItemStack>()
        val returned = ArrayList<ItemStack>()
        val sold = ArrayList<Appraisal>()
        var total = 0.0
        val economyReady = economy.available
        for (stack in stacks) {
            val appraisal = if (economyReady) appraise(stack) else null
            if (appraisal == null) {
                unsold += stack
                continue
            }
            total += appraisal.money
            appraisal.returned?.let(returned::add)
            sold += appraisal
        }
        if (total > 0 && !economy.deposit(player, total)) {
            messages.send(player, "something-wrong")
            giveBack(player, stacks)
            return
        }
        if (!economyReady) messages.send(player, "economy-missing")
        for (appraisal in sold) {
            if (appraisal.storageBox) sendStorageBoxSold(player, appraisal)
            log("SELL", player, appraisal.ref, appraisal.count, appraisal.money)
        }
        giveBack(player, returned)
        if (sold.isNotEmpty()) {
            messages.send(player, "sell", mapOf("money" to money(total)))
            playPurchaseSound(player)
        }
        if (unsold.isNotEmpty() && returnUnsold) {
            giveBack(player, unsold)
            messages.send(player, "cant-sell", mapOf("count" to unsold.sumOf { it.amount }.toString()))
        }
    }

    // ───────────────────────────── その他 ─────────────────────────────

    private fun playPurchaseSound(player: Player) {
        if (!settings.enableSound) return
        val sound = plugin.purchaseSound ?: return
        player.playSound(sound)
    }

    private fun log(action: String, player: Player, ref: ItemRef?, amount: Number, money: Double) {
        if (!settings.transactionLog) return
        plugin.logger.info("[$action] ${player.name} ${ref?.key} x$amount ${Text.number(money, false)}")
    }
}
