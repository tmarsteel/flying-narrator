package io.github.tmarsteel.flyingnarrator.nefs

fun <T : Any, V : Any> T.setNullableOptionalProto(setter: (T, V) -> Unit, value: V?): T {
    if (value != null) {
        setter(this, value)
    }
    return this
}