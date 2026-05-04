package io.github.tmarsteel.flyingnarrator.ui

import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D

inline fun withTransform(g: Graphics2D, transform: AffineTransform, crossinline block: () -> Unit) {
    g.transform(transform)
    try {
        block()
    } finally {
        transform.invert()
        try {
            g.transform(transform)
        }
        finally {
            transform.invert()
        }
    }
}

fun Point2D.toPoint(): Point = Point(x.toInt(), y.toInt())