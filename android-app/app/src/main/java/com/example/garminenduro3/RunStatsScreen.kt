package com.example.garminenduro3

import UIKit.app.Screen
import UIKit.app.data.Align
import UIKit.app.data.AlignV
import UIKit.app.data.EvsColor
import UIKit.app.resources.Font
import UIKit.controls.popup.PopupMessage
import UIKit.widgets.Text

class RunStatsScreen(
    private val initialPace: String = "--:--",
    private val initialDist: String = "-.--",
    private val initialElapsed: String = "-:--:--",
    private val initialHr: String = "--",
    initialStreetName: String = "",
    initialStreetVisible: Boolean = true,
) : Screen() {
    private val paceLabel = Text()
    private val paceValue = Text()
    private val distLabel = Text()
    private val distValue = Text()
    private val timeLabel = Text()
    private val timeValue = Text()
    private val hrLabel   = Text()
    private val hrValue   = Text()
    private val streetNameText = Text()

    // Seeded from constructor so a screen recreated on reconnect renders the last-known
    // values directly in onCreate() — no post-create replay, hence no timing race.
    private var lastStreetName = initialStreetName
    private var streetNameVisible = initialStreetVisible

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
        value(paceValue, initialPace,    col1, valueRow1)
        label(distLabel, "DIST",     col2, labelRow1)
        value(distValue, initialDist,    col2, valueRow1)
        label(timeLabel, "TIME",     col1, labelRow2)
        value(timeValue, initialElapsed, col1, valueRow2)
        label(hrLabel,   "HR",       col2, labelRow2)
        value(hrValue,   initialHr,      col2, valueRow2)

        streetNameText.setText(if (streetNameVisible) lastStreetName else "")
            .setResource(Font.StockFont.Small)
            .setTextAlign(Align.center)
            .setForegroundColor(EvsColor.White.rgba)
        streetNameText.setX(w * 0.5f).setY(h * 0.05f)
        add(streetNameText)
    }

    fun update(pace: String, dist: String, elapsed: String, hr: String) {
        paceValue.setText(pace)
        distValue.setText(dist)
        timeValue.setText(elapsed)
        hrValue.setText(hr)
    }

    fun updateStreetName(name: String) {
        lastStreetName = name
        if (streetNameVisible) streetNameText.setText(name)
    }

    fun setStreetNameVisible(visible: Boolean) {
        streetNameVisible = visible
        streetNameText.setText(if (visible) lastStreetName else "")
    }

    fun showLapSplit(lapPace: String) {
        val label = Text()
        label.setText("Mile  $lapPace /mi")
            .setResource(Font.StockFont.Medium)
            .setTextAlign(Align.center)
            .setForegroundColor(EvsColor.Green.rgba)
        showPopup(PopupMessage(label, AlignV.center, 5000))
    }
}
