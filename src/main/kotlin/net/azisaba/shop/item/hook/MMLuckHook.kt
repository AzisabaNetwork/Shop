package net.azisaba.shop.item.hook

import com.github.mori01231.mmluck.GiveOverflowCommandExecutor
import com.github.mori01231.mmluck.MMLuck
import net.azisaba.rarity.api.Rarity
import org.bukkit.entity.Player
import java.lang.reflect.Method

/**
 * MythicMobs アイテムを MMLuck の付与処理（/mlg・/mlgo と同じ処理）で渡す。
 * StorageBox への格納、インベントリが一杯の時の Stash 送り、説明文の式の評価などは MMLuck 側が行う。
 *
 * MMLuck が有効な場合のみインスタンス化すること
 */
class MMLuckHook {
    /** `GiveOverflowCommandExecutor.giveItems(Player, String, int, boolean, Rarity, double)`（package-private のためリフレクション） */
    private val giveItems: Method = GiveOverflowCommandExecutor::class.java.getDeclaredMethod(
        "giveItems",
        Player::class.java,
        String::class.java,
        Int::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
        Rarity::class.java,
        Double::class.javaPrimitiveType,
    ).apply { isAccessible = true }

    /** 購入品で「レアドロップ！」と表示されないよう、どのレア度よりも重いダミー */
    private val noRareMessage: Rarity = Rarity.SimpleRarity("shop", Int.MAX_VALUE, "", emptyList())

    /** [amount] 個を 1 回で渡す（最大スタック数を超えないよう呼び出し側で分割すること） */
    fun give(player: Player, mythicId: String, amount: Int) {
        val silent = MMLuck.getInstance().boostHolder.isSilentMode(player.uniqueId)
        giveItems.invoke(null, player, mythicId, amount, silent, noRareMessage, 0.0)
    }
}
