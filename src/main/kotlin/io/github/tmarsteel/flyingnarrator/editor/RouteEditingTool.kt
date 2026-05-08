package io.github.tmarsteel.flyingnarrator.editor

import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.JButton

interface RouteEditingTool {
    /**
     * Creates a button to be shown in a [javax.swing.JToolBar]. The returned button should already contain an
     * [java.awt.event.ActionListener] as necessary for the tool.
     */
    fun makeToolbarButton(routeComponent: RouteComponent): JButton

    interface Activation {
        val cursor: Cursor?
            get()= null

        fun onMouseClicked(event: MouseEvent)

        /**
         * Called when the user wants to stop using this tool for the given [RouteComponent]
         */
        fun onDeselected()
    }
}