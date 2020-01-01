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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

private const val TWENTY_GB = 21_474_836_480

@JsonDeserialize(using = TelkomFreeResourcesDeserializer::class)
data class TelkomFreeResourcesResponse(
    val resultCode: Int,
    val resultMessageCode: String,
    val freeResources: Array<TelkomFreeResource>
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TelkomFreeResourcesResponse
        if (resultCode != other.resultCode) return false
        if (resultMessageCode != other.resultMessageCode) return false
        if (!freeResources.contentEquals(other.freeResources)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = resultCode
        result = 31 * result + resultMessageCode.hashCode()
        result = 31 * result + freeResources.contentHashCode()
        return result
    }

}

data class TelkomFreeResource(
    val type: String,
    val name: String,
    val service: String,
    val totalAmount: Long,
    val usedAmount: Long,
    val expiryDate: LocalDate
) {

    val id: UUID

    val activationDate: LocalDate?
        get() {
            if (isMobileData) {
                when (type) {
                    TelkomFreeResourceType.LTE_ONCE_OFF_NIGHT_SURFER_DATA.string
                        -> return expiryDate.minusDays(30)
                    TelkomFreeResourceType.LTE_ONCE_OFF_ANYTIME_DATA.string
                        -> return if (totalAmount < TWENTY_GB) expiryDate.minusDays(30)
                                  else expiryDate.minusDays(60)
                }
            }
            return null
        }

    init {
        val digest = MessageDigest.getInstance("MD5")
        digest.update(type.toByteArray())
        digest.update(service.toByteArray())
        digest.update(expiryDate.toString().toByteArray())
        id = UUID.nameUUIDFromBytes(digest.digest())
    }

    val isMobileData: Boolean
        get() =
            service.toUpperCase() == "GPRS"
                && type !in setOf("5124", "5749", "5135", "5136", "5149", "5177")

}

enum class TelkomFreeResourceType(val string: String) {
    LTE_ONCE_OFF_NIGHT_SURFER_DATA("5125"),
    LTE_ONCE_OFF_ANYTIME_DATA("5127")
}

private class TelkomFreeResourcesDeserializer
    : StdDeserializer<TelkomFreeResourcesResponse>(TelkomFreeResourcesResponse::class.java) {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): TelkomFreeResourcesResponse {
        val root = parser.readValueAsTree<JsonNode>()
        if (!root.isObject) {
            throw MonitoringAPIException("Expected a root JSON object, but got ${root.nodeType}")
        }

        return TelkomFreeResourcesResponse(
            root["resultCode"]?.intValue()
                ?: throw MonitoringAPIException("Missing resultCode"),
            root["resultMessageCode"]?.textValue()
                ?: throw MonitoringAPIException("Missing resultMessageCode"),
            deserializeFreeResources(root["payload"]))
    }

    private fun deserializeFreeResources(payload: JsonNode?): Array<TelkomFreeResource> {
        val freeResources = mutableListOf<TelkomFreeResource>()
        if (payload != null && payload.isArray) {
            for (element in payload.elements()) {
                val resource = element["subscriberFreeResource"]
                if (resource == null || !resource.isObject) {
                    continue
                }
                freeResources += TelkomFreeResource(
                    resource["type"]?.asText()
                        ?: throw MonitoringAPIException("Missing subscriberFreeResource type"),
                    resource["typeName"]?.asText()
                        ?: throw MonitoringAPIException("Missing subscriberFreeResource typeName"),
                    resource["service"]?.asText()
                        ?: throw MonitoringAPIException("Missing subscriberFreeResource service"),
                    try {
                        resource["totalAmount"]?.asText()?.toLong()
                            ?: throw MonitoringAPIException("Missing subscriberFreeResource totalAmount")
                    } catch (e: NumberFormatException) {
                        throw MonitoringAPIException("Invalid subscriberFreeResource usedAmount: ${resource["totalAmount"]?.asText()}")
                    },
                    try {
                        resource["usedAmount"]?.asText()?.toLong()
                            ?: throw MonitoringAPIException("Missing subscriberFreeResource usedAmount")
                    } catch (e: NumberFormatException) {
                        throw MonitoringAPIException("Invalid subscriberFreeResource usedAmount: ${resource["usedAmount"]?.asText()}")
                    },
                    try {
                        LocalDate.parse(
                            resource["endBillCycle"]?.asText()
                                ?: throw MonitoringAPIException("Missing subscriberFreeResource endBillCycle"),
                            DateTimeFormatter.ofPattern("HH:mm:ss EEE MMM dd yyyy"))
                            .minusDays(1)
                    } catch (e: DateTimeParseException) {
                        throw MonitoringAPIException("Invalid subscriberFreeResource endBillCycle: ${resource["endBillCycle"]?.asText()}")
                    }
                )
            }
        }
        return freeResources.toTypedArray()
    }

}
