package quoi.api.events

import quoi.api.events.core.Event
import quoi.api.skyblock.Pet

abstract class PetEvent {
    class Change(val pet: Pet?, val cause: Cause) : Event()

    enum class Cause {
        SUMMON, DESPAWN, AUTOPET, MENU,
    }
}
