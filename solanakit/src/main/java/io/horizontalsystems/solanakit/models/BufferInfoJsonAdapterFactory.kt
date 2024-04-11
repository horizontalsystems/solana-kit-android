package io.horizontalsystems.solanakit.models

import com.solana.networking.MoshiAdapterFactory
import com.solana.vendor.borshj.Borsh

class BufferInfoJsonAdapterFactory : MoshiAdapterFactory {
    override fun create(borsh: Borsh): Object {
        return BufferInfoJsonAdapter(borsh)
    }
}
