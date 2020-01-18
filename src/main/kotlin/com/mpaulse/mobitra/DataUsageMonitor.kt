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
    private val onDataTrafficUpdate: (Long, Long) -> Unit
): CoroutineScope by MainScope() {

    private var monitoringAPIClient: MonitoringAPIClient? = null
    private val activeProductsMap = mutableMapOf<UUID, MobileDataProduct>()
    private var activeProductInUse: MobileDataProduct? = null
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
        pollDataUsage().collect { event ->
            if (logger.isDebugEnabled) {
                logger.debug("Data usage event: $event")
            }
            try {
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
                            var downloadAmount = 0L
                            var uploadAmount = 0L
                            var uncategorisedAmount = product.usedAmount - prevUsedAmount
                            if (activeProductInUse?.id == product.id) {
                                downloadAmount = event.downloadAmount
                                uploadAmount = event.uploadAmount
                                uncategorisedAmount -= (downloadAmount + uploadAmount)
                            }
                            val dataUsage = MobileDataUsage(
                                downloadAmount = downloadAmount,
                                uploadAmount = uploadAmount,
                                uncategorisedAmount = uncategorisedAmount)
                            if (logger.isDebugEnabled) {
                                logger.debug("Add data usage:\n\tproduct: $product\n\tusage: $dataUsage")
                            }
                            productDB.addDataUsage(product, dataUsage)
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
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                logger.error("Data usage monitoring error", e)
            }
        }
    }

    private fun pollDataUsage() = flow {
        var lastTrafficStats: HuaweiTrafficStats? = null
        var totalDownloadAmount = 0L
        var totalUploadAmount = 0L
        var downloadAmount = 0L
        var uploadAmount = 0L

        while (true) {
            try {
                yield()
                val trafficStats = monitoringAPIClient?.getHuaweiTrafficStatistics()
                if (trafficStats != null) {
                    var d = -1L
                    var u = -1L
                    if (lastTrafficStats != null) {
                        d = trafficStats.currentDownloadAmount - lastTrafficStats.currentDownloadAmount
                        u = trafficStats.currentUploadAmount - lastTrafficStats.currentUploadAmount
                    }
                    if (d >= 0 && u >= 0) {
                        downloadAmount += d
                        totalDownloadAmount += d
                        uploadAmount += u
                        totalUploadAmount += u
                    }
                    if (logger.isDebugEnabled) {
                        logger.debug("Tick: downloads = $downloadAmount B, uploads = $uploadAmount B")
                    }

                    // Emit event if:
                    // - there is a router restart or a switch a different router (d < 0 or u < 0)
                    // - the product remaining amount gets consumed
                    if ((d < 0 || u < 0)
                        || (activeProductInUse != null && (downloadAmount + uploadAmount) >= activeProductInUse!!.remainingAmount)) {
                        emit(DataUsageEvent(downloadAmount, uploadAmount))
                        downloadAmount = 0
                        uploadAmount = 0
                    }

                    lastTrafficStats = trafficStats
                    launch(Dispatchers.Main) {
                        onDataTrafficUpdate(totalDownloadAmount, totalUploadAmount)
                    }
                }

                // Delay until just before the next hour mark, or within 5 seconds, whichever comes first.
                // TODO: need to emit on the hour mark
                // TODO: t can be negative if we're within the last second of the hour
                val now = LocalDateTime.now()
                var t = now.until(now.truncatedTo(HOURS).plusHours(1).minusSeconds(1), MILLIS)
                if (t > 5000) {
                    t = 5000
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
            if (product.totalAmount > 0
                && product.msisdn == msisdn
                && product.type == type
                && (productInUse == null || product.activationDate <= productInUse.activationDate)) {
                productInUse = product
            }
        }
        return productInUse
    }

}

private data class DataUsageEvent(
    val downloadAmount: Long,
    val uploadAmount: Long
)
