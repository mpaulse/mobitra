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

package com.mpaulse.mobitra

import com.github.jknack.handlebars.Helper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.Admin
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.PostServeAction
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import java.time.LocalDateTime

private var anytimeUsedAmount = 11_421_665_230L
private var nightSurferUsedAmount = 240_778_113L
private val usageAmountUpdater = UsageAmountUpdater()

private class UsageAmountUpdater: PostServeAction() {

    override fun getName() = "UsageAmountUpdater"

    override fun doAction(serveEvent: ServeEvent?, admin: Admin?, parameters: Parameters?) {
        if (LocalDateTime.now().hour in 0..7) {
            nightSurferUsedAmount = getUpdatedAmount(nightSurferUsedAmount)
        } else {
            anytimeUsedAmount = getUpdatedAmount(anytimeUsedAmount)
        }
    }

    private fun getUpdatedAmount(amount: Long): Long {
        val a = amount - 52_428_800
        return if (a < 0) 0 else a
    }

}

private fun simulateGoodHost() {
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
        .withPostServeAction(usageAmountUpdater.name, Parameters.empty())
        .willReturn(okJson(
            """
                {
                    "resultCode": 0,
                    "resultMessageCode": "api-cos-005",
                    "resultMessage": "Onnet session created",
                    "friendlyCustomerMessage": "",
                    "payload": {
                        "msisdn": "0678912345"
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
                                "startBillCycle": "{{now offset='5 days' format='EEE MMM dd yyyy'}}",
                                "endBillCycle": "00:00:00 {{now offset='5 days' format='EEE MMM dd yyyy'}}",
                                "isTimeBased": false
                            },
                            "info": "SMS/MMS: 5 Items remaining 0 Items used  Expires on {{now offset='5 days' format='EEE MMM dd yyyy'}}",
                            "service": "SMS/MMS"
                        },
                        {
                            "subscriberFreeResource": {
                                "type": "5125",
                                "typeName": "Once-off LTE/LTE-A Night Surfer Data",
                                "service": "GPRS",
                                "totalAmount": "241591910400",
                                "totalAmountAndMeasure": "230400 MB",
                                "usedAmount": "{{nightSurferUsedAmount}}",
                                "usedAmountAndMeasure": "{{nightSurferUsedAmountFormatted}}",
                                "measure": "Bytes",
                                "startBillCycle": "{{now offset='20 days' format='EEE MMM dd yyyy'}}",
                                "endBillCycle": "00:00:00 {{now offset='20 days' format='EEE MMM dd yyyy'}}",
                                "isTimeBased": false
                            },
                            "info": "GPRS: 64183731327 Bytes remaining 240778113 Bytes used  Expires on {{now offset='20 days' format='EEE MMM dd yyyy'}}",
                            "service": "GPRS"
                        },
                        {
                            "subscriberFreeResource": {
                                "type": "5127",
                                "typeName": "Once-off LTE/LTE-A Anytime Data",
                                "service": "GPRS",
                                "totalAmount": "241591910400",
                                "totalAmountAndMeasure": "230400 MB",
                                "usedAmount": "{{anytimeUsedAmount}}",
                                "usedAmountAndMeasure": "{{anytimeUsedAmountFormatted}}",
                                "measure": "Bytes",
                                "startBillCycle": "{{now offset='50 days' format='EEE MMM dd yyyy'}}",
                                "endBillCycle": "00:00:00 {{now offset='50 days' format='EEE MMM dd yyyy'}}",
                                "isTimeBased": false
                            },
                            "info": "GPRS: 53002844210 Bytes remaining 11421665230 Bytes used  Expires on {{now offset='50 days' format='EEE MMM dd yyyy'}}",
                            "service": "GPRS"
                        }
                    ]
                }
                """.trimIndent())))
}

private fun simulateBadHost() {
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
}

fun main(args: Array<String>) {
    val wireMock = WireMockServer(
        WireMockConfiguration.options()
            .port(8880)
            .httpsPort(8843)
            .notifier(ConsoleNotifier(true))
            .extensions(
                usageAmountUpdater,
                ResponseTemplateTransformer(
                    true,
                    mapOf(
                        "anytimeUsedAmount" to Helper { _: Any, _ -> "$anytimeUsedAmount" },
                        "anytimeUsedAmountFormatted" to Helper { _: Any, _ -> DataAmountStringFormatter.toString(anytimeUsedAmount) },
                        "nightSurferUsedAmount" to Helper { _: Any, _ -> "$nightSurferUsedAmount" },
                        "nightSurferUsedAmountFormatted" to Helper { _: Any, _ -> DataAmountStringFormatter.toString(nightSurferUsedAmount) }))))
    configureFor("localhost", 8880)
    wireMock.start()
    if (args.isNotEmpty() && args[0] == "-bad") {
        simulateBadHost()
        println("Simulating bad host")
    } else {
        simulateGoodHost()
        println("Simulating good host")
    }
}
