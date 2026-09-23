package org.jellycopter.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeck(deck: DeckEntity)

    @Query("SELECT * FROM decks WHERE deckId = :deckId")
    suspend fun getDeckById(deckId: String): DeckEntity?

    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    fun getAllDecksFlow(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    suspend fun getAllDecks(): List<DeckEntity>

    @Query("DELETE FROM decks WHERE deckId = :deckId")
    suspend fun deleteDeck(deckId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: DeckProgressEntity)

    @Query("SELECT * FROM deck_progress WHERE deckId = :deckId")
    suspend fun getProgress(deckId: String): DeckProgressEntity?

    @Query("SELECT * FROM deck_progress WHERE deckId = :deckId")
    fun getProgressFlow(deckId: String): Flow<DeckProgressEntity?>

    @Query("DELETE FROM deck_progress WHERE deckId = :deckId")
    suspend fun deleteProgress(deckId: String)
}
