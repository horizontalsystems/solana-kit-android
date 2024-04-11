package io.horizontalsystems.solanakit.models

import com.solana.models.buffer.AccountInfo
import com.solana.models.buffer.Buffer
import com.solana.models.buffer.BufferInfo
import com.solana.vendor.borshj.Borsh
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

class BufferInfoJsonAdapter(val borsh: Borsh) : Object() {
    @FromJson
    fun fromJson(bufferInfoJson: HSBufferInfoJson<Any>): BufferInfo<AccountInfo> {
        val convertedRentEpoch = bufferInfoJson.rentEpoch.toULong().toLong()

        return BufferInfo(
            data = bufferInfoJson.data?.let { Buffer.create(borsh, it, AccountInfo::class.java) },
            executable = bufferInfoJson.executable,
            lamports = bufferInfoJson.lamports,
            owner = bufferInfoJson.owner.toBase58(),
            rentEpoch = convertedRentEpoch
        )
    }

    @ToJson
    fun toJson(accountInfoBufferInfo: BufferInfo<AccountInfo>): String {
        throw UnsupportedOperationException()
    }
}
