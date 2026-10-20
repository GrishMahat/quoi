package quoi.api.customtriggers.conditions

import quoi.api.customtriggers.Abobable
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeNamed

/**
 * Additional state check evaluated after a trigger matches.
 * All conditions in a rule must pass before its actions run.
 */
sealed class TriggerCondition : TypeNamed, Abobable {
    var expected: Boolean = true

    abstract fun matches(ctx: TriggerContext): Boolean
}