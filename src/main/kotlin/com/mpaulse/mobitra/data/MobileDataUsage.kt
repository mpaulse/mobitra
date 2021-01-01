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

package com.mpaulse.mobitra.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoField.HOUR_OF_DAY
import java.time.temporal.ChronoField.MILLI_OF_SECOND
import java.time.temporal.ChronoField.MINUTE_OF_HOUR
import java.time.temporal.ChronoField.SECOND_OF_MINUTE

fun defaultTimestamp(): Instant {
    val now = Instant.now()
    val time = now.atZone(ZoneId.systemDefault()).toLocalTime()
    if (time.get(HOUR_OF_DAY) == 0 && time.get(MINUTE_OF_HOUR) == 0 && time.get(SECOND_OF_MINUTE) == 0) {
        return now.minusMillis(now.get(MILLI_OF_SECOND) + 1L)
    }
    return now
}

data class MobileDataUsage(
    val timestamp: Instant = defaultTimestamp(),
    val downloadAmount: Long = 0,
    val uploadAmount: Long = 0,
    val uncategorisedAmount: Long = 0
) {

    val totalAmount: Long
        get() = downloadAmount + uploadAmount + uncategorisedAmount

}
