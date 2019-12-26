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
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

val productDB = MobileDataProductDB(Path.of(System.getProperty("user.home"), ".Mobitra"))

fun generateProductData(productName: String, totalAmount: Long, startDate: LocalDate, expiryDate: LocalDate) {
    val product = MobileDataProduct(
        UUID.randomUUID(),
        productName,
        totalAmount,
        Random.nextLong(0, totalAmount),
        expiryDate)
    println("Generating data for product: $product")
    productDB.storeProduct(product)
}

fun main() {
    productDB.clearAllData()

    val months = 14L + 3
    val today = LocalDate.now()
    var date = today.minusMonths(months)

    for (m in 0..months) {
        val totalAmount = Random.nextLong(10, 226) * 1_073_741_824
        generateProductData(
            "Once-off LTE/LTE-A Anytime Data",
            totalAmount,
            date,
            date.plusMonths(2))
        generateProductData(
            "Once-off LTE/LTE-A Night Surfer Data",
            totalAmount,
            date,
            date.plusMonths(1))
        date = date.plusMonths(1)
    }

    productDB.close()
    println("Done")
}
