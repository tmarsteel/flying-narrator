package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import com.formdev.flatlaf.ui.FlatUIUtils
import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.fenrur.signal.operators.flatMap
import io.github.fenrur.signal.operators.increment
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.editor.ReactiveRouteComponentChild
import io.github.tmarsteel.flyingnarrator.editor.RouteViewModel
import io.github.tmarsteel.flyingnarrator.editor.transform
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import javax.swing.UIManager
import kotlin.math.roundToInt

/**
 * Provides a grabbable(draggable) "handle" for a single point on the route. The location is obtained from [locationOnRoute]
 * and published back into it.
 */
open class LocationOnRouteComponent(
    val locationOnRoute: Signal<RouteViewModel.PreciseLocation>,
) : ReactiveRouteComponentChild() {
    private val resizeEvents = mutableSignalOf(0L)

    /**
     * converts from screen coordinate space of this component to route coordinate space
     */
    protected val selfRouteTransform: Signal<AffineTransform>
    init {
        setSize(16, 16)

        val targetSelfLocation = combine(
            locationOnRoute,
            parentRouteComponent.flatMap { pc -> pc?.routeTransform ?: signalOf(AffineTransform()) },
            resizeEvents,
        ) { routeLocation, routeTransform, _ ->
            val locationAsDouble = routeTransform.transform(routeLocation.point, null)
            Pair(
                Point(locationAsDouble.x.roundToInt() - width / 2, locationAsDouble.y.roundToInt() - height / 2),
                routeTransform
            )
        }
        targetSelfLocation.subscribeOn(lifecycle) {
            location = it.first
        }
        locationOnRoute.subscribeOn(lifecycle) {
            repaint()
        }
        selfRouteTransform = targetSelfLocation.map { (pt, transform) ->
            AffineTransform().apply {
                translate(-pt.x.toDouble(), -pt.y.toDouble())
                concatenate(transform)
            }
        }

        addComponentListener(object : ComponentListener {
            override fun componentResized(e: ComponentEvent) {
                resizeEvents.increment()
            }

            override fun componentMoved(e: ComponentEvent?) {
                // nothing to do
            }

            override fun componentShown(e: ComponentEvent?) {
                // nothing to do
            }

            override fun componentHidden(e: ComponentEvent?) {
                // nothing to do
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        g as Graphics2D

        FlatUIUtils.setRenderingHints(g)
        g.translate(BORDER_STROKE.lineWidth.toDouble(), BORDER_STROKE.lineWidth.toDouble())
        g.scale(width / (SHAPE.width + BORDER_STROKE.lineWidth * 2.0), height / (SHAPE.height + BORDER_STROKE.lineWidth * 2.0))
        g.color = UIManager.getColor(COLOR)
        g.fill(SHAPE)
        g.stroke = BORDER_STROKE
        g.color = UIManager.getColor(BORDER_COLOR)
        g.draw(SHAPE)
    }

    companion object {
        val SHAPE = Ellipse2D.Double(0.0, 0.0, 10.0, 10.0)
        val BORDER_STROKE = BasicStroke(2f)
        val BORDER_COLOR = "${LocationOnRouteComponent::class.simpleName}.borderColor"
        val COLOR = "${LocationOnRouteComponent::class.simpleName}.color"
    }
}