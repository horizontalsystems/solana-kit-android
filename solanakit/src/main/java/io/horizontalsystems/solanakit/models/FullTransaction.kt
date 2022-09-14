package io.horizontalsystems.solanakit.models

data class FullTransaction(
    val transaction: Transaction,
    val tokenTransfers: List<TokenTransfer>,

    val decoration: String? = null
)
