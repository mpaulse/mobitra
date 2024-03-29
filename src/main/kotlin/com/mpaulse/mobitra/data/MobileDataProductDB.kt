/*
 * Copyright (c) 2022 Marlon Paulse
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

import com.mpaulse.mobitra.data.MobileDataProductType.ANYTIME
import com.mpaulse.mobitra.data.MobileDataProductType.NIGHT_SURFER
import com.mpaulse.mobitra.data.MobileDataProductType.OFF_PEAK
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

private const val DB_NAME = "db"

class MobileDataProductDB(
    homePath: Path
) {

    private val conn: Connection

    init {
        val dbLockFile = homePath.resolve("$DB_NAME.lck").toFile()
        if (dbLockFile.exists()) {
            if (!dbLockFile.delete()) {
                throw MobileDataProductDBLockedException("Unable to delete DB lock file")
            }
        }

        try {
            Class.forName("org.hsqldb.jdbc.JDBCDriver")
            conn = DriverManager.getConnection(
                "jdbc:hsqldb:file:${homePath.resolve(DB_NAME).toAbsolutePath()}",
                "mobitra",
                "")
            createTables()
            reconcileDataUsageTotals()
        } catch (e: Exception) {
            throw MobileDataProductDBException("Mobile product DB init failure", e)
        }
    }

    private fun reconcileDataUsageTotals() {
        try {
            val reconciledProductUsages = mutableMapOf<UUID, MobileDataUsage>()
            conn.prepareStatement(
                    """
                    SELECT
                    id,
                    used_amount,
                    SUM(download_amount + upload_amount + uncategorised_amount) AS tracked_used_amount,
                    MAX(timestamp) AS last_timestamp
                    FROM mobile_data_products p INNER JOIN mobile_data_usage u on p.id = u.id
                    GROUP BY id
                    """.trimIndent()).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val usedAmountDiff = rs.getLong(2) - rs.getLong(3)
                        if (usedAmountDiff != 0L) {
                            val productId = rs.getObject(1, UUID::class.java)
                            reconciledProductUsages[productId] = MobileDataUsage(
                                timestamp = rs.getTimestamp(4).toInstant(),
                                uncategorisedAmount = usedAmountDiff)
                        }
                    }
                }
            }
            for ((productId, usage) in reconciledProductUsages) {
                addDataUsage(productId, usage)
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Error reconciling product data usages", e)
        }
    }

    @Synchronized
    fun storeProduct(product: MobileDataProduct) {
        try {
            conn.autoCommit = false

            var exists: Boolean
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
                            id, msisdn, name, type, available_amount, used_amount, activation_date, expiry_date, update_timestamp
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                        """.trimIndent()).use { stmt ->
                    stmt.setObject(1, product.id)
                    stmt.setString(2, product.msisdn)
                    stmt.setString(3, product.name)
                    stmt.setInt(4, productTypeToInt(product.type))
                    stmt.setLong(5, product.availableAmount)
                    stmt.setLong(6, product.usedAmount)
                    stmt.setDate(7, Date.valueOf(product.activationDate))
                    stmt.setDate(8, Date.valueOf(product.expiryDate))
                    stmt.executeUpdate()
                }
            } else {
                conn.prepareStatement(
                        """
                            UPDATE mobile_data_products SET
                            name = ?,
                            available_amount = ?,
                            used_amount = ?,
                            activation_date = ?,
                            update_timestamp = NOW()
                            WHERE id = ?
                        """.trimIndent()).use { stmt ->
                    stmt.setString(1, product.name)
                    stmt.setLong(2, product.availableAmount)
                    stmt.setLong(3, product.usedAmount)
                    stmt.setDate(4, Date.valueOf(product.activationDate))
                    stmt.setObject(5, product.id)
                    stmt.executeUpdate()
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

    @Synchronized
    fun getProduct(id: UUID): MobileDataProduct? {
        try {
            conn.prepareStatement(
                    """
                    SELECT msisdn, name, type, available_amount, used_amount, activation_date, expiry_date
                    FROM mobile_data_products
                    WHERE id = ?
                    """.trimIndent()).use { stmt ->
                stmt.setObject(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return MobileDataProduct(
                            id,
                            rs.getString(1),
                            rs.getString(2),
                            intToProductType(rs.getInt(3)),
                            rs.getLong(4),
                            rs.getLong(5),
                            rs.getDate(6).toLocalDate(),
                            rs.getDate(7).toLocalDate())
                    }
                }
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Error retrieving product: $id", e)
        }
        return null
    }

    fun getAllProducts(): List<MobileDataProduct> {
        return getProducts()
    }

    fun getActiveProducts(): List<MobileDataProduct> {
        return getProducts(activeOnly = true)
    }

    @Synchronized
    private fun getProducts(activeOnly: Boolean = false): List<MobileDataProduct> {
        val products = LinkedList<MobileDataProduct>()
        try {
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                        """
                        SELECT id, msisdn, name, type, available_amount, used_amount, activation_date, expiry_date
                        FROM mobile_data_products
                        """.trimIndent()
                        + if (activeOnly)
                            " WHERE (available_amount > 0 OR available_amount = " + UNLIMITED_AMOUNT
                                + ") AND expiry_date >= CURRENT_DATE"
                            else ""
                ).use { rs ->
                    while (rs.next()) {
                        products += MobileDataProduct(
                            rs.getObject(1, UUID::class.java),
                            rs.getString(2),
                            rs.getString(3),
                            intToProductType(rs.getInt(4)),
                            rs.getLong(5),
                            rs.getLong(6),
                            rs.getDate(7).toLocalDate(),
                            rs.getDate(8).toLocalDate())
                    }
                }
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Error retrieving products", e)
        }
        return products
    }

    fun addDataUsage(product: MobileDataProduct, dataUsage: MobileDataUsage) =
        addDataUsage(product.id, dataUsage)

    @Synchronized
    fun addDataUsage(productId: UUID, dataUsage: MobileDataUsage) {
        try {
            conn.prepareStatement(
                    """
                    INSERT INTO mobile_data_usage (
                        id, timestamp, download_amount, upload_amount, uncategorised_amount
                    ) VALUES (?, ?, ?, ?, ?)
                    """.trimIndent()).use { stmt ->
                stmt.setObject(1, productId)
                stmt.setTimestamp(2, Timestamp.from(dataUsage.timestamp))
                stmt.setLong(3, dataUsage.downloadAmount);
                stmt.setLong(4, dataUsage.uploadAmount)
                stmt.setLong(5, dataUsage.uncategorisedAmount)
                stmt.executeUpdate()
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Failed to save data usage", e)
        }
    }

    @Synchronized
    fun getDataUsage(
        product: MobileDataProduct? = null,
        timestampsEqual: ((t1: Instant, t2: Instant) -> Boolean)? = null
    ): MutableList<MobileDataUsage> {
        val dataUsage = LinkedList<MobileDataUsage>()
        var dataUsageTotal: Long
        var productUsageTotal = 0L
        var productUpdateTimestamp: Instant = Instant.now()

        try {
            conn.prepareStatement(
                    """
                    SELECT timestamp, download_amount, upload_amount, uncategorised_amount
                    FROM mobile_data_usage
                    """.trimIndent()
                    + (if (product != null) " WHERE id = ?" else "")
                    + " ORDER BY timestamp ASC").use { stmt ->
                if (product != null) {
                    stmt.setObject(1, product.id)
                }
                stmt.executeQuery().use { rs ->
                    dataUsageTotal = readDataUsage(rs, dataUsage, timestampsEqual)
                }
            }
            conn.prepareStatement(
                    """
                        SELECT SUM(used_amount), MAX(update_timestamp)
                        FROM mobile_data_products
                        """.trimIndent()
                        + (if (product != null) " WHERE id = ?" else "")).use { stmt ->
                if (product != null) {
                    stmt.setObject(1, product.id)
                }
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        productUsageTotal = rs.getLong(1)
                        val ts = rs.getTimestamp(2)
                        if (ts != null) {
                            productUpdateTimestamp = ts.toInstant()
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Failed to retrieve data usage", e)
        }

        adjustDataUsageIfTotalsMismatch(dataUsage, dataUsageTotal, productUsageTotal, productUpdateTimestamp)
        return dataUsage
    }

    fun getProductDataUsagePerDay(product: MobileDataProduct) =
        getDataUsage(product, ::timestampsEqualByDay)

    fun getAllProductDataUsagePerDay() =
        getDataUsage(timestampsEqual = ::timestampsEqualByDay)

    fun getAllProductDataUsagePerMonth() =
        getDataUsage(timestampsEqual = ::timestampsEqualByMonth)

    @Synchronized
    fun getActiveProductDataUsage(
        timestampsEqual: ((t1: Instant, t2: Instant) -> Boolean)? = null
    ): MutableList<MobileDataUsage> {
        val dataUsage = LinkedList<MobileDataUsage>()
        var dataUsageTotal: Long
        var productUsageTotal = 0L
        var productUpdateTimestamp: Instant = Instant.now()

        try {
            conn.prepareStatement(
                    """
                    SELECT timestamp, download_amount, upload_amount, uncategorised_amount
                    FROM mobile_data_usage u
                    INNER JOIN mobile_data_products p ON u.id = p.id 
                    WHERE available_amount > 0 AND expiry_date >= CURRENT_DATE
                    ORDER BY timestamp ASC
                    """.trimIndent()).use { stmt ->
                stmt.executeQuery().use { rs ->
                    dataUsageTotal = readDataUsage(rs, dataUsage, timestampsEqual)
                }
            }
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                        """
                        SELECT SUM(used_amount), MAX(update_timestamp)
                        FROM mobile_data_products
                        WHERE available_amount > 0 AND expiry_date >= CURRENT_DATE
                        """.trimIndent()).use { rs ->
                    if (rs.next()) {
                        productUsageTotal = rs.getLong(1)
                        val ts = rs.getTimestamp(2)
                        if (ts != null) {
                            productUpdateTimestamp = ts.toInstant()
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Failed to retrieve data usage", e)
        }

        adjustDataUsageIfTotalsMismatch(dataUsage, dataUsageTotal, productUsageTotal, productUpdateTimestamp)
        return dataUsage
    }

    private fun adjustDataUsageIfTotalsMismatch(
        dataUsage: MutableList<MobileDataUsage>,
        dataUsageTotal: Long,
        productUsageTotal: Long,
        productUpdateTimestamp: Instant
    ) {
        if (dataUsageTotal < productUsageTotal) {
            val first = if (dataUsage.isNotEmpty()) dataUsage.removeAt(0) else null
            dataUsage.add(0,
                MobileDataUsage(
                    first?.timestamp ?: productUpdateTimestamp,
                    first?.downloadAmount ?: 0,
                    first?.uploadAmount ?: 0,
                    productUsageTotal - dataUsageTotal))
        }
    }

    fun getActiveProductDataUsagePerDay() =
        getActiveProductDataUsage(::timestampsEqualByDay)

    private fun readDataUsage(
        rs: ResultSet,
        dataUsage: MutableList<MobileDataUsage>,
        timestampsEqual: ((t1: Instant, t2: Instant) -> Boolean)?
    ): Long {
        var downloadAmount = 0L
        var uploadAmount = 0L
        var uncategorisedAmount = 0L
        var usedAmount = 0L
        var timestamp: Instant? = null

        while (rs.next()) {
            val t = rs.getTimestamp(1).toInstant()
            val d = rs.getLong(2)
            val u = rs.getLong(3)
            val x = rs.getLong(4)
            if (timestampsEqual == null) {
                dataUsage += MobileDataUsage(t, d, u, x)
                usedAmount += dataUsage.last().totalAmount
            } else if (timestamp == null || timestampsEqual(timestamp, t)) {
                downloadAmount += d
                uploadAmount += u
                uncategorisedAmount += x
                usedAmount += d + u + x
                timestamp = t
            } else {
                dataUsage += MobileDataUsage(timestamp, downloadAmount, uploadAmount, uncategorisedAmount)
                downloadAmount = d
                uploadAmount = u
                uncategorisedAmount = x
                usedAmount += d + u + x
                timestamp = t
            }
        }
        if (timestamp != null) {
            dataUsage += MobileDataUsage(timestamp, downloadAmount, uploadAmount, uncategorisedAmount)
        }

        return usedAmount
    }

    @Synchronized
    fun clearAllData() {
        try {
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM mobile_data_products")
            }
        } catch (e: SQLException) {
            throw MobileDataProductDBException("Failed to clear data", e)
        }
    }

    @Synchronized
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
                    msisdn VARCHAR(15) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    type TINYINT NOT NULL,
                    available_amount BIGINT NOT NULL,
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
                    upload_amount BIGINT DEFAULT 0 NOT NULL,
                    uncategorised_amount BIGINT DEFAULT 0 NOT NULL
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

    private fun intToProductType(type: Int) =
        when (type) {
            0 -> ANYTIME
            1 -> NIGHT_SURFER
            2 -> OFF_PEAK
            else -> throw MobileDataProductDBException("Invalid MobileDataProductType value: $type")
        }

    private fun productTypeToInt(type: MobileDataProductType) =
        when (type) {
            ANYTIME -> 0
            NIGHT_SURFER -> 1
            OFF_PEAK -> 2
            else -> throw MobileDataProductDBException("Invalid MobileDataProductType: $type")
        }

}

open class MobileDataProductDBException(
    message: String,
    cause: Throwable? = null
): Exception(message, cause)

class MobileDataProductDBLockedException(
    message: String
): MobileDataProductDBException(message)
