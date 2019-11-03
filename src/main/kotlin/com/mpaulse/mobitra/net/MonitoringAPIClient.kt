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

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

private const val HTTP_TIMEOUT_MILLIS = 15000L

class MonitoringAPIException(
    message: String,
    cause: Throwable? = null
): Exception(message, cause)

class MonitoringAPIClient(
    private val huaweiIpAddr: String,
    private val huaweiPort: Int = 80,
    private val telkomApiBaseUrl: String = "http://onnet.telkom.co.za/onnet/public/api",
    private val timeout: Long = HTTP_TIMEOUT_MILLIS
) {

    private val httpClient = HttpClient.newBuilder().build()
    private val xmlMapper = XmlMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES)
    private val jsonMapper = jacksonObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES)

    suspend fun getHuaweiTrafficStatistics(): HuaweiTrafficStats = withContext(Dispatchers.IO) {
        try {
            xmlMapper.readValue(
                doHttpGet("http://$huaweiIpAddr:$huaweiPort/api/monitoring/traffic-statistics"),
                HuaweiTrafficStats::class.java)
        } catch (e: MonitoringAPIException) {
            throw e
        } catch (e: Exception) {
            throw MonitoringAPIException("Failed to get Huawei traffic statistics", e)
        }
    }

    suspend fun getTelkomFreeResources(): Array<TelkomFreeResource> {
        val checkRsp = checkTelkomOnnet()
        if (checkRsp.resultCode == 0 && checkRsp.sessionToken != null) {
            yield()
            val createSessionRsp = createTelkomOnnetSession(checkRsp.sessionToken)
            if (createSessionRsp.resultCode == 0 && createSessionRsp.msisdn != null) {
                yield()
                val freeResourcesRsp = getTelkomFreeResources(createSessionRsp.msisdn)
                if (freeResourcesRsp.resultCode == 0) {
                    return freeResourcesRsp.freeResources
                } else {
                    throw MonitoringAPIException("Failed to retrieve Telkom free resources:\n$freeResourcesRsp")
                }
            } else {
                throw MonitoringAPIException("Failed to create Telkom Onnet session:\n$createSessionRsp")
            }
        }
        throw MonitoringAPIException("Failed to check for Telkom Onnet:\n$checkRsp")
    }

    private suspend fun checkTelkomOnnet(): TelkomCheckOnnetResponse = withContext(Dispatchers.IO) {
        try {
            jsonMapper.readValue(
                doHttpGet("$telkomApiBaseUrl/checkOnnet"),
                TelkomCheckOnnetResponse::class.java)
        } catch (e: MonitoringAPIException) {
            throw e
        } catch (e: Exception) {
            throw MonitoringAPIException("Failed to check for Telkom Onnet", e)
        }
    }

    private suspend fun createTelkomOnnetSession(sessionToken: String): TelkomCreateOnnetSessionResponse = withContext(Dispatchers.IO) {
        try {
            jsonMapper.readValue(
                doUrlEncodedHttpPost(
                    "$telkomApiBaseUrl/createOnnetSession",
                    mapOf("sid" to sessionToken)),
                TelkomCreateOnnetSessionResponse::class.java)
        } catch (e: MonitoringAPIException) {
            throw e
        } catch (e: Exception) {
            throw MonitoringAPIException("Failed to create Telkom Onnet session", e)
        }
    }

    private suspend fun getTelkomFreeResources(msisdn: String): TelkomFreeResourcesResponse = withContext(Dispatchers.IO) {
        try {
            jsonMapper.readValue(
                doUrlEncodedHttpPost(
                    "$telkomApiBaseUrl/getFreeResources",
                    mapOf("msisdn" to msisdn)),
                TelkomFreeResourcesResponse::class.java)
        } catch (e: MonitoringAPIException) {
            throw e
        } catch (e: Exception) {
            throw MonitoringAPIException("Failed to retrieve Telkom free resources", e)
        }
    }

    private fun doHttpGet(uri: String): InputStream {
        return doHttpRequest(HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofMillis(timeout))
            .GET()
            .build())
    }

    private fun doUrlEncodedHttpPost(uri: String, params: Map<String, String>): InputStream {
        return doHttpRequest(HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofMillis(timeout))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(BodyPublishers.ofString(urlEncode(params)))
            .build())
    }

    private fun doHttpRequest(req: HttpRequest): InputStream {
        val rsp = httpClient.send(req, BodyHandlers.ofInputStream())
        val status = rsp.statusCode()
        if (rsp.statusCode() < 200 || rsp.statusCode() >= 300) {
            throw MonitoringAPIException("${req.method()} ${req.uri()} failed: response status $status")
        }
        return rsp.body()
    }

    private fun urlEncode(params: Map<String, String>): String {
        var first = true
        val s = StringBuilder()
        for ((param, value) in params.entries) {
            if (first) {
                first = false
            } else {
                s.append("&")
            }
            s.append(param)
            s.append("=")
            s.append(value)
        }
        return URLEncoder.encode(s.toString(), "utf8")
    }

}
