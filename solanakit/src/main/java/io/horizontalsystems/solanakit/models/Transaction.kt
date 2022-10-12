package io.horizontalsystems.solanakit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity
class Transaction(
    @PrimaryKey
    val hash: String,
    val timestamp: Long,
    val fee: BigDecimal? = null,
    val from: String? = null,
    val to: String? = null,
    val amount: BigDecimal? = null,
    val error: String? = null,
    val pending: Boolean = false
)
