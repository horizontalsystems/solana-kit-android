package io.horizontalsystems.solanakit.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class RpcKeyRotationInterceptor(
    private val keys: List<String>,
    private val urlRewriter: (HttpUrl, String) -> HttpUrl
) : Interceptor {

    private val currentKeyIndex = AtomicInteger(Random.nextInt(keys.size))

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val startIndex = currentKeyIndex.get()
        var previousResponse: Response? = null

        for (attempt in keys.indices) {
            val keyIndex = (startIndex + attempt) % keys.size
            val newUrl = urlRewriter(originalRequest.url, keys[keyIndex])
            val newRequest = originalRequest.newBuilder().url(newUrl).build()
            previousResponse?.close()
            val response = chain.proceed(newRequest)
            if (response.code != 429 || attempt == keys.lastIndex) {
                currentKeyIndex.set(keyIndex)
                return response
            }
            previousResponse = response
        }

        return chain.proceed(originalRequest)
    }
}
