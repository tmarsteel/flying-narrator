package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.MutableSignal
import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.route.LocationOnRoute
import io.github.tmarsteel.flyingnarrator.route.Route

class FeatureAnnotationViewModel(
    val route: Route,
) {
    val corners = mutableSignalOf<Set<CornerModel>>(emptySet())
    val obstacles = mutableSignalOf<Set<ObstacleModel>>(emptySet())

    fun makeCornerModel(corner: Feature.Corner): CornerModel = CornerModel(
        mutableSignalOf(route.segments.indexOf(corner.segments.first())),
        mutableSignalOf(route.segments.indexOf(corner.segments.last())),
        mutableSignalOf(false),
    )

    class CornerModel(
        val indexOfFirstSegment: MutableSignal<Int>,
        val indexOfLastSegment: MutableSignal<Int>,
        val touchedOrManuallyAdded: MutableSignal<Boolean>,
    ) {
        val segmentIndices: Signal<IntRange> = combine(indexOfFirstSegment, indexOfLastSegment, ::IntRange)
    }

    class ObstacleModel(
        val location: MutableSignal<LocationOnRoute>,
        val touchedOrManuallyAdded: MutableSignal<Boolean>,
        val type: Type,
    ) {
        override fun toString(): String = "ObstacleModel(location=${location.value}, type=$type)"

        sealed interface Type {
            val displayName: String
                get() = this::class.simpleName ?: "<?>"

            object Crest : Type
            object Dip : Type
            object Jump : Type
            object Narrows : Type
            object Widens : Type
            class Chicane(
                val entrySide: MutableSignal<EntrySide> = mutableSignalOf(EntrySide.UNSPECIFIED)
            ) : Type {
                enum class EntrySide {
                    LEFT,
                    RIGHT,
                    UNSPECIFIED,
                    ;
                }
            }
        }
    }
}