package com.example.pocketai.core.context

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.pocketai.core.trust.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditLogEntity): Long

    @Query("SELECT * FROM audit_log_chain ORDER BY chainIndex ASC")
    suspend fun getAllAscending(): List<AuditLogEntity>

    @Query("SELECT * FROM audit_log_chain ORDER BY chainIndex DESC")
    fun getAllDescendingFlow(): Flow<List<AuditLogEntity>>

    @Query("SELECT COUNT(*) FROM audit_log_chain")
    suspend fun getCount(): Int
}

@Database(
    entities = [
        DocumentEntity::class,
        ChunkEntity::class,
        ChunkFtsEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MemoryDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: MemoryDatabase? = null

        fun getInstance(context: Context): MemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    "pocketai_context.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
