package net.azisaba.shop

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.azisaba.shop.command.ShopCommands
import net.azisaba.shop.config.Messages
import net.azisaba.shop.config.Settings
import net.azisaba.shop.economy.EconomyService
import net.azisaba.shop.gui.GuiListener
import net.azisaba.shop.gui.GuiManager
import net.azisaba.shop.gui.GuiHolder
import net.azisaba.shop.gui.SellHolder
import net.azisaba.shop.item.ItemService
import net.azisaba.shop.shop.ShopRegistry
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Shop : JavaPlugin() {
    lateinit var settings: Settings
        private set
    lateinit var messages: Messages
        private set
    var purchaseSound: Sound? = null
        private set

    val items = ItemService(this)
    val registry = ShopRegistry(this)
    val economy by lazy { EconomyService(server) }
    val gui = GuiManager(this)

    override fun onEnable() {
        setupFiles()
        items.setup()
        reload()

        server.pluginManager.registerEvents(GuiListener(this), this)
        ShopCommands(this).register()

        if (!economy.available) logger.warning("Vault の経済プラグインが見つかりません。売買はできません。")
    }

    override fun onDisable() {
        // 無効化中はスケジューラを使えないので直接閉じる（PlugMan の unload/reload もここを通る）
        for (player in server.onlinePlayers) closeGui(player)
    }

    /** 初回起動時: plugins/GUIShop があればそこから移行し、無ければデフォルト設定を出力する */
    private fun setupFiles() {
        dataFolder.mkdirs()
        if (!Migration.hasExistingConfig(dataFolder) && Migration.source(dataFolder).isDirectory) {
            val count = Migration.import(dataFolder)
            logger.info("plugins/GUIShop から設定を $count ファイル移行しました")
        }
        for (name in listOf("config.yml", "messages.yml", "menu.yml", "transaction.yml", "sell.yml")) {
            if (!File(dataFolder, name).exists()) saveResource(name, false)
        }
        if (!File(dataFolder, "shops").isDirectory) saveResource("shops/Example.yml", false)
    }

    fun reload() {
        // Folia ではプレイヤーのインベントリはそのプレイヤーのリージョンスレッドでしか触れないため各自のスケジューラで閉じる
        for (player in server.onlinePlayers) player.scheduler.run(this, { closeGui(player) }, null)
        reloadConfig()
        settings = Settings(config)
        messages = Messages(YamlConfiguration.loadConfiguration(File(dataFolder, "messages.yml")), settings.abbreviatePrices)
        purchaseSound = resolveSound(settings.purchaseSound)
        registry.load()
        logger.info("ショップ ${registry.shops.size} 件 / /sell 価格 ${registry.sellPrices.size} 件を読み込みました")
    }

    /** 開いている GUI を閉じる（売却 GUI の中身は精算/返却する） */
    private fun closeGui(player: Player) {
        val holder = player.openInventory.topInventory.getHolder(false) as? GuiHolder ?: return
        if (holder is SellHolder) gui.processSell(player, holder)
        player.closeInventory()
    }

    /** `ENTITY_PLAYER_LEVELUP`（GUIShop 形式）と `entity.player.levelup` の両方を受け付ける */
    private fun resolveSound(name: String): Sound? {
        if (name.isBlank()) return null
        val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT)
        val key = if (name.contains('.') || name.contains(':')) {
            Key.key(name.lowercase())
        } else {
            val normalized = name.uppercase()
            registry.keyStream()
                .filter { it.value().replace('.', '_').replace('/', '_').uppercase() == normalized }
                .findFirst().orElse(null)
        }
        if (key == null) {
            logger.warning("purchase-sound '$name' は不明なサウンドです")
            return null
        }
        return Sound.sound(key, Sound.Source.MASTER, 1f, 1f)
    }
}
