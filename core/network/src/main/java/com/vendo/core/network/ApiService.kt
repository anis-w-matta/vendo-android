package com.vendo.core.network

import com.vendo.core.network.dto.AcceptIn
import com.vendo.core.network.dto.AcceptOut
import com.vendo.core.network.dto.AccountUpdateIn
import com.vendo.core.network.dto.ActivityLogOut
import com.vendo.core.network.dto.AssignSalesmanIn
import com.vendo.core.network.dto.CandidateOut
import com.vendo.core.network.dto.ChangePasswordIn
import com.vendo.core.network.dto.CustomerCandidateOut
import com.vendo.core.network.dto.CustomerDetailOut
import com.vendo.core.network.dto.IngestVoiceOut
import com.vendo.core.network.dto.LoginIn
import com.vendo.core.network.dto.LoginOut
import com.vendo.core.network.dto.CustomerCacheOut
import com.vendo.core.network.dto.ItemCacheOut
import com.vendo.core.network.dto.OkOut
import com.vendo.core.network.dto.QraHeaderCacheOut
import com.vendo.core.network.dto.QueueRow
import com.vendo.core.network.dto.RecentOrderOut
import com.vendo.core.network.dto.RegisterIn
import com.vendo.core.network.dto.RejectIn
import com.vendo.core.network.dto.RequestDetail
import com.vendo.core.network.dto.SalesmanOut
import com.vendo.core.network.dto.SalesmanUpdateIn
import com.vendo.core.network.dto.TranscribePreviewOut
import com.vendo.core.network.dto.VoiceStatusOut
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginIn): LoginOut

    @GET("auth/me")
    suspend fun me(): SalesmanOut

    @PATCH("auth/me")
    suspend fun updateMe(@Body body: AccountUpdateIn): SalesmanOut

    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordIn): OkOut

    @Multipart
    @POST("ingest/voice")
    suspend fun ingestVoice(
        @Part("phone") phone: RequestBody,
        @Part audio: MultipartBody.Part,
        @Part("transcript") transcript: RequestBody?,
        @Part("language") language: RequestBody?,
        @Part("submit_mode") submitMode: RequestBody?,
    ): IngestVoiceOut

    @GET("ingest/voice/{voiceId}")
    suspend fun voiceStatus(@Path("voiceId") voiceId: Int): VoiceStatusOut

    @Multipart
    @POST("ingest/transcribe-preview")
    suspend fun transcribePreview(@Part audio: MultipartBody.Part): TranscribePreviewOut

    @GET("queue")
    suspend fun listQueue(
        @Query("status") status: String? = null,
        @Query("flag") flag: String? = null,
        @Query("limit") limit: Int = 50,
    ): List<QueueRow>

    @GET("queue/{reqId}")
    suspend fun getRequest(@Path("reqId") reqId: Int): RequestDetail

    @GET("items/search")
    suspend fun searchItems(@Query("q") q: String): List<CandidateOut>

    @GET("customers/search")
    suspend fun searchCustomers(@Query("q") q: String): List<CustomerCandidateOut>

    // ---- offline cache refresh (see core/datastore's CacheRepository) ----

    @GET("customers/all")
    suspend fun listAllCustomers(): List<CustomerCacheOut>

    @GET("items/all")
    suspend fun listAllItems(): List<ItemCacheOut>

    @GET("orders/recent")
    suspend fun listRecentOrders(@Query("limit") limit: Int = 30): List<RecentOrderOut>

    @GET("qra/all")
    suspend fun listAllQra(): List<QraHeaderCacheOut>

    @POST("queue/{reqId}/claim")
    suspend fun claim(@Path("reqId") reqId: Int): OkOut

    @POST("requests/{reqId}/accept")
    suspend fun accept(@Path("reqId") reqId: Int, @Body body: AcceptIn): AcceptOut

    @POST("requests/{reqId}/reject")
    suspend fun reject(@Path("reqId") reqId: Int, @Body body: RejectIn): OkOut

    @GET("activity")
    suspend fun listActivity(
        @Query("event_type") eventType: String? = null,
        @Query("level") level: String? = null,
        @Query("cust_nb") custNb: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): List<ActivityLogOut>

    // ---- admin-only (see :admin app) ----

    @GET("customers/{custNb}")
    suspend fun getCustomerDetail(@Path("custNb") custNb: String): CustomerDetailOut

    @PATCH("customers/{custNb}/salesman")
    suspend fun assignCustomerSalesman(
        @Path("custNb") custNb: String,
        @Body body: AssignSalesmanIn,
    ): CustomerDetailOut

    @GET("salesmen")
    suspend fun listSalesmen(
        @Query("include_inactive") includeInactive: Boolean = false,
    ): List<SalesmanOut>

    @PATCH("salesmen/{loginId}")
    suspend fun updateSalesman(
        @Path("loginId") loginId: String,
        @Body body: SalesmanUpdateIn,
    ): SalesmanOut

    @POST("auth/register")
    suspend fun register(@Body body: RegisterIn): SalesmanOut
}
