package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BiquoteApiService {
    @GET("api/latest")
    suspend fun getLatestQuotes(
        @Query("symbols") symbols: List<String>
    ): Response<ResponseBody>

    @GET("api/{symbol}/ohlc")
    suspend fun getOhlc(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<ResponseBody>
}
