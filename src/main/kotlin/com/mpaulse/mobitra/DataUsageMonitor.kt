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

package com.mpaulse.mobitra

import com.mpaulse.mobitra.data.MobileDataProduct
import com.mpaulse.mobitra.data.MobileDataProductDB
import com.mpaulse.mobitra.data.MobileDataProductType
import com.mpaulse.mobitra.data.MobileDataProductType.ANYTIME
import com.mpaulse.mobitra.data.MobileDataProductType.NIGHT_SURFER
import com.mpaulse.mobitra.data.MobileDataProductType.OFF_PEAK
import com.mpaulse.mobitra.data.MobileDataProductType.UNSPECIFIED
import com.mpaulse.mobitra.data.MobileDataUsage
import com.mpaulse.mobitra.net.HuaweiMonitoringInfo
import com.mpaulse.mobitra.net.LTE_ONCE_OFF_10MBPS_OFF_PEAK_DATA_RESOURCE_TYPE
import com.mpaulse.mobitra.net.LTE_ONCE_OFF_4MBPS_OFF_PEAK_DATA_RESOURCE_TYPE
import com.mpaulse.mobitra.net.LTE_ONCE_OFF_ANYTIME_DATA_RESOURCE_TYPE
import com.mpaulse.mobitra.net.LTE_ONCE_OFF_NIGHT_SURFER_DATA_RESOURCE_TYPE
import com.mpaulse.mobitra.net.MonitoringAPIClient
import com.mpaulse.mobitra.net.TelkomFreeResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.HOURS
import java.time.temporal.ChronoUnit.MILLIS
import java.util.Collections
import java.util.Locale
import java.util.UUID

