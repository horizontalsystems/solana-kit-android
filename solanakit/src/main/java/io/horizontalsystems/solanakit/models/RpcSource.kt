package io.horizontalsystems.solanakit.models

import com.solana.networking.Network
import com.solana.networking.RPCEndpoint
import io.horizontalsystems.solanakit.network.RpcKeyRotationInterceptor
import okhttp3.Interceptor
import java.net.URL
import kotlin.random.Random

sealed class RpcSource(var name: String, var endpoint: RPCEndpoint, val syncInterval: Long) {
    val url: URL = endpoint.url

    abstract fun createInterceptor(): Interceptor?

    class Alchemy(apiKeys: String) : RpcSource(
        "Alchemy",
        buildEndpoint(apiKeys),
        30
    ) {
        private val keys = parseKeys(apiKeys)

        override fun createInterceptor(): Interceptor? {
            if (keys.size <= 1) return null
            return RpcKeyRotationInterceptor(keys) { url, key ->
                url.newBuilder()
                    .setPathSegment(url.pathSegments.size - 1, key)
                    .build()
            }
        }

        companion object {
            private fun parseKeys(apiKeys: String) =
                apiKeys.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            private fun buildEndpoint(apiKeys: String): RPCEndpoint {
                val keys = parseKeys(apiKeys)
                val initialKey = keys[Random.nextInt(keys.size)]
                return RPCEndpoint.custom(
                    URL("https://solana-mainnet.g.alchemy.com/v2/$initialKey"),
                    URL("https://solana-mainnet.g.alchemy.com/v2/$initialKey"),
                    Network.mainnetBeta
                )
            }
        }
    }

    class Helius(apiKeys: String) : RpcSource(
        "Helius",
        buildEndpoint(apiKeys),
        30
    ) {
        private val keys = parseKeys(apiKeys)

        override fun createInterceptor(): Interceptor? {
            if (keys.size <= 1) return null
            return RpcKeyRotationInterceptor(keys) { url, key ->
                url.newBuilder()
                    .setQueryParameter("api-key", key)
                    .build()
            }
        }

        companion object {
            private fun parseKeys(apiKeys: String) =
                apiKeys.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            private fun buildEndpoint(apiKeys: String): RPCEndpoint {
                val keys = parseKeys(apiKeys)
                val initialKey = keys[Random.nextInt(keys.size)]
                return RPCEndpoint.custom(
                    URL("https://mainnet.helius-rpc.com/?api-key=$initialKey"),
                    URL("https://mainnet.helius-rpc.com/?api-key=$initialKey"),
                    Network.mainnetBeta
                )
            }
        }
    }
}
