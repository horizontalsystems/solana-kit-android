package io.horizontalsystems.solanakit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.solana.vendor.borshj.BorshCodable
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

@Entity
class TokenAccount(
    @PrimaryKey
    val address: String,
    val mintAddress: String,
    val balance: BigDecimal,

): BorshCodable {

    override fun equals(other: Any?): Boolean {
        return (other as? TokenAccount)?.let { it.address == address } ?: false
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }

}
