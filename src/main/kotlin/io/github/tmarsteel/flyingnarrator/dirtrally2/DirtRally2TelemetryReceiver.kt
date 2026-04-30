package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2TelemetryReceiver.TELEMETRY_STALL_TIMEOUT
import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2TelemetryReceiver.TELEMETRY_STOP_TIMEOUT
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2HardwareSettingsConfig
import io.github.tmarsteel.flyingnarrator.io.FileCache
import io.github.tmarsteel.flyingnarrator.io.InetSocketAddressSerializer
import kotlinx.serialization.Serializable
import java.io.BufferedOutputStream
import java.io.OutputStreamWriter
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Paths
import java.util.Collections
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@OptIn(ExperimentalAtomicApi::class)
object DirtRally2TelemetryReceiver {
    /**
     * @see Listener.onTelemetryReceptionEnded
     */
    val TELEMETRY_STOP_TIMEOUT = 1.seconds

    /**
     * If all telemetry received for this long is identical, [Listener.onTelemetryStalled] is called. When the first
     * different packet arrives again, [Listener.onTelemetryUnstalled] is called
     */
    val TELEMETRY_STALL_TIMEOUT = 100.milliseconds

    private val LOCALHOST = InetAddress.getByName("127.0.0.1")
    private val listenAddress: InetSocketAddress = run {
        val cached = FileCache.get<CacheData>("dr2.telemetry-race-progress")
        if (cached != null) {
            return@run cached.listen
        }

        val chosenAddress = try {
            DatagramSocket(20777, LOCALHOST).use {
                InetSocketAddress(it.localAddress, it.localPort)
            }
        } catch (_: BindException) {
            DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use {
                InetSocketAddress(it.localAddress, it.localPort)
            }
        }
        FileCache.set("dr2.telemetry-race-progress", CacheData(chosenAddress))
        chosenAddress
    }

    /**
     * True iff the game is currently running and needs to be restarted for telemetry to become available.
     */
    var gameRequiresRestart: Boolean = false
        private set
    init {
        val telemetryWasConfigured = assureTelemetryIsConfigured()
        if (!telemetryWasConfigured) {
            scanForGameProcess()?.also { gameProcess ->
                gameRequiresRestart = true
                gameProcess.onExit().whenComplete { _, _ ->
                    gameRequiresRestart = false
                }
            }
        }
    }

    private var monitoringThread = AtomicReference<Thread?>(null)

    private tailrec fun assureStarted() {
        val localThread = monitoringThread.load()
        if (localThread != null) {
            if (localThread.isAlive) {
                return
            }
        }

        val newThread = Thread(::monitoringThreadMain, "dirtrally2-monitor")
        newThread.isDaemon = true
        if (!monitoringThread.compareAndSet(localThread, newThread)) {
            return assureStarted()
        }

        newThread.start()
    }

    private fun stop() {
        monitoringThread.load()?.interrupt()
    }

