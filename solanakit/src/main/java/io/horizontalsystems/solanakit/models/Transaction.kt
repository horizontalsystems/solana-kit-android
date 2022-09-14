package io.horizontalsystems.solanakit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity
class Transaction(
    @PrimaryKey
    val hash: String,
    val blockTime: Long,

    val fee: BigDecimal? = null,
    val from: String? = null,
    val to: String? = null,
    val amount: BigDecimal? = null
)
