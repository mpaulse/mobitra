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
import com.mpaulse.mobitra.net.MonitoringAPIClient
import com.mpaulse.mobitra.net.TelkomFreeResource
import com.mpaulse.mobitra.net.TelkomFreeResourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val onActiveProductsUpdate: () -> Unit,
    private val onDataTrafficUpdate: (delta: MobileDataUsage, total: MobileDataUsage) -> Unit
): CoroutineScope by MainScope() {

    private var monitoringAPIClient: MonitoringAPIClient? = null
    private val activeProductsMap = mutableMapOf<UUID, MobileDataProduct>()
    private var activeProductInUse: MobileDataProduct? = null
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
        launch {
            updateProductInfo()
        }
    }

    private suspend fun updateProductInfo() = withContext(Dispatchers.Default) {
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

                    launch(Dispatchers.Main) {
                        onActiveProductsUpdate()
                    }
                }
                updateProducts = false
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                logger.error("Data usage monitoring error", e)
                updateProducts = true
            }
        }
    }

    private fun pollDataUsage() = flow {
        var lastTrafficStats: HuaweiTrafficStats? = null
        var downloadAmountTick: Long
        var downloadAmountToLog = 0L
        var downloadAmountTotal = 0L
        var uploadAmountTick: Long
        var uploadAmountToLog = 0L
        var uploadAmountTotal = 0L

        while (true) {
            try {
                yield()
                val trafficStats = monitoringAPIClient?.getHuaweiTrafficStatistics()
                if (trafficStats != null) {
                    downloadAmountTick = -1L
                    uploadAmountTick = -1L
                    if (lastTrafficStats != null) {
                        downloadAmountTick = trafficStats.currentDownloadAmount - lastTrafficStats.currentDownloadAmount
                        uploadAmountTick = trafficStats.currentUploadAmount - lastTrafficStats.currentUploadAmount
                    }
                    if (downloadAmountTick >= 0 && uploadAmountTick >= 0) {
                        downloadAmountToLog += downloadAmountTick
                        downloadAmountTotal += downloadAmountTick
                        uploadAmountToLog += uploadAmountTick
                        uploadAmountTotal += uploadAmountTick
                        launch(Dispatchers.Main) {
                            onDataTrafficUpdate(
                                MobileDataUsage(downloadAmount = downloadAmountTick, uploadAmount = uploadAmountTick),
                                MobileDataUsage(downloadAmount = downloadAmountTotal, uploadAmount = uploadAmountTotal))
                        }
                    }
                    if (logger.isDebugEnabled) {
                        logger.debug("Update tick: downloads = $downloadAmountToLog B, uploads = $uploadAmountToLog B")
                    }

                    // Emit event if:
                    // - the data usage needs to be recorded and products from Telkom refreshed
                    // - there is a router restart or a switch a different router (d < 0 or u < 0)
                    // - the product remaining amount gets consumed
                    if (updateProducts
                            || (downloadAmountTick < 0 || uploadAmountTick < 0)
                            || (activeProductInUse != null
                                && (downloadAmountToLog + uploadAmountToLog) >= activeProductInUse!!.remainingAmount)) {
                        emit(MobileDataUsage(
                            downloadAmount = downloadAmountToLog,
                            uploadAmount = uploadAmountToLog))
                        if (!updateProducts) {
                            downloadAmountToLog = 0
                            uploadAmountToLog = 0
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
                logger.error("Data usage monitoring error", e)
            }
        }
    }

    private fun resourceToProduct(resource: TelkomFreeResource): MobileDataProduct? {
        val activationDate = resource.activationDate ?: return null
        val type = when (resource.type) {
            TelkomFreeResourceType.LTE_ONCE_OFF_ANYTIME_DATA.string -> MobileDataProductType.ANYTIME
            TelkomFreeResourceType.LTE_ONCE_OFF_NIGHT_SURFER_DATA.string -> MobileDataProductType.NIGHT_SURFER
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
            resource.totalAmount,
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
            if (product.remainingAmount > 0
                && product.msisdn == msisdn
                && product.type == type
                && (productInUse == null || product.activationDate <= productInUse.activationDate)) {
                productInUse = product
            }
        }
        return productInUse
    }

}
