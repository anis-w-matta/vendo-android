package com.vendo.core.datastore.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {

    // ---- customers ----

    @Query("SELECT * FROM cached_customers ORDER BY customerName")
    fun observeCustomers(): Flow<List<CachedCustomerEntity>>

    @Query(
        "SELECT * FROM cached_customers WHERE customerName LIKE '%' || :query || '%' " +
            "OR custNb LIKE '%' || :query || '%' ORDER BY customerName LIMIT :limit",
    )
    suspend fun searchCustomers(query: String, limit: Int = 20): List<CachedCustomerEntity>

    @Query("SELECT COUNT(*) FROM cached_customers")
    suspend fun customerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(rows: List<CachedCustomerEntity>)

    @Query("DELETE FROM cached_customers")
    suspend fun clearCustomers()

    // ---- items ----

    @Query("SELECT * FROM cached_items ORDER BY itemDesc")
    fun observeItems(): Flow<List<CachedItemEntity>>

    @Query(
        "SELECT * FROM cached_items WHERE itemDesc LIKE '%' || :query || '%' " +
            "OR itemNb LIKE '%' || :query || '%' ORDER BY itemDesc LIMIT :limit",
    )
    suspend fun searchItems(query: String, limit: Int = 20): List<CachedItemEntity>

    @Query("SELECT COUNT(*) FROM cached_items")
    suspend fun itemCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(rows: List<CachedItemEntity>)

    @Query("DELETE FROM cached_items")
    suspend fun clearItems()

    // ---- recent orders ----

    @Query("SELECT * FROM cached_orders ORDER BY sortOrder")
    fun observeOrders(): Flow<List<CachedOrderEntity>>

    @Query("SELECT COUNT(*) FROM cached_orders")
    suspend fun orderCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(rows: List<CachedOrderEntity>)

    @Query("DELETE FROM cached_orders")
    suspend fun clearOrders()

    // ---- QRA agreements ----

    @Query("SELECT * FROM cached_qra_headers ORDER BY custNb")
    fun observeQra(): Flow<List<CachedQraHeaderEntity>>

    @Query("SELECT COUNT(*) FROM cached_qra_headers")
    suspend fun qraCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQra(rows: List<CachedQraHeaderEntity>)

    @Query("DELETE FROM cached_qra_headers")
    suspend fun clearQra()

    // ---- full refresh ----

    /** Replaces one cache table's contents atomically - a refresh that
     * fails partway through (network drops mid-download) must never leave
     * the cache half-cleared with the old rows already gone but the new
     * ones not yet in. */
    @Transaction
    suspend fun replaceCustomers(rows: List<CachedCustomerEntity>) {
        clearCustomers()
        insertCustomers(rows)
    }

    @Transaction
    suspend fun replaceItems(rows: List<CachedItemEntity>) {
        clearItems()
        insertItems(rows)
    }

    @Transaction
    suspend fun replaceOrders(rows: List<CachedOrderEntity>) {
        clearOrders()
        insertOrders(rows)
    }

    @Transaction
    suspend fun replaceQra(rows: List<CachedQraHeaderEntity>) {
        clearQra()
        insertQra(rows)
    }
}
