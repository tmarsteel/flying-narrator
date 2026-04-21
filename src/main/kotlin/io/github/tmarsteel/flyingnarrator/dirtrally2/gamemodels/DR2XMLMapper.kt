package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import tools.jackson.databind.MapperFeature
import tools.jackson.dataformat.xml.XmlFactory
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.module.jaxb.JaxbAnnotationModule
import tools.jackson.module.kotlin.kotlinModule

val DR2XMLMapper by lazy {
    XmlMapper.Builder(XmlFactory())
        .addModule(kotlinModule())
        .addModule(JaxbAnnotationModule())
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        .build()
}