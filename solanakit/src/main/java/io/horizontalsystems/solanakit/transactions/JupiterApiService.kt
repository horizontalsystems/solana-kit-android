package io.horizontalsystems.solanakit.transactions

import io.horizontalsystems.solanakit.models.TokenInfo
import io.reactivex.Single
import kotlinx.coroutines.rx2.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class JupiterApiService(apiKey: String) {

    private val baseUrl = "https://api.jup.ag/"

    private val api: JupiterApi

    init {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("x-api-key", apiKey)
                    .build()
                chain.proceed(request)
            })

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient.build())
            .build()

        api = retrofit.create(JupiterApi::class.java)
    }

    suspend fun tokenInfo(mintAddress: String): TokenInfo {
        val response = api.searchToken(mintAddress).await()

        val token = response.firstOrNull()
            ?: throw Exception("Token not found: $mintAddress")

        return TokenInfo(
            name = token.name,
            symbol = token.symbol,
            decimals = token.decimals
        )
    }

    private interface JupiterApi {
        @GET("tokens/v2/search")
        fun searchToken(
            @Query("query") query: String
        ): Single<List<JupiterToken>>
    }

    data class JupiterToken(
        val id: String,
        val name: String,
        val symbol: String,
        val decimals: Int
    )
}
