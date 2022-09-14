package io.horizontalsystems.solanakit.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class MintAccount(
    @PrimaryKey
    val address: String,
    val supply: Long,
    val decimals: Int
) {

    var isNft: Boolean = (supply == 1L)

}
