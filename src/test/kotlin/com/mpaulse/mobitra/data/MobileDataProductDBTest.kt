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

package com.mpaulse.mobitra.data

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val TEST_DATA_PATH: Path = Path.of("src/test/data")

class MobileDataProductDBTest {

    var productDB: MobileDataProductDB? = null

    @BeforeEach
    fun setUp() {
        TEST_DATA_PATH.toFile().deleteRecursively()
        productDB = MobileDataProductDB(TEST_DATA_PATH)
    }

    @AfterEach
    fun tearDown() {
        productDB?.close()
    }

    @Test
    fun `storeProduct and getProduct`() {
        val product = MobileDataProduct(
            UUID.randomUUID(),
            "Test storeProduct and getProduct",
            7832478178423,
            32895894578,
            LocalDate.now())
        productDB!!.storeProduct(product)
        val product2 = productDB!!.getProduct(product.id)
        assertEquals(product, product2)
    }

    @Test
    fun `getProduct - available`() {
        val productList = mutableListOf<MobileDataProduct>()
        for (i in 0..9) {
            val product = MobileDataProduct(
                UUID.randomUUID(),
                "Test getProduct $i",
                i.toLong(),
                i.toLong(),
                LocalDate.now())
            productDB!!.storeProduct(product)
            productList += product
        }

        val productSet = productDB!!.getProducts().toSet()
        assertEquals(productList.size, productSet.size, "Incorrect no. products")
        for (product in productList) {
            assertTrue(product in productSet, "Product not found:\n$product")
        }
    }

    @Test
    fun `getProducts - none`() {
        val products = productDB!!.getProducts()
        assertTrue(products.isEmpty())
    }

    @Test
    fun `addDataUsage and getDataUsage`() {
        val product = MobileDataProduct(
            UUID.randomUUID(),
            "Test addDataUsage and getDataUsage",
            7832478178423,
            32895894578,
            LocalDate.now())
        productDB!!.storeProduct(product)

        productDB!!.addDataUsage(product)
        productDB!!.addDataUsage(product, 1)
        productDB!!.addDataUsage(product, uploadAmount = 2)
        productDB!!.addDataUsage(product, 3, 4)

        val usage = productDB!!.getDataUsage(
            product,
            LocalDate.now().atStartOfDay(),
            LocalDate.now().plusDays(1).atStartOfDay())

        assertEquals(4, usage.size, "Incorrect no. usage data")

        assertEquals(0, usage[0].downloadAmount, "Incorrect download amount")
        assertEquals(0, usage[0].uploadAmount, "Incorrect upload amount")

        assertEquals(1, usage[1].downloadAmount, "Incorrect download amount")
        assertEquals(0, usage[1].uploadAmount, "Incorrect upload amount")

        assertEquals(0, usage[2].downloadAmount, "Incorrect download amount")
        assertEquals(2, usage[2].uploadAmount, "Incorrect upload amount")

        assertEquals(3, usage[3].downloadAmount, "Incorrect download amount")
        assertEquals(4, usage[3].uploadAmount, "Incorrect upload amount")
    }

}