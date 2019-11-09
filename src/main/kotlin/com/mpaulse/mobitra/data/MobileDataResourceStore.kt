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

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class MobileDataResourceStore(
    homePath: Path
) {

    private val conn: Connection

    init {
        try {
            Class.forName("org.hsqldb.jdbc.JDBCDriver")
            conn = DriverManager.getConnection(
                "jdbc:hsqldb:file:${homePath.resolve("db").toAbsolutePath()}",
                "mobitra",
                "")
            createTables()
        } catch (e: Exception) {
            throw MobileDataResourceStoreException("Resource store connection failure", e)
        }
    }

    fun storeResource(resource: MobileDataResource) {
    }

    fun getResources(): List<MobileDataResource> {
        return emptyList()
    }

    fun close() {
        try {
            conn.createStatement().use { stmt ->
                stmt.execute("SHUTDOWN")
            }
            conn.close()
        } catch (e: SQLException) {
        }
    }

    private fun createTables() {
        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS mobile_data_resource (
                    id VARCHAR NOT NULL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    total_amount BIGINT NOT NULL,
                    used_amount BIGINT NOT NULL,
                    expiry_date DATE NULL
                )
                """.trimIndent())
            stmt.execute(
                """
                CREATE CACHED TABLE IF NOT EXISTS mobile_data_resource_usage (
                    id VARCHAR NOT NULL FOREIGN KEY REFERENCES resource.id ON DELETE CASCADE ON UPDATE CASCADE,
                    timestamp TIMESTAMP NOT NULL,
                    download_amount BIGINT NULL,
                    upload_amount BIGINT NULL
                )
                """.trimIndent())
        }
    }

}

class MobileDataResourceStoreException(
    message: String,
    cause: Throwable?
): Exception(message, cause)
