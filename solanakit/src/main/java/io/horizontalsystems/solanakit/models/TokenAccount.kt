package io.horizontalsystems.solanakit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.math.BigInteger

@Entity
class TokenAccount(
    @PrimaryKey
    val address: String,
    val mintAddress: String,
    val balance: BigDecimal
)
