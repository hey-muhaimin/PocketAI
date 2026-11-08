package com.example.pocketai.core.context

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for documents, chunks, and FTS4 inverted index.
 */
@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Query("SELECT * FROM documents ORDER BY indexedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<ChunkEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunkFts(fts: ChunkFtsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChunkFts(ftsList: List<ChunkFtsEntity>)

    @Query("DELETE FROM chunks WHERE documentId = :documentId")
    suspend fun deleteChunksByDocumentId(documentId: Long)

    @Query("DELETE FROM chunks_fts WHERE rowid IN (SELECT id FROM chunks WHERE documentId = :documentId)")
    suspend fun deleteFtsByDocumentId(documentId: Long)

    /**
     * Executes keyword search against SQLite FTS4 table.
     */
    @Query("SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH :sanitizedQuery LIMIT :limit")
    suspend fun searchFtsRowIds(sanitizedQuery: String, limit: Int): List<Long>

    @Query("SELECT * FROM chunks WHERE id IN (:ids)")
    suspend fun getChunksByIds(ids: List<Long>): List<ChunkEntity>

    @Query("SELECT * FROM chunks")
    suspend fun getAllChunks(): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getDocumentCount(): Int

    @Query("SELECT COUNT(*) FROM chunks")
    suspend fun getChunkCount(): Int

    @Transaction
    suspend fun insertDocumentWithChunks(
        document: DocumentEntity,
        chunkTexts: List<Pair<String, ByteArray>>
    ): Long {
        val docId = insertDocument(document)
        val entities = chunkTexts.mapIndexed { index, pair ->
            ChunkEntity(
                documentId = docId,
                chunkIndex = index,
                text = pair.first,
                embeddingBlob = pair.second,
                startOffset = 0,
                endOffset = pair.first.length
            )
        }
        val insertedIds = insertChunks(entities)
        val ftsList = insertedIds.mapIndexed { i, id ->
            ChunkFtsEntity(
                rowid = id,
                text = chunkTexts[i].first
            )
        }
        insertAllChunkFts(ftsList)
        return docId
    }
}
