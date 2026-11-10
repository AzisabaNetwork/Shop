package net.azisaba.shop.shop

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.azisaba.shop.config.Settings
import net.azisaba.shop.item.ItemRef
import net.azisaba.shop.util.doubleOrNull
import net.azisaba.shop.util.stringOrList
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.MemoryConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.plugin.Plugin
import java.io.File

/** menu.yml, shops フォルダ, transaction.yml, sell.yml を読み込み保持する */
class ShopRegistry(private val plugin: Plugin) {
    private val logger get() = plugin.logger

    /** キーは小文字のショップ名 */
    var shops: Map<String, ShopDefinition> = emptyMap()
        private set
    var menu: MenuDefinition = MenuDefinition(3, emptyList())
        private set
    var transaction: TransactionLayout = defaultLayout()
        private set
    var sell: SellConfig = SellConfig(6, true, true, true, false, true)
        private set

    /** ItemRef.key → 1 個あたりの売却価格 */
    var sellPrices: Map<String, Double> = emptyMap()
        private set

    fun shop(id: String): ShopDefinition? = shops[id.lowercase()]

    fun load() {
        val folder = plugin.dataFolder
        shops = loadShops(File(folder, "shops"))
        menu = loadMenu(File(folder, "menu.yml"))
        transaction = loadLayout(File(folder, "transaction.yml"))
        val sellYaml = YamlConfiguration.loadConfiguration(File(folder, "sell.yml"))
        sell = SellConfig(
            rows = sellYaml.getInt("gui-rows", 6).coerceIn(1, 6),
            vanillaStrict = sellYaml.getBoolean("vanilla-strict", true),
            useShopPrices = sellYaml.getBoolean("use-shop-sell-prices", true),
            mythicEnabled = sellYaml.getBoolean("mythic.enabled", true),
            crackShotEnabled = sellYaml.getBoolean("crackshot.enabled", false),
            vanillaEnabled = sellYaml.getBoolean("vanilla.enabled", true),
        )
        sellPrices = buildSellPrices(sellYaml)
    }

    /** /sell での 1 個あたりの価格。売却不可なら null */
    fun sellPrice(ref: ItemRef): Double? {
        val enabled = when (ref) {
            is ItemRef.Vanilla -> sell.vanillaEnabled
            is ItemRef.Mythic -> sell.mythicEnabled
            is ItemRef.CrackShot -> sell.crackShotEnabled
        }
        if (!enabled) return null
        return sellPrices[ref.key]?.takeIf { it > 0 }
    }

    private fun buildSellPrices(yaml: YamlConfiguration): Map<String, Double> {
        val prices = LinkedHashMap<String, Double>()
        if (sell.useShopPrices) {
            for (shop in shops.values) for (entry in shop.entries) {
                if (!entry.sellable) continue
                prices.putIfAbsent(entry.ref!!.key, entry.sellPrice!!)
            }
        }
        // sell.yml の個別指定はショップの価格より優先（0 以下で売却不可）
        fun section(path: String, toRef: (String) -> ItemRef?) {
            val section = yaml.getConfigurationSection(path) ?: return
            for (id in section.getKeys(false)) {
                val ref = toRef(id)
                val price = section.doubleOrNull(id)
                if (ref == null || price == null) {
                    logger.warning("sell.yml: $path.$id を読み込めませんでした")
                    continue
                }
                prices[ref.key] = price
            }
        }
        section("mythic.items") { ItemRef.Mythic(it) }
        section("crackshot.items") { ItemRef.CrackShot(it) }
        section("vanilla.items") { ItemRef.parse(it) as? ItemRef.Vanilla }
        return prices
    }