    private val listeners = Collections.synchronizedSet(mutableSetOf<Listener>())
    fun addListener(listener: Listener) {
        val wasEmpty = listeners.isEmpty()
        if (listeners.add(listener) && wasEmpty) {
            assureStarted()
        }
    }
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            stop()
        }
    }
    private fun dispatch(action: (Listener) -> Unit) {
        listeners.forEach {
            try {
                action(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Volatile
    private lateinit var monitoringState: MonitorState
    private fun monitoringThreadMain() {
        val process = scanForGameProcess()
        monitoringState = if (process == null) {
            MonitorState.GameNotRunning
        } else {
            MonitorState.GameRunning(process, true)
        }

        while (!Thread.currentThread().isInterrupted) {
            monitoringState = monitoringState.takeControl()
        }
    }

    interface Listener {
        fun onGameRunning(wasJustStarted: Boolean)
        fun onGameStopped()

        /**
         * Is called when the first packet of telemetry arrives, or after [onTelemetryReceptionEnded] had been called
         * and telemetry is now arriving again. [onTelemetryReceived] is called for the same [TelemetryRecord] right
         * afterwards.
         */
        fun onTelemetryReceptionStarted(firstRecord: TelemetryRecord)

        /**
         * Called for every packet of telemetry received, possibly at high frequencies (upwards of 50Hz). Is not called
         * between [onTelemetryStalled] and [onTelemetryUnstalled].
         */
        fun onTelemetryReceived(record: TelemetryRecord)

        /**
         * Called when all telemetry packets that had been received for [TELEMETRY_STALL_TIMEOUT] were identical (this
         * is the case when the game is paused). After this function has been called, [onTelemetryReceived] will not be
         * called again until [onTelemetryUnstalled] has occured.
         */
        fun onTelemetryStalled()

        /**
         * Called after [onTelemetryStalled] when the next telemetry packet arrives that differs from the one that triggered
         * the stall (this is the case when the game is unpaused).
         */
        fun onTelemetryUnstalled()

        /**
         * If telemetry is not received for [TELEMETRY_STOP_TIMEOUT], [Listener.onTelemetryReceptionEnded] is called.
         * When new telemetry arrives again, [Listener.onTelemetryReceptionStarted] is called again.
         */
        fun onTelemetryReceptionEnded()
    }

    class TelemetryRecord(val data: FloatArray) {
        // detailed mapping already worked out in https://github.com/ErlerPhilipp/dr2_logger/blob/2b28e10/source/dirt_rally/udp_data.py#L121

        // all values 0 seem to indicate end of a stage or shakedown

        /**
         * becomes negative when entering the service area after shakedown
         */
        val distance: Float get()= data[2]

        /**
         * usually between 0 and 1, but jumps to almost 2 after the finish line
         */
        val progress: Float get()= data[3]

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TelemetryRecord) return false

            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    private sealed interface MonitorState {
        fun takeControl(): MonitorState

        object GameNotRunning : MonitorState {
            override fun takeControl(): MonitorState {
                while (true) {
                    val gameProcess = scanForGameProcess()
                    if (gameProcess != null) {
                        return GameRunning(gameProcess)
                    }
                    Thread.sleep(10000)
                }
            }
        }

        class GameRunning(val process: ProcessHandle, private val isInitialState: Boolean = false) : MonitorState {
            private var telemetryReceptionOngoing = false
            private var telemetryStalled = false
            override fun takeControl(): MonitorState {
                dispatch { it.onGameRunning(!isInitialState) }

                process.onExit().whenComplete { _, _ ->
                    monitoringThread.load()?.interrupt()
                }

                val socket = DatagramSocket(listenAddress)
                socket.receiveBufferSize = 512
                socket.soTimeout = TELEMETRY_STOP_TIMEOUT.inWholeMilliseconds.toInt()

                val packet = DatagramPacket(ByteArray(2048), 512)
                telemetryReceptionOngoing = false
                telemetryStalled = false
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        socket.receive(packet)
                        handleTelemetryPacket(packet)
                    }
                    catch (_: SocketTimeoutException) {
                        if (telemetryReceptionOngoing) {
                            dispatch { it.onTelemetryReceptionEnded() }
                        }
                        telemetryReceptionOngoing = false
                    }
                    catch (_: InterruptedException) {
                        if (telemetryReceptionOngoing) {
                            dispatch { it.onTelemetryReceptionEnded() }
                        }
                        break
                    }
                }

                // DatagramSocket should close itself on interrupt
                if (!socket.isClosed) {
                    socket.close()
                }

                if (process.isAlive) {
                    return MonitoringStopped
                }

                dispatch { it.onGameStopped() }
                return GameNotRunning
            }

            var lastDistinctTelemetry: TelemetryRecord? = null
            var lastDistinctTelemetryReceivedAt: TimeSource.Monotonic.ValueTimeMark? = null
            private fun handleTelemetryPacket(packet: DatagramPacket) {
                val buffer = ByteBuffer.wrap(packet.data, packet.offset, packet.length)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                if (buffer.remaining() < 65) {
                    return
                }

                val floats = FloatArray(buffer.remaining())
                buffer.get(floats)

                val record = TelemetryRecord(floats)

                if (!telemetryReceptionOngoing) {
                    dispatch { it.onTelemetryReceptionStarted(record) }
                    telemetryReceptionOngoing = true
                }

                if (record != lastDistinctTelemetry) {
                    lastDistinctTelemetry = record
                    lastDistinctTelemetryReceivedAt = TimeSource.Monotonic.markNow()
                    if (telemetryStalled) {
                        dispatch { it.onTelemetryUnstalled() }
                        telemetryStalled = false
                    }
                } else {
                    val sinceLastDistinctTelemetry = lastDistinctTelemetryReceivedAt?.elapsedNow() ?: Duration.ZERO
                    if (sinceLastDistinctTelemetry >= TELEMETRY_STALL_TIMEOUT) {
                        if (!telemetryStalled) {
                            dispatch { it.onTelemetryStalled() }
                        }
                        telemetryStalled = true
                    }
                }

                if (!telemetryStalled) {
                    dispatch { it.onTelemetryReceived(record) }
                }
            }
        }

        object MonitoringStopped : MonitorState {
            override fun takeControl(): MonitorState {
                throw IllegalStateException("Monitoring thread was stopped")
            }
        }
    }

    @Serializable
    private data class CacheData(
        @Serializable(with = InetSocketAddressSerializer::class)
        val listen: InetSocketAddress,
    )

    private fun scanForGameProcess(): ProcessHandle? {
        return ProcessHandle.allProcesses()
            .filter { it.isAlive }
            .filter { it.info().command()?.orElse(null)?.let(Paths::get)?.fileName?.name == "dirtrally2.exe" }
            .findAny()
            .orElse(null)
    }

    /**
     * @return true iff telemetry was already properly configured when this method was called
     */
    private fun assureTelemetryIsConfigured(): Boolean {
        DR2HardwareSettingsConfig.readCurrent().use { dr2hwConfig ->
            val exportForThis = dr2hwConfig.telemetryExports.find(::canUse)

            if (exportForThis == null) {
                dr2hwConfig.addCustomTelemetryExport(true, 3, listenAddress.address, listenAddress.port.toUShort(), 10)
                return false
            }

            if (!exportForThis.isEnabled) {
                exportForThis.isEnabled = true
                return false
            }

            return true
        }
    }

    private fun canUse(telemetryExport: DR2HardwareSettingsConfig.TelemetryExport): Boolean {
        return telemetryExport.ip == listenAddress.address
            && telemetryExport.port == listenAddress.port.toUShort()
            && telemetryExport.extraData == 3
            && telemetryExport.delay <= 10
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (gameRequiresRestart) {
            println("!!!! the game needs to be restarted to enable telemetry !!!!")
            exitProcess(-1)
        }

        val outfile = Paths.get("dr2-telemetry-${System.currentTimeMillis() / 1000}.csv").toAbsolutePath()
        println("monitoring, writing to $outfile")

        val startedAt = TimeSource.Monotonic.markNow()
        val csvOut = outfile.outputStream()
            .let(::BufferedOutputStream)
            .let(::OutputStreamWriter)

        val listener = object : Listener {
            override fun onGameRunning(wasJustStarted: Boolean) {
                if (wasJustStarted) {
                    println("game started")
                } else {
                    println("game already running")
                }
            }

            override fun onGameStopped() {
                println("game stopped")
                csvOut.flush()
                csvOut.close()
                exitProcess(0)
            }

            override fun onTelemetryReceived(record: DirtRally2TelemetryReceiver.TelemetryRecord) {
                val time = startedAt.elapsedNow().inWholeMilliseconds
                csvOut.write("$time")
                for (p in record.data) {
                    csvOut.write(",$p")
                }
                csvOut.write("\n")
            }

            override fun onTelemetryReceptionStarted(firstRecord: TelemetryRecord) {
                println("run started")
            }

            override fun onTelemetryStalled() {
                println("paused")
            }

            override fun onTelemetryUnstalled() {
                println("resumed")
            }

            override fun onTelemetryReceptionEnded() {
                println("run ended")
            }
        }
        addListener(listener)

        while (true) {
            println("enter e to stop.")
            val cmd = readLine() ?: break
            if (cmd.trim() == "e") {
                removeListener(listener)
                break
            }
        }
    }
}