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

package com.mpaulse.mobitra.net

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.UUID

private const val TWENTY_GB = 21_474_836_480

const val LTE_ONCE_OFF_NIGHT_SURFER_DATA_RESOURCE_TYPE = "5125"
const val LTE_ONCE_OFF_ANYTIME_DATA_RESOURCE_TYPE = "5127"

data class TelkomFreeResource(
    val msisdn: String,
    val type: String,
    val name: String,
    val service: String,
    val availableAmount: Long,
    val usedAmount: Long,
    val expiryDate: LocalDate
) {

    val id: UUID

    val activationDate: LocalDate?
        get() {
            if (isMobileData) {
                when (type) {
                    LTE_ONCE_OFF_NIGHT_SURFER_DATA_RESOURCE_TYPE
                        -> return expiryDate.minusDays(30)
                    LTE_ONCE_OFF_ANYTIME_DATA_RESOURCE_TYPE
                        -> return if (availableAmount + usedAmount < TWENTY_GB) expiryDate.minusDays(30)
                                  else expiryDate.minusDays(60)
                }
            }
            return null
        }

    init {
        val buf = ByteArrayOutputStream()
        buf.writeBytes(msisdn.toByteArray())
        buf.writeBytes(type.toByteArray())
        buf.writeBytes(service.toByteArray())
        buf.writeBytes(expiryDate.toString().toByteArray())
        id = UUID.nameUUIDFromBytes(buf.toByteArray())
    }

    val isMobileData: Boolean
        get() =
            service.toUpperCase() == "GPRS"
                && type !in setOf("5124", "5749", "5135", "5136", "5149", "5177")

}
