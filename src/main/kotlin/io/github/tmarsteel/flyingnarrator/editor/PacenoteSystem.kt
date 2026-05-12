package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAtom
import io.github.tmarsteel.flyingnarrator.route.Route

interface PacenoteSystem<Item> {
    fun infer(route: Route, features: List<Feature>): List<Item>
    fun makeRouteChildComponent(route: Route, item: Item): ReactiveRouteComponentChild
    fun atomize(items: List<Item>): List<PacenoteAtom>
}