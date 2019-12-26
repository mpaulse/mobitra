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
import javafx.application.Application
import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.ChoiceBox
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

const val APP_NAME = "Mobitra"
const val VERSION = "0.1"
private val homePath = Path.of(System.getProperty("user.home"), ".Mobitra")

class MobitraApplication: Application() {

    private val appData = ApplicationData(homePath)
    private val productDB = MobileDataProductDB(homePath)
    private lateinit var products: List<MobileDataProduct>
    private val logger = LoggerFactory.getLogger(MobitraApplication::class.java)

    private lateinit var mainWindow: Stage
    @FXML private lateinit var mainWindowPane: BorderPane
    private lateinit var activeProductsPane: BorderPane
    private lateinit var historyPane: BorderPane
    private lateinit var noDataPane: Region

    @FXML private lateinit var historyBtn: ToggleButton
    @FXML private lateinit var activeProductsBtn: ToggleButton
    @FXML private lateinit var activeProductsMenu: ChoiceBox<ActiveProductMenuItem>

    override fun start(stage: Stage) {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            logger.error("Application error", e)
        }

        products = productDB.getProducts()

        createMainWindow(stage)
        noDataPane = loadFXMLPane("NoDataPane")
        createActiveProductsPane()
        createHistoryPane()
        initControls()
        onViewActiveProducts()
        mainWindow.show()
    }

    private fun createMainWindow(stage: Stage) {
        mainWindow = stage
        mainWindow.scene = Scene(loadFXMLPane("MainWindow"))
        //mainWindow?.scene?.stylesheets?.add("styles/Main.css")
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

        val allProductsItem = ActiveProductMenuItem("All", null)
        activeProductsMenu.items.add(allProductsItem)
        for (product in products) {
            activeProductsMenu.items.add(ActiveProductMenuItem(product.name, product.id))
        }
        activeProductsMenu.value = allProductsItem

        activeProductsMenu.setOnAction {
            onActiveProductSelected(it)
        }
    }

    private fun createHistoryPane() {
        historyPane = BorderPane()
        historyPane.center = noDataPane
    }

    private fun initControls() {
        val toggleGroup = ToggleGroup()
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
        if (products.isEmpty()) {
            activeProductsPane.center = noDataPane
        }
        event?.consume()
    }

    @FXML
    fun onViewHistory(event: ActionEvent) {
        mainWindowPane.center = historyPane
        if (products.isEmpty()) {
            historyPane.center = noDataPane
        }
        event.consume()
    }

    fun onActiveProductSelected(event: ActionEvent) {
        println(event)
        event.consume()
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
