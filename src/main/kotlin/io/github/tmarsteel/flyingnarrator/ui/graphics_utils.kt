package io.github.tmarsteel.flyingnarrator.ui

import java.awt.Graphics2D
import java.awt.geom.AffineTransform

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