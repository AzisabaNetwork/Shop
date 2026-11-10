package net.azisaba.shop.config

import net.azisaba.shop.util.Text
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration

/** messages.yml（GUIShop と同じ `messages.<key>` 形式） */
class Messages(private val config: FileConfiguration, private val abbreviate: Boolean) {

    fun raw(key: String): String = config.getString("messages.$key") ?: DEFAULTS[key] ?: key

    fun send(sender: CommandSender, key: String, placeholders: Map<String, String> = emptyMap()) {
        val text = raw(key)
        if (text.isEmpty()) return
        val prefix = raw("prefix")
        sender.sendMessage(Text.component(if (prefix.isEmpty()) text else "$prefix $text", placeholders))
    }

    fun money(value: Double): String = raw("currency-prefix") + Text.number(value, abbreviate) + raw("currency-suffix")

    companion object {
        val DEFAULTS = mapOf(
            "prefix" to "&f[&cShop&f]",
            "no-permission" to "&c権限がありません。",
            "disabled-world" to "&cこのワールドではショップを利用できません。",
            "players-only" to "&cこのコマンドはプレイヤーのみ実行できます。",
            "shop-not-found" to "&cショップ &f%shop% &cが見つかりません。",
            "purchase" to "&f購入しました。&c%taken% &fが支払われました。",
            "not-enough-money" to "&f購入には &c%needed% &fが必要です。",
            "command-error" to "&cコマンドの実行中にエラーが発生しました。管理者に連絡してください。",
            "sell" to "&fアイテムを売却し、&a%money% &fを受け取りました。",
            "cant-sell" to "&c%count% 個のアイテムは売却できないため返却しました。",
            "nothing-to-sell" to "&c売却できるアイテムがありません。",
            "not-sellable-hand" to "&c手に持っているアイテムは売却できません。",
            "sell-storagebox" to "&7Storage Box (&f%item% &7x%amount%) を売却: &a%money% &7(手数料 &c%fee%&7 / Storage Box は回収しました)",
            "sell-storagebox-kept" to "&7Storage Box (&f%item% &7x%amount%) の中身を売却: &a%money% &7(手数料 &c%fee%&7)",
            "iteminfo-storagebox" to "&fStorage Box の中身: &e%id% &7x%amount%\n&f設定用: &7id: '%config%'\n&f売却額: &a%price%",
            "cant-buy" to "&cこのアイテムは購入できません。",
            "partial-purchase" to "&eインベントリに入りきらなかった &f%amount% &e個を足元にドロップしました。",
            "went-wrong" to "&cアイテムの生成に失敗しました: &f%error%",
            "something-wrong" to "&c問題が発生しました。管理者に連絡してください。",
            "economy-missing" to "&c経済プラグインが見つからないため取引できません。",
            "currency-prefix" to "$",
            "currency-suffix" to "",
            "reloaded" to "&a設定を再読み込みしました。&7(ショップ %shops% 件 / 売却価格 %prices% 件)",
            "import-done" to "&aGUIShop から %files% 個のファイルをインポートしました。&7/shopadmin reload で反映されます。",
            "import-missing" to "&cplugins/GUIShop が見つかりません。",
            "import-exists" to "&c既に設定が存在します。上書きするには &f/shopadmin import force &cを実行してください（既存ファイルはバックアップされます）。",
            "iteminfo-empty" to "&c手にアイテムを持ってください。",
            "iteminfo" to "&fID: &e%id%\n&f設定用: &7id: '%config%'\n&f/sell 価格: &a%price%",
        )
    }
}
