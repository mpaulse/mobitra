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
import javafx.scene.chart.AreaChart
import javafx.scene.chart.NumberAxis
import javafx.util.StringConverter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class DataUsagePerDayCumulativeAreaChart(
    private val product: MobileDataProduct,
    usageData: List<MobileDataUsage>
): AreaChart<Number, Number>(
        NumberAxis(0.0, dateToXValue(product.expiryDate.plusDays(5), product).toDouble(), 1.0),
        NumberAxis()) {

    private val dataSeries = Series<Number, Number>()

    private var usedAmount = 0L

    init {
        createSymbols = false
        isLegendVisible = false
        
        val xAxis = xAxis as NumberAxis
        xAxis.minorTickCount = 0
        xAxis.tickLabelFormatter = object: StringConverter<Number>() {
            private val todayXValue = dateToXValue(LocalDate.now(), product).toDouble()
            private val expiryDateXValue = dateToXValue(product.expiryDate, product).toDouble()
            override fun toString(n: Number) =
                when (n) {
                    0.0 -> "Activation\n${product.activationDate}"
                    todayXValue -> "Today\n${LocalDate.now()}"
                    expiryDateXValue -> "Expiry\n${product.expiryDate}"
                    else -> ""
                }
            override fun fromString(s: String) = null
        }

        if (usageData.isNotEmpty()) {
            // Account for data usage at the beginning of the product period that was not tracked
            var usedAmountTracked = 0L
            for (usage in usageData) {
                usedAmountTracked += usage.totalAmount
            }
            if (usedAmountTracked < product.usedAmount) {
                addDataUsage(MobileDataUsage(
                    usageData.first().timestamp,
                    product.usedAmount - usedAmountTracked))
            }

            for (usage in usageData) {
                addDataUsage(usage)
            }
        }

        // An upper bound line to express the product total amount.
        val upperBoundSeries = Series<Number, Number>()
        upperBoundSeries.data.add(Data(0, product.totalAmount))
        upperBoundSeries.data.add(Data(dateToXValue(product.expiryDate, product), product.totalAmount))

        data.addAll(dataSeries, upperBoundSeries)
    }

    fun addDataUsage(dataUsage: MobileDataUsage) {
        usedAmount += dataUsage.downloadAmount + dataUsage.uploadAmount
        dataSeries.data.add(Data(
            timestampToXValue(dataUsage.timestamp, product),
            usedAmount))
    }

}

private fun dateToXValue(date: LocalDate, product: MobileDataProduct) =
    product.activationDate.until(date, ChronoUnit.DAYS)

private fun timestampToXValue(timestamp: Instant, product: MobileDataProduct) =
    dateToXValue(timestamp.atZone(ZoneId.systemDefault()).toLocalDate(), product)

