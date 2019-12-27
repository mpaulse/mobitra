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
import javafx.scene.chart.CategoryAxis
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.StackedBarChart

class DataUsageBarChart(
    //product: MobileDataProduct?,
    dataUsage: List<MobileDataUsage>
): StackedBarChart<String, Number>(CategoryAxis(), NumberAxis()) {

    private val downloadDataSeries = Series<String, Number>()
    private val uploadDataSeries = Series<String, Number>()

    init {
        downloadDataSeries.name = "Download"
        uploadDataSeries.name = "Upload"

        for (usage in dataUsage) {
            addDataUsage(usage)
        }

        data.addAll(uploadDataSeries, downloadDataSeries)
    }

    fun addDataUsage(usage: MobileDataUsage) {
        downloadDataSeries.data.add(Data(usage.timestamp.toString(), usage.downloadAmount))
        uploadDataSeries.data.add(Data(usage.timestamp.toString(), usage.uploadAmount))
    }

}
