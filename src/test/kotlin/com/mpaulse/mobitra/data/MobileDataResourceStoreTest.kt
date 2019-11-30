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

class MobileDataResourceStoreTest {

    var store: MobileDataResourceStore? = null

    @BeforeEach
    fun setUp() {
        TEST_DATA_PATH.toFile().deleteRecursively()
        store = MobileDataResourceStore(TEST_DATA_PATH)
    }

    @AfterEach
    fun tearDown() {
        store?.close()
    }

    @Test
    fun `storeResource and getResource`() {
        val resource = MobileDataResource(
            UUID.randomUUID(),
            "Test storeResource and getResource",
            7832478178423,
            32895894578,
            LocalDate.now())
        store!!.storeResource(resource)
        val resource2 = store!!.getResource(resource.id)
        assertEquals(resource2, resource2)
    }

    @Test
    fun `getResources - available`() {
        val resourceList = mutableListOf<MobileDataResource>()
        for (i in 0..9) {
            val resource = MobileDataResource(
                UUID.randomUUID(),
                "Test getResources $i",
                i.toLong(),
                i.toLong(),
                LocalDate.now())
            store!!.storeResource(resource)
            resourceList += resource
        }

        val resourceSet = store!!.getResources().toSet()
        assertEquals(resourceList.size, resourceSet.size, "Incorrect no. resources")
        for (resource in resourceList) {
            assertTrue(resource in resourceSet, "Resource not found:\n$resource")
        }
    }

    @Test
    fun `getResources - none`() {
        val resources = store!!.getResources()
        assertTrue(resources.isEmpty())
    }

    @Test
    fun `addUsage`() {
        val resource = MobileDataResource(
            UUID.randomUUID(),
            "Test addUsage",
            7832478178423,
            32895894578,
            LocalDate.now())
        store!!.storeResource(resource)

        store!!.addUsage(resource)
        store!!.addUsage(resource, 1)
        store!!.addUsage(resource, uploadAmount = 2)
        store!!.addUsage(resource, 3, 4)

        // TODO: get usage
    }

}
