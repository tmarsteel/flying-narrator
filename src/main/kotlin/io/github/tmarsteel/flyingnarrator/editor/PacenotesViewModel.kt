package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.route.LocationOnRoute
import javax.swing.Icon

class PacenotesViewModel(
    val items: List<ItemModel>
) {
    interface ItemModel {
        val icon: Icon
        val subjectLocation: LocationOnRoute
    }
}