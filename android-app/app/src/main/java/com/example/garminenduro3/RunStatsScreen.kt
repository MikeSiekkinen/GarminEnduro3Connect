package com.example.garminenduro3

import UIKit.app.Screen
import UIKit.app.data.Align
import UIKit.app.data.EvsColor
import UIKit.app.resources.Font
import UIKit.widgets.Text

class RunStatsScreen : Screen() {
    private val paceLabel = Text()
    private val paceValue = Text()
    private val distLabel = Text()
    private val distValue = Text()
    private val timeLabel = Text()
    private val timeValue = Text()
    private val hrLabel   = Text()
    private val hrValue   = Text()

    override fun onCreate() {
        val w = getWidth()
        val h = getHeight()
        val col1 = w * 0.25f
        val col2 = w * 0.75f
        val labelRow1 = h * 0.22f
        val valueRow1 = h * 0.38f
        val labelRow2 = h * 0.60f
        val valueRow2 = h * 0.76f

        fun label(t: Text, s: String, x: Float, y: Float) {
            t.setText(s).setResource(Font.StockFont.Small)
                .setTextAlign(Align.center)
                .setForegroundColor(EvsColor.Green.rgba)
            t.setX(x).setY(y)
            add(t)
        }
        fun value(t: Text, s: String, x: Float, y: Float) {
            t.setText(s).setResource(Font.StockFont.Medium)
                .setTextAlign(Align.center)
                .setForegroundColor(EvsColor.White.rgba)
            t.setX(x).setY(y)
            add(t)
        }

        label(paceLabel, "PACE",     col1, labelRow1)
        value(paceValue, "--:--",    col1, valueRow1)
        label(distLabel, "DIST",     col2, labelRow1)
        value(distValue, "-.--",     col2, valueRow1)
        label(timeLabel, "TIME",     col1, labelRow2)
        value(timeValue, "-:--:--",  col1, valueRow2)
        label(hrLabel,   "HR",       col2, labelRow2)
        value(hrValue,   "--",       col2, valueRow2)
    }

    fun update(pace: String, dist: String, elapsed: String, hr: String) {
        paceValue.setText(pace)
        distValue.setText(dist)
        timeValue.setText(elapsed)
        hrValue.setText(hr)
    }
}
