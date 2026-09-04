package me.proton.android.lumo.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.proton.android.lumo.data.db.entity.LumoEntity

@Dao
interface LumoDao {

    @Query("SELECT * FROM lumos WHERE archived = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LumoEntity>>

    @Query("SELECT * FROM lumos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LumoEntity?

    @Query("SELECT * FROM lumos WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<LumoEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lumo: LumoEntity): Long

    @Update
    suspend fun update(lumo: LumoEntity)

    @Delete
    suspend fun delete(lumo: LumoEntity)

    @Query("UPDATE lumos SET archived = 1, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: Long, now: Long = System.currentTimeMillis())
}
