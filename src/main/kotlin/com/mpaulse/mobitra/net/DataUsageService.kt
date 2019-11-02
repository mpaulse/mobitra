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

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

private const val HTTP_TIMEOUT_SEC = 15L

class DataUsageServiceException(
    message: String
): IOException(message)

class DataUsageService(
    huaweiIpAddr: String
) {

    private val httpClient = HttpClient.newBuilder().build()

    private val huaweiTrafficStatsReq = HttpRequest.newBuilder()
        .uri(URI.create("http://${huaweiIpAddr}/api/monitoring/traffic-statistics"))
        .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SEC))
        .GET()
        .build()

    private val xmlMapper = XmlMapper()
    private val jsonMapper = jacksonObjectMapper()

    suspend fun getHuaweiTrafficStatistics(): HuaweiTrafficStats? = withContext(Dispatchers.IO) {
        val rsp = httpClient.send(huaweiTrafficStatsReq, BodyHandlers.ofInputStream())
        val status = rsp.statusCode()
        if (rsp.statusCode() < 200 || rsp.statusCode() >= 300) {
            throw DataUsageServiceException("Unable to get Huawei traffic statistics (response status $status)")
        }
        xmlMapper.readValue(rsp.body(), HuaweiTrafficStats::class.java)
    }

    suspend fun getTelkomFreeResources() {

    }

}
