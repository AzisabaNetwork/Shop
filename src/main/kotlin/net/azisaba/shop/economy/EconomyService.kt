package net.azisaba.shop.economy

import net.milkbowl.vault.economy.Economy
import org.bukkit.OfflinePlayer
import org.bukkit.Server

/** Vault 経由の経済連携。経済プラグインの登録が遅れても動くよう毎回取得する */
class EconomyService(private val server: Server) {
    private val economy: Economy? get() = server.servicesManager.load(Economy::class.java)

    val available: Boolean get() = economy != null

    fun balance(player: OfflinePlayer): Double = economy?.getBalance(player) ?: 0.0

    fun has(player: OfflinePlayer, amount: Double): Boolean = economy?.has(player, amount) ?: false

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0) return true
        return economy?.withdrawPlayer(player, amount)?.transactionSuccess() ?: false
    }

    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0) return true
        return economy?.depositPlayer(player, amount)?.transactionSuccess() ?: false
    }
}
