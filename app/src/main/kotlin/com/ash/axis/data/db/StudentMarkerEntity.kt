package com.ash.axis.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "student_markers")
data class StudentMarkerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: String,
    val title: String,
    val type: String,
    val startDate: String,
    val endDate: String,
    val createdAt: Long = System.currentTimeMillis(),
)
