package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.player.EquipmentSwapper
import quoi.utils.skyblock.player.container.CONTAINER_MENU
import quoi.utils.skyblock.player.container.IContainerSettings

@TypeName("equip")
class EquipAction(
    var name: String = "",
    var blockInput: Boolean = false,
) : TriggerAction {
    override fun validationError() = if (name.isBlank()) "Enter an equipment name." else null

    override fun execute(ctx: TriggerContext) {
        check(EquipmentSwapper.equip(
            ctx.expand(name).trim(),
            settings = object : IContainerSettings by CONTAINER_MENU {
                override val blockInput = this@EquipAction.blockInput
            },
        )) { "Could not start equipment swap" }
    }

    override fun displayString() = "Equip: $name"

    override fun ElementScope<*>.draw() = settingRow(
        { textField("Equipment name", name) { name = it } },
        { toggleField("Block inputs", ::blockInput) },
    )
}
