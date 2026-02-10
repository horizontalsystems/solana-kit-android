package io.horizontalsystems.solanakit.transactions

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TransactionResponse(
    val blockTime: Long?,
    val meta: TransactionMeta?,
    val slot: Long?,
    val transaction: TransactionDetail?
)

@JsonClass(generateAdapter = true)
data class TransactionMeta(
    val err: Any?,
    val fee: Long,
    val preBalances: List<Long>,
    val postBalances: List<Long>,
    val preTokenBalances: List<TokenBalance>?,
    val postTokenBalances: List<TokenBalance>?
)

@JsonClass(generateAdapter = true)
data class TokenBalance(
    val accountIndex: Int,
    val mint: String,
    val owner: String?,
    val uiTokenAmount: UiTokenAmount?
)

@JsonClass(generateAdapter = true)
data class UiTokenAmount(
    val amount: String,
    val decimals: Int,
    val uiAmountString: String?
)

@JsonClass(generateAdapter = true)
data class TransactionDetail(
    val message: TransactionMessage?
)

@JsonClass(generateAdapter = true)
data class TransactionMessage(
    val accountKeys: List<AccountKey>?
)

@JsonClass(generateAdapter = true)
data class AccountKey(
    val pubkey: String,
    val signer: Boolean?,
    val writable: Boolean?
)
