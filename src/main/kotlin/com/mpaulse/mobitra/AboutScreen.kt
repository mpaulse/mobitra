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

import javafx.fxml.FXML
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Pane
import javafx.scene.text.Text
import java.io.InputStreamReader

class AboutScreen: BorderPane() {

    @FXML private lateinit var appNameText: Text
    @FXML private lateinit var versionText: Text
    @FXML private lateinit var licenseText: Text
    @FXML private lateinit var thirdPartyLicenseText: Text

    init {
        center = loadFXMLPane<Pane>("AboutPane", this)

        appNameText.text = APP_NAME
        versionText.text = APP_VERSION

        InputStreamReader(javaClass.getResourceAsStream("/LICENSE.txt")).use {
            licenseText.text = it.readText()
        }

        InputStreamReader(javaClass.getResourceAsStream("/LICENSE_THIRD_PARTY.txt")).use {
            thirdPartyLicenseText.text = it.readText()
        }
    }

}
