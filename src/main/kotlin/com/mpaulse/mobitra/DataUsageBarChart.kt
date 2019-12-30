/*
 * Copyright (c) 2019 Marlon Paulse
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.mpaulse.mobitra

import com.mpaulse.mobitra.data.MobileDataUsage
import javafx.collections.FXCollections
import javafx.geometry.Pos.CENTER
import javafx.scene.Cursor
import javafx.scene.chart.CategoryAxis
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.StackedBarChart
import javafx.scene.chart.XYChart.Data
import javafx.scene.chart.XYChart.Series
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.LinkedList

private fun timestampToDate(timestamp: Instant) =
    timestamp.atZone(ZoneId.systemDefault()).toLocalDate()

class DataUsageBarChart(
    private val dataUsage: List<MobileDataUsage>
): BorderPane() {

    private var lowerBound = LocalDate.now()
    private var upperBound = LocalDate.now()
    private val categories = FXCollections.observableList(LinkedList<String>())
    private val xAxis = CategoryAxis(categories)
    private val yAxis = NumberAxis()
    private val chart = StackedBarChart<String, Number>(xAxis, yAxis)
    private val downloadDataSeries = Series<String, Number>()
    private val uploadDataSeries = Series<String, Number>()

    init {
        if (dataUsage.isNotEmpty()) {
            adjustCategories(timestampToDate(dataUsage.first().timestamp), timestampToDate(dataUsage.last().timestamp))
        }

        xAxis.isGapStartAndEnd = false
        yAxis.tickLabelFormatter = DataAmountStringFormatter

        downloadDataSeries.name = "Download"
        uploadDataSeries.name = "Upload"

        for (usage in dataUsage) {
            addDataUsage(usage)
        }

        chart.animated = false
        chart.categoryGap = 1.0
        chart.data.addAll(uploadDataSeries, downloadDataSeries)

        val chartPane = StackPane()
        chartPane.children.addAll(chart, DataUsageBarChartOverlay(chart, ::onHorizontalPan))
        center = chartPane

        // Use our own title label instead of the chart's internal title, so that the y-axis
        // coordinates are not affected.
        val titleBox = HBox()
        titleBox.alignment = CENTER
        titleBox.children.add(Label("Data usage per day"))
        top = titleBox
    }

    fun addDataUsage(usage: MobileDataUsage) {
        val d = timestampToDate(usage.timestamp).toString()
        downloadDataSeries.data.add(Data(d, usage.downloadAmount))
        uploadDataSeries.data.add(Data(d, usage.uploadAmount))
    }

    private fun adjustCategories(from: LocalDate, to: LocalDate) {
        // TODO: cater per-month adjustment too.
        if (categories.isEmpty()) {
            categories += from.toString()
            lowerBound = from
            upperBound = from
        } else {
            if (from < lowerBound) {
                for (i in 0 until from.until(lowerBound, ChronoUnit.DAYS)) {
                    categories.add(0, from.plusDays(i).toString())
                }
            } else if (from > lowerBound) {
                do {
                    categories.removeAt(0)
                    lowerBound = LocalDate.parse(categories.first())
                } while (lowerBound < from)
            }
            lowerBound = from
        }

        if (to < upperBound) {
            do {
                categories.removeAt(categories.size - 1)
                upperBound = LocalDate.parse(categories.last())
            } while (to < upperBound)
        } else if (to > upperBound) {
            for (i in 1..upperBound.until(to, ChronoUnit.DAYS)) {
                categories.add(upperBound.plusDays(i).toString())
            }
        }
        upperBound = to
    }

    private fun onHorizontalPan(delta: Int) {
        // TODO: Handle per-month too
        adjustCategories(lowerBound.plusDays(delta.toLong()), upperBound.plusDays(delta.toLong()))

        uploadDataSeries.data.clear()
        downloadDataSeries.data.clear()

        // TODO: Don't bother adding data outside the lower and upper bound range
        for (usage in dataUsage) {
            addDataUsage(usage)
        }
    }

}

private class DataUsageBarChartOverlay(
    private val chart: StackedBarChart<String, Number>,
    private val onHorizontalPan: (delta: Int) -> Unit
): Region() {

    private val xAxis = chart.xAxis as CategoryAxis
    private val yAxis = chart.yAxis as NumberAxis
    private val uploadDataSeries = chart.data[0]
    private val downloadDataSeries = chart.data[1]
    private var dataUsagePopup: DataUsageBarChartPopup? = null
    private var mouseAchorX = 0.0

    init {
        setOnMouseMoved {
            onMouseMoved(it)
        }
        setOnMouseExited {
            onMouseExited(it)
        }
        setOnMousePressed {
            onMousePressed(it)
        }
        setOnMouseDragged {
            onMouseDragged(it)
        }
        setOnMouseReleased {
            onMouseReleased(it)
        }
    }

    private fun onMouseMoved(event: MouseEvent) {
        if (dataUsagePopup != null) {
            children.remove(dataUsagePopup)
            dataUsagePopup = null
        }

        val chartValue = getChartValue(event.x, event.y)
        if (chartValue != null) {
            cursor = Cursor.MOVE

            var uploadAmount = 0L
            var downloadAmount = 0L
            for (point in uploadDataSeries.data) {
                if (chartValue.first == point.xValue) {
                    uploadAmount = point.yValue.toLong()
                    break
                }
            }
            for (point in downloadDataSeries.data) {
                if (chartValue.first == point.xValue) {
                    downloadAmount = point.yValue.toLong()
                    break
                }
            }
            if (chartValue.second <= uploadAmount + downloadAmount) {
                dataUsagePopup = DataUsageBarChartPopup(chartValue.first, downloadAmount, uploadAmount)
                val x =
                    if (event.x + 100 > width) width - 116
                    else event.x
                dataUsagePopup!!.relocate(x, event.y + 16)
                children += dataUsagePopup
            }
        } else {
            cursor = Cursor.DEFAULT
        }

        event.consume()
    }

    private fun onMouseExited(event: MouseEvent) {
        cursor = Cursor.DEFAULT
        if (dataUsagePopup != null) {
            children.remove(dataUsagePopup)
        }
        event.consume()
    }

    private fun onMousePressed(event: MouseEvent) {
        if (getChartValue(event.x, event.y) != null) {
            cursor = Cursor.CLOSED_HAND
            mouseAchorX = event.x
        }
        event.consume()
    }

    private fun onMouseDragged(event: MouseEvent) {
        if (cursor == Cursor.CLOSED_HAND) {
            val delta = ((mouseAchorX - event.x) / 30).toInt()
            if (delta != 0) {
                mouseAchorX = event.x
                onHorizontalPan(delta)
            }
        }
        event.consume()
    }

    private fun onMouseReleased(event: MouseEvent) {
        cursor =
            if (getChartValue(event.x, event.y) != null) Cursor.MOVE
            else Cursor.DEFAULT
        event.consume()
    }

    private fun getChartValue(x: Double, y: Double): Pair<String, Long>? {
        val date = xAxis.getValueForDisplay(xAxis.parentToLocal(x, y).x)
        val y = yAxis.getValueForDisplay(yAxis.parentToLocal(x, y).y - chart.padding.top).toLong()
        return if (date != null && y > 0) Pair(date, y) else null
    }

}

private class DataUsageBarChartPopup(
    date: String,
    downloadAmount: Long,
    uploadAmount: Long
): Region() {

    init {
        val layout = VBox()
        layout.children.addAll(
            Label("Date: $date"),
            Label("Download: ${DataAmountStringFormatter.toString(downloadAmount)}"),
            Label("Upload: ${DataAmountStringFormatter.toString(uploadAmount)}"))
        children += layout
    }

}
