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
import java.sql.Date
import java.sql.DriverManager
import java.sql.SQLException
import java.util.LinkedList
import java.util.UUID

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
        try {
            conn.autoCommit = false

            var exists = false
            conn.prepareStatement("SELECT id FROM mobile_data_resource WHERE id = ?").use { stmt ->
                stmt.setObject(1, resource.id)
                stmt.executeQuery().use { rs ->
                    exists = rs.next()
                }
            }

            if (!exists) {
                conn.prepareStatement(
                        """
                        INSERT INTO mobile_data_resource (
                            id, name, total_amount, used_amount, expiry_date, update_timestamp
                        ) VALUES (?, ?, ?, ?, ?, NOW())
                        """.trimIndent()).use { stmt ->
                    stmt.setObject(1, resource.id)
                    stmt.setString(2, resource.name)
                    stmt.setLong(3, resource.totalAmount)
                    stmt.setLong(4, resource.usedAmount)
                    stmt.setDate(5, Date.valueOf(resource.expiryDate))
                    stmt.executeUpdate()
                }
            } else {
                conn.prepareStatement(
                        """
                            UPDATE mobile_data_resource SET
                            name = ?,
                            total_amount = ?,
                            used_amount = ?,
                            expiry_date = ?,
                            update_timestamp = NOW()
                            WHERE id = ?
                        """.trimIndent()).use { stmt ->
                    stmt.setString(1, resource.name)
                    stmt.setLong(2, resource.totalAmount)
                    stmt.setLong(3, resource.usedAmount)
                    stmt.setDate(4, Date.valueOf(resource.expiryDate))
                    stmt.setObject(5, resource.id)
                }
            }

            conn.commit()
        } catch (e: SQLException) {
            try {
                conn.rollback()
            } catch (re: SQLException) {
                throw MobileDataResourceStoreException("Error rolling back failed resource store operation", re)
            }
            throw MobileDataResourceStoreException("Failed to store resource", e)
        } finally {
            try {
                conn.autoCommit = false
            } catch (e: SQLException) {
                throw MobileDataResourceStoreException("Unexpected failure", e)
            }
        }
    }

    fun getResource(id: UUID): MobileDataResource {
        try {
            conn.prepareStatement(
                    """
                    SELECT name, total_amount, used_amount, expiry_date
                    FROM mobile_data_resource
                    WHERE id = ?
                    """.trimIndent()).use { stmt ->
                stmt.setObject(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return MobileDataResource(
                            id,
                            rs.getString(1),
                            rs.getLong(2),
                            rs.getLong(3),
                            rs.getDate(4).toLocalDate())
                    }
                }
            }
        } catch (e: SQLException) {
            throw MobileDataResourceStoreException("Error retrieving resource: $id", e)
        }
        throw MobileDataResourceStoreException("Failed to retrieve unknown resource: $id")
    }

    fun getResources(): List<MobileDataResource> {
        val resources = LinkedList<MobileDataResource>()
        try {
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                        """
                        SELECT id, name, total_amount, used_amount, expiry_date
                        FROM mobile_data_resource
                        """.trimIndent()).use { rs ->
                    while (rs.next()) {
                        resources += MobileDataResource(
                            rs.getObject(1, UUID::class.java),
                            rs.getString(2),
                            rs.getLong(3),
                            rs.getLong(4),
                            rs.getDate(5).toLocalDate())
                    }
                }
            }
        } catch (e: SQLException) {
            throw MobileDataResourceStoreException("Error retrieving resources", e)
        }
        return resources
    }

    fun addUsage(resource: MobileDataResource, downloadAmount: Long = 0, uploadAmount: Long = 0) {
        try {
            conn.prepareStatement(
                    """
                    INSERT INTO mobile_data_resource_usage (
                        id, timestamp, download_amount, upload_amount
                    ) VALUES (?, NOW(), ?, ?)
                    """.trimIndent()).use { stmt ->
                stmt.setObject(1, resource.id)
                stmt.setLong(2, downloadAmount);
                stmt.setLong(3, uploadAmount)
                stmt.executeUpdate()
            }
        } catch (e: SQLException) {
            throw MobileDataResourceStoreException("Failed to save resource usage data", e)
        }
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
                    id UUID NOT NULL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    total_amount BIGINT NOT NULL,
                    used_amount BIGINT NOT NULL,
                    expiry_date DATE NULL,
                    update_timestamp TIMESTAMP NOT NULL
                )
                """.trimIndent())
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS ix_md_res_exp_date ON mobile_data_resource (expiry_date ASC)
                """.trimIndent())
            stmt.execute(
                """
                CREATE CACHED TABLE IF NOT EXISTS mobile_data_resource_usage (
                    id UUID NOT NULL FOREIGN KEY REFERENCES mobile_data_resource(id) ON DELETE CASCADE ON UPDATE CASCADE,
                    timestamp TIMESTAMP NOT NULL,
                    download_amount BIGINT DEFAULT 0 NOT NULL,
                    upload_amount BIGINT DEFAULT 0 NOT NULL
                )
                """.trimIndent())
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS ix_mb_res_usage_ts ON mobile_data_resource_usage (timestamp ASC)
                """.trimIndent())

        }
    }

}

class MobileDataResourceStoreException(
    message: String,
    cause: Throwable? = null
): Exception(message, cause)
