package quoi.utils.skyblock.player

import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.item.ItemUtils.cleanName
import quoi.utils.skyblock.player.container.CONTAINER_MENU
import quoi.utils.skyblock.player.container.IContainerSettings
import quoi.utils.skyblock.player.container.task.ContainerManager
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.menu

@Init
object WardrobeUtils {
    init {
        QuoiCommand.command.sub("wardrobe") { slot: Int ->
            if (!equip(slot)) modMessage("&cA container action is already in progress.")
        }.description("Equips a wardrobe slot from 1 to 9.")
    }

    @JvmOverloads
    fun equip(
        slot: Int,
        settings: IContainerSettings = CONTAINER_MENU,
        disableUnequip: Boolean = false,
    ): Boolean {
        if (slot !in 1..9) {
            modMessage("&cInvalid wardrobe slot. Use &e/quoi wardrobe <1-9>&c.")
            return false
        }
        if (ContainerManager.active) return false

        val targetSlot = slot + 35
        var state = WardrobeState.UNKNOWN

        val newTask = containerTask(name = "Wardrobe $slot", settings = settings) {
            openContainer("wardrobe", "(1/3) Armor Sets")

            action {
                state = mc.player?.containerMenu?.items?.getOrNull(targetSlot)?.wardrobeState() ?: WardrobeState.EMPTY
            }
            check("Wardrobe slot $slot is empty") { state != WardrobeState.EMPTY }
            check("Wardrobe slot $slot is locked") { state != WardrobeState.LOCKED }
            check("Wardrobe slot $slot is not ready") { state != WardrobeState.UNKNOWN }

            pickup(targetSlot.menu).unless { disableUnequip && it.wardrobeState() == WardrobeState.EQUIPPED }
            catchContainer("(1/3) Armor Sets")

            onFinished { result ->
                when (result) {
                    ContainerTaskResult.Success -> when {
                        disableUnequip && state == WardrobeState.EQUIPPED -> modMessage("&eWardrobe slot &f$slot &eis already equipped.")
                        else -> modMessage("&aEquipped wardrobe slot &f$slot")
                    }
                    ContainerTaskResult.Busy,
                    ContainerTaskResult.Cancelled -> Unit
                    is ContainerTaskResult.Failure -> modMessage("&c${result.message}.")
                }

            }
        }

        return newTask.run().result == null
    }

    private fun ItemStack.wardrobeState(): WardrobeState {
        return when {
            cleanName.endsWith(": Empty") -> WardrobeState.EMPTY
            cleanName.endsWith(": Equipped") -> WardrobeState.EQUIPPED
            cleanName.endsWith(": Ready") -> WardrobeState.READY
            cleanName.endsWith(": Locked") -> WardrobeState.LOCKED
            else -> WardrobeState.UNKNOWN
        }
    }

    private enum class WardrobeState {
        EQUIPPED, READY, EMPTY, LOCKED, UNKNOWN,
    }
}
