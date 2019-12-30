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
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
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
            conn.prepareStatement("SELECT id FROM mobile_data_products WHERE id = ?").use { stmt ->
                stmt.setObject(1, product.id)
                stmt.executeQuery().use { rs ->
                    exists = rs.next()
                }
            }

            if (!exists) {
                conn.prepareStatement(
                        """
                        INSERT INTO mobile_data_products (
                            id, name, total_amount, used_amount, activation_date, expiry_date, update_timestamp
                        ) VALUES (?, ?, ?, ?, ?, ?, NOW())
                        """.trimIndent()).use { stmt ->
                    stmt.setObject(1, product.id)
                    stmt.setString(2, product.name)
                    stmt.setLong(3, product.totalAmount)
                    stmt.setLong(4, product.usedAmount)
                    stmt.setDate(5, Date.valueOf(product.activationDate))
                    stmt.setDate(6, Date.valueOf(product.expiryDate))
                    stmt.executeUpdate()
                }
            } else {
                conn.prepareStatement(
                        """
                            UPDATE mobile_data_products SET
                            name = ?,
                            total_amount = ?,
                            used_amount = ?,
                            activation_date = ?,
                            expiry_date = ?,
                            update_timestamp = NOW()
                            WHERE id = ?
                        """.trimIndent()).use { stmt ->
                    stmt.setString(1, product.name)
                    stmt.setLong(2, product.totalAmount)
                    stmt.setLong(3, product.usedAmount)
                    stmt.setDate(4, Date.valueOf(product.activationDate))
                    stmt.setDate(5, Date.valueOf(product.expiryDate))
                    stmt.setObject(6, product.id)
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
                    SELECT name, total_amount, used_amount, activation_date, expiry_date
                    FROM mobile_data_products
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
                            rs.getDate(4).toLocalDate(),
                            rs.getDate(5).toLocalDate())
                    }
                }
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Error retrieving product: $id", e)
        }
        throw MobileDataProductDBException("Failed to retrieve unknown product: $id")
    }

    fun getAllProducts(): List<MobileDataProduct> {
        return getProducts()
    }

    fun getActiveProducts(): List<MobileDataProduct> {
        return getProducts(activeOnly = true)
    }

    private fun getProducts(activeOnly: Boolean = false): List<MobileDataProduct> {
        val products = LinkedList<MobileDataProduct>()
        try {
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                        """
                        SELECT id, name, total_amount, used_amount, activation_date, expiry_date
                        FROM mobile_data_products
                        """.trimIndent()
                        + if (activeOnly) " WHERE expiry_date > NOW()" else "").use { rs ->
                    while (rs.next()) {
                        products += MobileDataProduct(
                            rs.getObject(1, UUID::class.java),
                            rs.getString(2),
                            rs.getLong(3),
                            rs.getLong(4),
                            rs.getDate(5).toLocalDate(),
                            rs.getDate(6).toLocalDate())
                    }
                }
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Error retrieving products", e)
        }
        return products
    }

    fun addDataUsage(
        product: MobileDataProduct,
        downloadAmount: Long = 0,
        uploadAmount: Long = 0,
        timestamp: Instant = Instant.now()
    ) {
        try {
            conn.prepareStatement(
                    """
                    INSERT INTO mobile_data_usage (
                        id, timestamp, download_amount, upload_amount
                    ) VALUES (?, ?, ?, ?)
                    """.trimIndent()).use { stmt ->
                stmt.setObject(1, product.id)
                stmt.setTimestamp(2, Timestamp.from(timestamp))
                stmt.setLong(3, downloadAmount);
                stmt.setLong(4, uploadAmount)
                stmt.executeUpdate()
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Failed to save data usage", e)
        }
    }

    fun getDataUsage(
        product: MobileDataProduct? = null,
        timestampsEqual: ((t1: Instant, t2: Instant) -> Boolean)? = null
    ): List<MobileDataUsage> {
        val dataUsage = LinkedList<MobileDataUsage>()
        try {
            conn.prepareStatement(
                    """
                    SELECT timestamp, download_amount, upload_amount
                    FROM mobile_data_usage
                    """.trimIndent()
                    + (if (product != null) " WHERE id = ?" else "")
                    + " ORDER BY timestamp ASC").use { stmt ->
                if (product != null) {
                    stmt.setObject(1, product.id)
                }
                stmt.executeQuery().use { rs ->
                    readDataUsage(rs, dataUsage, timestampsEqual)
                }
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Failed to retrieve data usage", e)
        }
        return dataUsage
    }

    fun getProductDataUsagePerDay(product: MobileDataProduct) =
        getDataUsage(product, ::timestampsEqualByDay)

    fun getAllProductDataUsagePerDay() =
        getDataUsage(timestampsEqual = ::timestampsEqualByDay)

    fun getAllProductDataUsagePerMonth() =
        getDataUsage(timestampsEqual = ::timestampsEqualByMonth)

    fun getActiveProductDataUsage(
        timestampsEqual: ((t1: Instant, t2: Instant) -> Boolean)? = null
    ): List<MobileDataUsage> {
        val dataUsage = LinkedList<MobileDataUsage>()
        try {
            conn.prepareStatement(
                    """
                    SELECT timestamp, download_amount, upload_amount
                    FROM mobile_data_usage u
                    INNER JOIN mobile_data_products p ON u.id = p.id 
                    WHERE expiry_date > NOW()
                    ORDER BY timestamp ASC
                    """.trimIndent()).use { stmt ->
                stmt.executeQuery().use { rs ->
                    readDataUsage(rs, dataUsage, timestampsEqual)
                }
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Failed to retrieve data usage", e)
        }
        return dataUsage
    }

    fun getActiveProductDataUsagePerDay() =
        getActiveProductDataUsage(::timestampsEqualByDay)

    private fun readDataUsage(
        rs: ResultSet,
        dataUsage: MutableList<MobileDataUsage>,
        timestampsEqual: ((t1: Instant, t2: Instant) -> Boolean)?
    ) {
        var downloadAmount = 0L
        var uploadAmount = 0L
        var timestamp: Instant? = null

        while (rs.next()) {
            val t = rs.getTimestamp(1).toInstant()
            val d = rs.getLong(2)
            val u = rs.getLong(3)
            if (timestampsEqual == null) {
                dataUsage += MobileDataUsage(t, d, u)
            } else if (timestamp == null || timestampsEqual(timestamp, t)) {
                downloadAmount += d
                uploadAmount += u
                timestamp = t
            } else {
                dataUsage += MobileDataUsage(timestamp, downloadAmount, uploadAmount)
                downloadAmount = 0
                uploadAmount = 0
                timestamp = null
            }
        }
        if (timestamp != null) {
            dataUsage += MobileDataUsage(timestamp, downloadAmount, uploadAmount)
        }
    }

    fun clearAllData() {
        try {
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM mobile_data_products")
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Failed to clear data", e)
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
                CREATE TABLE IF NOT EXISTS mobile_data_products (
                    id UUID NOT NULL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    total_amount BIGINT NOT NULL,
                    used_amount BIGINT NOT NULL,
                    activation_date DATE NULL,
                    expiry_date DATE NULL,
                    update_timestamp TIMESTAMP NOT NULL
                )
                """.trimIndent())
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS ix_prod_exp_date ON mobile_data_products (expiry_date ASC)
                """.trimIndent())
            stmt.execute(
                """
                CREATE CACHED TABLE IF NOT EXISTS mobile_data_usage (
                    id UUID NOT NULL FOREIGN KEY REFERENCES mobile_data_products(id) ON DELETE CASCADE ON UPDATE CASCADE,
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

    private fun timestampsEqualByDay(t1: Instant, t2: Instant): Boolean {
        val zone = ZoneId.systemDefault()
        return t1.atZone(zone).toLocalDate() == t2.atZone(zone).toLocalDate()
    }

    private fun timestampsEqualByMonth(t1: Instant, t2: Instant): Boolean {
        val zone = ZoneId.systemDefault()
        val ld1 = t1.atZone(zone).toLocalDate()
        val ld2 = t2.atZone(zone).toLocalDate()
        return ld1.year == ld2.year && ld1.month == ld2.month
    }

}

class MobileDataProductDBException(
    message: String,
    cause: Throwable? = null
): Exception(message, cause)
