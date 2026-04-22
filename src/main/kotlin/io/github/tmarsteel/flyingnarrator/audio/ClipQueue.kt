package io.github.tmarsteel.flyingnarrator.audio

import java.util.concurrent.LinkedBlockingQueue
import javax.sound.sampled.Clip
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Plays back [javax.sound.sampled.Clip]s from a background thread, one after the other from a queue.
 */
@OptIn(ExperimentalAtomicApi::class)
class ClipQueue : AutoCloseable {
    private val commandQueue = LinkedBlockingQueue<PlayerCommand>()

    @Volatile
    private var shouldStop = false
    private val playerThread = AtomicReference<Thread?>(null)

    fun queue(clip: Clip) {
        assureThreadRunning()
        commandQueue.put(PlayerCommand.Queue(clip))
    }

    fun pause() {
        TODO()
    }

    fun resume() {
        TODO()
    }

    /**
     * Stops playback and clears the playback queue.
     * @return whether any action was taken (false if no playback running)
     */
    fun stop(): Boolean {
        val localPlayerThread = playerThread.load()
        if (localPlayerThread == null || !localPlayerThread.isAlive) {
            return false
        }

        commandQueue.put(PlayerCommand.Stop)
        localPlayerThread.interrupt()
        return true
    }

    override fun close() {
        commandQueue.clear()
        if (stop()) {
            playerThread.load()?.join(1.seconds.toJavaDuration())
        }
    }

    private fun assureThreadRunning(): Boolean {
        val localCurrentThread = playerThread.load()
        if (localCurrentThread != null && localCurrentThread.isAlive) {
            return false
        }

        val newThread = Thread(::playerThreadMain, "ClipQueue")
        if (playerThread.compareAndSet(localCurrentThread, newThread)) {
            shouldStop = false
            newThread.start()
            return true
        } else {
            // race condition, try again
            return assureThreadRunning()
        }
    } 
    
    private fun playerThreadMain() {
        val playbackQueue = ArrayDeque<Clip>()
        var lastStartedClip: Clip? = null
        
        while (true) {
            if (lastStartedClip != null && !lastStartedClip.isRunning) {
                lastStartedClip = null
            }

            val command = if (lastStartedClip == null && playbackQueue.isEmpty()) {
                // no clip to play/wait for, long wait for new commands
                try {
                    commandQueue.take()
                } catch (_: InterruptedException) {
                    null
                }
            } else {
                commandQueue.poll()
            }
            when (command) {
                is PlayerCommand.Queue -> {
                    playbackQueue.addLast(command.clip)
                }
                is PlayerCommand.Pause -> {
                    TODO()
                }
                is PlayerCommand.Resume -> {
                    TODO()
                }
                is PlayerCommand.Stop -> {
                    playbackQueue.clear()
                    while (commandQueue.peek() == PlayerCommand.Stop) {
                        commandQueue.poll()
                    }
                    lastStartedClip?.stop()
                    lastStartedClip = null
                }
                null -> {
                    // nothing to do
                }
            }

            // isRunning doesn't immediately update (tada, threads!), so this is needed
            var newClipJustStarted = false

            if (lastStartedClip == null || !lastStartedClip.isRunning) {
                lastStartedClip = playbackQueue.removeFirstOrNull()
                lastStartedClip?.start()
                newClipJustStarted = true
            }

            if ((lastStartedClip != null && lastStartedClip.isRunning) || newClipJustStarted) {
                try {
                    Thread.sleep((lastStartedClip?.remaining ?: 0.seconds).toJavaDuration())
                } catch (_: InterruptedException) { }
            }
        }
    }
    
    private sealed interface PlayerCommand {
        object Pause : PlayerCommand
        object Resume : PlayerCommand
        object Stop : PlayerCommand
        data class Queue(val clip: Clip) : PlayerCommand
    }
}