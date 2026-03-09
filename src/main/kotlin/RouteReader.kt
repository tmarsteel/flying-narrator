package io.github.tmarsteel.flyingnarrator

import java.util.stream.Stream

interface RouteReader {
    fun read(): Route
}