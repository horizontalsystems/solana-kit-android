package io.horizontalsystems.solanakit.models

import com.solana.networking.Network
import com.solana.networking.RPCEndpoint
import java.net.URL

sealed class RpcSource(var endpoint: RPCEndpoint) {
    object TritonOne: RpcSource(RPCEndpoint.mainnetBetaSolana)
    class Custom(httpURL: URL, websocketURL: URL): RpcSource(RPCEndpoint.custom(httpURL, websocketURL, Network.mainnetBeta))
}
