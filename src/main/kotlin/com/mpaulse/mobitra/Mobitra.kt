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

@file:JvmName("Mobitra")

package com.mpaulse.mobitra

import com.mpaulse.mobitra.data.ApplicationData
import com.mpaulse.mobitra.data.MobileDataProduct
import com.mpaulse.mobitra.data.MobileDataProductDB
import com.mpaulse.mobitra.data.MobileDataUsage
import javafx.application.Application
import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.ChoiceBox
import javafx.scene.control.MenuButton
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID

const val APP_NAME = "Mobitra"
const val VERSION = "0.1"
private val homePath = Path.of(System.getProperty("user.home"), ".Mobitra")

class MobitraApplication: Application(), CoroutineScope by MainScope() {

    private val appData = ApplicationData(homePath)
    private val productDB = MobileDataProductDB(homePath)
    private val activeProducts = mutableMapOf<UUID, MobileDataProduct>()
    private val logger = LoggerFactory.getLogger(MobitraApplication::class.java)
    private var loadHistory = true

    private lateinit var mainWindow: Stage
    @FXML private lateinit var mainWindowPane: BorderPane
    private lateinit var activeProductsPane: BorderPane
    private lateinit var historyPane: BorderPane
    private lateinit var noDataPane: Region

    @FXML private lateinit var menuBtn: MenuButton
    @FXML private lateinit var historyBtn: ToggleButton
    @FXML private lateinit var activeProductsBtn: ToggleButton
    @FXML private lateinit var activeProductsMenu: ChoiceBox<ActiveProductMenuItem>
    private val loadingSpinner = ProgressIndicator(-1.0)

    override fun start(stage: Stage) {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            logger.error("Application error", e)
        }

        val products = productDB.getActiveProducts()
        for (product in products) {
            activeProducts[product.id] = product
        }

