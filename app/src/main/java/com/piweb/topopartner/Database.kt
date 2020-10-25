package com.piweb.topopartner

import androidx.room.*


@Entity
data class Recording(
    @ColumnInfo(name = "uploaded") var uploaded: Boolean = false,
    @ColumnInfo(name = "gpx") var gpx: String?,
    @ColumnInfo(name = "date") var date: Long?
) {
    @PrimaryKey(autoGenerate = true)
    var uid: Int = 0
}


@Dao
interface RecordingDao {
    @Query("SELECT * FROM recording")
    fun getAll(): List<Recording>

    @Query("SELECT * FROM recording WHERE uid = :recordingId")
    fun getById(recordingId: Int): Recording

    @Insert
    fun insert(recording: Recording)

    @Delete
    fun delete(recording: Recording)

    @Query("UPDATE recording SET uploaded = 1 WHERE uid = :recordingId")
    fun setUploaded(recordingId: Int)
}


@Entity
data class Itinerary(
    @ColumnInfo(name = "label") var label: String?,
    @ColumnInfo(name = "gpx") var gpx: String?,
    @ColumnInfo(name = "distance") var distance: Double?,
    @ColumnInfo(name = "uphill") var uphill: Double?
) {
    @PrimaryKey(autoGenerate = true)
    var uid: Int = 0
}


@Dao
interface ItineraryDao {
    @Query("SELECT * FROM itinerary")
    fun getAll(): List<Itinerary>

    @Query("SELECT * FROM itinerary WHERE uid = :itineraryId")
    fun getById(itineraryId: Int): Itinerary

    @Insert
    fun insert(itinerary: Itinerary)

    @Delete
    fun delete(itinerary: Itinerary)

    @Query("DELETE FROM itinerary")
    fun deleteAll()
}


@Database(entities = [Recording::class, Itinerary::class], version = 2)
abstract class TopopartnerDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun itineraryDao(): ItineraryDao
}


