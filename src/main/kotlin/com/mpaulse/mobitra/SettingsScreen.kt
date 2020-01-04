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

import com.mpaulse.mobitra.data.ApplicationData
import com.mpaulse.mobitra.net.MonitoringAPIClient
import com.mpaulse.mobitra.net.MonitoringAPIException
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class SettingsScreen(
    private val appData: ApplicationData
): SecondaryScreen(), CoroutineScope by MainScope() {

    @FXML private lateinit var routerIPAddressField: TextField
    @FXML private lateinit var testConnBtn: Button
    @FXML private lateinit var testConnLabel: Label
    @FXML private lateinit var autoStartCheckBox: CheckBox

    private val routerIPAddress: String? get() =
        routerIPAddressField.text?.trim()?.let {
            if (it.isEmpty()) null
            else it
        }

    private val testConnProgressSpinner = ProgressIndicator(-1.0)
    private val greenTickIcon = ImageView("/images/green-tick.png")
    private val redCrossIcon = ImageView("/images/red-cross.png")

    override val toggleButton = ToggleButton("Settings")
    override val backButtonText = "Save"

    private val logger = LoggerFactory.getLogger(MobitraApplication::class.java)

    init {
        center = loadFXMLPane<Pane>("SettingsPane", this)

        testConnProgressSpinner.prefWidth = 16.0
        testConnProgressSpinner.prefHeight = 16.0

        greenTickIcon.fitWidth = 16.0
        greenTickIcon.fitHeight = 16.0

        redCrossIcon.fitWidth = 16.0
        redCrossIcon.fitHeight = 16.0

        routerIPAddressField.text = appData.routerIPAddress
        autoStartCheckBox.isSelected = appData.autoStart
    }

    override fun onShow() {
        if (!testConnBtn.isDisable) {
            testConnLabel.graphic = null
            testConnLabel.text = null
            testConnLabel.styleClass.clear()
        }
    }

    override fun onBack(): Boolean {
        appData.routerIPAddress = routerIPAddress
        appData.autoStart = autoStartCheckBox.isSelected
        appData.save()
        return true
    }

    @FXML
    fun onTestConnection(event: ActionEvent) {
        val routerIPAddress = routerIPAddress
        if (routerIPAddress != null) {
            val monitoringAPI = MonitoringAPIClient(routerIPAddress)

            testConnLabel.graphic = testConnProgressSpinner
            testConnLabel.text = "Testing connection..."
            testConnLabel.styleClass.clear()
            testConnBtn.isDisable = true

            launch {
                try {
                    try {
                        monitoringAPI.getHuaweiTrafficStatistics()
                    } catch (e: MonitoringAPIException) {
                        logger.error("Connection test to Huawei LTE router was unsuccessful", e)
                        throw ConnectionTestException("Huawei router")
                    }

                    try {
                        val rsp = monitoringAPI.checkTelkomOnnet()
                        if (rsp.resultCode != 0 || rsp.sessionToken == null) {
                            logger.error("Connection test to Telkom was unsuccessful:\n$rsp")
                            throw ConnectionTestException("Telkom")
                        }
                    } catch (e: MonitoringAPIException) {
                        logger.error("Connection test to Telkom was unsuccessful", e)
                        throw ConnectionTestException("Telkom")
                    }

                    testConnLabel.graphic = greenTickIcon
                    testConnLabel.text = "Connection successful."
                    testConnLabel.styleClass.setAll("test-conn-success")
                } catch (e: ConnectionTestException) {
                    testConnLabel.graphic = redCrossIcon
                    testConnLabel.text = "Connecting to ${e.failedEntity} failed."
                    testConnLabel.styleClass.setAll("test-conn-failure")
                } finally {
                    testConnBtn.isDisable = false
                }
            }
        }

        event.consume()
    }

}

private class ConnectionTestException(
    val failedEntity: String
): Exception()
