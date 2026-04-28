package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2HardwareSettingsConfig
import io.github.tmarsteel.flyingnarrator.io.FileCache
import io.github.tmarsteel.flyingnarrator.io.InetSocketAddressSerializer
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class TelemetryRaceProgressMonitor(
    val listenAddress: InetSocketAddress,
) : AutoCloseable {
    constructor() : this(getDefaultListenAddress())

    private var closed = false
    private val socket = DatagramSocket(listenAddress)

    init {
        DR2HardwareSettingsConfig.readCurrent().use { dr2hwConfig ->
            var exportForThis = dr2hwConfig.telemetryExports.find { it.ip == listenAddress.address && it.port == listenAddress.port.toUShort() }
            if (exportForThis == null) {
                if (DirtRally2GameObserver.currentGameProcess != null) {
                    close()
                    throw IOException("Telemetry is not enabled for $listenAddress and the game is running. Stop the game, try again and then start the game.")
                }

                exportForThis = dr2hwConfig.addCustomTelemetryExport(true, 3, listenAddress.address, listenAddress.port.toUShort(), 10)
            } else if (!exportForThis.isEnabled) {
                if (DirtRally2GameObserver.currentGameProcess != null) {
                    close()
                    throw IOException("Telemetry is not enabled for $listenAddress and the game is running. Stop the game, try again and then start the game.")
                }

                exportForThis.isEnabled = true
            }
        }
    }

    private val listeners = mutableSetOf<TelemetryListener>()
    fun addProgressListener(listener: TelemetryListener) {
        listeners.add(listener)
    }
    fun removeProgressListener(listener: TelemetryListener) {
        listeners.remove(listener)
    }
    fun start() {}

    private val thread = Thread(::receiverThreadMain, "dr2-telemetry-receiver")
    init {
        thread.isDaemon = true
        thread.start()
    }

    private fun receiverThreadMain() {
        socket.receiveBufferSize = 2048

        val packet = DatagramPacket(ByteArray(2048), 512)
        while (!Thread.currentThread().isInterrupted) {
            try {
                socket.receive(packet)
                handleTelemetryPacket(packet)
            }
            catch (_: InterruptedException) {
                break
            }
        }

        // DatagramSocket should close itself on interrupt
        if (!socket.isClosed) {
            socket.close()
        }
    }

    private fun handleTelemetryPacket(packet: DatagramPacket) {
        val buffer = ByteBuffer.wrap(packet.data, packet.offset, packet.length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
        if (buffer.remaining() < 65) {
            return
        }

        listeners.forEach {
            it.onTelemetryReceived(TelemetryRecord(buffer))
        }
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        thread.interrupt()
    }

    data class TelemetryRecord(val data: FloatBuffer) {
        // detailed mapping already worked out in https://github.com/ErlerPhilipp/dr2_logger/blob/2b28e10/source/dirt_rally/udp_data.py#L121

        // all values 0 seem to indicate end of a stage or shakedown

        /**
         * becomes negative when entering the service area after shakedown
         */
        val distance: Float get()= data.get(2)

        /**
         * usually between 0 and 1, but jumps to almost 2 after the finish line
         */
        val progress: Float get()= data.get(3)
    }

    interface TelemetryListener {
        fun onTelemetryReceived(record: TelemetryRecord)
    }

    @Serializable
    private data class CacheData(
        @Serializable(with = InetSocketAddressSerializer::class)
        val listen: InetSocketAddress,
    )

    companion object {
        private val LOCALHOST = InetAddress.getByName("127.0.0.1")
        private fun getDefaultListenAddress(): InetSocketAddress {
            val cached = FileCache.get<CacheData>("dr2.telemetry-race-progress")
            if (cached != null) {
                return cached.listen
            }

            val chosenAddress = try {
                DatagramSocket(20777, LOCALHOST).use {
                    InetSocketAddress(it.localAddress, it.localPort)
                }
            } catch (e: BindException) {
                DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use {
                    InetSocketAddress(it.localAddress, it.localPort)
                }
            }
            FileCache.set("dr2.telemetry-race-progress", CacheData(chosenAddress))
            return chosenAddress
        }
    }
}