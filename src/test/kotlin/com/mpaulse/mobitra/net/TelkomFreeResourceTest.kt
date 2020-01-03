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

package com.mpaulse.mobitra.net

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month

class TelkomFreeResourceTest {

    @Test
    fun `isMobileData - true`() {
        val resource = TelkomFreeResource(
            "0123456789",
            "5125",
            "LTE",
            "GPRS",
            21343254,
            23432432,
            LocalDate.now())
        assertTrue(resource.isMobileData)
    }

    @Test
    fun `isMobileData - false`() {
        for (type in setOf("5124", "5749", "5135", "5136", "5149", "5177")) {
            val resource = TelkomFreeResource(
                "0123456789",
                type,
                "LTE",
                "GPRS",
                21343254,
                23432432,
                LocalDate.now())
            assertFalse(resource.isMobileData)
        }

        val resource = TelkomFreeResource(
            "0123456789",
            "5036",
            "SMS",
            "SMS",
            21343254,
            23432432,
            LocalDate.now())
        assertFalse(resource.isMobileData)
    }

    @Test
    fun `activationDate - night surfer data`() {
        val resource = TelkomFreeResource(
            "0123456789",
            "5125",
            "LTE",
            "GPRS",
            21343254,
            23432432,
            LocalDate.of(2019, Month.DECEMBER, 31))
        assertEquals(LocalDate.of(2019, Month.DECEMBER, 1), resource.activationDate)
    }

    @Test
    fun `activationDate - anytime data less than 20GB`() {
        val resource = TelkomFreeResource(
            "0123456789",
            "5127",
            "LTE",
            "GPRS",
            21_474_836_479,
            23432432,
            LocalDate.of(2019, Month.DECEMBER, 31))
        assertEquals(LocalDate.of(2019, Month.DECEMBER, 1), resource.activationDate)
    }

    @Test
    fun `activationDate - anytime data greater than or equal to 20GB`() {
        var resource = TelkomFreeResource(
            "0123456789",
            "5127",
            "LTE",
            "GPRS",
            21_474_836_480,
            23432432,
            LocalDate.of(2019, Month.DECEMBER, 31))
        assertEquals(LocalDate.of(2019, Month.NOVEMBER, 1), resource.activationDate)

        resource = TelkomFreeResource(
            "0123456789",
            "5127",
            "LTE",
            "GPRS",
            21_474_836_481,
            23432432,
            LocalDate.of(2019, Month.DECEMBER, 31))
        assertEquals(LocalDate.of(2019, Month.NOVEMBER, 1), resource.activationDate)
    }

}
