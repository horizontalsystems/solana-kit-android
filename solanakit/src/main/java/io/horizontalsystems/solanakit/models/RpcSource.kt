package io.horizontalsystems.solanakit.models

import com.solana.networking.Network
import com.solana.networking.RPCEndpoint
import java.net.URL

sealed class RpcSource(var endpoint: RPCEndpoint) {
    object MainnetBeta: RpcSource(RPCEndpoint.mainnetBetaSolana)
    object MainnetSerum: RpcSource(RPCEndpoint.mainnetBetaSerum)
    object MainnetTestnet: RpcSource(RPCEndpoint.testnetSolana)
    class Custom(httpURL: URL, websocketURL: URL): RpcSource(RPCEndpoint.custom(httpURL, websocketURL, Network.mainnetBeta))
}
