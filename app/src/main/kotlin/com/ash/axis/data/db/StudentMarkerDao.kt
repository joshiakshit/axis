package com.ash.axis.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentMarkerDao {
    @Query("SELECT * FROM student_markers WHERE ownerId = :ownerId ORDER BY startDate, createdAt")
    fun observe(ownerId: String): Flow<List<StudentMarkerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(marker: StudentMarkerEntity): Long

    @Query("DELETE FROM student_markers WHERE id = :id AND ownerId = :ownerId")
    suspend fun delete(
        id: Long,
        ownerId: String,
    )
}
