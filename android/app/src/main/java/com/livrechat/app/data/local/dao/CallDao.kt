package com.livrechat.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.livrechat.app.data.local.entity.CallRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun getAllCallsFlow(): Flow<List<CallRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallRecordEntity)

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteCall(id: String)

    @Query("DELETE FROM call_records")
    suspend fun clearHistory()
}
