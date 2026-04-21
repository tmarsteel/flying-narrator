package io.github.tmarsteel.flyingnarrator.pacenote

import io.github.tmarsteel.flyingnarrator.feature.Feature

/**
 * An _arbitrarily sized_ piece of information about the race route, which should be delivered to the driver
 * uninterrupted. The decision how to piecemeal the entire route description is in the domain logic of a
 * pacenote system and may vary as per driver preference.
 */
sealed interface PacenoteAtom {
    data class Straight(val distance: Int) : PacenoteAtom {
        override fun toString(): String {
            return distance.toString(10)
        }
    }
    interface Transition : PacenoteAtom
    data object ImmediateTransition : Transition {
        override fun toString(): String {
            return "into"
        }
    }
    data object ShortTransition : Transition {
        override fun toString(): String {
            return "to"
        }
    }
    data class Corner(
        val direction: Feature.Corner.Direction,
        /**
         * Whether this corner is across a junction/intersection
         */
        val isAtJunction: Boolean,
        val sections: List<Section>,
    ) : PacenoteAtom {
        data class Section(
            val radiusStart: Double,
            val severityStart: Severity,
            val radiusEnd: Double,
            val severityEnd: Severity,
            val length: Double,
            val modifiers: List<Modifier>,
        ) {
            override fun toString(): String {
                val sb = StringBuilder()
                sb.append(severityStart)
                sb.append("(r=")
                sb.append(radiusStart.toInt().toString())
                sb.append("m)")
                if (severityEnd != severityStart) {
                    sb.append("->")
                    sb.append(severityEnd)
                    sb.append("(r=")
                    sb.append(radiusEnd.toInt().toString())
                    sb.append("m)")
                }
                sb.append("(d=")
                sb.append(length.toInt().toString())
                sb.append("m)")
                for (modifier in modifiers) {
                    sb.append(" ")
                    sb.append(modifier.toString())
                }
                return sb.toString()
            }
        }

        override fun toString(): String {
            val sb = StringBuilder()
            if (isAtJunction) {
                sb.append("turn ")
            }
            var directionWritten = false
            var currentSeverity = sections.first().severityStart
            for (section in sections) {
                var severityChange = currentSeverity.compareTo(section.severityStart)
                when {
                    severityChange < 0 -> {
                        sb.append("opens ")
                    }

                    severityChange > 0 -> {
                        sb.append("tightens ")
                    }
                }
                if (severityChange >= 0 || section.severityStart < Severity.SLIGHT) {
                    sb.append(section.severityStart)
                }
                sb.append("(r=")
                sb.append(section.radiusStart.toInt().toString())
                sb.append("m)")
                sb.append(' ')
                if (!directionWritten) {
                    sb.append(direction)
                    sb.append(' ')
                    directionWritten = true
                }
                severityChange = section.severityStart.compareTo(section.severityEnd)
                when {
                    severityChange < 0 -> {
                        sb.append("opens ")
                    }

                    severityChange > 0 -> {
                        sb.append("tightens ")
                    }
                }

                if (severityChange != 0 && (severityChange > 0 || section.severityEnd < Severity.SLIGHT)) {
                    sb.append(section.severityEnd)
                    sb.append("(r=")
                    sb.append(section.radiusEnd.toInt().toString())
                    sb.append("m)")
                    sb.append(' ')
                }

                for (mod in section.modifiers) {
                    sb.append(' ')
                    sb.append(mod)
                }

                currentSeverity = section.severityEnd
            }

            sb.append("(d=")
            sb.append(sections.sumOf { it.length }.toInt().toString())
            sb.append("m)")

            return sb.toString()
        }

        interface Modifier : SectionModifier {
            /**
             * Non-standard corner length
             */
            data class Length(val length: Value) : Modifier {
                override fun toString() = length.toString()

                enum class Value {
                    SHORT,
                    LONG,
                    EXTRA_LONG,
                    EXTRA_EXTRA_LONG,
                    ;

                    override fun toString(): String {
                        return name.replace('_', ' ').lowercase()
                    }
                }
            }
        }

        enum class Severity {
            SQUARE,
            ONE,
            TWO,
            THREE,
            FOUR,
            FIVE,
            SIX,
            SLIGHT,
            ;

            override fun toString(): String {
                return name.lowercase()
            }
        }
    }

    data class Hairpin(
        val direction: Feature.Corner.Direction,
        val minSeverity: Corner.Severity,
    ) : PacenoteAtom {
        override fun toString(): String {
            val sb = StringBuilder()
            if (minSeverity == Corner.Severity.THREE) {
                sb.append("open ")
            }
            sb.append("hairpin ")
            sb.append(direction)
            return sb.toString()
        }
    }

    /**
     * Additional information applicable to _any_ stretch of road.
     */
    interface SectionModifier {
        data object OverCrest : SectionModifier
    }
}