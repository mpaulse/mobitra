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
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.LinkedList
import java.util.UUID

class MobileDataProductDB(
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
            throw MobileDataProductDBException("Resource store connection failure", e)
        }
    }

    fun storeProduct(product: MobileDataProduct) {
        try {
            conn.autoCommit = false

            var exists = false
            conn.prepareStatement("SELECT id FROM mobile_data_product WHERE id = ?").use { stmt ->
                stmt.setObject(1, product.id)
                stmt.executeQuery().use { rs ->
                    exists = rs.next()
                }
            }

            if (!exists) {
                conn.prepareStatement(
                        """
                        INSERT INTO mobile_data_product (
                            id, name, total_amount, used_amount, expiry_date, update_timestamp
                        ) VALUES (?, ?, ?, ?, ?, NOW())
                        """.trimIndent()).use { stmt ->
                    stmt.setObject(1, product.id)
                    stmt.setString(2, product.name)
                    stmt.setLong(3, product.totalAmount)
                    stmt.setLong(4, product.usedAmount)
                    stmt.setDate(5, Date.valueOf(product.expiryDate))
                    stmt.executeUpdate()
                }
            } else {
                conn.prepareStatement(
                        """
                            UPDATE mobile_data_product SET
                            name = ?,
                            total_amount = ?,
                            used_amount = ?,
                            expiry_date = ?,
                            update_timestamp = NOW()
                            WHERE id = ?
                        """.trimIndent()).use { stmt ->
                    stmt.setString(1, product.name)
                    stmt.setLong(2, product.totalAmount)
                    stmt.setLong(3, product.usedAmount)
                    stmt.setDate(4, Date.valueOf(product.expiryDate))
                    stmt.setObject(5, product.id)
                }
            }

            conn.commit()
        } catch (e: SQLException) {
            try {
                conn.rollback()
            } catch (re: SQLException) {
                throw MobileDataProductDBException("Error rolling back failed product store operation", re)
            }
            throw MobileDataProductDBException("Failed to store product", e)
        } finally {
            try {
                conn.autoCommit = true
            } catch (e: SQLException) {
                throw MobileDataProductDBException("Unexpected failure", e)
            }
        }
    }

    fun getProduct(id: UUID): MobileDataProduct {
        try {
            conn.prepareStatement(
                    """
                    SELECT name, total_amount, used_amount, expiry_date
                    FROM mobile_data_product
                    WHERE id = ?
                    """.trimIndent()).use { stmt ->
                stmt.setObject(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return MobileDataProduct(
                            id,
                            rs.getString(1),
                            rs.getLong(2),
                            rs.getLong(3),
                            rs.getDate(4).toLocalDate())
                    }
                }
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Error retrieving product: $id", e)
        }
        throw MobileDataProductDBException("Failed to retrieve unknown product: $id")
    }

    fun getProducts(): List<MobileDataProduct> {
        val product = LinkedList<MobileDataProduct>()
        try {
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                        """
                        SELECT id, name, total_amount, used_amount, expiry_date
                        FROM mobile_data_product
                        """.trimIndent()).use { rs ->
                    while (rs.next()) {
                        product += MobileDataProduct(
                            rs.getObject(1, UUID::class.java),
                            rs.getString(2),
                            rs.getLong(3),
                            rs.getLong(4),
                            rs.getDate(5).toLocalDate())
                    }
                }
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Error retrieving products", e)
        }
        return product
    }

    fun addDataUsage(product: MobileDataProduct, downloadAmount: Long = 0, uploadAmount: Long = 0) {
        try {
            conn.prepareStatement(
                    """
                    INSERT INTO mobile_data_usage (
                        id, timestamp, download_amount, upload_amount
                    ) VALUES (?, NOW(), ?, ?)
                    """.trimIndent()).use { stmt ->
                stmt.setObject(1, product.id)
                stmt.setLong(2, downloadAmount);
                stmt.setLong(3, uploadAmount)
                stmt.executeUpdate()
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Failed to save data usage", e)
        }
    }

    fun getDataUsage(
        resource: MobileDataProduct,
        timestampFrom: LocalDateTime = LocalDateTime.now(),
        timestampTo: LocalDateTime = LocalDateTime.now()
    ): List<MobileDataUsage> {
        val usage = LinkedList<MobileDataUsage>()
        try {
            conn.prepareStatement(
                    """
                    SELECT timestamp, download_amount, upload_amount
                    FROM mobile_data_usage
                    WHERE id = ? AND timestamp >= ? AND timestamp <= ?
                    """.trimIndent()).use { stmt ->
                stmt.setObject(1, resource.id)
                stmt.setTimestamp(2, Timestamp.valueOf(timestampFrom))
                stmt.setTimestamp(3, Timestamp.valueOf(timestampTo))
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        usage += MobileDataUsage(
                            rs.getTimestamp(1).toLocalDateTime(),
                            rs.getLong(2),
                            rs.getLong(3))
                    }
                }
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Failed to retrieve data usage", e)
        }
        return usage
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
                CREATE TABLE IF NOT EXISTS mobile_data_product (
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
                CREATE INDEX IF NOT EXISTS ix_prod_exp_date ON mobile_data_product (expiry_date ASC)
                """.trimIndent())
            stmt.execute(
                """
                CREATE CACHED TABLE IF NOT EXISTS mobile_data_usage (
                    id UUID NOT NULL FOREIGN KEY REFERENCES mobile_data_product(id) ON DELETE CASCADE ON UPDATE CASCADE,
                    timestamp TIMESTAMP NOT NULL,
                    download_amount BIGINT DEFAULT 0 NOT NULL,
                    upload_amount BIGINT DEFAULT 0 NOT NULL
                )
                """.trimIndent())
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS ix_usage_ts ON mobile_data_usage (timestamp ASC)
                """.trimIndent())

        }
    }

}

class MobileDataProductDBException(
    message: String,
    cause: Throwable? = null
): Exception(message, cause)