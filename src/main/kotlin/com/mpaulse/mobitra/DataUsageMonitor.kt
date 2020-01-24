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

import com.mpaulse.mobitra.data.MobileDataProduct
import com.mpaulse.mobitra.data.MobileDataProductDB
import com.mpaulse.mobitra.data.MobileDataProductType
import com.mpaulse.mobitra.data.MobileDataUsage
import com.mpaulse.mobitra.net.HuaweiTrafficStats
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.HOURS
import java.time.temporal.ChronoUnit.MILLIS
import java.util.Collections
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
                product.availableAmount -= dataUsage.totalAmount
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
                        if (logger.isDebugEnabled) {
                            logger.debug("Store product: $product")
                        }
                        productDB.storeProduct(product)
                        val prevUsedAmount = activeProductsMap[product.id]?.usedAmount ?: 0
                        if (product.usedAmount != prevUsedAmount) {
                            activeProductsMap[product.id] = product
                            var uncategorisedAmount = product.usedAmount - prevUsedAmount
                            val dataUsageToAdd =
                                if (activeProductInUse?.id == product.id) {
                                    dataUsage.copy(uncategorisedAmount = uncategorisedAmount - (dataUsage.downloadAmount + dataUsage.uploadAmount))
                                } else {
                                    MobileDataUsage(uncategorisedAmount = uncategorisedAmount)
                                }
                            if (logger.isDebugEnabled) {
                                logger.debug("Add data usage:\n\tproduct: $product\n\tusage: $dataUsageToAdd")
                            }
                            productDB.addDataUsage(product, dataUsageToAdd)
                        }
                    }

                    purgeExpiredProducts()

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
        var lastTrafficStats: HuaweiTrafficStats? = null
        var downloadAmountPolled: Long
        var downloadAmountTotal = 0L
        var uploadAmountPolled: Long
        var uploadAmountTotal = 0L

        while (true) {
            try {
                yield()
                val trafficStats = monitoringAPIClient?.getHuaweiTrafficStatistics()
                if (trafficStats != null) {
                    downloadAmountPolled = -1L
                    uploadAmountPolled = -1L
                    if (lastTrafficStats != null) {
                        downloadAmountPolled = trafficStats.currentDownloadAmount - lastTrafficStats.currentDownloadAmount
                        uploadAmountPolled = trafficStats.currentUploadAmount - lastTrafficStats.currentUploadAmount
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
                                && (downloadAmountUnrecorded + uploadAmountUnrecorded) >= activeProductInUse!!.availableAmount)) {
                        emit(MobileDataUsage(
                            downloadAmount = downloadAmountUnrecorded,
                            uploadAmount = uploadAmountUnrecorded))
                        if (!updateProducts) {
                            downloadAmountUnrecorded = 0
                            uploadAmountUnrecorded = 0
                        }
                    }

                    lastTrafficStats = trafficStats
                }

                // Delay until just the next hour mark when we emit an event,
                // or within 5 seconds, whichever comes first.
                val now = LocalDateTime.now()
                var t = now.until(now.truncatedTo(HOURS).plusHours(1), MILLIS)
                if (t > 5000) {
                    t = 5000
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
                listener.onDataUsageMonitoringException(
                    DataUsageMonitoringException("Error retrieving data traffic information from router", e))
            }
        }
    }

    private fun resourceToProduct(resource: TelkomFreeResource): MobileDataProduct? {
        val activationDate = resource.activationDate ?: return null
        val type = when (resource.type) {
            LTE_ONCE_OFF_ANYTIME_DATA_RESOURCE_TYPE -> MobileDataProductType.ANYTIME
            LTE_ONCE_OFF_NIGHT_SURFER_DATA_RESOURCE_TYPE -> MobileDataProductType.NIGHT_SURFER
            else -> MobileDataProductType.UNSPECIFIED
        }
        if (type == MobileDataProductType.UNSPECIFIED) {
            return null
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

    private fun purgeExpiredProducts() {
        val currentDate = LocalDate.now()
        for (product in activeProductsMap.values) {
            if (currentDate >= product.expiryDate) {
                activeProductsMap.remove(product.id)
            }
        }
    }

    private fun getActiveProductInUse(msisdn: String): MobileDataProduct? {
        var productInUse: MobileDataProduct? = null
        if (LocalDateTime.now().hour in 0..7) {
            productInUse = getProductInUseOfType(msisdn, MobileDataProductType.NIGHT_SURFER)
        }
        if (productInUse == null) {
            productInUse = getProductInUseOfType(msisdn, MobileDataProductType.ANYTIME)
        }
        return productInUse
    }

    private fun getProductInUseOfType(msisdn: String, type: MobileDataProductType): MobileDataProduct? {
        var productInUse: MobileDataProduct? = null
        for (product in activeProductsMap.values) {
            if (product.availableAmount > 0
                && product.msisdn == msisdn
                && product.type == type
                && (productInUse == null || product.activationDate <= productInUse.activationDate)) {
                productInUse = product
            }
        }
        return productInUse
    }

}

class DataUsageMonitoringException(
    message: String, cause: Throwable? = null
): Exception(message, cause)
