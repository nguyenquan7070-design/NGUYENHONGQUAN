package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members")
    fun getAllMembers(): Flow<List<Member>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: Member)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<Member>)

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteMember(id: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dateAssigned DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Int)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY timestamp ASC")
    fun getMessagesForChannel(channelId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY timestamp ASC")
    suspend fun getMessagesForChannelList(channelId: String): List<Message>

    @Query("SELECT * FROM messages WHERE isPinned = 1 ORDER BY timestamp DESC")
    fun getPinnedMessages(): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Int)

    @Query("UPDATE messages SET isRead = 1 WHERE channelId = :channelId")
    suspend fun markChannelAsRead(channelId: String)
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins ORDER BY timestamp DESC")
    fun getAllCheckIns(): Flow<List<CheckIn>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIn(checkIn: CheckIn)
}

@Dao
interface DutyScheduleDao {
    @Query("SELECT * FROM duty_schedules ORDER BY id DESC")
    fun getSchedulesFlow(): Flow<List<DutySchedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: DutySchedule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<DutySchedule>)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY timestamp DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Int)
}

@Database(
    entities = [
        Member::class,
        Task::class,
        Message::class,
        CheckIn::class,
        DutySchedule::class,
        Document::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun taskDao(): TaskDao
    abstract fun messageDao(): MessageDao
    abstract fun checkInDao(): CheckInDao
    abstract fun dutyScheduleDao(): DutyScheduleDao
    abstract fun documentDao(): DocumentDao
}
