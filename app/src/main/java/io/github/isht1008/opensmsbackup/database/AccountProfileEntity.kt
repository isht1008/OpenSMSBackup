package io.github.isht1008.opensmsbackup.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "account_profiles",
    indices = [
        Index(
            value = ["account_email"],
            unique = true
        ),
        Index(
            value = ["provider_account_id"],
            unique = true
        )
    ]
)
data class AccountProfileEntity(

    @PrimaryKey
    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "provider_account_id")
    val providerAccountId: String? = null,

    @ColumnInfo(name = "account_email")
    val accountEmail: String,

    @ColumnInfo(name = "display_name")
    val displayName: String? = null,

    @ColumnInfo(name = "photo_url")
    val photoUrl: String? = null,

    @ColumnInfo(name = "connection_state")
    val connectionState: String = CONNECTION_STATE_CONNECTED,

    @ColumnInfo(name = "created_time")
    val createdTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_time")
    val updatedTime: Long = System.currentTimeMillis()
) {

    companion object {

        const val CONNECTION_STATE_CONNECTED =
            "CONNECTED"

        const val CONNECTION_STATE_DISCONNECTED =
            "DISCONNECTED"
    }
}
