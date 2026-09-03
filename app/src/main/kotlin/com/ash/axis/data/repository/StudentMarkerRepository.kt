package com.ash.axis.data.repository

import com.ash.axis.data.db.StudentMarkerDao
import com.ash.axis.data.db.StudentMarkerEntity
import com.ash.axis.domain.model.StudentMarker
import com.ash.axis.domain.model.StudentMarkerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentMarkerRepository
    @Inject
    constructor(
        private val markerDao: StudentMarkerDao,
    ) {
        fun observe(ownerId: String): Flow<List<StudentMarker>> =
            markerDao.observe(ownerId).map { entries -> entries.map { entry -> entry.toDomain() } }

        suspend fun add(
            ownerId: String,
            title: String,
            type: StudentMarkerType,
            startDate: LocalDate,
            endDate: LocalDate,
        ) {
            markerDao.insert(
                StudentMarkerEntity(
                    ownerId = ownerId,
                    title = title.trim(),
                    type = type.name,
                    startDate = startDate.toString(),
                    endDate = endDate.toString(),
                ),
            )
        }

        suspend fun delete(
            ownerId: String,
            markerId: Long,
        ) {
            markerDao.delete(markerId, ownerId)
        }

        private fun StudentMarkerEntity.toDomain(): StudentMarker =
            StudentMarker(
                id = id,
                title = title,
                type = StudentMarkerType.valueOf(type),
                startDate = LocalDate.parse(startDate),
                endDate = LocalDate.parse(endDate),
            )
    }
