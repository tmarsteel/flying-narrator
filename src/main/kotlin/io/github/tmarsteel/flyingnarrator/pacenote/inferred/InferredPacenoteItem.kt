package io.github.tmarsteel.flyingnarrator.pacenote.inferred

import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.sumOf

sealed interface InferredPacenoteItem {
    data class Straight(val distance: Distance) : InferredPacenoteItem {
        override fun toString(): String {
            return distance.toDoubleInMeters().toInt().toString(10)
        }
    }
    interface Transition : InferredPacenoteItem
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
    ) : InferredPacenoteItem {
        data class Section(
            val radiusStart: Distance,
            val severityStart: Severity,
            val radiusEnd: Distance,
            val severityEnd: Severity,
            val length: Distance,
            val modifiers: List<Modifier>,
        ) {
            override fun toString(): String {
                val sb = StringBuilder()
                sb.append(severityStart)
                sb.append("(r=")
                sb.append(radiusStart)
                sb.append(")")
                if (severityEnd != severityStart) {
                    sb.append("->")
                    sb.append(severityEnd)
                    sb.append("(r=")
                    sb.append(radiusEnd)
                    sb.append(")")
                }
                sb.append("(d=")
                sb.append(length)
                sb.append(")")
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
                sb.append(section.radiusStart)
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
                    sb.append(section.radiusEnd)
                    sb.append(")")
                    sb.append(' ')
                }

                for (mod in section.modifiers) {
                    sb.append(' ')
                    sb.append(mod)
                }

                currentSeverity = section.severityEnd
            }

            sb.append("(d=")
            sb.append(sections.sumOf { it.length })
            sb.append(")")

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

            data object DontCut : Modifier
            data object Caution : Modifier
        }

        enum class Severity {
            HAIRPIN,
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

    /**
     * a standalone crest
     */
    data object Crest : InferredPacenoteItem {
        override fun toString(): String {
            return "crest"
        }
    }

    /**
     * a standalone dip
     */
    data object Dip : InferredPacenoteItem {
        override fun toString(): String {
            return "dip"
        }
    }

    data object FinishLine : InferredPacenoteItem {
        override fun toString(): String {
            return "over finish"
        }
    }

    /**
     * Additional information applicable to _any_ stretch of road.
     */
    interface SectionModifier {
        data object OverCrest : SectionModifier
        data object ThroughDip : SectionModifier
    }
}