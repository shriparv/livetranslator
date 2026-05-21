package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "translations")
data class Translation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String
)

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations ORDER BY timestamp DESC")
    fun getAllTranslations(): Flow<List<Translation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(translation: Translation)

    @Query("DELETE FROM translations WHERE id = :id")
    suspend fun deleteTranslation(id: Int)

    @Query("DELETE FROM translations")
    suspend fun clearAllTranslations()
}

@Database(entities = [Translation::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "translator_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class TranslationRepository(private val translationDao: TranslationDao) {
    val allTranslations: Flow<List<Translation>> = translationDao.getAllTranslations()

    suspend fun insert(translation: Translation) {
        translationDao.insertTranslation(translation)
    }

    suspend fun delete(id: Int) {
        translationDao.deleteTranslation(id)
    }

    suspend fun clearAll() {
        translationDao.clearAllTranslations()
    }
}
