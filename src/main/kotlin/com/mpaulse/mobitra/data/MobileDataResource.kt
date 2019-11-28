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

package com.mpaulse.mobitra.data

import com.mpaulse.mobitra.net.TelkomFreeResource
import java.time.LocalDate
import java.util.UUID

data class MobileDataResource(
    val id: UUID,
    val name: String,
    val totalAmount: Long,
    val usedAmount: Long,
    val expiryDate: LocalDate
) {

    constructor(telkomFreeResource: TelkomFreeResource):
        this(
            generateId(telkomFreeResource),
            telkomFreeResource.name,
            telkomFreeResource.totalAmount,
            telkomFreeResource.usedAmount,
            telkomFreeResource.expiryDate)

}

private fun generateId(telkomFreeResource: TelkomFreeResource): UUID {
    // TODO
    return UUID.fromString("")
}
