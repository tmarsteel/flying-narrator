package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.codriver.CodriverRouteInfo
import io.github.tmarsteel.flyingnarrator.codriver.GameObserver
import io.github.tmarsteel.flyingnarrator.io.FlyingNarratorJsonFormat
import io.github.tmarsteel.flyingnarrator.ui.LocalizedString
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Paths
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.io.path.inputStream

/**
 * A [GameObserver] based on [DirtRally2TelemetryReceiver]
 */
object DirtRally2TelemetryGameObserver : GameObserver {
    private val listenersMutex = Any()
    private val listeners = IdentityHashMap<GameObserver.Listener, DirtRally2TelemetryReceiver.Listener>()
    override fun addListener(listener: GameObserver.Listener) {
        synchronized(listenersMutex) {
            if (listener in listeners) {
                return
            }
            val adapter = ListenerAdapter(listener)
            listeners.putIfAbsent(listener, adapter)
            DirtRally2TelemetryReceiver.addListener(adapter)
        }
    }

    override fun removeListener(listener: GameObserver.Listener) {
        synchronized(listenersMutex) {
            val adapter = listeners[listener] ?: return
            DirtRally2TelemetryReceiver.removeListener(adapter)
            listeners.remove(listener)
        }
    }

    private class ListenerAdapter(
        val delegate: GameObserver.Listener,
    ) : DirtRally2TelemetryReceiver.Listener {
        override fun onTelemetryReceptionStarted(firstRecord: DirtRally2TelemetryReceiver.TelemetryRecord) {
            // TODO: actually determine the track from telemetry
            val routeInfo = CodriverRouteInfo(
                "dirt-rally-2",
                "germany_01_route_0",
                LocalizedString(
                    mapOf(
                        Locale.ENGLISH to "Oberstein",
                    )
                ),
                11487.19.meters,
                Paths.get("dr2-tracks/germany/01_00.speedmap.json").inputStream().use {
                    FlyingNarratorJsonFormat.decodeFromStream(it)
                },
            )
            delegate.onRunStarting(routeInfo)
        }

        override fun onTelemetryReceived(record: DirtRally2TelemetryReceiver.TelemetryRecord) {
            delegate.onRunProgress(record.progress.toDouble())
        }

        override fun onTelemetryStalled() {
            delegate.onGamePaused()
        }

        override fun onTelemetryUnstalled() {
            delegate.onGameResumed()
        }

        override fun onTelemetryReceptionEnded() {
            delegate.onStageEnded()
        }

        override fun onGameRunning(wasJustStarted: Boolean) {
            // not relevant
        }

        override fun onGameStopped() {
            // not relevant
        }
    }
}