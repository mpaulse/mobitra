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
import javafx.scene.control.Button
import javafx.scene.control.ChoiceBox
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.nio.file.Files
import java.time.LocalDate
import java.util.UUID
import java.awt.Font as AWTFont
import java.awt.Image as AWTImage
import java.awt.MenuItem as AWTMenuItem
import java.awt.event.ActionEvent as AWTActionEvent

fun <T> loadFXMLPane(pane: String, controller: Any): T {
    val loader = FXMLLoader()
    loader.setController(controller)
    loader.setControllerFactory {
        APP_NAME
        controller // Needed for imported/nested FXML files
    }
    loader.location = controller.javaClass.getResource("/fxml/$pane.fxml")
    return loader.load<T>()
}

class MobitraApplication: Application(), CoroutineScope by MainScope() {

    private val appData = ApplicationData(APP_HOME_PATH)
    private val productDB = MobileDataProductDB(APP_HOME_PATH)
    private val activeProducts = mutableMapOf<UUID, MobileDataProduct>()
    private val logger = LoggerFactory.getLogger(MobitraApplication::class.java)
    private var loadHistory = true

    private lateinit var mainWindow: Stage
    @FXML private lateinit var mainWindowPane: BorderPane
    @FXML private lateinit var toggleBtnBox: HBox
    private lateinit var activeProductsScreen: BorderPane
    private lateinit var historyScreen: BorderPane
    private val noDataPane: Pane = loadFXMLPane("NoDataPane", this)
    private val settingsScreen = SettingsScreen(appData)
    private val aboutScreen = AboutScreen()

    @FXML private lateinit var menuBtn: MenuButton
    @FXML private lateinit var hideMenuItem: MenuItem

    private val toggleGroup = ToggleGroup()
    private var toggleBtnToFireOnBack: ToggleButton? = null
    @FXML private lateinit var historyBtn: ToggleButton
    @FXML private lateinit var activeProductsBtn: ToggleButton
    @FXML private lateinit var backBtn: Button

    @FXML private lateinit var activeProductsMenu: ChoiceBox<ActiveProductMenuItem>

    private var sysTrayIcon: TrayIcon? = null
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
        createSystemTrayIcon()
        createActiveProductsPane()
        createHistoryPane()
        initControls()

        if (appData.routerIPAddress == null) {
            showSecondaryScreen(settingsScreen)
        }

