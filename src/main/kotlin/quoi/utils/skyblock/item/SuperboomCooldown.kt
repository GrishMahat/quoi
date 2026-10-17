package quoi.utils.skyblock.item

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import quoi.QuoiMod.mc
import quoi.utils.skyblock.item.ItemUtils.skyblockId

//? if <26.3 {
import net.minecraft.network.protocol.game.ServerboundSwingPacket
//?} else {
/*import net.minecraft.network.protocol.game.ServerboundPunchPacket
*///?}

/**
 * TODO:
 *  - make this a setting or whatever
 *  - find a better solution than that idk
 */
object SuperboomCooldown {
    private const val COOLDOWN_MILLIS = 2_000L

    @Volatile
    private var readyAt = 0L

    @Volatile
    var lastUsePacket: Packet<*>? = null
        private set

    val remainingMillis: Long
        get() = (readyAt - System.currentTimeMillis()).coerceAtLeast(0L)

    fun onPacketSent(packet: Packet<*>) {
        val mainHandUse = when (packet) {
            is ServerboundUseItemOnPacket -> packet.hand == InteractionHand.MAIN_HAND
            is ServerboundUseItemPacket -> packet.hand == InteractionHand.MAIN_HAND
            //? if <26.3 {
            is ServerboundSwingPacket -> packet.hand == InteractionHand.MAIN_HAND
            //?} else {
            /*is ServerboundPunchPacket -> true
            *///?}
            is ServerboundPlayerActionPacket -> packet.action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
            else -> false
        }
        if (mainHandUse || packet is ServerboundUseItemPacket || packet is ServerboundUseItemOnPacket) lastUsePacket = packet
        if (!mainHandUse) return
        if (mc.player?.mainHandItem.skyblockId != "INFINITE_SUPERBOOM_TNT") return

        val now = System.currentTimeMillis()
        if (now >= readyAt) readyAt = now + COOLDOWN_MILLIS
    }
}
