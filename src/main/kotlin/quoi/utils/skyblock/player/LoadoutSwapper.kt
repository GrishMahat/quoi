package quoi.utils.skyblock.player

import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.item.ItemUtils.getCleanLore
import quoi.utils.skyblock.player.container.CONTAINER_MENU
import quoi.utils.skyblock.player.container.IContainerSettings
import quoi.utils.skyblock.player.container.task.ContainerManager
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.menu

@Init
object LoadoutSwapper {
    private val loadoutSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)

    init {
        QuoiCommand.command.sub("loadout") { slot: Int ->
            if (!equip(slot)) modMessage("&cA container action is already in progress.")
        }.description("Equips a loadout slot from 1 to ${loadoutSlots.size}.")
    }

    @JvmOverloads
    fun equip(
        slot: Int,
        settings: IContainerSettings = CONTAINER_MENU,
    ): Boolean {
        if (slot !in 1..loadoutSlots.size) {
            modMessage("&cInvalid loadout slot. Use &e/quoi loadout <1-${loadoutSlots.size}>&c.")
            return false
        }
        if (ContainerManager.active) return false

        val targetSlot = loadoutSlots[slot - 1]
        val newTask = containerTask(name = "Loadout $slot", settings = settings) {
            openContainer("loadout", "(1/3) Loadouts")
            check("Loadout slot $slot is not equipable") {
                mc.player?.containerMenu?.items?.getOrNull(targetSlot)?.getCleanLore()
                    ?.any { it.contains("Left-click to equip!") } == true
            }
            pickup(targetSlot.menu)
            catchContainer("(1/3) Loadouts")

            onFinished { result ->
                when (result) {
                    ContainerTaskResult.Success -> modMessage("&aEquipped loadout &f$slot")
                    ContainerTaskResult.Busy,
                    ContainerTaskResult.Cancelled -> Unit
                    is ContainerTaskResult.Failure -> modMessage("&c${result.message}.")
                }

            }
        }

        return newTask.run().result == null
    }
}
