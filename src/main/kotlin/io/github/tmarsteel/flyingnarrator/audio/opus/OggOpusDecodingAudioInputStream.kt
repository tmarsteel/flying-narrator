package io.github.tmarsteel.flyingnarrator.audio.opus

import com.sun.jna.ptr.PointerByReference
import okhttp3.internal.closeQuietly
import org.gagravarr.ogg.OggFile
import org.gagravarr.ogg.OggStreamIdentifier
import org.gagravarr.opus.OpusAudioData
import org.gagravarr.opus.OpusFile
import org.gagravarr.opus.OpusInfo
import tomp2p.opuswrapper.Opus
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.IntBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * construct new instances using [OggOpusDecodingAudioInputStream.Companion.peek]
 */
class OggOpusDecodingAudioInputStream private constructor(
    private val opusFile: OpusFile,
    targetFormat: AudioFormat,
    duration: Duration?,
) : AudioInputStream(nullInputStream(), targetFormat, durationToFrameLength(duration, targetFormat)) {
    @Volatile
    private var closed = false
    private val nativeDecoder: PointerByReference?

    private val sourcePackets = sequence {
        while (true) {
            yield(opusFile.nextAudioPacket ?: break)
        }
    }.iterator()

    init {
        assureOpusNativeLibraryIsLoadable()
        val error = IntBuffer.allocate(1)
        nativeDecoder = Opus.INSTANCE.opus_decoder_create(format.sampleRate.toInt(), format.channels, error)
        throwOnOpusError(error.get())
        check(nativeDecoder != null && nativeDecoder.value != null)
    }

    private val decoderOutputBuffer = ByteBuffer.allocateDirect(2 * format.channels * 48 * 60)
    private val decoderOutputBufferShortView = decoderOutputBuffer.asShortBuffer()
    private var sourceIsEof = false
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        checkNotClosed()

        var writeHead = off
        val writeLimit = off + len
        while (writeHead < writeLimit && (decoderOutputBuffer.hasRemaining()|| !sourceIsEof)) {
            val nBytesInOutputBuffer = decoderOutputBuffer.remaining()
            if (nBytesInOutputBuffer > 0) {
                val nBytesToCopy = (writeLimit - writeHead).coerceAtMost(nBytesInOutputBuffer)
                decoderOutputBuffer.get(b, writeHead, nBytesToCopy)
                writeHead += nBytesToCopy
                continue
            }

            if (!sourcePackets.hasNext()) {
                sourceIsEof = true
                break
            }

            val nextPacket = sourcePackets.next()
            val anticipatedDecodedDataSize = nextPacket.numberOfFrames * format.frameSize
            check(anticipatedDecodedDataSize <= decoderOutputBuffer.capacity())
            decoderOutputBufferShortView.clear()
            val nSamplesDecoded = Opus.INSTANCE.opus_decode(nativeDecoder, nextPacket.data, nextPacket.data.size, decoderOutputBufferShortView, decoderOutputBufferShortView.capacity() / format.channels, 0)
            if (nSamplesDecoded < 0) {
                throwOnOpusError(nSamplesDecoded)
            }
            decoderOutputBuffer.position(0)
            decoderOutputBuffer.limit(nSamplesDecoded * format.channels * 2)
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

    override fun available(): Int {
        return decoderOutputBuffer.remaining()
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
        closed = true

        if (nativeDecoder != null && nativeDecoder.value != null) {
            Opus.INSTANCE.opus_decoder_destroy(nativeDecoder)
        }

        if (opusFile.oggFile != null) {
            opusFile.close()
        }
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

    override fun read(): Int {
        val dummy = ByteArray(1)
        val nBytes = super.read(dummy, 0, 1)
        if (nBytes < 0) {
            return -1
        }
        return dummy[0].toInt()
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    sealed interface PeekedStream {
        fun close()

        data class Unsupported(
            val reason: String?,
            private val closeAction: () -> Unit,
        ) : PeekedStream {
            var closed = false
            override fun close() {
                if (closed) {
                    return
                }
                closed = true
                closeAction()
            }
        }
        data class Supported(
            val sourceFormat: AudioFormat,
            val opusFile: OpusFile,
            val duration: Duration?,
        ) : PeekedStream {
            private var consumed = false
            private var closed = false
            fun toStream(targetFormat: AudioFormat? = null): OggOpusDecodingAudioInputStream {
                val decodedFormat = getDecodedFormat(sourceFormat, targetFormat)
                check(!consumed)
                check(!closed)
                consumed = true
                return OggOpusDecodingAudioInputStream(opusFile, decodedFormat, duration)
            }

            override fun close() {
                if (closed) {
                    return
                }
                check(!consumed)

                closed = true
                consumed = true
                opusFile.close()
            }
        }
    }

    companion object {
        fun peek(reReadable: () -> InputStream): PeekedStream {
            return peekInternal(reReadable, true)
        }

        fun peek(inputStream: InputStream): PeekedStream {
            return peekInternal({ inputStream }, false)
        }

        fun peekInternal(getInputStream: () -> InputStream, canGetMultipleStreams: Boolean): PeekedStream {
            val firstStream = getInputStream()
            val firstOpusFile = try {
                OpusFile(OggFile(firstStream).packetReader)
            } catch (e: IllegalArgumentException) {
                return PeekedStream.Unsupported(e.message, firstStream::close)
            } catch (e: IOException) {
                if (e.message?.startsWith("Next ogg packet header not found") == true) {
                    return PeekedStream.Unsupported("This file seems not to contain an ogg container", firstStream::close)
                }

                throw e
            }

            if (firstOpusFile.type.kind != OggStreamIdentifier.OggStreamType.Kind.AUDIO) {
                return PeekedStream.Unsupported("This ogg file does not contain audio data", firstStream::close)
            }

            val duration: Duration?
            val actualOpusFile: OpusFile
            if (canGetMultipleStreams) {
                duration = firstOpusFile.consumeReadingAudioLength()
                if (firstOpusFile.oggFile != null) {
                    firstOpusFile.closeQuietly()
                }
                actualOpusFile = OpusFile(OggFile(getInputStream()))
            } else {
                duration = null
                actualOpusFile = firstOpusFile
            }

            return PeekedStream.Supported(formatOf(firstOpusFile.info), actualOpusFile, duration)
        }

        fun formatOf(info: OpusInfo): AudioFormat {
            val sampleRate = info.sampleRate.toFloat()
            return AudioFormat(
                OggOpusEncoding.OPUS_NOT_FURTHER_SPECIFIED,
                sampleRate,
                16,
                info.numChannels,
                info.numChannels * 2,
                sampleRate,
                false,
            )
        }

        fun getDecodedFormat(sourceFormat: AudioFormat, intendedFormat: AudioFormat?): AudioFormat {
            require(sourceFormat.encoding is OggOpusEncoding)
            if (intendedFormat == null) {
                return AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.sampleRate,
                    16,
                    sourceFormat.channels,
                    sourceFormat.channels * 2,
                    sourceFormat.sampleRate,
                    false,
                )
            }

            getReasonCannotDecodeToFormat(intendedFormat)?.let {
                throw IllegalArgumentException("Cannot decode to $intendedFormat: $it")
            }

            return intendedFormat
        }

        fun getReasonCannotDecodeToEncoding(encoding: AudioFormat.Encoding): String? {
            if (encoding != AudioFormat.Encoding.PCM_SIGNED) {
                return "Must be PCM_SIGNED encoded"
            }

            return null
        }

        fun getReasonCannotDecodeToFormat(format: AudioFormat): String? {
            getReasonCannotDecodeToEncoding(format.encoding)?.let {
                return it
            }

            if (format.isBigEndian) {
                return "big-endian requested, opus is little-endian"
            }

            if (format.channels !in OpusAudioFormat.SUPPORTED_CHANNELS) {
                return "opus only supports ${OpusAudioFormat.SUPPORTED_CHANNELS} channels"
            }

            if (format.sampleSizeInBits != 16) {
                return "opus only supports 16-bit samples"
            }

            if (format.sampleRate !in OpusAudioFormat.SAMPLE_RATE_TO_BANDWIDTH_CONSTANT) {
                return "opus only supports decoding at these sample rates: ${OpusAudioFormat.SAMPLE_RATE_TO_BANDWIDTH_CONSTANT.keys}; use AudioSystem.getAudioInputStream(format, opusStream) to resample to ${format.frameRate}Hz"
            }

            return null
        }

        private fun OpusFile.consumeReadingAudioLength(): Duration? {
            var lastPacket: OpusAudioData = this.nextAudioPacket
                ?: return null
            while (true) {
                lastPacket = this.nextAudioPacket ?: break
            }

            return (lastPacket.granulePosition.toDouble() / 48000.0).seconds
        }

        private fun durationToFrameLength(duration: Duration?, decodedFormat: AudioFormat): Long {
            if (duration == null) {
                return AudioSystem.NOT_SPECIFIED.toLong()
            }

            val decodedFrameRate = decodedFormat.frameRate.toInt()
                .takeUnless { it == AudioSystem.NOT_SPECIFIED }
                ?: decodedFormat.sampleRate.toInt()
                .takeUnless { it == AudioSystem.NOT_SPECIFIED }
                ?: return AudioSystem.NOT_SPECIFIED.toLong()

            return (duration.toDouble(DurationUnit.SECONDS) * decodedFrameRate.toDouble()).toLong()
        }
    }
}