class DataUsageMonitor(
    routerIPAddress: String?,
    private val productDB: MobileDataProductDB,
    private val listener: DataUsageMonitorListener
): CoroutineScope by MainScope() {

    private var monitoringAPIClient: MonitoringAPIClient? = null
    private var monitoringJob: Job? = null
    private val activeProductsMap = mutableMapOf<UUID, MobileDataProduct>()
    private var activeProductInUse: MobileDataProduct? = null
    private var downloadAmountUnrecorded = 0L
    private var uploadAmountUnrecorded = 0L
    private var updateProducts = true
    private val logger = LoggerFactory.getLogger(DataUsageMonitor::class.java)

    var routerIPAddress: String? = null
        set(value) {
            if (value != null) {
                if (monitoringAPIClient == null) {
                    monitoringAPIClient = MonitoringAPIClient(value)
                } else {
                    monitoringAPIClient?.huaweiHost = value
                }
            } else {
                monitoringAPIClient = null
            }
        }

    val activeProducts: Map<UUID, MobileDataProduct>
        get() = Collections.unmodifiableMap(activeProductsMap)

    val currentProduct: MobileDataProduct?
        get() = activeProductInUse

    init {
        for (product in productDB.getActiveProducts()) {
            activeProductsMap[product.id] = product
        }
        this.routerIPAddress = routerIPAddress
    }

    fun start() {
        monitoringJob = launch(Dispatchers.Default) {
            updateProductInfo()
        }
    }

    fun stop() {
        monitoringJob?.cancel()

        val product = activeProductInUse
        if (product != null) {
            val dataUsage = MobileDataUsage(downloadAmount = downloadAmountUnrecorded, uploadAmount = uploadAmountUnrecorded)
            if (dataUsage.totalAmount > 0) {
                if (!product.isUnlimited) {
                    product.availableAmount -= dataUsage.totalAmount
                }
                product.usedAmount += dataUsage.totalAmount

                if (logger.isDebugEnabled) {
                    logger.debug("Store product: $product")
                }
                productDB.storeProduct(product)

                if (logger.isDebugEnabled) {
                    logger.debug("Add data usage:\n\tproduct: $product\n\tusage: $dataUsage")
                }
                productDB.addDataUsage(product, dataUsage)
            }
        }
    }

    private suspend fun updateProductInfo() {
        pollDataUsage().collect { dataUsage ->
            if (logger.isDebugEnabled) {
                logger.debug("Data usage event: $dataUsage")
            }
            try {
                yield()
                val telkomFreeResources = monitoringAPIClient?.getTelkomFreeResources()
                if (telkomFreeResources != null) {
                    var msisdn: String? = null
                    for (resource in telkomFreeResources) {
                        val product = resourceToProduct(resource) ?: continue
                        if (msisdn == null) {
                            msisdn = product.msisdn
                        }

                        val prevProductInfo = productDB.getProduct(product.id)
                        val prevUsedAmount = prevProductInfo?.usedAmount ?: 0

                        if (logger.isDebugEnabled) {
                            logger.debug("Store product: $product")
                        }
                        productDB.storeProduct(product)
                        activeProductsMap[product.id] = product

                        if (product.usedAmount != prevUsedAmount) {
                            val uncategorisedAmount = product.usedAmount - prevUsedAmount
                            var dataUsageToAdd =
                                if (activeProductInUse?.id == product.id) {
                                    dataUsage.copy(uncategorisedAmount = uncategorisedAmount - (dataUsage.downloadAmount + dataUsage.uploadAmount))
                                } else {
                                    MobileDataUsage(uncategorisedAmount = uncategorisedAmount)
                                }
                            if (!product.isUnlimited && dataUsageToAdd.totalAmount > product.availableAmount) {
                                val overExhaustedAmount = dataUsageToAdd.totalAmount - product.availableAmount
                                if (logger.isDebugEnabled) {
                                    logger.debug("Forcing zero available amount for over-exhausted product: $product")
                                    logger.debug("Over-exhausted amount: $overExhaustedAmount")
                                }
                                dataUsageToAdd = dataUsageToAdd.copy(uncategorisedAmount = dataUsageToAdd.uncategorisedAmount - overExhaustedAmount)
                                product.availableAmount = 0
                                productDB.storeProduct(product)
                            }
                            if (dataUsageToAdd.totalAmount != 0L) {
                                if (logger.isDebugEnabled) {
                                    logger.debug("Add data usage:\n\tproduct: $product\n\tusage: $dataUsageToAdd")
                                }
                                productDB.addDataUsage(product, dataUsageToAdd)
                            }
                        }
                    }

                    val expiredProductIds = mutableListOf<UUID>()
                    for (product in activeProductsMap.values) {
                        if (product.isExpired) {
                            expiredProductIds += product.id
                        }
                    }
                    for (productId in expiredProductIds) {
                        activeProductsMap.remove(productId)
                    }

                    activeProductInUse = if (msisdn != null) getActiveProductInUse(msisdn) else null
                    if (logger.isDebugEnabled) {
                        logger.debug("Active product: $activeProductInUse")
                    }

                    listener.onActiveProductsUpdate()
                }
                updateProducts = false
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                updateProducts = true
                listener.onDataUsageMonitoringException(
                    DataUsageMonitoringException("Error retrieving usage information from Telkom", e))
            }
        }
    }

    private fun pollDataUsage() = flow {
        var lastMonitoringInfo: HuaweiMonitoringInfo? = null
        var downloadAmountPolled: Long
        var downloadAmountTotal = 0L
        var uploadAmountPolled: Long
        var uploadAmountTotal = 0L
        val pollDelay = 5000L

        while (true) {
            try {
                yield()
                val monitoringInfo = monitoringAPIClient?.getHuaweiMonitoringInfo()
                if (monitoringInfo != null) {
                    downloadAmountPolled = -1L
                    uploadAmountPolled = -1L
                    if (lastMonitoringInfo != null
                            && monitoringInfo.deviceInfo.deviceName == lastMonitoringInfo.deviceInfo.deviceName
                            && monitoringInfo.wirelessLANSettings.ssid == lastMonitoringInfo.wirelessLANSettings.ssid) {
                        downloadAmountPolled = monitoringInfo.trafficStats.currentDownloadAmount - lastMonitoringInfo.trafficStats.currentDownloadAmount
                        uploadAmountPolled = monitoringInfo.trafficStats.currentUploadAmount - lastMonitoringInfo.trafficStats.currentUploadAmount
                    }
                    if (downloadAmountPolled >= 0 && uploadAmountPolled >= 0) {
                        downloadAmountUnrecorded += downloadAmountPolled
                        downloadAmountTotal += downloadAmountPolled
                        uploadAmountUnrecorded += uploadAmountPolled
                        uploadAmountTotal += uploadAmountPolled
                        listener.onDataTrafficUpdate(
                            MobileDataUsage(downloadAmount = downloadAmountPolled, uploadAmount = uploadAmountPolled),
                            MobileDataUsage(downloadAmount = downloadAmountTotal, uploadAmount = uploadAmountTotal))
                    }
                    if (logger.isDebugEnabled) {
                        logger.debug("Unrecorded traffic: download = $downloadAmountUnrecorded B, upload = $uploadAmountUnrecorded B")
                    }

                    // Emit event if:
                    // - the data usage needs to be recorded and products from Telkom refreshed
                    // - there is a router restart or a switch a different router (d < 0 or u < 0)
                    // - the product remaining amount gets consumed
                    if (updateProducts
                            || (downloadAmountPolled < 0 || uploadAmountPolled < 0)
                            || (activeProductInUse != null
                                && !activeProductInUse!!.isUnlimited
                                && (downloadAmountUnrecorded + uploadAmountUnrecorded) >= activeProductInUse!!.availableAmount)) {
                        emit(MobileDataUsage(
                            downloadAmount = downloadAmountUnrecorded,
                            uploadAmount = uploadAmountUnrecorded))
                        if (!updateProducts) {
                            downloadAmountUnrecorded = 0
                            uploadAmountUnrecorded = 0
                        }
                    }

                    lastMonitoringInfo = monitoringInfo
                }

                // Delay until just the next hour mark when we emit an event,
                // or within 5 seconds, whichever comes first.
                val now = LocalDateTime.now()
                var t = now.until(now.truncatedTo(HOURS).plusHours(1), MILLIS)
                if (t > pollDelay) {
                    t = pollDelay
                } else {
                    updateProducts = true
                }
                if (logger.isDebugEnabled) {
                    logger.debug("Poll in ${t}ms")
                }
                delay(t)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                updateProducts = true
                lastMonitoringInfo = null
                listener.onDataUsageMonitoringException(
                    DataUsageMonitoringException("Error retrieving data traffic information from router", e))
                delay(pollDelay)
            }
        }
    }

    private fun resourceToProduct(resource: TelkomFreeResource): MobileDataProduct? {
        val activationDate = resource.activationDate ?: return null
        var type = when (resource.type) {
            LTE_ONCE_OFF_ANYTIME_DATA_RESOURCE_TYPE -> ANYTIME
            LTE_ONCE_OFF_NIGHT_SURFER_DATA_RESOURCE_TYPE -> NIGHT_SURFER
            LTE_ONCE_OFF_4MBPS_OFF_PEAK_DATA_RESOURCE_TYPE,
            LTE_ONCE_OFF_10MBPS_OFF_PEAK_DATA_RESOURCE_TYPE -> OFF_PEAK
            else -> UNSPECIFIED
        }
        if (type == UNSPECIFIED) {
            if ("off peak" in resource.name.lowercase(Locale.getDefault())) {
                type = OFF_PEAK // Unlimited
            } else {
                return null
            }
        }
        return MobileDataProduct(
            resource.id,
            resource.msisdn,
            resource.name,
            type,
            resource.availableAmount,
            resource.usedAmount,
            activationDate,
            resource.expiryDate)
    }

    private fun getActiveProductInUse(msisdn: String): MobileDataProduct? {
        var productInUse: MobileDataProduct? = null
        val now = LocalDateTime.now()
        if (now.hour in 0..6) {
            productInUse = getProductInUseOfType(msisdn, NIGHT_SURFER)
        }
        if (productInUse == null && now.hour in 0..18) {
            productInUse = getProductInUseOfType(msisdn, OFF_PEAK)
        }
        if (productInUse == null) {
            productInUse = getProductInUseOfType(msisdn, ANYTIME)
        }
        return productInUse
    }

    private fun getProductInUseOfType(msisdn: String, type: MobileDataProductType): MobileDataProduct? {
        val candidateProducts = mutableListOf<MobileDataProduct>()
        for (product in activeProductsMap.values) {
            if (product.msisdn == msisdn
                    && product.type == type
                    && (product.isUnlimited || (!product.isUnlimited && product.availableAmount > 0))) {
                if (candidateProducts.isEmpty()) {
                    candidateProducts += product
                } else {
                    val p = candidateProducts[0]
                    if (product.activationDate < p.activationDate) {
                        candidateProducts.clear()
                    }
                    if (product.activationDate <= p.activationDate) {
                        candidateProducts += product
                    }
                }
            }
        }
        candidateProducts.sortBy {
            it.initialAvailableAmount
        }
        return if (candidateProducts.isEmpty()) null
            else candidateProducts[candidateProducts.size - 1]
    }

}

class DataUsageMonitoringException(
    message: String, cause: Throwable? = null
): Exception(message, cause)
