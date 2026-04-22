package io.github.tmarsteel.flyingnarrator.io

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

val FlyingNarratorJsonFormat = Json {
    serializersModule = SerializersModule {
        include(CompactObjectListSerializer.MODULE)
    }
}