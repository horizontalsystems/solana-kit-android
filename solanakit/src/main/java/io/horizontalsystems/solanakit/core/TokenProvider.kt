package io.horizontalsystems.solanakit.core

import io.horizontalsystems.solanakit.transactions.JupiterApiService

class TokenProvider(private val jupiterApiService: JupiterApiService) {

    suspend fun getTokenInfo(mintAddress: String) = jupiterApiService.tokenInfo(mintAddress)

}
