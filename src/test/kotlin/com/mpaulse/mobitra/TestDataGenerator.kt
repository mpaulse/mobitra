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
import com.mpaulse.mobitra.data.MobileDataProductDB
import com.mpaulse.mobitra.data.MobileDataProductType
import com.mpaulse.mobitra.data.MobileDataUsage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

val productDB = MobileDataProductDB(APP_HOME_PATH)

fun generateProductData(
    productName: String,
    type: MobileDataProductType,
    availableAmount: Long,
    activationDate: LocalDate,
    expiryDate: LocalDate
) {
    val usedAmount = Random.nextLong(0, availableAmount / 3)

    val product = MobileDataProduct(
        UUID.randomUUID(),
        "0678912345",
        productName,
        type,
        availableAmount - usedAmount,
        usedAmount,
        activationDate,
        expiryDate)
    println("Generating data for product: $product")
    productDB.storeProduct(product)

    val now = Instant.now()
    val expiry = expiryDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
    val endTimestamp = if (now < expiry) now else expiry
    var timestamp = activationDate.atStartOfDay(ZoneId.systemDefault()).toInstant()

    val maxBytesPerHour = 268_435_456L
    while (product.availableAmount > 0 && timestamp < endTimestamp) {
        var max = if (product.availableAmount > maxBytesPerHour) maxBytesPerHour else product.availableAmount
        val downloadAmount = Random.nextLong(0, max + 1)
        product.availableAmount -= downloadAmount

        max = if (product.availableAmount > maxBytesPerHour) maxBytesPerHour else product.availableAmount
        val uploadAmount =
            if (product.availableAmount > 0) Random.nextLong(0, max + 1)
            else 0
        product.availableAmount -= uploadAmount

        productDB.addDataUsage(
            product,
            MobileDataUsage(
                downloadAmount = downloadAmount,
                uploadAmount = uploadAmount,
                timestamp = timestamp))

        timestamp = timestamp.plus(1, ChronoUnit.HOURS)
        product.usedAmount += (downloadAmount + uploadAmount)
    }

    productDB.storeProduct(product)
    println("Updated product: $product")
}

fun main() {
    productDB.clearAllData()

    val months = 14L
    val today = LocalDate.now()
    var date = today.minusMonths(months)

    for (m in 0..months) {
        val availableAmount = 225L * 1_073_741_824
        generateProductData(
            "Once-off LTE/LTE-A Anytime Data",
            MobileDataProductType.ANYTIME,
            availableAmount,
            date,
            date.plusDays(60))
        generateProductData(
            "Once-off LTE/LTE-A Night Surfer Data",
            MobileDataProductType.NIGHT_SURFER,
            availableAmount,
            date,
            date.plusDays(30))
        date = date.plusMonths(1)
    }

    productDB.close()
    println("Done")
}
