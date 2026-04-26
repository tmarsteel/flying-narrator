package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import io.github.tmarsteel.flyingnarrator.WindowsRegistry
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.FileWriter
import java.lang.AutoCloseable
import java.net.InetAddress
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class DR2HardwareSettingsConfig(
    private val path: Path,
) : AutoCloseable {
    private val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(path.toFile())

    private val motionPlatformEl: Element
    private val _telemetryExports: MutableList<TelemetryExport>
    init {
        val root = document.documentElement
        if (root.nodeName != "hardware_settings_config") {
            throw IllegalStateException("Root node is not hardware_settings_config")
        }

        var localMotionPlatformEl = root.childNodes.asSequence()
            .find { it.nodeName == "motion_platform" }
        if (localMotionPlatformEl !is Element?) {
            throw IllegalStateException("motion_platform is not a tag")
        }
        if (localMotionPlatformEl == null) {
            localMotionPlatformEl = document.createElement("motion_platform")
            root.appendChild(localMotionPlatformEl)
        }

        motionPlatformEl = localMotionPlatformEl
        _telemetryExports = localMotionPlatformEl.childNodes.asSequence()
            .filterIsInstance<Element>()
            .filter { it.nodeName == "custom_udp" || it.nodeName == "udp" }
            .map(::TelemetryExportImpl)
            .toMutableList()
    }

    val telemetryExports: List<TelemetryExport> = _telemetryExports

    fun addCustomTelemetryExport(
        enabled: Boolean,
        extraData: Int,
        ip: InetAddress,
        port: UShort,
        delay: Int,
    ) {
        checkNotClosed()

        val element = document.createElement("custom_udp")
        element.setAttribute("enabled", enabled.toString())
        element.setAttribute("extra_data", extraData.toString())
        element.setAttribute("ip", ip.hostAddress)
        element.setAttribute("port", port.toString())
        element.setAttribute("delay", delay.toString())
        motionPlatformEl.appendChild(element)
        _telemetryExports.add(TelemetryExportImpl(element))
    }

    interface TelemetryExport {
        val isCustom: Boolean
        var isEnabled: Boolean
        val extraData: Int
        val ip: InetAddress
        val port: UShort
        val delay: Int
    }

    private class TelemetryExportImpl(
        val node: Element,
    ) : TelemetryExport {
        override val isCustom get()= node.nodeName == "custom_udp"
        override var isEnabled
            get()= node.getAttribute("enabled") == "true"
            set(value) {
                node.setAttribute("enabled", if (value) "true" else "false")
            }
        override val extraData get()= node.getAttribute("extradata").takeUnless { it.isBlank() }?.toInt() ?: 0
        override val ip get()= InetAddress.getByName(node.getAttribute("ip"))
        override val port get()= node.getAttribute("port").toUShort()
        override val delay get()= node.getAttribute("delay").toInt()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TelemetryExportImpl

            if (isCustom != other.isCustom) return false
            if (isEnabled != other.isEnabled) return false
            if (extraData != other.extraData) return false
            if (delay != other.delay) return false
            if (ip != other.ip) return false
            if (port != other.port) return false

            return true
        }

        override fun hashCode(): Int {
            var result = isCustom.hashCode()
            result = 31 * result + isEnabled.hashCode()
            result = 31 * result + extraData
            result = 31 * result + delay
            result = 31 * result + (ip?.hashCode() ?: 0)
            result = 31 * result + port.hashCode()
            return result
        }

        override fun toString(): String {
            return "TelemetryExport(isCustom=$isCustom, isEnabled=$isEnabled, extraData=$extraData, ip=$ip, port=$port, delay=$delay)"
        }
    }

    private var closed = false
    private fun checkNotClosed() {
        if (closed) {
            throw IllegalStateException("This object is closed")
        }
    }

    override fun close() {
        if (closed) {
            return
        }

        closed = true
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.VERSION, "1.0")
            setOutputProperty(OutputKeys.INDENT, "yes")
        }

        transformer.transform(DOMSource(document), StreamResult(FileWriter(path.toFile())))
    }

    companion object {
        private val documentDir by lazy {
            WindowsRegistry.query(
                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders",
                "Personal",
            )
                .singleOrNull()
                ?.values
                ?.getValue("Personal")
                ?.let {
                    (it as? WindowsRegistry.RegExpandSz)?.value
                        ?: (it as? WindowsRegistry.RegSz)?.value
                }
                ?.let(Paths::get)
                ?: throw IllegalStateException("Could not find the users document directory")
        }

        fun readCurrent(): DR2HardwareSettingsConfig {
            return DR2HardwareSettingsConfig(
                documentDir
                    .resolve("My Games")
                    .resolve("DiRT Rally 2.0")
                    .resolve("hardwaresettings")
                    .resolve("hardware_settings_config.xml")
            )
        }

        private fun NodeList.asSequence(): Sequence<Node> = (0 until length).asSequence().map(::item)
    }
}

fun main() {
    val config = DR2HardwareSettingsConfig.readCurrent()
    println(config.telemetryExports)
}