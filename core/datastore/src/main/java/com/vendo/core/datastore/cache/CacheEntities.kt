package com.vendo.core.datastore.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Local offline mirror of the server's customer table - see
 * CacheRepository (:app) for how/when this gets populated (only on an
 * explicit operator-triggered Refresh, never automatically). */
@Entity(tableName = "cached_customers")
data class CachedCustomerEntity(
    @PrimaryKey val custNb: String,
    val customerName: String,
)

/** Local offline mirror of the server's item catalogue. */
@Entity(tableName = "cached_items")
data class CachedItemEntity(
    @PrimaryKey val itemNb: String,
    val itemDesc: String,
    val category: String,
)

/** One line of a cached recent order - `lines` is a JSON-encoded
 * List<CachedOrderLine> rather than a second joined table, since it's
 * always read/written as a whole set per order and never queried on its
 * own (see CacheConverters for the (de)serialization). */
@Serializable
data class CachedOrderLine(
    val itemNb: String?,
    val itemDesc: String,
    val qty: String,
    val uom: String?,
)

/** One of this salesman's own most recently committed orders -
 * (orderNb, orderType) mirrors the server's real composite key, since a
 * RETURN can reuse the order_nb of the SO it returns against.
 *
 * The server has no order timestamp to sort by (order_header dropped
 * created_at) - /orders/recent already comes back most-recent-first
 * (sorted server-side by the committing request's decided_at), so
 * sortOrder just records each row's position in that response and
 * CacheDao.observeOrders() replays it, rather than the cache re-deriving
 * a recency ordering it has no data left to compute.
 */
@Entity(tableName = "cached_orders", primaryKeys = ["orderNb", "orderType"])
data class CachedOrderEntity(
    val orderNb: String,
    val orderType: String,
    val custNb: String,
    val customerName: String?,
    val sortOrder: Int,
    val lines: List<CachedOrderLine>,
)

/** One row of a cached QRA agreement's detail table - see
 * CachedQraHeaderEntity. Quantities/price stay as the raw String the
 * server sends (Decimal serialized as JSON string), same convention as
 * CachedOrderLine.qty above - this cache mirror never does arithmetic on
 * them itself, so there's no reason to parse eagerly. */
@Serializable
data class CachedQraDetail(
    // itemNbBuy/itemNbGet/qtyGet are null for a type P row; itemNbPrice
    // is null for type T/B.
    val itemNbBuy: String?,
    val itemNbGet: String?,
    val itemNbPrice: String?,
    val qtyBuy: String,
    val qtyGet: String?,
    val qraType: String,
    val qraPrice: String?,
)

/** Local offline mirror of the server's QRA agreements - synced only on
 * an explicit operator Refresh, same as everything else in this file.
 * QRA is applied automatically at order-commit time on the server (see
 * backend's app/services/qra_engine.py); nothing in the Android app reads
 * this cache yet, it exists so a future screen doesn't need a second
 * caching mechanism built for it. */
@Entity(tableName = "cached_qra_headers")
data class CachedQraHeaderEntity(
    // Each customer has at most one QRA agreement (backend's qra_header is
    // keyed on cust_nb, not a surrogate id), so cust_nb is the natural
    // Room primary key too.
    @PrimaryKey val custNb: String,
    val fromDate: String,
    val toDate: String,
    val status: String,
    val details: List<CachedQraDetail>,
)
