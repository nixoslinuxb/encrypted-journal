package com.encryptedjournal.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY isPinned DESC, timestamp DESC")
    fun getAllStories(): Flow<List<Story>>

    @Query("SELECT * FROM stories")
    suspend fun getAllStoriesOnce(): List<Story>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: Story)

    @Delete
    suspend fun deleteStory(story: Story)

    @Update
    suspend fun updateStory(story: Story)

    @Query("SELECT * FROM stories WHERE id = :id")
    suspend fun getStoryById(id: String): Story?
}
