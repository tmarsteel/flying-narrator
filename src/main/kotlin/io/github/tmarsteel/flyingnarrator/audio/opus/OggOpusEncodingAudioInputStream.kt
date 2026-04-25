package io.github.tmarsteel.flyingnarrator.audio.opus

import com.sun.jna.ptr.PointerByReference
import io.github.tmarsteel.flyingnarrator.audio.opus.OpusAudioFormat.SAMPLE_RATE_TO_BANDWIDTH_CONSTANT
import org.gagravarr.ogg.OggFile
import org.gagravarr.opus.OpusAudioData
import org.gagravarr.opus.OpusInfo
import org.gagravarr.opus.OpusTags
import tomp2p.opuswrapper.Opus
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.util.Arrays
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * @see https://wiki.xiph.org/OggOpus
 */
class OggOpusEncodingAudioInputStream private constructor(
    val pcmStream: AudioInputStream,
    validatedFormat: AudioFormat,
    val encoding: OggOpusEncoding,
    val alsoCloseBase: Boolean,
) : AudioInputStream(nullInputStream(), validatedFormat, pcmStream.frameLength) {
    constructor(
        pcmStream: AudioInputStream,
        format: AudioFormat,
        alsoCloseBase: Boolean = true,
    ) : this(
        pcmStream,
        format,
        (format.encoding as? OggOpusEncoding
            ?: throw IllegalArgumentException("initialTargetFormat must be an ${OggOpusEncoding::class.simpleName}"))
            .concretizeForEncoding(),
        alsoCloseBase
    )

    private val _format: AudioFormat = validatedFormat

    init {
        require(canEncode(pcmStream.format))
    }

    @Volatile
    private var closed = false
    private val nativeEncoder: PointerByReference?

    /** This many samples in the output are not relevant/can be discarded by the decoder */
    val lookeheadSampleCount: Int

    init {
        assureOpusNativeLibraryIsLoadable()
        val error = IntBuffer.allocate(1)
        nativeEncoder = Opus.INSTANCE.opus_encoder_create(_format.sampleRate.toInt(), _format.channels, encoding.application.value, error)
        throwOnOpusError(error.get())
        check(nativeEncoder != null && nativeEncoder.value != null)
        throwOnOpusError(Opus.INSTANCE.opus_encoder_ctl(nativeEncoder, Opus.OPUS_SET_BITRATE_REQUEST, encoding.bitsPerSecond))
        throwOnOpusError(Opus.INSTANCE.opus_encoder_ctl(nativeEncoder, Opus.OPUS_SET_COMPLEXITY_REQUEST, encoding.complexity))
        throwOnOpusError(Opus.INSTANCE.opus_encoder_ctl(nativeEncoder, Opus.OPUS_SET_SIGNAL_REQUEST, encoding.signal.value))
        throwOnOpusError(Opus.INSTANCE.opus_encoder_ctl(nativeEncoder, Opus.OPUS_SET_MAX_BANDWIDTH_REQUEST, SAMPLE_RATE_TO_BANDWIDTH_CONSTANT[_format.sampleRate] ?: error("sample rate passed validation when it shouldn't have")))
        val lookahead = IntBuffer.allocate(1)
        Opus.INSTANCE.opus_encoder_ctl(nativeEncoder, Opus.OPUS_GET_LOOKAHEAD_REQUEST, lookahead)
        lookeheadSampleCount = lookahead.get()
    }

    var maxEncodedFrameSizeBytes: Int = encoding.maxEncodedFrameSizeInBytes
        set(value) {
            require(value > 0)
            field = value
        }

    /** opus requires frame size as the number of samples per channel */
    private val opusFrameSizeInSamples: Int get() = frameSizeToSamples(_format, encoding.opusFrameSize)
    private val opusFrameSizeInBytes: Int get() = frameSizeToBytes(_format, encoding.opusFrameSize)
    private val encoderOutputBuffer = ByteBuffer.allocateDirect(maxEncodedFrameSizeBytes.coerceAtMost(opusFrameSizeInBytes))
    init {
        encoderOutputBuffer.clear()
        encoderOutputBuffer.limit(encoderOutputBuffer.position()) // start with an empty output buffer
    }
    private val encoderInputBufferArray = ByteArray(opusFrameSizeInBytes)
    private val encoderInputBuffer = ByteBuffer.allocateDirect(encoderInputBufferArray.size)
    private val encoderInputBufferAsShortBuffer = encoderInputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    private var sourceIsEof = false

    private val oggEncodingBuffer = Sponge(8192)
    private val oggFileHandle = OggFile(oggEncodingBuffer.outputStream)
    private val oggPacketWriter = oggFileHandle.getPacketWriter()
    init {
        oggPacketWriter.bufferPacket(OpusInfo().apply {
            numChannels = format.channels
            setSampleRate(format.sampleRate.toLong())
            preSkip = lookeheadSampleCount
        }.write())
        oggPacketWriter.flush() // this is CRUCIAL, otherwise players will refuse the file!
        oggPacketWriter.bufferPacket(OpusTags().write())
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        checkNotClosed()

        var writeHead = off
        val writeLimit = off + len
        while (writeHead < writeLimit && (oggEncodingBuffer.inputStream.available() > 0 || !sourceIsEof)) {
            val nOggEncodedBytesRead = oggEncodingBuffer.inputStream.read(b, writeHead, writeLimit - writeHead)
            if (nOggEncodedBytesRead > 0) {
                writeHead += nOggEncodedBytesRead
                continue
            }

            val nPcmBytes = pcmStream.read(encoderInputBufferArray, 0, encoderInputBufferArray.size)
            if (nPcmBytes < encoderInputBufferArray.size) {
                sourceIsEof = true
                // fill silence
                Arrays.fill(encoderInputBufferArray, nPcmBytes, encoderInputBufferArray.size, 0)
            }

            // this copy is necessary because of API mismatch: the input stream wants to write to a byte[],
            // but JNA wants a directly allocated ShortBuffer
            encoderInputBufferAsShortBuffer.clear()
            encoderInputBuffer.clear()
            encoderInputBuffer.put(encoderInputBufferArray, 0, encoderInputBufferArray.size)

            encoderOutputBuffer.clear()
            val encodedLength = Opus.INSTANCE.opus_encode(nativeEncoder, encoderInputBufferAsShortBuffer, opusFrameSizeInSamples, encoderOutputBuffer, encoderOutputBuffer.capacity())
            if (encodedLength < 0) {
                throwOnOpusError(encodedLength)
            }
            encoderOutputBuffer.position(0)
            encoderOutputBuffer.limit(encodedLength)

            val packetData = ByteArray(encodedLength)
            encoderOutputBuffer.get(packetData)
            val opusAudio = OpusAudioData(packetData)
            oggPacketWriter.setGranulePosition(oggPacketWriter.currentGranulePosition + nPcmBytes / (format.sampleSizeInBits / 8))
            oggPacketWriter.bufferPacket(opusAudio.write())
            oggPacketWriter.flush()
        }

        val nBytesRead = writeHead - off
        if (nBytesRead == 0 && sourceIsEof) {
            return -1
        }
        return nBytesRead
    }

    override fun skip(n: Long): Long {
        if (n == 0L) {
            return n
        }
        val dummy = ByteArray(1024)
        var skipped = 0L
        while (skipped < n) {
            val nBytes = read(dummy, 0, (n - skipped).toInt().coerceAtMost(dummy.size))
            if (nBytes < 0) {
                return skipped
            }
            skipped += nBytes
        }
        return skipped
    }

    override fun read(): Int {
        val buf = ByteArray(1)
        val n = read(buf, 0, 1)
        if (n == 0) {
            return -1
        }

        return buf[0].toInt()
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun available(): Int {
        return encoderOutputBuffer.remaining()
    }

    override fun mark(readlimit: Int) {
        throw IOException("mark/reset not supported")
    }

    override fun reset() {
        throw IOException("mark/reset not supported")
    }

    override fun markSupported(): Boolean {
        return false
    }

    private fun checkNotClosed() {
        if (closed) {
            throw IOException("Stream is closed")
        }
    }

    override fun close() {
        if (closed) {
            return
        }

        if (nativeEncoder != null && nativeEncoder.value != null) {
            Opus.INSTANCE.opus_encoder_destroy(nativeEncoder)
        }

        if (alsoCloseBase) {
            pcmStream.close()
        }
    }

    companion object {
        fun frameSizeToSamples(audioFormat: AudioFormat, opusFrameSize: Duration): Int {
            return Math.toIntExact(audioFormat.sampleRate.toLong() * opusFrameSize.toLong(DurationUnit.NANOSECONDS) / 1.seconds.toLong(DurationUnit.NANOSECONDS))
        }

        fun frameSizeToBytes(audioFormat: AudioFormat, opusFrameSize: Duration): Int {
            return frameSizeToSamples(audioFormat, opusFrameSize) * audioFormat.channels * 2
        }

        /**
         * @return the reason why [format] cannot be encoded with opus, or `null` if it _can_ be.
         */
        fun getReasonFormatCannotBeEncoded(format: AudioFormat): String? {
            if (format.isBigEndian) {
                return "Must be little-endian"
            }

            if (format.encoding != AudioFormat.Encoding.PCM_SIGNED) {
                return "Must be PCM_SIGNED encoded"
            }

            if (format.sampleSizeInBits != 16) {
                return "Bit-depth must be 16"
            }

            if (format.channels !in OpusAudioFormat.SUPPORTED_CHANNELS) {
                return "Must be one or two channels"
            }

            if (format.sampleRate !in SAMPLE_RATE_TO_BANDWIDTH_CONSTANT) {
                return "Opus can only encode audio with one of these sample rates: ${SAMPLE_RATE_TO_BANDWIDTH_CONSTANT.keys}; use AudioSystem.getAudioInputStream(OpusAudioFormat.closestEncodableFormat(format), stream)"
            }

            return null
        }

        fun canEncode(format: AudioFormat): Boolean {
            return getReasonFormatCannotBeEncoded(format) == null
        }
    }
}