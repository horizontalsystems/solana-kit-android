package io.horizontalsystems.solanakit.noderpc.endpoints

import com.solana.api.Api
import com.solana.core.PublicKey
import com.solana.models.ConfirmedSignFAddr2
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types


fun Api.getSignaturesForAddress(
    account: PublicKey,
    limit: Int? = null,
    before: String?  = null,
    until: String? = null,
    onComplete: (Result<List<SignatureInfo>>) -> Unit
) {
    val params: MutableList<Any> = ArrayList()
    params.add(account.toString())
    params.add( ConfirmedSignFAddr2(limit = limit?.toLong(), before = before, until = until) )

    router.request<List<SignatureInfo>>(
        "getSignaturesForAddress", params,
        Types.newParameterizedType(List::class.java, SignatureInfo::class.java)
    ) { result ->
        result.onSuccess {
            onComplete(Result.success(it))
        }.onFailure {
            onComplete(Result.failure(it))
        }
    }
}

@JsonClass(generateAdapter = true)
data class SignatureInfo(
    var err: Any?,
    val memo: Any?,
    val signature: String?,
    val slot: Long?,
    val blockTime: Long?
)
