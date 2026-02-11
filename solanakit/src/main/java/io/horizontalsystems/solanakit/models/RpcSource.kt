package io.horizontalsystems.solanakit.models

import com.solana.networking.Network
import com.solana.networking.RPCEndpoint
import java.net.URL

sealed class RpcSource(var name: String, var endpoint: RPCEndpoint, val syncInterval: Long) {
    val url: URL = endpoint.url

    class Alchemy(apiKey: String) : RpcSource(
        "Alchemy",
        RPCEndpoint.custom(
            URL("https://solana-mainnet.g.alchemy.com/v2/$apiKey"),
            URL("https://solana-mainnet.g.alchemy.com/v2/$apiKey"),
            Network.mainnetBeta
        ),
        30
    )
}
