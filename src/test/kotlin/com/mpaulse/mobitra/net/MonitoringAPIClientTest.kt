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
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import kotlin.test.assertEquals

private const val HTTP_TIMEOUT = 2000L

private fun createMockSSLContext(): SSLContext {
    val context= SSLContext.getInstance("TLS")
    context.init(null, arrayOf(MockTrustManager()), null)
    return context
}

private class MockTrustManager: X509ExtendedTrustManager() {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
    override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
}

class MonitoringAPIClientTest {

    private val client = MonitoringAPIClient(
        "localhost",
        "localhost",
        "localhost",
        8080,
        8433,
        createMockSSLContext(),
        HTTP_TIMEOUT)

    private val wireMock = WireMockServer(
        options()
            .port(8080)
            .httpsPort(8433)
            .notifier(ConsoleNotifier(false)))

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

    @Test
    fun `getTelkomFreeResources - successful response`() {
        stubFor(get(urlEqualTo("/onnet/public/api/checkOnnet"))
            .willReturn(okJson(
                """
                {
                    "resultCode": 0,
                    "resultMessageCode": "api-co-002",
                    "resultMessage": "Onnet session successfully established.",
                    "friendlyCustomerMessage": "",
                    "payload": {
                        "sessionToken": "8474625425622783908",
                        "friendlySecurityLevel": "Unprotected",
                        "securityLevel": 0,
                        "response": null,
                        "secureHost": "onnetsecure.telkom.co.za"
                    }
                }
                """.trimIndent())))
        stubFor(post(urlEqualTo("/onnet/public/api/createOnnetSession"))
            .willReturn(okJson(
                """
                {
                    "resultCode": 0,
                    "resultMessageCode": "api-cos-005",
                    "resultMessage": "Onnet session created",
                    "friendlyCustomerMessage": "",
                    "payload": {
                        "msisdn": "0123456789"
                    }
                }
                """.trimIndent())))
        stubFor(post(urlEqualTo("/onnet/public/api/getFreeResources"))
            .willReturn(okJson(
                """
                {
                    "resultCode": 0,
                    "resultMessageCode": "api-gfr-009",
                    "resultMessage": "Free resources successfully retrieved",
                    "friendlyCustomerMessage": "",
                    "payload": [
                        {
                            "subscriberFreeResource": {
                                "type": "5036",
                                "typeName": "Campaign Welcome Bonus Messaging",
                                "service": "SMS/MMS",
                                "totalAmount": "5",
                                "totalAmountAndMeasure": "5 Items",
                                "usedAmount": "0",
                                "usedAmountAndMeasure": "0 Items",
                                "measure": "Items",
                                "startBillCycle": "Tue Nov 05 2019",
                                "endBillCycle": "00:00:00 Tue Nov 05 2019",
                                "isTimeBased": false
                            },
                            "info": "SMS/MMS: 5 Items remaining 0 Items used  Expires on Tue Nov 05 2019",
                            "service": "SMS/MMS"
                        },
                        {
                            "subscriberFreeResource": {
                                "type": "5125",
                                "typeName": "Once-off LTE/LTE-A Night Surfer Data",
                                "service": "GPRS",
                                "totalAmount": "64183731327",
                                "totalAmountAndMeasure": "61210 MB",
                                "usedAmount": "240778113",
                                "usedAmountAndMeasure": "230 MB",
                                "measure": "Bytes",
                                "startBillCycle": "Fri Nov 29 2019",
                                "endBillCycle": "00:00:00 Fri Nov 29 2019",
                                "isTimeBased": false
                            },
                            "info": "GPRS: 64183731327 Bytes remaining 240778113 Bytes used  Expires on Fri Nov 29 2019",
                            "service": "GPRS"
                        },
                        {
                            "subscriberFreeResource": {
                                "type": "5127",
                                "typeName": "Once-off LTE/LTE-A Anytime Data",
                                "service": "GPRS",
                                "totalAmount": "53002844210",
                                "totalAmountAndMeasure": "50547 MB",
                                "usedAmount": "11421665230",
                                "usedAmountAndMeasure": "10893 MB",
                                "measure": "Bytes",
                                "startBillCycle": "Sun Dec 29 2019",
                                "endBillCycle": "00:00:00 Sun Dec 29 2019",
                                "isTimeBased": false
                            },
                            "info": "GPRS: 53002844210 Bytes remaining 11421665230 Bytes used  Expires on Sun Dec 29 2019",
                            "service": "GPRS"
                        }
                    ]
                }
                """.trimIndent())))

        runBlocking {
            val resources = client.getTelkomFreeResources()
            for (r in resources) {
                println(r)
            }
        }
    }

}
