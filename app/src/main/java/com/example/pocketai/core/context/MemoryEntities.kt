package com.example.pocketai.core.context

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata record for an indexed document source.
 */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val uri: String,
    val sizeBytes: Long,
    val indexedAt: Long = System.currentTimeMillis(),
    val chunkCount: Int = 0
)

/**
 * Text passage chunk containing feature-hashed embedding vectors.
 */
@Entity(
    tableName = "chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("documentId")
    ]
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val chunkIndex: Int,
    val text: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray,
    val startOffset: Int,
    val endOffset: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChunkEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * SQLite FTS4 virtual table for high-performance keyword indexing.
 * [rowid] maps to [ChunkEntity.id].
 */
@Fts4
@Entity(tableName = "chunks_fts")
data class ChunkFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Long,
    val text: String
)
