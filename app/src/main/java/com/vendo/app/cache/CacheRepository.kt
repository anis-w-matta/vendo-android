package com.vendo.app.cache

import com.vendo.core.datastore.cache.CacheDao
import com.vendo.core.datastore.cache.CachedCustomerEntity
import com.vendo.core.datastore.cache.CachedItemEntity
import com.vendo.core.datastore.cache.CachedOrderEntity
import com.vendo.core.datastore.cache.CachedOrderLine
import com.vendo.core.datastore.cache.CachedQraDetail
import com.vendo.core.datastore.cache.CachedQraHeaderEntity
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.CandidateOut
import com.vendo.core.network.dto.CustomerCandidateOut
import javax.inject.Inject
import javax.inject.Singleton

data class CacheSyncResult(val customers: Int, val items: Int, val orders: Int, val qra: Int)

/** Pulls the operator's customers/items/recent-orders down from the
 * server into the local Room cache (core/datastore's CacheDatabase) so
 * the Request screen's customer/item pickers and reorder flow keep
 * working with no network at all. Lives in :app (not core/datastore)
 * because it needs both ApiService (core/network) and CacheDao
 * (core/datastore), and core/network already depends on core/datastore
 * for auth - core/datastore can't depend back on core/network.
 *
 * Only ever runs when the operator explicitly taps Refresh (see
 * AppViewModel.refreshCache / VendoNavGraph's drawer entry) - never
 * polled automatically, so it never surprises a salesman with a slow
 * background sync or a cache that silently went stale without their
 * knowledge.
 */
@Singleton
class CacheRepository @Inject constructor(
    private val api: ApiService,
    private val dao: CacheDao,
) {
    suspend fun refresh(): Result<CacheSyncResult> = runCatching {
        val customers = api.listAllCustomers()
        val items = api.listAllItems()
        val orders = api.listRecentOrders()
        val qra = api.listAllQra()

        dao.replaceCustomers(customers.map {
            CachedCustomerEntity(custNb = it.cust_nb, customerName = it.customer_name)
        })
        dao.replaceItems(items.map {
            CachedItemEntity(itemNb = it.item_nb, itemDesc = it.item_desc,
                             category = it.category)
        })
        dao.replaceOrders(orders.mapIndexed { index, o ->
            CachedOrderEntity(
                orderNb = o.order_nb,
                orderType = o.order_type,
                custNb = o.cust_nb,
                customerName = o.customer_name,
                sortOrder = index,
                lines = o.lines.map { l ->
                    CachedOrderLine(itemNb = l.item_nb, itemDesc = l.item_desc,
                                    qty = l.qty, uom = l.uom)
                },
            )
        })

        dao.replaceQra(qra.map { h ->
            CachedQraHeaderEntity(
                custNb = h.cust_nb, fromDate = h.from_date,
                toDate = h.to_date, status = h.status,
                details = h.details.map { d ->
                    CachedQraDetail(itemNbBuy = d.item_nb_buy, itemNbGet = d.item_nb_get,
                                    itemNbPrice = d.item_nb_price,
                                    qtyBuy = d.qty_buy, qtyGet = d.qty_get,
                                    qraType = d.qra_type, qraPrice = d.qra_price)
                },
            )
        })

        CacheSyncResult(customers.size, items.size, orders.size, qra.size)
    }

    /** Timestamp-free "is there anything cached at all" check, so a
     * fresh install with no cache yet doesn't silently look like an
     * empty result set for offline search. */
    suspend fun isEmpty(): Boolean =
        dao.customerCount() == 0 && dao.itemCount() == 0 && dao.orderCount() == 0 &&
            dao.qraCount() == 0

    /** Plain substring match against the local cache, in the same shape
     * the live /customers/search endpoint returns - used as a fallback
     * when that call fails (no network), not as the primary search path,
     * so this never needs the server's fuzzy/tie-margin logic. */
    suspend fun searchCustomersOffline(query: String, limit: Int = 20): List<CustomerCandidateOut> =
        dao.searchCustomers(query, limit).map {
            CustomerCandidateOut(cust_nb = it.custNb, customer_name = it.customerName,
                                 score = 100.0)
        }

    suspend fun searchItemsOffline(query: String, limit: Int = 20): List<CandidateOut> =
        dao.searchItems(query, limit).map {
            CandidateOut(item_nb = it.itemNb, item_desc = it.itemDesc,
                        category = it.category, score = 100.0, method = "offline_cache")
        }
}
