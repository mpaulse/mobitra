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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

val TEST_DATA_PATH: Path = Path.of("src/test/data")

class MobileDataProductDBTest {

    lateinit var productDB: MobileDataProductDB

    @BeforeEach
    fun setUp() {
        TEST_DATA_PATH.toFile().deleteRecursively()
        productDB = MobileDataProductDB(TEST_DATA_PATH)
    }

    @AfterEach
    fun tearDown() {
        productDB.close()
    }

    @Test
    fun `storeProduct and getProduct`() {
        val product = MobileDataProduct(
            UUID.randomUUID(),
            "0123456789",
            "Test storeProduct and getProduct",
            MobileDataProductType.ANYTIME,
            7832478178423,
            32895894578,
            LocalDate.now(),
            LocalDate.now().plusDays(1))
        productDB.storeProduct(product)
        val product2 = productDB.getProduct(product.id)
        assertEquals(product, product2)
    }

    @Test
    fun `storeProduct - update`() {
        val product = MobileDataProduct(
            UUID.randomUUID(),
            "0123456789",
            "Test storeProduct and getProduct",
            MobileDataProductType.ANYTIME,
            7832478178423,
            32895894578,
            LocalDate.now(),
            LocalDate.now().plusDays(1))
        productDB.storeProduct(product)

        product.usedAmount = 887326786432
        productDB.storeProduct(product)

        val product2 = productDB.getProduct(product.id)
        assertEquals(product, product2)
    }

    @Test
    fun `getAllProducts - available`() {
        val productList = mutableListOf<MobileDataProduct>()
        for (i in 0..9) {
            val product = MobileDataProduct(
                UUID.randomUUID(),
                "0123456789",
                "Test getProduct $i",
                if (i % 2 == 0) MobileDataProductType.ANYTIME else MobileDataProductType.NIGHT_SURFER,
                i.toLong(),
                i.toLong(),
                LocalDate.now(),
                LocalDate.now().plusDays(1))
            productDB.storeProduct(product)
            productList += product
        }

        val productSet = productDB.getAllProducts().toSet()
        assertEquals(productList.size, productSet.size, "Incorrect no. products")
        for (product in productList) {
            assertTrue(product in productSet, "Product not found:\n$product")
        }
    }

    @Test
    fun `getAllProducts - none`() {
        val products = productDB.getAllProducts()
        assertTrue(products.isEmpty())
    }

    @Test
    fun `getActiveProducts - available`() {
        val activeProductList = mutableListOf<MobileDataProduct>()
        for (i in 0..9) {
            val product = MobileDataProduct(
                UUID.randomUUID(),
                "0123456789",
                "Test getProduct $i",
                MobileDataProductType.ANYTIME,
                i.toLong(),
                i.toLong(),
                LocalDate.now().minusDays(1),
                LocalDate.now())
            productDB.storeProduct(product)
        }
        for (i in 10..12) {
            val product = MobileDataProduct(
                UUID.randomUUID(),
                "0123456789",
                "Test getProduct $i",
                MobileDataProductType.ANYTIME,
                i.toLong(),
                i.toLong(),
                LocalDate.now(),
                LocalDate.now().plusDays(1))
            productDB.storeProduct(product)
            activeProductList += product
        }

        val productSet = productDB.getActiveProducts().toSet()
        assertEquals(activeProductList.size, productSet.size, "Incorrect no. products")
        for (product in activeProductList) {
            assertTrue(product in productSet, "Product not found:\n$product")
        }
    }

