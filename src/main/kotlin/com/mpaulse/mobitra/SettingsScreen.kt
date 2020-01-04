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
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.layout.Pane

class SettingsScreen(
    private val appData: ApplicationData
): SecondaryScreen() {

    @FXML private lateinit var routerIPAddressField: TextField
    @FXML private lateinit var testConnBtn: Button
    @FXML private lateinit var testConnErrorLabel: Label
    @FXML private lateinit var autoStartCheckBox: CheckBox

    override val toggleButton = ToggleButton("Settings")
    override val backButtonText = "Save"

    init {
        center = loadFXMLPane<Pane>("SettingsPane", this)

        routerIPAddressField.text = appData.routerIPAddress
        autoStartCheckBox.isSelected = appData.autoStart
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

    @FXML
    fun onTestConnection(event: ActionEvent) {
        event.consume()
    }

}
