package io.github.tmarsteel.flyingnarrator.ui

import java.awt.Dimension
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference

class TileImage(
    val base: BufferedImage,
    val tileSize: Dimension,
) {
    init {
        require(base.width % tileSize.width == 0)
        require(base.height % tileSize.height == 0)
    }
    val rows = base.height / tileSize.height
    val columns = base.width / tileSize.width

    private val tileCache = mutableMapOf<Int, WeakReference<BufferedImage>>()

    /**
     * gets a subtile
     * @param index row-major
     */
    operator fun get(index: Int): BufferedImage {
        tileCache[index]?.get()?.let { return it }
        val subimage = base.getSubimage(
            (index % columns) * tileSize.width,
            index / columns * tileSize.height,
            tileSize.width,
            tileSize.height,
        )
        tileCache[index] = WeakReference(subimage)
        return subimage
    }
}