        createMainWindow(stage)
        noDataPane = loadFXMLPane("NoDataPane")
        createActiveProductsPane()
        createHistoryPane()
        initControls()
        onViewActiveProducts()
        mainWindow.show()
    }

    override fun stop() {
        productDB.close()
        appData.windowPosition = Pair(mainWindow.x, mainWindow.y)
        appData.windowSize = Pair(mainWindow.width, mainWindow.height)
        appData.save()
    }

    private fun createMainWindow(stage: Stage) {
        mainWindow = stage
        mainWindow.scene = Scene(loadFXMLPane("MainWindow"))
        mainWindow.scene.stylesheets.add("style.css")
        mainWindow.minWidth = 600.0
        mainWindow.width = if (appData.windowSize.first >= mainWindow.minWidth) appData.windowSize.first else mainWindow.minWidth
        mainWindow.minHeight = 480.0
        mainWindow.height = if (appData.windowSize.second >= mainWindow.minHeight) appData.windowSize.second else mainWindow.minHeight
        //mainWindow?.icons?.add(Images.get("images/Mobitra.png"))

        val pos = appData.windowPosition
        if (pos != null) {
            mainWindow.x = pos.first
            mainWindow.y = pos.second
        } else {
            mainWindow.centerOnScreen()
        }

        mainWindow.title = APP_NAME
    }

    private fun createActiveProductsPane() {
        activeProductsPane = loadFXMLPane("ActiveProductsPane")
        refreshActiveProductsMenu()
        activeProductsMenu.setOnAction {
            onActiveProductSelected(it)
        }
        onActiveProductSelected()
    }

    private fun refreshActiveProductsMenu() {
        var selectedItem = activeProductsMenu.selectionModel.selectedItem

        activeProductsMenu.items.clear()

        val allProductsItem = ActiveProductMenuItem("All", null)
        activeProductsMenu.items.add(allProductsItem)

        for ((productId, product) in activeProducts) {
            val item = ActiveProductMenuItem(product.name, productId)
            activeProductsMenu.items.add(item)
            if (productId == selectedItem?.productId) {
                selectedItem = item
            }
        }

        activeProductsMenu.value = selectedItem ?: allProductsItem
    }

    private fun createHistoryPane() {
        historyPane = BorderPane()
        historyPane.center = noDataPane
    }

    private fun initControls() {
        menuBtn.graphic = ImageView("images/menu.png")

        val toggleGroup = ToggleGroup()
        toggleGroup.selectedToggleProperty().addListener { _, prevSelected, currSelected ->
            if (currSelected == null) {
                prevSelected.isSelected = true // Prevent no toggle in the group being selected.
            }
        }
        activeProductsBtn.toggleGroup = toggleGroup
        historyBtn.toggleGroup = toggleGroup
        activeProductsBtn.selectedProperty().set(true)
    }

    private fun <T> loadFXMLPane(pane: String): T {
        val loader = FXMLLoader()
        loader.setController(this)
        loader.setControllerFactory {
            this // Needed for imported/nested FXML files
        }
        loader.location = javaClass.getResource("/fxml/$pane.fxml")
        return loader.load<T>()
    }

    @FXML
    fun onViewActiveProducts(event: ActionEvent? = null) {
        mainWindowPane.center = activeProductsPane
        if (activeProducts.isEmpty()) {
            activeProductsPane.center = noDataPane
        }
        event?.consume()
    }

    @FXML
    fun onViewHistory(event: ActionEvent) {
        if (loadHistory) {
            historyPane.center = loadingSpinner

            launch {
                // TODO: Implement paged loading for better memory efficiency?
                val dataUsagePerMonth = withContext(Dispatchers.IO) {
                    productDB.getAllProductDataUsagePerMonth()
                }
                yield()
                val dataUsagePerDay = withContext(Dispatchers.IO) {
                    productDB.getAllProductDataUsagePerDay()
                }
                yield()
                if (dataUsagePerMonth.isNotEmpty()) {
                    val charts = VBox()
                    charts.spacing = 25.0
                    withContext(Dispatchers.IO) {
                        charts.children.addAll(
                            DataUsageBarChart(dataUsagePerMonth, DataUsageBarChartType.MONTHLY),
                            DataUsageBarChart(dataUsagePerDay, DataUsageBarChartType.DAILY))
                    }
                    yield()
                    historyPane.center = charts
                    loadHistory = false
                } else {
                    historyPane.center = noDataPane
                }
            }
        }

        mainWindowPane.center = historyPane
        event.consume()
    }

    fun onActiveProductSelected(event: ActionEvent? = null) {
        activeProductsPane.center = loadingSpinner

        launch {
            var product: MobileDataProduct? = null
            var dataUsage: List<MobileDataUsage>? = null

            if (activeProducts.isNotEmpty()) {
                val productId = activeProductsMenu.selectionModel.selectedItem.productId
                if (productId != null) {
                    product = activeProducts[productId]
                    if (product != null) {
                        dataUsage = withContext(Dispatchers.IO) {
                            productDB.getProductDataUsagePerDay(product!!)
                        }
                        yield()
                    }
                } else {
                    var totalAmount = 0L
                    var usedAmount = 0L
                    var activationDate: LocalDate? = null
                    var expiryDate: LocalDate? = null
                    for (p in activeProducts.values) {
                        totalAmount += p.totalAmount
                        usedAmount += p.usedAmount
                        if (activationDate == null || activationDate > p.activationDate) {
                            activationDate = p.activationDate
                        }
                        if (expiryDate == null || expiryDate < p.expiryDate) {
                            expiryDate = p.expiryDate
                        }
                    }
                    product = MobileDataProduct(
                        UUID.randomUUID(),
                        "All",
                        totalAmount,
                        usedAmount,
                        activationDate as LocalDate,
                        expiryDate as LocalDate)
                    dataUsage = withContext(Dispatchers.IO) {
                        productDB.getActiveProductDataUsagePerDay()
                    }
                    yield()
                }
            }

            if (product != null && dataUsage != null) {
                val charts = VBox()
                withContext(Dispatchers.IO) {
                    charts.children.addAll(
                        CumulativeDataUsagePerDayChart(dataUsage, product),
                        DataUsageBarChart(dataUsage))
                }
                yield()
                activeProductsPane.center = charts
            } else {
                activeProductsPane.center = noDataPane
            }
        }

        event?.consume()
    }

    @FXML
    fun onExit(event: ActionEvent) {
        Platform.exit()
        event.consume()
    }

}

fun main(args: Array<String>) {
    if (!Files.exists(homePath)) {
        Files.createDirectories(homePath)
    }

    Application.launch(MobitraApplication::class.java, *args)
}
