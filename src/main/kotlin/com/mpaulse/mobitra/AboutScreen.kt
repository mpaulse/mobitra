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

import javafx.application.HostServices
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Hyperlink
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.text.Text
import java.time.LocalDate

class AboutScreen(
    private val hostServices: HostServices
): Region() {

    @FXML private lateinit var appNameText: Text
    @FXML private lateinit var versionText: Text
    @FXML private lateinit var licenseText: Text

    private val thirdPartyLicenses = mapOf(
        "HSQLDB" to "http://hsqldb.org/web/hsqlLicense.html",
        "Jackson" to "https://github.com/FasterXML/jackson-core/blob/master/LICENSE",
        "Kotlin" to "https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt",
        "Logback" to "http://logback.qos.ch/license.html",
        "OpenJDK" to "https://openjdk.java.net/legal/gplv2+ce.html",
        "OpenJFX" to "https://github.com/openjdk/jfx/blob/master/LICENSE",
        "SLF4J" to "http://www.slf4j.org/license.html")

    init {
        children += loadFXMLPane<Pane>("AboutPane", this)

        appNameText.text = APP_NAME
        versionText.text = APP_VERSION

        licenseText.text =
            """
            Copyright (c) ${LocalDate.now().year} Marlon Paulse
            
            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files (the "Software"), to deal
            in the Software without restriction, including without limitation the rights
            to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
            copies of the Software, and to permit persons to whom the Software is
            furnished to do so, subject to the following conditions:

            The above copyright notice and this permission notice shall be included in all
            copies or substantial portions of the Software.

            THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
            IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
            FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
            AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
            LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
            OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
            SOFTWARE.
            """.trimIndent()
    }

    @FXML
    fun onShowThirdPartyLicense(event: ActionEvent) {
        val hyperlink = event.source as? Hyperlink
        if (hyperlink != null) {
            val licenseUrl = thirdPartyLicenses[hyperlink.text]
            if (licenseUrl != null) {
                hostServices.showDocument(licenseUrl)
            }
        }
        event.consume()
    }

}
