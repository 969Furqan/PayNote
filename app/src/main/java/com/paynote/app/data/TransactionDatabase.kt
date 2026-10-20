package com.paynote.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.paynote.app.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis DESC")
    suspend fun getRecentTransactionsSince(sinceMillis: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE counterparty = 'Unknown Counterparty' OR (provider = 'SadaPay' AND counterparty = 'Money Received')")
    suspend fun getTransactionsWithIncompleteDetails(): List<TransactionEntity>

    @Query("UPDATE transactions SET counterparty = :name, direction = :newDirection WHERE id = :id AND counterparty = :oldCounterparty AND direction = :oldDirection")
    suspend fun repairTransactionDetails(id: Long, oldCounterparty: String, oldDirection: String, name: String, newDirection: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("UPDATE transactions SET purpose = :purpose, isLogged = 1 WHERE id = :id")
    suspend fun updatePurpose(id: Long, purpose: String)

    @Query("UPDATE transactions SET customProvider = :wallet, customCounterparty = :name, customDirection = :direction, category = :category, purpose = :purpose, isLogged = 1 WHERE id = :id")
    suspend fun updateUserDetails(id: Long, wallet: String, name: String, direction: String, category: String?, purpose: String?)

    @Query(
        "UPDATE transactions SET " +
        "transactionId = :newTid, counterparty = :newCounterparty, " +
        "sourcePackage = COALESCE(:preferredPackage, sourcePackage), " +
        "rawNotificationText = COALESCE(:preferredText, rawNotificationText), " +
        "provider = COALESCE(:preferredProvider, provider), " +
        "direction = COALESCE(:preferredDirection, direction) " +
        "WHERE id = :id"
    )
    suspend fun enrichDuplicateMetadata(id: Long, newTid: String?, newCounterparty: String,
                                       preferredPackage: String?, preferredText: String?,
                                       preferredProvider: String?, preferredDirection: String?)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [TransactionEntity::class], version = 2, exportSchema = false)
abstract class TransactionDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN customProvider TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN customCounterparty TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN customDirection TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT")
            }
        }
        @Volatile
        private var INSTANCE: TransactionDatabase? = null

        fun getInstance(context: Context): TransactionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransactionDatabase::class.java,
                    "paynote_transactions.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