        onOpenMainWindow()
    }

    override fun stop() {
        productDB.close()
        appData.windowPosition = Pair(mainWindow.x, mainWindow.y)
        appData.windowSize = Pair(mainWindow.width, mainWindow.height)
        appData.save()
    }

    private fun createMainWindow(stage: Stage) {
        mainWindow = stage
        mainWindow.scene = Scene(loadFXMLPane("MainWindow", this))
        mainWindow.scene.stylesheets.add("style.css")
        mainWindow.minWidth = 600.0
        mainWindow.width = if (appData.windowSize.first >= mainWindow.minWidth) appData.windowSize.first else mainWindow.minWidth
        mainWindow.minHeight = 520.0
        mainWindow.height = if (appData.windowSize.second >= mainWindow.minHeight) appData.windowSize.second else mainWindow.minHeight
        mainWindow.icons.add(Image(APP_ICON))

        val pos = appData.windowPosition
        if (pos != null) {
            mainWindow.x = pos.first
            mainWindow.y = pos.second
        } else {
            mainWindow.centerOnScreen()
        }

        mainWindow.title = APP_NAME
    }

    private fun createSystemTrayIcon() {
        if (!SystemTray.isSupported()) {
            return
        }

        val sysTray = SystemTray.getSystemTray()
        val sysTrayMenu = PopupMenu()
        sysTrayIcon = TrayIcon(
            Toolkit.getDefaultToolkit().getImage(javaClass.getResource(APP_ICON)).getScaledInstance(16, 16, AWTImage.SCALE_DEFAULT),
            APP_NAME,
            sysTrayMenu)

        val openMenuItem = AWTMenuItem("Open $APP_NAME")
        openMenuItem.font = AWTFont.decode(null).deriveFont(AWTFont.BOLD)
        openMenuItem.addActionListener(::onOpenMainWindow)

        val settingsMenuItem = AWTMenuItem("Settings")
        settingsMenuItem.addActionListener(::onSettingsFromSystemTray)

        val exitMenuItem = AWTMenuItem("Exit")
        exitMenuItem.addActionListener {
            onExit()
        }

        sysTrayMenu.add(openMenuItem)
        sysTrayMenu.add(settingsMenuItem)
        sysTrayMenu.addSeparator()
        sysTrayMenu.add(exitMenuItem)

        sysTrayIcon?.addActionListener(::onOpenMainWindow)

        sysTray.add(sysTrayIcon)
        Platform.setImplicitExit(false)
    }

    private fun createActiveProductsPane() {
        activeProductsScreen = loadFXMLPane("ActiveProductsPane", this)
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
            val item = ActiveProductMenuItem("${product.msisdn} - ${product.name}", productId)
            activeProductsMenu.items.add(item)
            if (productId == selectedItem?.productId) {
                selectedItem = item
            }
        }

        activeProductsMenu.value = selectedItem ?: allProductsItem
    }

    private fun createHistoryPane() {
        historyScreen = BorderPane()
        historyScreen.center = noDataPane
    }

    private fun initControls() {
        menuBtn.graphic = ImageView("images/menu.png")
        menuBtn.isFocusTraversable = false
        hideMenuItem.isDisable = !SystemTray.isSupported()

        backBtn.graphic = ImageView("images/back.png")
        backBtn.isFocusTraversable = false

        toggleGroup.selectedToggleProperty().addListener { _, prevSelected, currSelected ->
            if (currSelected == null) {
                prevSelected.isSelected = true // Prevent no toggle in the group being selected.
            }
        }

        activeProductsBtn.toggleGroup = toggleGroup
        activeProductsBtn.isFocusTraversable = false

        historyBtn.toggleGroup = toggleGroup
        historyBtn.isFocusTraversable = false

        settingsScreen.toggleButton.toggleGroup = toggleGroup
        settingsScreen.toggleButton.isFocusTraversable = false

        aboutScreen.toggleButton.toggleGroup = toggleGroup
        aboutScreen.toggleButton.isFocusTraversable = false

        activeProductsBtn.fire()
    }

    @FXML
    fun onHideMainWindow(event: ActionEvent) {
        mainWindow.hide()
        event.consume()
    }

    private fun onOpenMainWindow(event: AWTActionEvent? = null) {
        val action = {
            mainWindow.show()
            mainWindow.toFront()
        }
        if (event != null) {
            Platform.runLater(action)
        } else {
            action()
        }
    }

    @FXML
    fun onViewActiveProducts(event: ActionEvent) {
        mainWindowPane.center = activeProductsScreen
        if (activeProducts.isEmpty()) {
            activeProductsScreen.center = noDataPane
        }
        event.consume()
    }

    @FXML
    fun onViewHistory(event: ActionEvent) {
        if (loadHistory) {
            historyScreen.center = loadingSpinner

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
                    historyScreen.center = charts
                    loadHistory = false
                } else {
                    historyScreen.center = noDataPane
                }
            }
        }

        mainWindowPane.center = historyScreen
        event.consume()
    }

    fun onActiveProductSelected(event: ActionEvent? = null) {
        activeProductsScreen.center = loadingSpinner

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
                activeProductsScreen.center = charts
            } else {
                activeProductsScreen.center = noDataPane
            }
        }

        event?.consume()
    }

    private fun onSettingsFromSystemTray(event: AWTActionEvent) {
        onOpenMainWindow(event)
        onSettings()
    }

    @FXML
    fun onSettings(event: ActionEvent? = null) {
        if (event != null) {
            showSecondaryScreen(settingsScreen)
            event.consume()
        } else {
            Platform.runLater {
                showSecondaryScreen(settingsScreen)
            }
        }
    }

    @FXML
    fun onAbout(event: ActionEvent) {
        showSecondaryScreen(aboutScreen)
        event.consume()
    }

    private fun showSecondaryScreen(screen: SecondaryScreen) {
        backBtn.text = screen.backButtonText
        backBtn.isVisible = true
        menuBtn.isVisible = false
        toggleBtnBox.children.clear()
        toggleBtnBox.children += screen.toggleButton
        if (toggleBtnToFireOnBack == null) {
            toggleBtnToFireOnBack = toggleGroup.selectedToggle as ToggleButton
        }
        screen.toggleButton.fire()
        mainWindowPane.center = screen
        screen.onShow()
    }

    @FXML
    fun onBack(event: ActionEvent? = null) {
        val screen = mainWindowPane.center
        if (screen is SecondaryScreen && screen.onBack()) {
            backBtn.isVisible = false
            menuBtn.isVisible = true
            toggleBtnBox.children.clear()
            toggleBtnBox.children.addAll(activeProductsBtn, historyBtn)
            toggleBtnToFireOnBack?.fire()
            toggleBtnToFireOnBack = null
        }
        event?.consume()
    }

    @FXML
    fun onKeyPressed(event: KeyEvent) {
        if (toggleBtnToFireOnBack != null && event.code == KeyCode.ESCAPE) {
            onBack()
        }
        event.consume()
    }

    @FXML
    fun onExit(event: ActionEvent? = null) {
        if (sysTrayIcon != null) {
            SystemTray.getSystemTray().remove(sysTrayIcon)
        }
        Platform.exit()
        event?.consume()
    }

}

fun main(args: Array<String>) {
    if (!Files.exists(APP_HOME_PATH)) {
        Files.createDirectories(APP_HOME_PATH)
    }

    Application.launch(MobitraApplication::class.java, *args)
}
