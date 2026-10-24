package quoi.api.events

import quoi.api.events.core.Event
import quoi.api.skyblock.dungeon.enums.DungeonClass

abstract class PartyEvent {
    class Message(val sender: String, val content: String) : Event()

    abstract class Member {
        class Join(val name: String, val clazz: DungeonClass? = null) : Event()
    }

    class Disband : Event()
}
