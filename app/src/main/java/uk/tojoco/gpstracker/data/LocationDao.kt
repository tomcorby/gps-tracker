package uk.tojoco.gpstracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(location: LocationEntity)

    @Query("SELECT * FROM locations ORDER BY timestamp DESC")
    suspend fun getAll(): List<LocationEntity>
}