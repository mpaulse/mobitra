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
import com.mpaulse.mobitra.APP_NAME
import com.mpaulse.mobitra.APP_VERSION
import com.mpaulse.mobitra.devModeEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.InputStream
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

private const val HTTP_USER_AGENT = "$APP_NAME/$APP_VERSION"
private const val HTTP_TIMEOUT_MILLIS = 15000L
private const val TELKOM_ONNET_BASE_PATH = "/onnet/public/api"

class MonitoringAPIException(
    message: String,
    cause: Throwable? = null
): Exception(message, cause)

class MonitoringAPIClient(
    var huaweiHost: String,
    private val huaweiPort: Int = 80,
    private val telkomOnnetHttpHost: String = if (!devModeEnabled) "onnet.telkom.co.za" else "localhost",
    private val telkomOnnetHttpPort: Int = if (!devModeEnabled) 80 else 8880,
    private val telkomOnnetHttpsHost: String = if (!devModeEnabled) "onnetsecure.telkom.co.za" else "localhost",
    private val telkomOnnetHttpsPort: Int = if (!devModeEnabled) 443 else 8843,
    private val timeout: Long = HTTP_TIMEOUT_MILLIS,
    validateSSLCert: Boolean = !devModeEnabled
) {

    private val httpClient: HttpClient

    private val xmlMapper = XmlMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES)
    private val jsonMapper = jacksonObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES)

    init {
        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(
            null,
            if (validateSSLCert) null else arrayOf(NonValidatingTrustManager()),
            null)
        httpClient = HttpClient.newBuilder().sslContext(sslContext).sslParameters(sslContext.supportedSSLParameters).build()
    }

    suspend fun getHuaweiTrafficStatistics(): HuaweiTrafficStats = withContext(Dispatchers.IO) {
        try {
            xmlMapper.readValue(
                doHttpGet("http://$huaweiHost:$huaweiPort/api/monitoring/traffic-statistics").body(),
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
            if (createSessionRsp.resultCode == 0 && createSessionRsp.msisdn != null && createSessionRsp.jSessionIdCookie != null) {
                yield()
                val freeResourcesRsp = getTelkomFreeResources(createSessionRsp.msisdn, createSessionRsp.jSessionIdCookie)
                if (freeResourcesRsp.resultCode == 0) {
                    return mergeTelkomFreeResources(freeResourcesRsp.freeResources)
                } else {
                    throw MonitoringAPIException("Failed to retrieve Telkom free resources:\n$freeResourcesRsp")
                }
            } else {
                throw MonitoringAPIException("Failed to create Telkom Onnet session:\n$createSessionRsp")
            }
        }
        throw MonitoringAPIException("Failed to check for Telkom Onnet:\n$checkRsp")
    }

    private fun mergeTelkomFreeResources(freeResources: Array<TelkomFreeResource>): Array<TelkomFreeResource> {
        val merged = mutableMapOf<UUID, TelkomFreeResource>()
        for (r in freeResources) {
            val r2 = merged[r.id]
            if (r2 != null) {
               merged[r.id] = TelkomFreeResource(
                   r.msisdn,
                   r.type,
                   r.name,
                   r.service,
                   r.availableAmount + r2.availableAmount,
                   r.usedAmount + r2.usedAmount,
                   r.expiryDate)
            } else {
                merged[r.id] = r
            }
        }
        return merged.values.toTypedArray()
    }

    suspend fun checkTelkomOnnet(): TelkomCheckOnnetResponse = withContext(Dispatchers.IO) {
        try {
            jsonMapper.readValue(
                doHttpGet("http://$telkomOnnetHttpHost:$telkomOnnetHttpPort$TELKOM_ONNET_BASE_PATH/checkOnnet").body(),
                TelkomCheckOnnetResponse::class.java)
        } catch (e: MonitoringAPIException) {
            throw e
        } catch (e: Exception) {
            throw MonitoringAPIException("Failed to check for Telkom Onnet", e)
        }
    }

    private suspend fun createTelkomOnnetSession(sessionToken: String): TelkomCreateOnnetSessionResponse = withContext(Dispatchers.IO) {
        try {
            val rsp = doUrlEncodedHttpPost(
                "https://$telkomOnnetHttpsHost:$telkomOnnetHttpsPort$TELKOM_ONNET_BASE_PATH/createOnnetSession",
                mapOf("sid" to sessionToken))
            var jSessionIdCookie: String? = null
            val cookies = rsp.headers().allValues("Set-Cookie")
            for (cookie in cookies) {
                val nameValue = cookie.split("=", limit = 2)
                if (nameValue.size == 2 && nameValue[0] == "JSESSIONID") {
                    jSessionIdCookie = nameValue[1].substringBefore(";")
                    break
                }
            }
            jsonMapper
                .reader()
                .forType(TelkomCreateOnnetSessionResponse::class.java)
                .withAttribute("jSessionIdCookie", jSessionIdCookie)
                .readValue<TelkomCreateOnnetSessionResponse>(rsp.body())
        } catch (e: MonitoringAPIException) {
            throw e
        } catch (e: Exception) {
            throw MonitoringAPIException("Failed to create Telkom Onnet session", e)
        }
    }

    private suspend fun getTelkomFreeResources(msisdn: String, jSessionIdCookie: String): TelkomFreeResourcesResponse = withContext(Dispatchers.IO) {
        try {
            val rsp = doUrlEncodedHttpPost(
                "https://$telkomOnnetHttpsHost:$telkomOnnetHttpsPort$TELKOM_ONNET_BASE_PATH/getFreeResources",
                mapOf("msisdn" to msisdn),
                mapOf("Cookie" to "JSESSIONID=$jSessionIdCookie"))
            jsonMapper
                .reader()
                .forType(TelkomFreeResourcesResponse::class.java)
                .withAttribute("msisdn", msisdn)
                .readValue<TelkomFreeResourcesResponse>(rsp.body())
        } catch (e: MonitoringAPIException) {
            throw e
        } catch (e: Exception) {
            throw MonitoringAPIException("Failed to retrieve Telkom free resources", e)
        }
    }

    private fun doHttpGet(uri: String): HttpResponse<InputStream> {
        return doHttpRequest(HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .header("User-Agent", HTTP_USER_AGENT)
            .timeout(Duration.ofMillis(timeout))
            .GET()
            .build())
    }

    private fun doUrlEncodedHttpPost(
        uri: String,
        params: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse<InputStream> {
        val reqBuilder = HttpRequest.newBuilder()
        for ((name, value) in headers) {
            reqBuilder.header(name, value)
        }
        return doHttpRequest(reqBuilder
            .uri(URI.create(uri))
            .header("User-Agent", HTTP_USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofMillis(timeout))
            .POST(BodyPublishers.ofString(urlEncode(params)))
            .build())
    }

    private fun doHttpRequest(req: HttpRequest): HttpResponse<InputStream> {
        val rsp = httpClient.send(req, BodyHandlers.ofInputStream())
        val status = rsp.statusCode()
        if (rsp.statusCode() < 200 || rsp.statusCode() >= 300) {
            throw MonitoringAPIException(
                "${req.method()} ${req.uri()} failed: response status $status\n"
                + "${rsp.headers()}\n${String(rsp.body().readAllBytes())}")
        }
        return rsp
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
            s.append(URLEncoder.encode(param, "utf8"))
            s.append("=")
            s.append(URLEncoder.encode(value, "utf8"))
        }
        return s.toString()
    }

}

private class NonValidatingTrustManager : X509ExtendedTrustManager() {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = Unit
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = Unit
    override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
}
