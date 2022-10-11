package io.horizontalsystems.solanakit.models

import com.solana.networking.Network
import com.solana.networking.RPCEndpoint
import java.net.URL

sealed class RpcSource(var endpoint: RPCEndpoint, val syncInterval: Long) {
    object Serum: RpcSource(RPCEndpoint.mainnetBetaSerum, 30)
    object TritonOne: RpcSource(RPCEndpoint.mainnetBetaSolana, 30)
    class Custom(httpURL: URL, websocketURL: URL, syncInterval: Long): RpcSource(RPCEndpoint.custom(httpURL, websocketURL, Network.mainnetBeta), syncInterval)
}