    private fun loadShops(dir: File): Map<String, ShopDefinition> {
        if (!dir.isDirectory) return emptyMap()
        val result = LinkedHashMap<String, ShopDefinition>()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".yml") }?.sortedBy { it.name } ?: return emptyMap()
        for (file in files) {
            val id = file.nameWithoutExtension
            val yaml = YamlConfiguration.loadConfiguration(file)
            convertLegacy(file, yaml, yaml)
            val entries = loadEntries(yaml, "shops/${file.name}")
            result[id.lowercase()] = ShopDefinition(id, yaml.getString("title") ?: id, yaml.getString("permission"), entries)
        }
        return result
    }

    private fun loadMenu(file: File): MenuDefinition {
        val yaml = YamlConfiguration.loadConfiguration(file)
        val section = yaml.getConfigurationSection("Menu") ?: return MenuDefinition(3, emptyList())
        convertLegacy(file, yaml, section)
        return MenuDefinition(section.getInt("rows", 3).coerceIn(1, 6), loadEntries(section, "menu.yml"))
    }

    /** GUIShop 形式（pages/スロット番号）のファイルなら items リスト形式に書き換えて保存する（元ファイルは backup/ へ） */
    private fun convertLegacy(file: File, yaml: YamlConfiguration, section: ConfigurationSection) {
        if (!LegacyFormat.isLegacy(section)) return
        val backup = File(plugin.dataFolder, "backup/legacy-$backupStamp/${file.relativeTo(plugin.dataFolder).path}")
        file.copyTo(backup, overwrite = true)
        LegacyFormat.convert(section)
        yaml.save(file)
        logger.info("${file.relativeTo(plugin.dataFolder).path} を GUIShop 形式からページ/スロット番号なしの形式に変換しました（元ファイル: ${backup.relativeTo(plugin.dataFolder).path}）")
    }

    private val backupStamp: String by lazy {
        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    }

    /** `items:` リストを書かれた順に読み込む */
    private fun loadEntries(section: ConfigurationSection, source: String): List<ShopEntry> {
        val list = section.getMapList("items")
        if (list.isEmpty() && section.contains("items") && !section.isList("items")) {
            logger.warning("$source: items はリスト（- id: ...）で書いてください")
        }
        return list.mapIndexed { index, map ->
            val item = MemoryConfiguration().createSection("item", map)
            parseEntry(item, "$source items[$index]")
        }
    }

    private fun parseEntry(s: ConfigurationSection, where: String): ShopEntry {
        val rawId = s.getString("id")
        val ref = ItemRef.parse(rawId)
        if (rawId != null && ref == null) logger.warning("$where: id '$rawId' は不明なアイテムです")

        val buyPrice = s.doubleOrNull("buy-price")
        val sellPrice = s.doubleOrNull("sell-price")
        val targetShop = s.getString("target-shop")
        val commands = s.stringOrList("commands")
        val type = s.getString("type")?.uppercase()?.let { name ->
            EntryType.entries.firstOrNull { it.name == name }
                ?: EntryType.DUMMY.also { logger.warning("$where: type '$name' は未対応のため DUMMY として扱います") }
        } ?: if (buyPrice != null || sellPrice != null) EntryType.SHOP else EntryType.DUMMY

        return ShopEntry(
            type = type,
            rawId = rawId,
            ref = ref,
            buyPrice = buyPrice?.coerceAtLeast(0.0),
            sellPrice = sellPrice,
            shopName = s.getString("shop-name"),
            shopLore = s.stringOrList("shop-lore"),
            name = s.getString("name") ?: s.getString("buy-name"),
            lore = s.stringOrList("lore").ifEmpty { s.stringOrList("buy-lore") },
            enchantments = parseEnchantments(s.stringOrList("enchantments"), where),
            commands = commands,
            permission = s.getString("permission"),
            targetShop = targetShop,
        )
    }

    private fun parseEnchantments(values: List<String>, where: String): Map<Enchantment, Int> {
        if (values.isEmpty()) return emptyMap()
        val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
        val result = LinkedHashMap<Enchantment, Int>()
        for (token in values.flatMap { it.split(' ', ',') }.map(String::trim).filter(String::isNotEmpty)) {
            val name = token.substringBefore(':')
            val level = token.substringAfter(':', "1").toIntOrNull() ?: 1
            val key = NamespacedKey.fromString(name.lowercase())
            val enchantment = key?.let { registry.get(it) }
            if (enchantment == null) {
                logger.warning("$where: エンチャント '$name' は不明です")
                continue
            }
            result[enchantment] = level
        }
        return result
    }

    private fun loadLayout(file: File): TransactionLayout {
        if (!file.isFile) return defaultLayout()
        val yaml = YamlConfiguration.loadConfiguration(file)
        val layout = yaml.getConfigurationSection("layout") ?: return defaultLayout()
        val rows = yaml.getInt("rows", 3).coerceIn(1, 6)
        val slots = LinkedHashMap<Int, LayoutSlot>()
        for (key in layout.getKeys(false)) {
            val slot = key.toIntOrNull()?.takeIf { it in 0 until rows * 9 } ?: continue
            val s = layout.getConfigurationSection(key) ?: continue
            val type = SlotType.entries.firstOrNull { it.name == s.getString("type")?.uppercase() } ?: continue
            slots[slot] = LayoutSlot(type, s.getString("id")?.let { Settings.material(it, org.bukkit.Material.STONE) }, s.getString("name"), s.stringOrList("lore"))
        }
        return TransactionLayout(
            title = yaml.getString("title") ?: "&8%item%",
            rows = rows,
            slots = slots,
            buyName = yaml.getString("buttons.buy.name"),
            buyLore = yaml.stringOrList("buttons.buy.lore"),
            sellName = yaml.getString("buttons.sell.name"),
            sellLore = yaml.stringOrList("buttons.sell.lore"),
            notBuyableName = yaml.getString("buttons.not-buyable.name"),
            notBuyableLore = yaml.stringOrList("buttons.not-buyable.lore"),
            notSellableName = yaml.getString("buttons.not-sellable.name"),
            notSellableLore = yaml.stringOrList("buttons.not-sellable.lore"),
        )
    }

    private fun defaultLayout(): TransactionLayout {
        val slots = LinkedHashMap<Int, LayoutSlot>()
        val types = mapOf(
            10 to SlotType.SELL_3, 11 to SlotType.SELL_2, 12 to SlotType.SELL_1, 13 to SlotType.ITEM_DISPLAY,
            14 to SlotType.BUY_1, 15 to SlotType.BUY_2, 16 to SlotType.BUY_3, 18 to SlotType.PLAYER_HEAD, 26 to SlotType.BACK,
        )
        for (i in 0 until 27) {
            slots[i] = LayoutSlot(types[i] ?: SlotType.DUMMY, org.bukkit.Material.BLACK_STAINED_GLASS_PANE, " ", emptyList())
        }
        return TransactionLayout("&8%item%", 3, slots, null, emptyList(), null, emptyList(), null, emptyList(), null, emptyList())
    }
}
