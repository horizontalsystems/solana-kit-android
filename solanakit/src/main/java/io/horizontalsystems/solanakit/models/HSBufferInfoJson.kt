package io.horizontalsystems.solanakit.models

import com.solana.core.PublicKey

data class HSBufferInfoJson<T>(
    val data: T?,
    val lamports: Long,
    val owner: PublicKey,
    val executable: Boolean,
    val rentEpoch: String
)
