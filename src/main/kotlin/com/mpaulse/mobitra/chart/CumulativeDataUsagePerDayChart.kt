/*
 * Copyright (c) 2020 Marlon Paulse
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

package com.mpaulse.mobitra.chart

import com.mpaulse.mobitra.DataAmountStringFormatter
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
    product: MobileDataProduct
): Chart, BorderPane() {

    private val product = product.copy()
    private val xAxis = NumberAxis(0.0, dateToXValue(product.expiryDate.plusDays(7), product).toDouble(), 1.0)
    private val yAxis = NumberAxis()
    private val chart = AreaChart<Number, Number>(xAxis, yAxis)
    private val chartOverlay: CumulativeDataUsagePerDayChartOverlay
    private val dataSeries = Series<Number, Number>()
    private var lastDataPointYValue = 0L

    init {
        chart.createSymbols = false
        chart.isLegendVisible = false

        xAxis.minorTickCount = 0
        xAxis.tickLabelFormatter = object: StringConverter<Number>() {
            private val todayXValue = dateToXValue(LocalDate.now(), product)
            private val expiryDateXValue = dateToXValue(product.expiryDate, product)
            override fun toString(n: Number) =
                when (n.toLong()) {
                    0L -> "Activation${if (todayXValue == 0L) " (Today)" else ""}\n${product.activationDate}"
                    expiryDateXValue -> "Expiry${if (expiryDateXValue == todayXValue) " (Today)" else ""}\n${product.expiryDate}"
                    todayXValue -> {
                        if (todayXValue in 4..(expiryDateXValue-4)) "Today\n${LocalDate.now()}"
                        else ""
                    }
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
        upperBoundSeries.data.add(Data(0, product.initialAvailableAmount))
        upperBoundSeries.data.add(Data(dateToXValue(product.expiryDate, product), product.initialAvailableAmount))

        chart.data.addAll(dataSeries, upperBoundSeries)
        chartOverlay = CumulativeDataUsagePerDayChartOverlay(chart, this.product)

        val chartPane = StackPane()
        chartPane.children.addAll(chart, chartOverlay)
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
        lastDataPointYValue += dataUsage.totalAmount
        dataSeries.data.add(Data(
            timestampToXValue(dataUsage.timestamp, product),
            lastDataPointYValue))
    }

    override fun addDataUsage(dataUsage: MobileDataUsage) {
        var lastDataPointUpdated = false
        if (dataSeries.data.isNotEmpty()) {
            val lastDataPoint = dataSeries.data.last()
            if (timestampToXValue(dataUsage.timestamp, product) == lastDataPoint.xValue) {
                lastDataPointYValue += dataUsage.totalAmount
                lastDataPoint.yValue = lastDataPointYValue
                lastDataPointUpdated = true
            }
        }
        if (!lastDataPointUpdated) {
            plotDataUsage(dataUsage)
        }
        product.availableAmount -= dataUsage.totalAmount
        product.usedAmount += dataUsage.totalAmount
        chartOverlay.refreshDataUsageInfo()
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
    private var totalAmountLabel: Label? = null
    private var usedAmountLabel: Label? = null
    private var dataUsagePopup: CumulativeDataUsagePerDayPopup? = null
    private var dataUsagePopupMousePos: Point2D? = null

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
            if (totalAmountLabel != null) {
                children.remove(totalAmountLabel)
            }
            totalAmountLabel = Label("${DataAmountStringFormatter.toString(product.initialAvailableAmount)} total")
            setLabelDataPoint(totalAmountLabel!!, upperBoundSeries.last())
            children += totalAmountLabel

            if (usedAmountLabel != null) {
                children.remove(usedAmountLabel)
            }
            if (dataSeries.isNotEmpty()) {
                usedAmountLabel = Label(
                    "${DataAmountStringFormatter.toString(product.usedAmount)} used\n"
                        + "${DataAmountStringFormatter.toString(product.availableAmount)} remaining")
                setLabelDataPoint(usedAmountLabel!!, dataSeries.last())

                // Prevent the used amount label touching the upper bound line
                if (usedAmountLabel!!.layoutY <= totalAmountLabel!!.layoutY + 12) {
                    usedAmountLabel!!.translateY = 12 - (totalAmountLabel!!.layoutY - usedAmountLabel!!.layoutY)
                }
                children += usedAmountLabel
            }
        }
    }

    private fun setLabelDataPoint(label: Label, dataPoint: Data<Number, Number>) {
        label.relocate(
            xAxis.localToParent(Point2D(xAxis.getDisplayPosition(dataPoint.xValue.toLong() + 1), 0.0)).x,
            yAxis.localToParent(Point2D(0.0, yAxis.getDisplayPosition(dataPoint.yValue))).y - 3)
    }

    private fun onMouseMoved(event: MouseEvent) {
        showPopup(Point2D(event.x, event.y))
        event.consume()
    }

    private fun showPopup(mousePos: Point2D) {
        removePopup()

        val x = xAxis.getValueForDisplay(xAxis.parentToLocal(mousePos.x, mousePos.y).x).toLong()
        val y = yAxis.getValueForDisplay(yAxis.parentToLocal(mousePos.x, mousePos.y).y - chart.padding.top).toLong()
        if (x >= 0 && y >= 0 && dataSeries.isNotEmpty()) {
            var closestPoint: Data<Number, Number>? = null
            for (point in dataSeries) {
                if (point.xValue.toLong() <= x) {
                    closestPoint = point
                } else {
                    break
                }
            }
            if (closestPoint != null && x <= dataSeries.last().xValue.toLong() && y <= closestPoint.yValue.toLong()) {
                val date = xValueToDate(x, product)
                if (date != null) {
                    val usedAmount = closestPoint.yValue.toLong()
                    dataUsagePopup = CumulativeDataUsagePerDayPopup(date, product.initialAvailableAmount - usedAmount, usedAmount)
                }
            }
        }

        if (dataUsagePopup != null) {
            dataUsagePopup!!.relocate(mousePos.x, mousePos.y + 16)
            children += dataUsagePopup
            dataUsagePopupMousePos = mousePos
        }
    }

    fun refreshDataUsageInfo() {
        addChartLabels()
        val mousePos = dataUsagePopupMousePos
        if (mousePos != null) {
            showPopup(mousePos)
        }
    }

    private fun onMouseExited(event: MouseEvent) {
        removePopup()
        event.consume()
    }

    private fun removePopup() {
        if (dataUsagePopup != null) {
            children.remove(dataUsagePopup)
            dataUsagePopup = null
            dataUsagePopupMousePos = null
        }
    }

}

private class CumulativeDataUsagePerDayPopup(
    date: LocalDate,
    availableAmount: Long,
    usedAmount: Long
): StackPane() {

    init {
        val layout = VBox()
        layout.children.addAll(
            Label("Date: ${date}"),
            Label("Data used: ${DataAmountStringFormatter.toString(usedAmount)}"),
            Label("Data remaining: ${DataAmountStringFormatter.toString(availableAmount)}"))
        children += layout

        styleClass += "chart-pane-popup"
    }

}
