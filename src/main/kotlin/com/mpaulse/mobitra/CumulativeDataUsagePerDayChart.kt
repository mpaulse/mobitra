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

import com.mpaulse.mobitra.data.MobileDataProduct
import com.mpaulse.mobitra.data.MobileDataUsage
import javafx.application.Platform
import javafx.geometry.Point2D
import javafx.geometry.Pos.CENTER
import javafx.scene.chart.AreaChart
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart.Data
import javafx.scene.chart.XYChart.Series
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.util.StringConverter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private fun xValueToDate(x: Long, product: MobileDataProduct) =
    if (x >= 0) product.activationDate.plusDays(x)
    else null

private fun dateToXValue(date: LocalDate, product: MobileDataProduct) =
    product.activationDate.until(date, ChronoUnit.DAYS)

private fun timestampToXValue(timestamp: Instant, product: MobileDataProduct) =
    dateToXValue(timestamp.atZone(ZoneId.systemDefault()).toLocalDate(), product)

class CumulativeDataUsagePerDayChart(
    dataUsageList: List<MobileDataUsage>,
    private val product: MobileDataProduct
): Chart, BorderPane() {

    private val xAxis = NumberAxis(0.0, dateToXValue(product.expiryDate.plusDays(5), product).toDouble(), 1.0)
    private val yAxis = NumberAxis()
    private val chart = AreaChart<Number, Number>(xAxis, yAxis)
    private val dataSeries = Series<Number, Number>()

    private var usedAmount = 0L

    init {
        chart.createSymbols = false
        chart.isLegendVisible = false

        xAxis.minorTickCount = 0
        xAxis.tickLabelFormatter = object: StringConverter<Number>() {
            private val todayXValue = dateToXValue(LocalDate.now(), product)
            private val expiryDateXValue = dateToXValue(product.expiryDate, product)
            override fun toString(n: Number) =
                when (n.toLong()) {
                    0L -> "Activation\n${product.activationDate}"
                    todayXValue -> "Today\n${LocalDate.now()}"
                    expiryDateXValue -> "Expiry\n${product.expiryDate}"
                    else -> ""
                }
            override fun fromString(s: String) = null
        }

        yAxis.tickLabelFormatter = DataAmountStringFormatter

        for (usage in dataUsageList) {
            plotDataUsage(usage)
        }

        // An upper bound line to express the product total amount.
        val upperBoundSeries = Series<Number, Number>()
        upperBoundSeries.data.add(Data(0, product.totalAmount))
        upperBoundSeries.data.add(Data(dateToXValue(product.expiryDate.minusDays(4), product), product.totalAmount))

        chart.data.addAll(dataSeries, upperBoundSeries)

        val chartPane = StackPane()
        chartPane.children.addAll(chart, CumulativeDataUsagePerDayChartOverlay(chart, product))
        center = chartPane

        // Use our own title label instead of the chart's internal title, so that the y-axis
        // coordinates are not affected.
        val title = Label("Cumulative data usage per day")
        title.styleClass += "chart-pane-title"
        val titleBox = HBox()
        titleBox.alignment = CENTER
        titleBox.children.add(title)
        top = titleBox

        styleClass += "chart-pane"
    }

    private fun plotDataUsage(dataUsage: MobileDataUsage) {
        usedAmount += dataUsage.totalAmount
        dataSeries.data.add(Data(
            timestampToXValue(dataUsage.timestamp, product),
            usedAmount))
    }

    override fun addDataUsage(dataUsage: MobileDataUsage) {
        var lastDataPointUpdated = false
        if (dataSeries.data.isNotEmpty()) {
            val lastDataPoint = dataSeries.data.last()
            if (timestampToXValue(dataUsage.timestamp, product) == lastDataPoint.xValue) {
                usedAmount += dataUsage.totalAmount
                lastDataPoint.yValue = usedAmount
                lastDataPointUpdated = true
            }
        }
        if (!lastDataPointUpdated) {
            plotDataUsage(dataUsage)
        }
    }

}

