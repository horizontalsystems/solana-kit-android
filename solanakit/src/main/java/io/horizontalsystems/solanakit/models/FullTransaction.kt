package io.horizontalsystems.solanakit.models

data class FullTransaction(
    val transaction: Transaction,
    val tokenTransfers: List<FullTokenTransfer>,

    val decoration: String? = null
)

data class FullTokenTransfer(
    val tokenTransfer: TokenTransfer,
    val mintAccount: MintAccount
)
