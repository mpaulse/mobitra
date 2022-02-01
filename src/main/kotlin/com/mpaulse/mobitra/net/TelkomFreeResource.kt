/*
 * Copyright (c) 2022 Marlon Paulse
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

import com.mpaulse.mobitra.data.UNLIMITED_AMOUNT
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

private const val THREE_GB = 3_221_225_472

const val LTE_ONCE_OFF_NIGHT_SURFER_DATA_RESOURCE_TYPE = "5125"
const val LTE_ONCE_OFF_ANYTIME_DATA_RESOURCE_TYPE = "5127"
const val LTE_ONCE_OFF_10MBPS_OFF_PEAK_DATA_RESOURCE_TYPE = "5242"
const val LTE_ONCE_OFF_4MBPS_OFF_PEAK_DATA_RESOURCE_TYPE = "5243"
const val LTE_ONCE_OFF_AGGREGATED_DATA_RESOURCE_TYPE = "aggregated"

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
                return when (type) {
                    LTE_ONCE_OFF_ANYTIME_DATA_RESOURCE_TYPE ->
                        if (!isUnlimited && availableAmount + usedAmount <= THREE_GB) expiryDate.minusDays(13)
                        else expiryDate.minusDays(60)
                    else ->
                        expiryDate.minusDays(30)
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
        = service.uppercase(Locale.getDefault()) == "GPRS"
            && type !in setOf("5124", "5749", "5135", "5136", "5149", "5177")

    val isUnlimited: Boolean
        = availableAmount == UNLIMITED_AMOUNT

}
