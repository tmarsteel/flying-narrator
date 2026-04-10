package io.github.tmarsteel.flyingnarrator.feature

import kotlin.math.absoluteValue
import kotlin.math.sign

internal sealed interface FeatureDetectionState {
    fun process(buffer: ArrayDeque<TrackSegment>, features: MutableList<Feature>): FeatureDetectionState
    fun finish(buffer: ArrayDeque<TrackSegment>, features: MutableList<Feature>)

    object Straight : FeatureDetectionState {
        override fun process(
            buffer: ArrayDeque<TrackSegment>,
            features: MutableList<Feature>
        ): FeatureDetectionState {
            val cornerEntryIdx = detectCornerEntry(buffer)
            if (cornerEntryIdx < 0) {
                return this
            }

            if (cornerEntryIdx != 0) {
                val straightSegments = buffer.subList(0, cornerEntryIdx).toList()
                features += Feature.Straight(straightSegments)
                repeat(straightSegments.size) {
                    buffer.removeFirst()
                }
            }

            return Corner(buffer.first().turnyness.sign)
        }

        private fun detectCornerEntry(buffer: ArrayDeque<TrackSegment>): Int {
            return buffer.indexOfLast { it.turnyness.absoluteValue >= CORNER_SEVERITY_THRESHOLD }
        }

        override fun finish(
            buffer: ArrayDeque<TrackSegment>,
            features: MutableList<Feature>
        ) {
            features += Feature.Straight(buffer.toList())
            buffer.clear()
        }
    }

    class Corner(val severitySign: Double) : FeatureDetectionState {
        override fun process(
            buffer: ArrayDeque<TrackSegment>,
            features: MutableList<Feature>
        ): FeatureDetectionState {
            val straightStartsAtIndex = detectCornerExitToStraight(buffer)
            if (straightStartsAtIndex >= 0) {
                if (straightStartsAtIndex != 0) {
                    val cornerSegments = buffer.subList(0, straightStartsAtIndex).toList()
                    features += Feature.Corner(cornerSegments)
                    repeat(cornerSegments.size) {
                        buffer.removeFirst()
                    }
                }

                return Straight
            }

            val cornerDirectionChangesAtIndex = detectCornerDirectionChange(buffer)
            if (cornerDirectionChangesAtIndex >= 0) {
                if (cornerDirectionChangesAtIndex != 0) {
                    val cornerSegments = buffer.subList(0, cornerDirectionChangesAtIndex).toList()
                    features += Feature.Corner(cornerSegments)
                    repeat(cornerSegments.size) {
                        buffer.removeFirst()
                    }
                }

                return Corner(-severitySign)
            }

            return this
        }

        override fun finish(
            buffer: ArrayDeque<TrackSegment>,
            features: MutableList<Feature>
        ) {
            features += Feature.Corner(buffer.toList())
            buffer.clear()
        }

        private fun detectCornerExitToStraight(buffer: ArrayDeque<TrackSegment>): Int {
            return buffer.indexOfLast { it.turnyness.absoluteValue < CORNER_SEVERITY_THRESHOLD }
        }

        private fun detectCornerDirectionChange(buffer: ArrayDeque<TrackSegment>): Int {
            return buffer.indexOfLast { it.turnyness.sign == -severitySign }
        }
    }
}