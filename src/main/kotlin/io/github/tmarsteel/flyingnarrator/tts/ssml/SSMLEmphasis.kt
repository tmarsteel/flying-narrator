package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Node

class SSMLEmphasis(
    val level: Level? = null,
    val children: List<SSMLElement>,
) : SSMLElement {
    override val isEmpty get()= children.all { it.isEmpty }

    constructor(level: Level? = null, vararg children: SSMLElement) : this(
        level,
        children.toList(),
    )

    override fun toDOMNode(document: Document): Node {
        return document.createElement("emphasis").apply {
            if (level != null) {
                setAttribute("level", level.name.lowercase())
            }
            children.forEach {
                appendChild(it.toDOMNode(document))
            }
        }
    }

    enum class Level {
        STRONG,
        MODERATE,
        NONE,
        REDUCED,
        ;
    }
}