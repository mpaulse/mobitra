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

import com.mpaulse.mobitra.DataUsageBarChartType.DAILY
import com.mpaulse.mobitra.DataUsageBarChartType.MONTHLY
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

enum class DataUsageBarChartType {
    DAILY,
    MONTHLY
}

class DataUsageBarChart(
    private val dataUsage: List<MobileDataUsage>,
    private val type: DataUsageBarChartType = DAILY
): BorderPane() {

    private lateinit var dateRangeFrom: LocalDate
    private lateinit var dateRangeTo: LocalDate
    private val dates = FXCollections.observableList(LinkedList<String>())
    private val xAxis = CategoryAxis(dates)
    private val yAxis = NumberAxis()
    private val chart = StackedBarChart<String, Number>(xAxis, yAxis)
    private val downloadDataSeries = Series<String, Number>()
    private val uploadDataSeries = Series<String, Number>()

    init {
        setInitialDateRange()
        plotDataUsage()

        xAxis.isGapStartAndEnd = false
        xAxis.tickLabelRotation = 90.0
        yAxis.tickLabelFormatter = DataAmountStringFormatter

        downloadDataSeries.name = "Download"
        uploadDataSeries.name = "Upload"

        chart.animated = false
        chart.categoryGap = 1.0
        chart.data.addAll(downloadDataSeries, uploadDataSeries)

        val chartPane = StackPane()
        chartPane.children.addAll(chart, DataUsageBarChartOverlay(chart, ::onHorizontalPan))
        center = chartPane

        // Use our own title label instead of the chart's internal title, so that the y-axis
        // coordinates are not affected.
        val title = Label("Data usage per " + (if (type == DAILY) "day" else "month"))
        title.styleClass += "chart-pane-title"
        val titleBox = HBox()
        titleBox.alignment = CENTER
        titleBox.children.add(title)
        top = titleBox

        styleClass += "chart-pane"
    }

    private fun setInitialDateRange() {
        val numBars = 40L
        var dateFrom: LocalDate
        var dateTo: LocalDate
        if (dataUsage.size >= numBars) {
            dateTo = timestampToDate(dataUsage.last().timestamp)
            if (type == MONTHLY) {
                dateTo = dateTo.withDayOfMonth(1)
            }
            dateFrom =
                if (type == DAILY) dateTo.minusDays(numBars - 1)
                else dateTo.minusMonths(numBars - 1)
        } else {
            dateFrom =
                if (dataUsage.isNotEmpty()) timestampToDate(dataUsage.first().timestamp)
                else LocalDate.now()
            if (type == MONTHLY) {
                dateFrom = dateFrom.withDayOfMonth(1)
            }
            dateTo =
                if (type == DAILY) dateFrom.plusDays(numBars - 1)
                else dateFrom.plusMonths(numBars - 1)
        }
        adjustDateRange(dateFrom, dateTo)
    }

    private fun plotDataUsage() {
        downloadDataSeries.data.clear()
        uploadDataSeries.data.clear()

        if (dateRangeFrom <= timestampToDate(dataUsage.last().timestamp)
                && dateRangeTo >= timestampToDate(dataUsage.first().timestamp)) {
            for (usage in dataUsage) {
                val date = timestampToDate(usage.timestamp)
                if (date < dateRangeFrom) {
                    continue
                } else if (date > dateRangeTo) {
                    break
                }

                val dateStr = dateToString(date)
                downloadDataSeries.data.add(Data(dateStr, usage.downloadAmount))
                uploadDataSeries.data.add(Data(dateStr, usage.uploadAmount))
            }
        }
    }

    private fun adjustDateRange(from: LocalDate, to: LocalDate) {
        if (dates.isEmpty()) {
            dates += dateToString(from)
            dateRangeFrom = from
            dateRangeTo = from
        } else {
            if (from < dateRangeFrom) {
                val n = from.until(dateRangeFrom,
                    if (type == DAILY) ChronoUnit.DAYS
                    else ChronoUnit.MONTHS) - 1
                for (i in n downTo 0) {
                    dates.add(0, dateToString(
                        if (type == DAILY) from.plusDays(i)
                        else from.plusMonths(i)))
                }
            } else if (from > dateRangeFrom) {
                do {
                    dates.removeAt(0)
                    dateRangeFrom = stringToDate(dates.first())
                } while (from > dateRangeFrom)
            }
            dateRangeFrom = from
        }

        if (to < dateRangeTo) {
            do {
                dates.removeAt(dates.size - 1)
                dateRangeTo = stringToDate(dates.last())
            } while (to < dateRangeTo)
        } else if (to > dateRangeTo) {
            val n = dateRangeTo.until(to,
                if (type == DAILY) ChronoUnit.DAYS
                else ChronoUnit.MONTHS)
            for (i in 1..n) {
                dates.add(dateToString(
                    if (type == DAILY) dateRangeTo.plusDays(i)
                    else dateRangeTo.plusMonths(i)))
            }
        }
        dateRangeTo = to
    }

    private fun onHorizontalPan(delta: Long) {
        adjustDateRange(
            if (type == DAILY) dateRangeFrom.plusDays(delta)
            else dateRangeFrom.plusMonths(delta),
            if (type == DAILY) dateRangeTo.plusDays(delta)
            else dateRangeTo.plusMonths(delta))
        plotDataUsage()
    }

    private fun dateToString(date: LocalDate) =
        if (type == DAILY) date.toString()
        else date.toString().substringBeforeLast("-")

    private fun stringToDate(date: String) =
        if (type == DAILY) LocalDate.parse(date)
        else LocalDate.parse("$date-01")

}

