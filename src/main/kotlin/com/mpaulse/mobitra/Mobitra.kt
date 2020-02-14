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

import com.mpaulse.mobitra.chart.Chart
import com.mpaulse.mobitra.chart.CumulativeDataUsagePerDayChart
import com.mpaulse.mobitra.chart.DataUsageBarChart
import com.mpaulse.mobitra.chart.DataUsageBarChartType
import com.mpaulse.mobitra.data.ApplicationData
import com.mpaulse.mobitra.data.MobileDataProduct
import com.mpaulse.mobitra.data.MobileDataProductDB
import com.mpaulse.mobitra.data.MobileDataProductType
import com.mpaulse.mobitra.data.MobileDataUsage
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import javafx.application.Application
import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ChoiceBox
import javafx.scene.control.Label
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
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.ZoneId
import java.util.LinkedList
import java.util.UUID
import javax.swing.JDialog
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.UIManager
import kotlin.concurrent.thread
import java.awt.Font as AWTFont
import java.awt.Image as AWTImage
import java.awt.event.ActionEvent as AWTActionEvent
import java.awt.event.MouseEvent as AWTMouseEvent

private const val HIDE_IN_BACKGROUND_PARAMETER = "-b"
private const val RUN_AT_WIN_LOGIN_REGISTRY_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"

const val DEFAULT_MIN_WINDOW_WIDTH = 900.0
const val DEFAULT_MIN_WINDOW_HEIGHT = 650.0

val devModeEnabled = System.getProperty("dev")?.toBoolean() ?: false

class MobitraApplication: Application(), CoroutineScope by MainScope(), DataUsageMonitorListener {

    val appData = ApplicationData(APP_HOME_PATH)
    private lateinit var productDB: MobileDataProductDB
    private lateinit var dataUsageMonitor: DataUsageMonitor
    private val unrecordedDataUsage = LinkedList<MobileDataUsage>()
    private val logger = LoggerFactory.getLogger(MobitraApplication::class.java)
    private var loadHistory = true

    private lateinit var mainWindow: Stage
    @FXML private lateinit var mainWindowPane: BorderPane
    @FXML private lateinit var toggleBtnBox: HBox
    private lateinit var activeProductsScreen: BorderPane
    private lateinit var historyScreen: BorderPane
    private val noDataPane: Pane = loadFXMLPane("NoDataPane", this)
    private val settingsScreen = SettingsScreen(this)
    private val aboutScreen = AboutScreen(this)
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
    @FXML private lateinit var statusBarLeftLabel: Label
    @FXML private lateinit var statusBarRightLabel: Label

