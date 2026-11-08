package com.example.pocketai.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_content") val fileContent: String
)

@Fts4(contentEntity = DocumentEntity::class)
@Entity(tableName = "documents_fts")
data class DocumentFtsEntity(
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_content") val fileContent: String
)

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(docs: List<DocumentEntity>)

    // Executes a lightning-fast indexed database lookup using SQLite match syntax
    @Query("""
        SELECT documents.file_name FROM documents 
        JOIN documents_fts ON documents.id = documents_fts.docid 
        WHERE documents_fts MATCH :searchQuery
    """)
    suspend fun searchIndexedDocuments(searchQuery: String): List<String>

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun countDocuments(): Int
}

@Database(entities = [DocumentEntity::class, DocumentFtsEntity::class], version = 1, exportSchema = false)
abstract class AppDocumentDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDocumentDatabase? = null

        fun getInstance(context: Context): AppDocumentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDocumentDatabase::class.java,
                    "pocket_docs.db"
                ).addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default workplace contracts and notes matching mockup specs
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getInstance(context).documentDao()
                            seedDefaultDocuments(dao)
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedDefaultDocuments(dao: DocumentDao) {
            val defaultDocs = listOf(
                DocumentEntity(
                    fileName = "supplier-agreement.md",
                    fileContent = """
                        # Master Supply Agreement
                        Delivery is split into two shipments: the first by 14 March and the rest by 2 April.
                        If either is more than ten days late, the buyer may cancel the remaining order.
                        Supplier shall maintain active quality logs and adhere to ISO-9001 audit standards.
                    """.trimIndent()
                ),
                DocumentEntity(
                    fileName = "terms-notes.txt",
                    fileContent = """
                        Internal Planning Notes - Supplier Call
                        Discussion points on shipment delivery schedule.
                        Confirmed dates: 14 March for batch A, 2 April for batch B.
                        Buyer reserve rights for cancellation if shipment delay exceeds 10 calendar days.
                    """.trimIndent()
                ),
                DocumentEntity(
                    fileName = "planning-meeting-minutes.md",
                    fileContent = """
                        Sprint Planning Meeting
                        Action items:
                        1. Deploy local on-device LiteRT-LM inference engine.
                        2. Verify Room FTS4 fast keyword indexing benchmarks (< 50ms).
                        3. Review guardian privacy audit scores for all installed third-party apps.
                    """.trimIndent()
                )
            )
            dao.insertAll(defaultDocs)
        }
    }
}
