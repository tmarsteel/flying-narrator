package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2CoordinateSystem.toAppSystem
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2TrackProgressPosition
import io.github.tmarsteel.flyingnarrator.geometry.Vector3

/**
 *
 */
object DirtRally2CoordinateSystem {
    /**
     * @param vector a vector where the x/y plane is parallel to the ground and z indicates elevation
     * @return the same vector, as DiRT Rally 2 would have it
     */
    fun toGameSystem(vector: Vector3): Vector3 {
        return Vector3(vector.y, vector.z, vector.x)
    }

    /**
     * @param vector a vector in DiRT Rally 2s coordinate system
     * @return a vector where the x/y plane is parallel to the ground and z indicates elevation
     */
    fun toAppSystem(vector: Vector3): Vector3 {
        return Vector3(vector.z, vector.x, vector.y)
    }

    /**
     * see [toAppSystem] ([Vector3])
     */
    fun toAppSystem(position: DR2TrackProgressPosition): Vector3 {
        return Vector3(position.z, position.x, position.y)
    }
}