package com.scorpk.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, CommandEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ScorpkDatabase : RoomDatabase() {

    abstract fun commandDao(): CommandDao

    companion object {
        private const val DATABASE_NAME = "scorpk.db"

        /** v2: adjuntos en los mensajes del chat. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commands ADD COLUMN attachment_name TEXT")
                db.execSQL("ALTER TABLE commands ADD COLUMN attachment_mime TEXT")
                db.execSQL("ALTER TABLE commands ADD COLUMN attachment_uri TEXT")
            }
        }

        fun build(context: Context): ScorpkDatabase =
            Room.databaseBuilder(context.applicationContext, ScorpkDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
