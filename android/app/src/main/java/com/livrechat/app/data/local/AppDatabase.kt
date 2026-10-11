package com.livrechat.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.livrechat.app.data.local.dao.CallDao
import com.livrechat.app.data.local.dao.ContactDao
import com.livrechat.app.data.local.dao.ConversationDao
import com.livrechat.app.data.local.dao.MessageDao
import com.livrechat.app.data.local.dao.UserDao
import com.livrechat.app.data.local.entity.CallRecordEntity
import com.livrechat.app.data.local.entity.ContactEntity
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        CallRecordEntity::class,
        ContactEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun callDao(): CallDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "livrechat.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
