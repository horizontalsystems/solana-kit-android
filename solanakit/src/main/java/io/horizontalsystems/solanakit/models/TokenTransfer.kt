package io.horizontalsystems.solanakit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity
class TokenTransfer(
    val transactionHash: String,
    val mintAddress: String,
    val amount: BigDecimal,

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0
)
