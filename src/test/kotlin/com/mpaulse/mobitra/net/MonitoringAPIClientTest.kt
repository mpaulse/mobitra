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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okForContentType
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

private const val HTTP_TIMEOUT = 2000L

class MonitoringAPIClientTest {

    private val client = MonitoringAPIClient(
        "localhost",
        8080,
        "http://localhost:8080/onnet/public/api",
        HTTP_TIMEOUT)

    private val wireMock = WireMockServer()

    @BeforeEach
    fun setUp() {
        wireMock.start()
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Test
    fun `getHuaweiTrafficStatistics - successful response`() {
        stubFor(get(urlEqualTo("/api/monitoring/traffic-statistics"))
            .willReturn(okForContentType("text/html",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <response>
                    <CurrentConnectTime>254310</CurrentConnectTime>
                    <CurrentUpload>787033379</CurrentUpload>
                    <CurrentDownload>20707877903</CurrentDownload>
                    <CurrentDownloadRate>23442</CurrentDownloadRate>
                    <CurrentUploadRate>1704</CurrentUploadRate>
                    <TotalUpload>1140231732</TotalUpload>
                    <TotalDownload>31003595763</TotalDownload>
                    <TotalConnectTime>431017</TotalConnectTime>
                    <showtraffic>1</showtraffic>
                </response>
                """.trimIndent())))

        runBlocking {
            val stats = client.getHuaweiTrafficStatistics()
            assertEquals(stats.sessionConnectionUptime, 254310, "Incorrect session connection uptime")
            assertEquals(stats.sessionTotalBytesUploaded, 787033379, "Incorrect session total bytes uploaded")
            assertEquals(stats.sessionTotalBytesDownloaded, 20707877903, "Incorrect session total bytes downloaded")
            assertEquals(stats.totalBytesDownloaded, 31003595763, "Incorrect total bytes downloaded")
            assertEquals(stats.totalBytesUploaded, 1140231732, "Incorrect total bytes uploaded")
        }
    }

    @Test
    fun `getHuaweiTrafficStatistics - missing response data`() {
        stubFor(get(urlEqualTo("/api/monitoring/traffic-statistics"))
            .willReturn(okForContentType("text/html",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <response>
                    <CurrentDownloadRate>23442</CurrentDownloadRate>
                    <CurrentUploadRate>1704</CurrentUploadRate>
                    <TotalConnectTime>431017</TotalConnectTime>
                    <showtraffic>1</showtraffic>
                </response>
                """.trimIndent())))

        runBlocking {
            val stats = client.getHuaweiTrafficStatistics()
            assertEquals(stats.sessionConnectionUptime, 0, "Incorrect session connection uptime")
            assertEquals(stats.sessionTotalBytesUploaded, 0, "Incorrect session total bytes uploaded")
            assertEquals(stats.sessionTotalBytesDownloaded, 0, "Incorrect session total bytes downloaded")
            assertEquals(stats.totalBytesDownloaded, 0, "Incorrect total bytes downloaded")
            assertEquals(stats.totalBytesUploaded, 0, "Incorrect total bytes uploaded")
        }
    }

    @Test
    fun `getHuaweiTrafficStatistics - blank response`() {
        stubFor(get(urlEqualTo("/api/monitoring/traffic-statistics"))
            .willReturn(ok()))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getHuaweiTrafficStatistics()
            }
        }
    }

    @Test
    fun `getHuaweiTrafficStatistics - server error status`() {
        stubFor(get(urlEqualTo("/api/monitoring/traffic-statistics"))
            .willReturn(serverError()))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getHuaweiTrafficStatistics()
            }
        }
    }

    @Test
    fun `getHuaweiTrafficStatistics - bad XML response`() {
        stubFor(get(urlEqualTo("/api/monitoring/traffic-statistics"))
            .willReturn(okForContentType("text/html",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <response>
                    <CurrentDownloadRate>23442</CurrentDownloadRate>
                    <CurrentUploadRate>1704</Curre
                </response>
                """.trimIndent())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getHuaweiTrafficStatistics()
            }
        }
    }

    @Test
    fun `getHuaweiTrafficStatistics - no connection`() {
        wireMock.stop()

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getHuaweiTrafficStatistics()
            }
        }
    }

    @Test
    fun `getHuaweiTrafficStatistics - response timeout`() {
        stubFor(get(urlEqualTo("/api/monitoring/traffic-statistics"))
            .willReturn(ok().withFixedDelay((HTTP_TIMEOUT + 500).toInt())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getHuaweiTrafficStatistics()
            }
        }
    }

}
