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

import com.mpaulse.mobitra.data.UNLIMITED_AMOUNT
import javafx.util.StringConverter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private const val GB = 1_073_741_824
private const val MB = 1_048_576
private const val KB = 1_024

private val decimalFormat = DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.UK))

object DataAmountStringFormatter: StringConverter<Number>() {

    override fun toString(n: Number): String {
        if (n == UNLIMITED_AMOUNT) {
            return "Unlimited"
        }
        val d = n.toDouble()
        if (d >= GB) {
            return "${decimalFormat.format(d / GB)} GB"
        } else if (d >= MB) {
            return "${decimalFormat.format(d / MB)} MB"
        } else if (d >= KB) {
            return "${decimalFormat.format(d / KB)} KB"
        }
        return "${d.toLong()} B"
    }

    override fun fromString(s: String) = null

}