private class DataUsageBarChartOverlay(
    private val chart: StackedBarChart<String, Number>,
    private val onHorizontalPan: (delta: Long) -> Unit
): Region() {

    private val xAxis = chart.xAxis as CategoryAxis
    private val yAxis = chart.yAxis as NumberAxis
    private val downloadDataSeries = chart.data[0]
    private val uploadDataSeries = chart.data[1]
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
        showPopup(event.x, event.y)
        event.consume()
    }

    private fun onMouseExited(event: MouseEvent) {
        cursor = Cursor.DEFAULT
        removePopup()
        event.consume()
    }

    private fun onMousePressed(event: MouseEvent) {
        removePopup()
        if (getChartValue(event.x, event.y) != null) {
            cursor = Cursor.CLOSED_HAND
            mouseAchorX = event.x
        }
        event.consume()
    }

    private fun onMouseDragged(event: MouseEvent) {
        if (cursor == Cursor.CLOSED_HAND) {
            val delta = ((mouseAchorX - event.x) / 30).toLong()
            if (delta != 0L) {
                mouseAchorX = event.x
                onHorizontalPan(delta)
            }
        }
        event.consume()
    }

    private fun onMouseReleased(event: MouseEvent) {
        showPopup(event.x, event.y)
        event.consume()
    }

    private fun getChartValue(x: Double, y: Double): Pair<String, Long>? {
        val date = xAxis.getValueForDisplay(xAxis.parentToLocal(x, y).x)
        val y = yAxis.getValueForDisplay(yAxis.parentToLocal(x, y).y - chart.padding.top).toLong()
        return if (date != null && y >= 0) Pair(date, y) else null
    }

    private fun showPopup(x: Double, y: Double) {
        removePopup()

        val chartValue = getChartValue(x, y)
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
            val dataAmount = uploadAmount + downloadAmount
            if (dataAmount > 0L && chartValue.second <= dataAmount) {
                dataUsagePopup = DataUsageBarChartPopup(chartValue.first, downloadAmount, uploadAmount)
                dataUsagePopup!!.relocate(
                    if (x + 100 > width) width - 116 else x,
                    y + 16)
                children += dataUsagePopup
            }
        } else {
            cursor = Cursor.DEFAULT
        }
    }

    private fun removePopup() {
        if (dataUsagePopup != null) {
            children.remove(dataUsagePopup)
            dataUsagePopup = null
        }
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
