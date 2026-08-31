package com.vendo.core.network.dto

import kotlinx.serialization.Serializable

/** Mirrors backend/app/schemas/api_out.py's QraDetailCacheOut/
 * QraHeaderCacheOut exactly - see core/datastore's CacheRepository for how
 * this gets pulled into the offline cache (same Refresh flow as items/
 * customers). Quantities/price follow the same String convention as
 * RecentOrderLineOut.qty in this file, since the backend serializes
 * Decimal fields as JSON strings, not numbers. */
@Serializable
data class QraDetailCacheOut(
    // item_nb_buy/item_nb_get/qty_get are null for a type P row;
    // item_nb_price is null for type T/B - see backend's
    // app/models/qra.py QraDetail docstring.
    val item_nb_buy: String? = null,
    val item_nb_get: String? = null,
    val item_nb_price: String? = null,
    val qty_buy: String,
    val qty_get: String? = null,
    val qra_type: String,
    val qra_price: String? = null,
)

@Serializable
data class QraHeaderCacheOut(
    val cust_nb: String,
    val from_date: String,
    val to_date: String,
    val status: String,
    val details: List<QraDetailCacheOut> = emptyList(),
)
