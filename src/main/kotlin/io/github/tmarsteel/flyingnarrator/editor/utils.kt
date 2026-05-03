package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D

fun AffineTransform.transform(point: Vector3, dst: Point2D?): Point2D {
    return transform(Point2D.Double(point.x, point.y), dst)
}