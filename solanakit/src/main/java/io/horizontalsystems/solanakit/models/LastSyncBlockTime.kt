package io.horizontalsystems.solanakit.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class LastSyncBlockTime(@PrimaryKey var syncSourceName: String, val blockTime: Long)
