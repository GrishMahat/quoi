package quoi.module.impl.misc.slayers.vampire

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import quoi.api.colour.Colour
import quoi.module.impl.misc.slayers.ISlayer
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.group.SettingGroup
import quoi.api.skyblock.location.Island

object VampireSlayer : SettingGroup(Slayers, "Bloodfiend", area = Island.Rift), ISlayer {
    override val features = setOf(BloodfiendESP, TwinClaws, ManiaAlert, BloodItems, BloodfiendAssist)

    override val entitiesForRender: List<Pair<LivingEntity, Colour?>>
        get() = BloodfiendESP.renderBosses

    override val debugString: String
        get() = BloodfiendESP.debug

    override val running: Boolean
        get() = super.running && features.any { it.enabled }
}