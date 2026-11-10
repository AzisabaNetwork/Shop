package net.azisaba.shop

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * plugins/GUIShop の設定を plugins/Shop にコピーする。
 * messages.yml は GUIShop 側が英語のデフォルトのため対象外（本プラグインの日本語版を使う）。
 */
object Migration {
    private val FILES = listOf("config.yml", "menu.yml", "transaction.yml")

    fun source(dataFolder: File): File = File(dataFolder.parentFile, "GUIShop")

    fun hasExistingConfig(dataFolder: File): Boolean =
        File(dataFolder, "shops").isDirectory || File(dataFolder, "menu.yml").isFile

    /** @return コピーしたファイル数。GUIShop が無ければ -1 */
    fun import(dataFolder: File): Int {
        val source = source(dataFolder)
        if (!source.isDirectory) return -1
        dataFolder.mkdirs()
        backup(dataFolder)

        var count = 0
        for (name in FILES) {
            val file = File(source, name)
            if (!file.isFile) continue
            file.copyTo(File(dataFolder, name), overwrite = true)
            count++
        }
        val shops = File(source, "shops")
        if (shops.isDirectory) {
            val target = File(dataFolder, "shops")
            target.mkdirs()
            shops.listFiles { f -> f.isFile && f.name.endsWith(".yml") }?.forEach {
                it.copyTo(File(target, it.name), overwrite = true)
                count++
            }
        }
        return count
    }

    private fun backup(dataFolder: File) {
        if (!hasExistingConfig(dataFolder)) return
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val backup = File(dataFolder, "backup/$stamp")
        for (name in FILES) {
            val file = File(dataFolder, name)
            if (file.isFile) file.copyTo(File(backup, name), overwrite = true)
        }
        File(dataFolder, "shops").takeIf { it.isDirectory }?.copyRecursively(File(backup, "shops"), overwrite = true)
    }
}
