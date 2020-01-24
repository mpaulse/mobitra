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
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okForContentType
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.mpaulse.mobitra.APP_NAME
import com.mpaulse.mobitra.APP_VERSION
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val HTTP_TIMEOUT = 2000L

class MonitoringAPIClientTest {

    private val client = MonitoringAPIClient(
        "localhost",
        8080,
        "localhost",
        8080,
        "localhost",
        8433,
        HTTP_TIMEOUT,
        false)

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
            assertEquals(stats.currentConnectTime, 254310, "Incorrect session connection uptime")
            assertEquals(stats.currentUploadAmount, 787033379, "Incorrect session total bytes uploaded")
            assertEquals(stats.currentDownloadAmount, 20707877903, "Incorrect session total bytes downloaded")
            assertEquals(stats.totalDownloadAmount, 31003595763, "Incorrect total bytes downloaded")
            assertEquals(stats.totalUploadAmount, 1140231732, "Incorrect total bytes uploaded")
        }

        verify(getRequestedFor(
            urlEqualTo("/api/monitoring/traffic-statistics"))
            .withHeader("User-Agent", equalTo("$APP_NAME/$APP_VERSION")))
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
            assertEquals(stats.currentConnectTime, 0, "Incorrect session connection uptime")
            assertEquals(stats.currentUploadAmount, 0, "Incorrect session total bytes uploaded")
            assertEquals(stats.currentDownloadAmount, 0, "Incorrect session total bytes downloaded")
            assertEquals(stats.totalDownloadAmount, 0, "Incorrect total bytes downloaded")
            assertEquals(stats.totalUploadAmount, 0, "Incorrect total bytes uploaded")
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
    fun `getHuaweiTrafficStatistics - bad field data types`() {
        stubFor(get(urlEqualTo("/api/monitoring/traffic-statistics"))
            .willReturn(okForContentType("text/html",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <response>
                    <CurrentConnectTime>sdfsdfsdf</CurrentConnectTime>
                    <CurrentUpload>fewfef</CurrentUpload>
                    <CurrentDownload>dsf32f2</CurrentDownload>
                    <CurrentDownloadRate>sdfsdfsdf</CurrentDownloadRate>
                    <CurrentUploadRate>32r23fse</CurrentUploadRate>
                    <TotalUpload>sdfsdf32wf</TotalUpload>
                    <TotalDownload>sdfdsf32</TotalDownload>
                    <TotalConnectTime>431017</TotalConnectTime>
                    <showtraffic>1</showtraffic>
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
        val sessionToken = "8474625425622783908"
        val msisdn = "0123456789"
        val jSessionIdCookie = "9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579"

        stubFor(get(urlEqualTo("/onnet/public/api/checkOnnet"))
            .willReturn(okJson(
                """
                {
                    "resultCode": 0,
                    "resultMessageCode": "api-co-002",
                    "resultMessage": "Onnet session successfully established.",
                    "friendlyCustomerMessage": "",
                    "payload": {
                        "sessionToken": "$sessionToken",
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
                        "msisdn": "$msisdn"
                    }
                }
                """.trimIndent())
                .withHeader("Set-Cookie", "JSESSIONID=$jSessionIdCookie; path=/; HttpOnly")))
        stubFor(post(urlEqualTo("/onnet/public/api/getFreeResources"))
            .withCookie("JSESSIONID", equalTo(jSessionIdCookie))
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
            assertEquals(3, resources.size, "Incorrect no. resources")

            assertEquals(msisdn, resources[0].msisdn, "Incorrect MSISDN")
            assertEquals("5036", resources[0].type, "Incorrect type")
            assertEquals("Campaign Welcome Bonus Messaging", resources[0].name, "Incorrect name")
            assertEquals("SMS/MMS", resources[0].service, "Incorrect service")
            assertEquals(5, resources[0].availableAmount, "Incorrect available amount")
            assertEquals(0, resources[0].usedAmount, "Incorrect used amount")
            assertEquals(LocalDate.of(2019, 11, 4), resources[0].expiryDate, "Incorrect expiry date")
            assertFalse(resources[0].isMobileData, "Should not be mobile data")

            assertEquals(msisdn, resources[1].msisdn, "Incorrect MSISDN")
            assertEquals("5125", resources[1].type, "Incorrect type")
            assertEquals("Once-off LTE/LTE-A Night Surfer Data", resources[1].name, "Incorrect name")
            assertEquals("GPRS", resources[1].service, "Incorrect service")
            assertEquals(64183731327, resources[1].availableAmount, "Incorrect available amount")
            assertEquals(240778113, resources[1].usedAmount, "Incorrect used amount")
            assertEquals(LocalDate.of(2019, 11, 28), resources[1].expiryDate, "Incorrect expiry date")
            assertTrue(resources[1].isMobileData, "Should be mobile data")

            assertEquals(msisdn, resources[2].msisdn, "Incorrect MSISDN")
            assertEquals("5127", resources[2].type, "Incorrect type")
            assertEquals("Once-off LTE/LTE-A Anytime Data", resources[2].name, "Incorrect name")
            assertEquals("GPRS", resources[2].service, "Incorrect service")
            assertEquals(53002844210, resources[2].availableAmount, "Incorrect available amount")
            assertEquals(11421665230, resources[2].usedAmount, "Incorrect used amount")
            assertEquals(LocalDate.of(2019, 12, 28), resources[2].expiryDate, "Incorrect expiry date")
            assertTrue(resources[2].isMobileData, "Should be mobile data")
        }

        verify(getRequestedFor(
            urlEqualTo("/onnet/public/api/checkOnnet"))
            .withHeader("User-Agent", equalTo("$APP_NAME/$APP_VERSION")))
        verify(postRequestedFor(
            urlEqualTo("/onnet/public/api/createOnnetSession"))
            .withHeader("User-Agent", equalTo("$APP_NAME/$APP_VERSION"))
            .withRequestBody(equalTo("sid=$sessionToken")))
        verify(postRequestedFor(
            urlEqualTo("/onnet/public/api/getFreeResources"))
            .withHeader("User-Agent", equalTo("$APP_NAME/$APP_VERSION"))
            .withRequestBody(equalTo("msisdn=$msisdn")))
    }

    @Test
    fun `getTelkomFreeResources - check Onnet server error status`() {
        stubFor(get(urlEqualTo("/onnet/public/api/checkOnnet"))
            .willReturn(serverError()))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - check Onnet no connection`() {
        wireMock.stop()

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - check Onnet response timeout`() {
        stubFor(get(urlEqualTo("/onnet/public/api/checkOnnet"))
            .willReturn(ok().withFixedDelay((HTTP_TIMEOUT + 500).toInt())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - check Onnet failure result code`() {
        stubFor(get(urlEqualTo("/onnet/public/api/checkOnnet"))
            .willReturn(okJson(
                """
                {
                    "resultCode": 1,
                    "resultMessageCode": "api-co-009",
                    "resultMessage": "Onnet not established.",
                    "friendlyCustomerMessage": "",
                    "payload": {
                    }
                }
                """.trimIndent())))
        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - check Onnet no session token`() {
        stubFor(get(urlEqualTo("/onnet/public/api/checkOnnet"))
            .willReturn(okJson(
                """
                {
                    "resultCode": 0,
                    "resultMessageCode": "api-co-002",
                    "resultMessage": "Onnet session successfully established.",
                    "friendlyCustomerMessage": "",
                    "payload": {
                        "friendlySecurityLevel": "Unprotected",
                        "securityLevel": 0,
                        "response": null,
                        "secureHost": "onnetsecure.telkom.co.za"
                    }
                }
                """.trimIndent())))
        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - check Onnet empty response`() {
        stubFor(get(urlEqualTo("/onnet/public/api/checkOnnet"))
            .willReturn(okJson(
                """
                {
                }
                """.trimIndent())))
        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - check Onnet bad response`() {
        stubFor(get(urlEqualTo("/onnet/public/api/checkOnnet"))
            .willReturn(okJson(
                """
                {
                    "resultCode": 0,
                    "resultMessageCode": "api-co-002",
                    "resultMessage": "Onnet se
                        "response": null,
                        "secureHost": "onnetsecure.telkom.co.za"
                    }
                }
                """.trimIndent())))
        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - create Onnet session bad response`() {
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
                    "friendlyCustomerMessag
                    }
                }
                """.trimIndent())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - create Onnet session empty response`() {
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
                }
                """.trimIndent())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - create Onnet session failure result code`() {
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
                    "resultCode": 1,
                    "resultMessageCode": "api-cos-009",
                    "resultMessage": "Onnet session creation failed",
                    "friendlyCustomerMessage": "",
                    "payload": {
                    }
                }
                """.trimIndent())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - create Onnet session no msisdn and no jSessionId cookie`() {
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
                    }
                }
                """.trimIndent())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - create Onnet session server error status`() {
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
            .willReturn(serverError()))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - create Onnet session response timeout`() {
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
            .willReturn(ok().withFixedDelay((HTTP_TIMEOUT + 500).toInt())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - get free resources bad response`() {
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
                """.trimIndent())
                .withHeader("Set-Cookie", "JSESSIONID=9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579; path=/; HttpOnly")))
        stubFor(post(urlEqualTo("/onnet/public/api/getFreeResources"))
            .withCookie("JSESSIONID", equalTo("9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579"))
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
                                remaining 0 Items used  Expires on Tue Nov 05 2019",
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
                    ]
                }
                """.trimIndent())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - get free resource empty response`() {
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
                """.trimIndent())
                .withHeader("Set-Cookie", "JSESSIONID=9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579; path=/; HttpOnly")))
        stubFor(post(urlEqualTo("/onnet/public/api/getFreeResources"))
            .withCookie("JSESSIONID", equalTo("9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579"))
            .willReturn(okJson(
                """
                {
                }
                """.trimIndent())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - get free resources failure result code`() {
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
                """.trimIndent())
                .withHeader("Set-Cookie", "JSESSIONID=9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579; path=/; HttpOnly")))
        stubFor(post(urlEqualTo("/onnet/public/api/getFreeResources"))
            .withCookie("JSESSIONID", equalTo("9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579"))
            .willReturn(okJson(
                """
                {
                    "resultCode": 1,
                    "resultMessageCode": "api-gfr-010",
                    "resultMessage": "Free resources retrieval failed",
                    "friendlyCustomerMessage": "",
                    "payload": [
                    ]
                }
                """.trimIndent())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - get free resources empty payload`() {
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
                """.trimIndent())
                .withHeader("Set-Cookie", "JSESSIONID=9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579; path=/; HttpOnly")))
        stubFor(post(urlEqualTo("/onnet/public/api/getFreeResources"))
            .withCookie("JSESSIONID", equalTo("9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579"))
            .willReturn(okJson(
                """
                {
                    "resultCode": 0,
                    "resultMessageCode": "api-gfr-009",
                    "resultMessage": "Free resources successfully retrieved",
                    "friendlyCustomerMessage": "",
                    "payload": [
                    ]
                }
                """.trimIndent())))

        runBlocking {
            assertEquals(0, client.getTelkomFreeResources().size, "Incorrect no. free resources")
        }
    }

    @Test
    fun `getTelkomFreeResources - get free resources server error code`() {
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
                """.trimIndent())
                .withHeader("Set-Cookie", "JSESSIONID=9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579; path=/; HttpOnly")))
        stubFor(post(urlEqualTo("/onnet/public/api/getFreeResources"))
            .withCookie("JSESSIONID", equalTo("9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579"))
            .willReturn(serverError()))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - get free resources response timeout`() {
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
                """.trimIndent())
                .withHeader("Set-Cookie", "JSESSIONID=9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579; path=/; HttpOnly")))
        stubFor(post(urlEqualTo("/onnet/public/api/getFreeResources"))
            .withCookie("JSESSIONID", equalTo("9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579"))
            .willReturn(ok().withFixedDelay((HTTP_TIMEOUT + 500).toInt())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

    @Test
    fun `getTelkomFreeResources - get free resources invalid fields`() {
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
                """.trimIndent())
                .withHeader("Set-Cookie", "JSESSIONID=9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579; path=/; HttpOnly")))
        stubFor(post(urlEqualTo("/onnet/public/api/getFreeResources"))
            .withCookie("JSESSIONID", equalTo("9QyJpqFGYM9PHhQ5W11LkbTQ7wrqtvFqpq3Qm2KLMLcG6hHd9yDv!-372627579"))
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
                                "usedAmount": "sdfdsfs33",
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
                                "endBillCycle": "dfgerg43gf43",
                                "isTimeBased": false
                            },
                            "info": "GPRS: 53002844210 Bytes remaining 11421665230 Bytes used  Expires on Sun Dec 29 2019",
                            "service": "GPRS"
                        }
                    ]
                }
                """.trimIndent())))

        assertThrows<MonitoringAPIException>("MonitoringAPIException not thrown") {
            runBlocking() {
                client.getTelkomFreeResources()
            }
        }
    }

}
