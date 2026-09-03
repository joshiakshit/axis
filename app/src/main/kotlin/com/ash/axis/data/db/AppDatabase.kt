package com.ash.axis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CacheEntity::class, StudentMarkerEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    abstract fun studentMarkerDao(): StudentMarkerDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS student_markers " +
                            "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ownerId TEXT NOT NULL, " +
                            "title TEXT NOT NULL, type TEXT NOT NULL, startDate TEXT NOT NULL, " +
                            "endDate TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                    )
                }
            }

        // Markers became exam-only, so the type column is gone. SQLite needs a table rebuild to drop it.
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS student_markers_new " +
                            "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ownerId TEXT NOT NULL, " +
                            "title TEXT NOT NULL, startDate TEXT NOT NULL, endDate TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "INSERT INTO student_markers_new (id, ownerId, title, startDate, endDate, createdAt) " +
                            "SELECT id, ownerId, title, startDate, endDate, createdAt FROM student_markers " +
                            "WHERE type = 'EXAM'",
                    )
                    db.execSQL("DROP TABLE student_markers")
                    db.execSQL("ALTER TABLE student_markers_new RENAME TO student_markers")
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE student_markers ADD COLUMN type TEXT NOT NULL DEFAULT 'EXAM'")
                }
            }
    }
}