    @Test
    fun `addDataUsage and getDataUsage for product`() {
        val usedAmount = 32895894578
        val product = MobileDataProduct(
            UUID.randomUUID(),
            "0123456789",
            "Test addDataUsage and getDataUsage",
            MobileDataProductType.ANYTIME,
            7832478178423,
            usedAmount,
            LocalDate.now(),
            LocalDate.now().plusDays(1))
        productDB.storeProduct(product)

        productDB.addDataUsage(product, MobileDataUsage())
        productDB.addDataUsage(product, MobileDataUsage(downloadAmount = 1))
        productDB.addDataUsage(product, MobileDataUsage(uploadAmount = 2))
        productDB.addDataUsage(product, MobileDataUsage(downloadAmount = 3, uploadAmount = 4))
        productDB.addDataUsage(product, MobileDataUsage(uncategorisedAmount = 5))

        val usage = productDB.getDataUsage(product)

        assertAll(
            { assertEquals(5, usage.size, "Incorrect no. usage data") },

            { assertEquals(0, usage[0].downloadAmount, "Incorrect download amount") },
            { assertEquals(0, usage[0].uploadAmount, "Incorrect upload amount") },
            { assertEquals(usedAmount - (1 + 2 + 3 + 4 + 5), usage[0].uncategorisedAmount, "Incorrect uncategorised amount") }, // adjusted

            { assertEquals(1, usage[1].downloadAmount, "Incorrect download amount") },
            { assertEquals(0, usage[1].uploadAmount, "Incorrect upload amount") },
            { assertEquals(0, usage[1].uncategorisedAmount, "Incorrect uncategorised amount") },

            { assertEquals(0, usage[2].downloadAmount, "Incorrect download amount") },
            { assertEquals(2, usage[2].uploadAmount, "Incorrect upload amount") },
            { assertEquals(0, usage[2].uncategorisedAmount, "Incorrect uncategorised amount") },

            { assertEquals(3, usage[3].downloadAmount, "Incorrect download amount") },
            { assertEquals(4, usage[3].uploadAmount, "Incorrect upload amount") },
            { assertEquals(0, usage[3].uncategorisedAmount, "Incorrect uncategorised amount") },

            { assertEquals(0, usage[4].downloadAmount, "Incorrect download amount") },
            { assertEquals(0, usage[4].uploadAmount, "Incorrect upload amount") },
            { assertEquals(5, usage[4].uncategorisedAmount, "Incorrect uncategorised amount") }
        )
    }

    @Test
    fun `getActiveProductDataUsage`() {
        for (i in 0..4) {
            val product = MobileDataProduct(
                UUID.randomUUID(),
                "0123456789",
                "Test getDataUsage - expired",
                MobileDataProductType.ANYTIME,
                7832478178423,
                32895894578,
                LocalDate.now().minusDays(1),
                LocalDate.now())
            productDB.storeProduct(product)
            productDB.addDataUsage(product, MobileDataUsage(downloadAmount = 123, uploadAmount = 567))
        }

        val usedAmount = 32895894578
        for (i in 0..1) {
            val product = MobileDataProduct(
                UUID.randomUUID(),
                "0123456789",
                "Test getDataUsage - active",
                MobileDataProductType.ANYTIME,
                7832478178423,
                usedAmount,
                LocalDate.now(),
                LocalDate.now().plusDays(1))
            productDB.storeProduct(product)

            productDB.addDataUsage(product, MobileDataUsage(downloadAmount = 1))
            productDB.addDataUsage(product, MobileDataUsage(uploadAmount = 2, timestamp = Instant.now().plusSeconds(30)))
            productDB.addDataUsage(product, MobileDataUsage(downloadAmount = 3, uploadAmount = 4, timestamp = Instant.now().plusSeconds(60)))
        }

        val usage = productDB.getActiveProductDataUsage()

        assertAll(
            { assertEquals(6, usage.size, "Incorrect no. usage data") },

            { assertEquals(1, usage[0].downloadAmount, "Incorrect download amount") },
            { assertEquals(0, usage[0].uploadAmount, "Incorrect upload amount") },
            { assertEquals(usedAmount * 2 - (1 + 2 + 3 + 4) * 2, usage[0].uncategorisedAmount, "Incorrect uncategorised amount") }, // adjusted
            { assertEquals(1, usage[1].downloadAmount, "Incorrect download amount") },
            { assertEquals(0, usage[1].uploadAmount, "Incorrect upload amount") },
            { assertEquals(0, usage[1].uncategorisedAmount, "Incorrect uncategorised amount") }, // adjusted

            { assertEquals(0, usage[2].downloadAmount, "Incorrect download amount") },
            { assertEquals(2, usage[2].uploadAmount, "Incorrect upload amount") },
            { assertEquals(0, usage[2].uncategorisedAmount, "Incorrect uncategorised amount") },
            { assertEquals(0, usage[3].downloadAmount, "Incorrect download amount") },
            { assertEquals(2, usage[3].uploadAmount, "Incorrect upload amount") },
            { assertEquals(0, usage[3].uncategorisedAmount, "Incorrect uncategorised amount") },

            { assertEquals(3, usage[4].downloadAmount, "Incorrect download amount") },
            { assertEquals(4, usage[4].uploadAmount, "Incorrect upload amount") },
            { assertEquals(0, usage[4].uncategorisedAmount, "Incorrect uncategorised amount") },
            { assertEquals(3, usage[5].downloadAmount, "Incorrect download amount") },
            { assertEquals(4, usage[5].uploadAmount, "Incorrect upload amount") },
            { assertEquals(0, usage[5].uncategorisedAmount, "Incorrect uncategorised amount") }
        )
    }

}