    override fun start(stage: Stage) {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            logger.error("Application error", e)
        }
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            onJVMShutdown()
        })

        productDB = MobileDataProductDB(APP_HOME_PATH)
        dataUsageMonitor = DataUsageMonitor(appData.routerIPAddress, productDB, this)
        dataUsageMonitor.start()

        verifyAutoStartConfig()
        createMainWindow(stage)
        createSystemTrayIcon()
        createActiveProductsPane()
        createHistoryPane()
        initControls()
        setStatusBarProductInfoText()
        startMainWindow()
    }

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

    private fun startMainWindow() {
        var showWindow = true
        val showSettings = appData.routerIPAddress == null
        if (!showSettings) {
            for (param in parameters.unnamed) {
                if (param == HIDE_IN_BACKGROUND_PARAMETER) {
                    showWindow = false
                    break
                }
            }
        }
        if (showWindow) {
            if (showSettings) {
                showSecondaryScreen(settingsScreen)
            }
            showMainWindow()
        }
    }

    private fun showMainWindow() {
        mainWindow.show()
        mainWindow.toFront()
    }

    private fun verifyAutoStartConfig() {
        if (appData.autoStart) {
            val launcherPath = getLauncherPath()?.toString()
            val expectedRegistryConfig = "$launcherPath $HIDE_IN_BACKGROUND_PARAMETER"

            var registryConfig: String? = null
            try {
                registryConfig = Advapi32Util.registryGetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    RUN_AT_WIN_LOGIN_REGISTRY_KEY,
                    APP_NAME)
            } catch (e: Exception) {
            }

            if (expectedRegistryConfig != registryConfig) {
                if (launcherPath == null) {
                    disableAutoStart()
                } else {
                    enableAutoStart()
                }
            }
        }
    }

    private fun getLauncherPath(): Path? {
        // CWD is the "app" directory containing the JAR.
        // The EXE launcher should be in the parent directory.
        val path = Paths.get("..", "$APP_NAME.exe").toAbsolutePath().normalize()
        return if (path.toFile().exists()) path else null
    }

    private fun enableAutoStart() {
        if (devModeEnabled) {
            return
        }
        val launcherPath = getLauncherPath()
        if (launcherPath != null) {
            try {
                Advapi32Util.registrySetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    RUN_AT_WIN_LOGIN_REGISTRY_KEY,
                    APP_NAME,
                    "$launcherPath $HIDE_IN_BACKGROUND_PARAMETER")
            } catch (e: Exception) {
                logger.error("Error enabling auto startup", e)
            }
        }
    }

    private fun disableAutoStart() {
        if (devModeEnabled) {
            return
        }
        try {
            Advapi32Util.registryDeleteValue(
                WinReg.HKEY_CURRENT_USER,
                RUN_AT_WIN_LOGIN_REGISTRY_KEY,
                APP_NAME)
        } catch (e: Exception) {
        }
    }

    private fun onJVMShutdown() {
        if (::dataUsageMonitor.isInitialized) {
            dataUsageMonitor.stop()
        }
        if (::productDB.isInitialized) {
            productDB.close()
        }
        appData.windowPosition = mainWindow.x to mainWindow.y
        appData.windowSize = mainWindow.width to mainWindow.height
        appData.save()
    }

    private fun createMainWindow(stage: Stage) {
        mainWindow = stage
        mainWindow.scene = Scene(loadFXMLPane("MainWindow", this))
        mainWindow.scene.stylesheets.add("style.css")
        mainWindow.minWidth = DEFAULT_MIN_WINDOW_WIDTH
        mainWindow.width = if (appData.windowSize.first >= mainWindow.minWidth) appData.windowSize.first else mainWindow.minWidth
        mainWindow.minHeight = DEFAULT_MIN_WINDOW_HEIGHT
        mainWindow.height = if (appData.windowSize.second >= mainWindow.minHeight) appData.windowSize.second else mainWindow.minHeight
        mainWindow.icons.add(Image(APP_ICON))

        val pos = appData.windowPosition
        if (pos != null) {
            mainWindow.x = pos.first
            mainWindow.y = pos.second
        } else {
            mainWindow.centerOnScreen()
            appData.windowPosition = mainWindow.x to mainWindow.y
        }

        mainWindow.title = APP_NAME
    }

    private fun createSystemTrayIcon() {
        if (!SystemTray.isSupported()) {
            return
        }

        val sysTray = SystemTray.getSystemTray()
        sysTrayIcon = TrayIcon(
            Toolkit.getDefaultToolkit().getImage(javaClass.getResource(APP_ICON)).getScaledInstance(16, 16, AWTImage.SCALE_DEFAULT),
            APP_NAME,
            null)

        // Use JPopupMenu instead of the AWT PopupMenu for a native system look and feel.
        val sysTrayMenu = JPopupMenu()
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

        // A hack to allow the system tray popup menu to disappear when clicking outside it.
        // Hiding the hidden invoker dialog window when it loses focus also hides the popup menu.
        // This is only a problem when using JPopupMenu instead of the AWT PopupMenu for the system tray.
        val sysTrayMenuInvoker = JDialog()
        sysTrayMenuInvoker.isUndecorated = true
        sysTrayMenuInvoker.addWindowFocusListener(object: WindowFocusListener {
            override fun windowGainedFocus(e: WindowEvent?) = Unit
            override fun windowLostFocus(e: WindowEvent?) {
                sysTrayMenuInvoker.isVisible = false
            }
        })
        sysTrayMenu.invoker = sysTrayMenuInvoker

        val openMenuItem = JMenuItem("Open $APP_NAME")
        openMenuItem.font = AWTFont.decode(null).deriveFont(AWTFont.BOLD)
        setupTrayMenuItemMouseListener(openMenuItem)
        openMenuItem.addActionListener(::onOpenMainWindowFromSystemTray)

        val settingsMenuItem = JMenuItem("Settings")
        setupTrayMenuItemMouseListener(settingsMenuItem)
        settingsMenuItem.addActionListener(::onSettingsFromSystemTray)

        val exitMenuItem = JMenuItem("Exit")
        setupTrayMenuItemMouseListener(exitMenuItem)
        exitMenuItem.addActionListener {
            sysTrayMenuInvoker.dispose()
            onExit()
        }

        sysTrayMenu.add(openMenuItem)
        sysTrayMenu.add(settingsMenuItem)
        sysTrayMenu.addSeparator()
        sysTrayMenu.add(exitMenuItem)

        sysTrayIcon?.addActionListener(::onOpenMainWindowFromSystemTray)
        sysTrayIcon?.addMouseListener(object: MouseAdapter() {
            override fun mouseReleased(event: AWTMouseEvent) {
                if (event.isPopupTrigger) {
                    sysTrayMenu.setLocation(event.x, event.y)
                    sysTrayMenuInvoker.isVisible = true
                    sysTrayMenu.isVisible = true
                }
            }
        })

        sysTray.add(sysTrayIcon)
        Platform.setImplicitExit(false)
    }

    private fun setupTrayMenuItemMouseListener(menuItem: JMenuItem) {
        if (menuItem.mouseListeners.isNotEmpty()) {
            val listener = menuItem.mouseListeners.first()
            menuItem.removeMouseListener(listener)
            menuItem.addMouseListener(LeftClickOnlyMouseListenerDelegate(listener))
        }
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

        val activeProducts = dataUsageMonitor.activeProducts.values.sortedWith(Comparator {
            p1: MobileDataProduct, p2: MobileDataProduct ->
            var c = 0
            if (p1.availableAmount == 0L && p2.availableAmount > 0L) {
                c = 1
            } else if (p2.availableAmount == 0L && p1.availableAmount > 0L) {
                c = -1
            }
            if (c == 0) {
                c = p1.activationDate.compareTo(p2.activationDate)
            }
            if (c == 0) {
                c = p1.type.compareTo(p2.type)
            }
            c
        })

        for (product in activeProducts) {
            val item = ActiveProductMenuItem("${product.msisdn} - ${product.name} (${product.activationDate})", product.id)
            activeProductsMenu.items.add(item)
            if (product.id == selectedItem?.productId) {
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

    private fun onOpenMainWindowFromSystemTray(event: AWTActionEvent) {
        Platform.runLater {
            showMainWindow()
        }
    }

    override fun onActiveProductsUpdate() {
        Platform.runLater {
            refreshActiveProductsMenu()
            loadHistory = true
            unrecordedDataUsage.clear()
            if (toggleGroup.selectedToggle == historyBtn) {
                onViewHistory()
            } else if (toggleGroup.selectedToggle == activeProductsBtn) {
                onActiveProductSelected()
            }

            setStatusBarProductInfoText(dataUsageMonitor.currentProduct)
        }
    }

    private fun setStatusBarProductInfoText(currentProduct: MobileDataProduct? = null) {
        val currentProductString =
            if (currentProduct != null) "${currentProduct.name} (${DataAmountStringFormatter.toString(currentProduct.availableAmount)})"
            else "Unknown"
        val msisdn = currentProduct?.msisdn ?: "Unknown"
        statusBarLeftLabel.text = "SIM: $msisdn    Current Product: $currentProductString"
        statusBarLeftLabel.styleClass.clear()
    }

    private fun getTotalUnrecordedDataUsage(): MobileDataUsage {
        var download = 0L
        var upload = 0L
        var uncategorised = 0L
        for (usage in unrecordedDataUsage) {
            download += usage.downloadAmount
            upload += usage.uploadAmount
            uncategorised = usage.uncategorisedAmount
        }
        return MobileDataUsage(downloadAmount = download, uploadAmount = upload, uncategorisedAmount = uncategorised)
    }

    override fun onDataTrafficUpdate(delta: MobileDataUsage, total: MobileDataUsage) {
        Platform.runLater {
            // Keep track of deltas, so that they can be added when a chart is loaded for the first time
            var usage = delta
            if (unrecordedDataUsage.isNotEmpty()) {
                var lastUsage = unrecordedDataUsage.last()
                val zoneId = ZoneId.systemDefault()
                if (lastUsage.timestamp.atZone(zoneId).toLocalDate() == usage.timestamp.atZone(zoneId).toLocalDate()) {
                    unrecordedDataUsage.removeLast()
                    usage = lastUsage.copy(
                        downloadAmount = lastUsage.downloadAmount + delta.downloadAmount,
                        uploadAmount = lastUsage.uploadAmount + delta.uploadAmount,
                        uncategorisedAmount = lastUsage.uncategorisedAmount + delta.uncategorisedAmount)
                }
            }
            unrecordedDataUsage += usage
            val totalUnrecordedUsage = getTotalUnrecordedDataUsage()

            // Update charts with delta
            val selectedProductId = activeProductsMenu.selectionModel.selectedItem.productId
            if (selectedProductId == null || selectedProductId == dataUsageMonitor.currentProduct?.id) {
                addDataUsageToChartScreen(delta, activeProductsScreen)
            }
            addDataUsageToChartScreen(delta, historyScreen)

            // Update status bar and system tray tooltip
            var currentProduct = dataUsageMonitor.currentProduct
            if (currentProduct != null) {
                currentProduct = currentProduct.copy(
                    availableAmount = currentProduct.availableAmount - totalUnrecordedUsage.totalAmount,
                    usedAmount = currentProduct.usedAmount + totalUnrecordedUsage.totalAmount)
                setStatusBarProductInfoText(currentProduct)
            }
            val availableAmountStr = DataAmountStringFormatter.toString(currentProduct?.availableAmount ?: 0)
            val downloadAmountStr = DataAmountStringFormatter.toString(total.downloadAmount)
            val uploadAmountStr = DataAmountStringFormatter.toString(total.uploadAmount)
            statusBarRightLabel.text = "Current Download / Upload: $downloadAmountStr / $uploadAmountStr"
            sysTrayIcon?.toolTip =
                """
                $APP_NAME
                Remaining: $availableAmountStr
                Download: $downloadAmountStr
                Upload: $uploadAmountStr
                """.trimIndent()
        }
    }

    override fun onDataUsageMonitoringException(error: DataUsageMonitoringException) {
        logger.error("Data usage monitoring error", error)
        Platform.runLater {
            statusBarLeftLabel.text = error.message
            statusBarLeftLabel.styleClass += "error"
        }
    }

    private fun addDataUsageToChartScreen(dataUsage: MobileDataUsage, chartScreen: BorderPane) {
        val charts = chartScreen.center as? Pane
        if (charts != null) {
            for (chart in charts.children) {
                if (chart is Chart) {
                    chart.addDataUsage(dataUsage)
                }
            }
        }
    }

    @FXML
    fun onViewActiveProducts(event: ActionEvent) {
        mainWindowPane.center = activeProductsScreen
        if (dataUsageMonitor.activeProducts.isEmpty()) {
            activeProductsScreen.center = noDataPane
        }
        event.consume()
    }

    @FXML
    fun onViewHistory(event: ActionEvent? = null) {
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
                    for (dataUsage in unrecordedDataUsage) {
                        addDataUsageToChartScreen(dataUsage, historyScreen)
                    }
                    loadHistory = false
                } else {
                    historyScreen.center = noDataPane
                }
            }
        }

        mainWindowPane.center = historyScreen
        event?.consume()
    }

    fun onActiveProductSelected(event: ActionEvent? = null) {
        activeProductsScreen.center = loadingSpinner

        launch {
            var product: MobileDataProduct? = null
            var dataUsage: MutableList<MobileDataUsage>? = null

            val selectedProductId = activeProductsMenu.selectionModel.selectedItem.productId
            if (dataUsageMonitor.activeProducts.isNotEmpty()) {
                if (selectedProductId != null) {
                    product = dataUsageMonitor.activeProducts[selectedProductId]
                    if (product != null) {
                        dataUsage = withContext(Dispatchers.IO) {
                            productDB.getProductDataUsagePerDay(product!!)
                        }
                        yield()
                    }
                } else {
                    var availableAmount = 0L
                    var usedAmount = 0L
                    var activationDate: LocalDate? = null
                    var expiryDate: LocalDate? = null
                    for (p in dataUsageMonitor.activeProducts.values) {
                        availableAmount += p.availableAmount
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
                        MobileDataProductType.UNSPECIFIED,
                        availableAmount,
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
                if (selectedProductId == null || product.id == dataUsageMonitor.currentProduct?.id) {
                    for (du in unrecordedDataUsage) {
                        addDataUsageToChartScreen(du, activeProductsScreen)
                    }
                }
            } else {
                activeProductsScreen.center = noDataPane
            }
        }

        event?.consume()
    }

    private fun onSettingsFromSystemTray(event: AWTActionEvent) {
        onOpenMainWindowFromSystemTray(event)
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

    fun onExitSettings() {
        dataUsageMonitor.routerIPAddress = appData.routerIPAddress
        appData.save()
        if (appData.autoStart) {
            enableAutoStart()
        } else {
            disableAutoStart()
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
