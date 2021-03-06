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

@JsonDeserialize(using = TelkomCreateOnnetSessionDeserializer::class)
data class TelkomCreateOnnetSessionResponse(
    val resultCode: Int,
    val resultMessageCode: String,
    val resultMessage: String?,
    val msisdn: String?,
    val jSessionIdCookie: String?
)

private class TelkomCreateOnnetSessionDeserializer
    : StdDeserializer<TelkomCreateOnnetSessionResponse>(TelkomCreateOnnetSessionResponse::class.java) {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): TelkomCreateOnnetSessionResponse {
        val root = parser.readValueAsTree<JsonNode>()
        if (!root.isObject) {
            throw MonitoringAPIException("Expected a root JSON object, but got ${root.nodeType}")
        }

        return TelkomCreateOnnetSessionResponse(
            root["resultCode"]?.intValue()
                ?: throw MonitoringAPIException("Missing resultCode"),
            root["resultMessageCode"]?.textValue()
                ?: throw MonitoringAPIException("Missing resultMessageCode"),
            root["resultMessage"]?.textValue(),
            root["payload"]?.get("msisdn")?.textValue(),
            context.getAttribute("jSessionIdCookie") as? String)
    }

}
