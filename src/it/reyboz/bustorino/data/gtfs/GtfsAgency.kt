package it.reyboz.bustorino.data.gtfs

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = GtfsAgency.TABLE_NAME)
data class GtfsAgency(
        @PrimaryKey
        @ColumnInfo(name = COL_GTFS_ID)
        val gtfsId: String,
        @ColumnInfo(name = COL_NAME)
        val name: String,
        @ColumnInfo(name = COL_URL)
        val url: String,
        @ColumnInfo(name = COL_FAREURL)
        val fareUrl: String?,
        @ColumnInfo(name = COL_PHONE)
        val phone: String?,
        @Embedded var feed: GtfsFeed?
): GtfsTable{
    constructor(valuesByColumn: Map<String,String>) : this(
            valuesByColumn[COL_GTFS_ID]!!,
            valuesByColumn[COL_NAME]!!,
            valuesByColumn[COL_URL]!!,
            valuesByColumn[COL_FAREURL],
            valuesByColumn[COL_PHONE],
        null
    )

    companion object{
        const val TABLE_NAME="gtfs_agencies"

        const val COL_GTFS_ID="gtfs_id"
        const val COL_NAME="ag_name"
        const val COL_URL="ag_url"
        const val COL_FAREURL = "fare_url"
        const val COL_PHONE = "phone"

        val COLUMNS = arrayOf(
                COL_GTFS_ID,
                COL_NAME,
                COL_URL,
                COL_FAREURL,
                COL_PHONE
        )
        const val CREATE_SQL =
                "CREATE TABLE $TABLE_NAME ( $COL_GTFS_ID  )"
    }

    override fun getColumns(): Array<String> {
        return COLUMNS
    }
}
