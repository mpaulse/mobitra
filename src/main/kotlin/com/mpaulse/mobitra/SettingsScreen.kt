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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsScreen(
    private val appData: ApplicationData
): SecondaryScreen(), CoroutineScope by MainScope() {

    @FXML private lateinit var routerIPAddressField: TextField
    @FXML private lateinit var testConnBtn: Button
    @FXML private lateinit var testConnLabel: Label
    @FXML private lateinit var autoStartCheckBox: CheckBox

    private val testConnProgressSpinner = ProgressIndicator(-1.0)
    private val greenTickIcon = ImageView("/images/green-tick.png")
    private val redCrossIcon = ImageView("/images/red-cross.png")

    override val toggleButton = ToggleButton("Settings")
    override val backButtonText = "Save"

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
        testConnLabel.graphic = null
        testConnLabel.text = null
        testConnLabel.styleClass.clear()
    }

    override fun onBack(): Boolean {
        appData.routerIPAddress = routerIPAddressField.text?.trim()?.let {
            if (it.isEmpty()) null
            else it
        }
        appData.autoStart = autoStartCheckBox.isSelected
        appData.save()
        return true
    }

    private var b = false

    @FXML
    fun onTestConnection(event: ActionEvent) {
        testConnLabel.graphic = testConnProgressSpinner
        testConnLabel.text = "Testing connection..."
        testConnLabel.styleClass.clear()
        testConnBtn.isDisable = true

        launch {
            delay(3000)

            if (b) {
                testConnLabel.graphic = greenTickIcon
                testConnLabel.text = "Connection successful"
                testConnLabel.styleClass.setAll("test-conn-success")
            } else {
                testConnLabel.graphic = redCrossIcon
                testConnLabel.text = "Connection failed"
                testConnLabel.styleClass.setAll("test-conn-failure")
            }
            b = !b

            testConnBtn.isDisable = false
        }

        event.consume()
    }

}