private class CumulativeDataUsagePerDayChartOverlay(
    private val chart: AreaChart<Number, Number>,
    private val product: MobileDataProduct
): Region() {

    private val xAxis = chart.xAxis as NumberAxis
    private val yAxis = chart.yAxis as NumberAxis
    private val dataSeries = chart.data[0].data
    private val upperBoundSeries = chart.data[1].data
    private var dataUsagePopup: CumulativeDataUsagePerDayPopup? = null

    init {
        chart.widthProperty().addListener { _, _, _ ->
            addChartLabels()
        }
        chart.heightProperty().addListener { _, _, _ ->
            addChartLabels()
        }

        setOnMouseMoved(::onMouseMoved)
        setOnMouseExited(::onMouseExited)
    }

    private fun addChartLabels() {
        Platform.runLater {
            val totalAmountLabel = Label("${DataAmountStringFormatter.toString(product.totalAmount)} total")
            setLabelDataPoint(totalAmountLabel,upperBoundSeries.last())

            val usedAmountLabel = Label(
                "${DataAmountStringFormatter.toString(product.usedAmount)} used"
                    + " (${DataAmountStringFormatter.toString(product.remainingAmount)} remaining)")
            setLabelDataPoint(usedAmountLabel, dataSeries.last())

            // Prevent the used amount label touching the upper bound line
            if (usedAmountLabel.layoutY <= totalAmountLabel.layoutY + 8) {
                usedAmountLabel.translateY = 8 - (totalAmountLabel.layoutY - usedAmountLabel.layoutY)
            }

            children.clear()
            children.addAll(totalAmountLabel, usedAmountLabel)
        }
    }

    private fun setLabelDataPoint(label: Label, dataPoint: Data<Number, Number>) {
        label.relocate(
            xAxis.localToParent(Point2D(xAxis.getDisplayPosition(dataPoint.xValue.toLong() + 1), 0.0)).x,
            yAxis.localToParent(Point2D(0.0, yAxis.getDisplayPosition(dataPoint.yValue))).y - 3)
    }

    private fun onMouseMoved(event: MouseEvent) {
        removePopup()

        val x = xAxis.getValueForDisplay(xAxis.parentToLocal(event.x, event.y).x).toLong()
        val y = yAxis.getValueForDisplay(yAxis.parentToLocal(event.x, event.y).y - chart.padding.top).toLong()
        if (x >= 0 && y >= 0 && dataSeries.isNotEmpty()) {
            var closestPoint: Data<Number, Number>? = null
            for (point in dataSeries) {
                if (point.xValue.toLong() <= x) {
                    closestPoint = point
                }
            }
            if (closestPoint != null && x <= dataSeries.last().xValue.toLong() && y <= closestPoint.yValue.toLong()) {
                val date = xValueToDate(x, product)
                if (date != null) {
                    dataUsagePopup = CumulativeDataUsagePerDayPopup(date, product.totalAmount, closestPoint.yValue.toLong())
                }
            }
        }

        if (dataUsagePopup != null) {
            dataUsagePopup!!.relocate(event.x, event.y + 16)
            children += dataUsagePopup
        }

        event.consume()
    }

    private fun onMouseExited(event: MouseEvent) {
        removePopup()
        event.consume()
    }

    private fun removePopup() {
        if (dataUsagePopup != null) {
            children.remove(dataUsagePopup)
            dataUsagePopup = null
        }
    }

}

private class CumulativeDataUsagePerDayPopup(
    date: LocalDate,
    totalAmount: Long,
    usedAmount: Long
): StackPane() {

    init {
        val layout = VBox()
        layout.children.addAll(
            Label("Date: ${date}"),
            Label("Data used: ${DataAmountStringFormatter.toString(usedAmount)}"),
            Label("Data remaining: ${DataAmountStringFormatter.toString(totalAmount - usedAmount)}"))
        children += layout

        styleClass += "chart-pane-popup"
    }

}
