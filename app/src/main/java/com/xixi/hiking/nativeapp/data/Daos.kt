package com.xixi.hiking.nativeapp.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RecordEntity?

    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    suspend fun observeAllOnce(): List<RecordEntity>

    @Query("SELECT COUNT(*) FROM records")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RecordEntity>)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: String)

    /** ★批量删除（记录页「✓ 批量管理」用；照片由调用方一并清理） */
    @Query("DELETE FROM records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** ★抹掉所有足迹 */
    @Query("DELETE FROM records")
    suspend fun deleteAll()
}

@Dao
interface PlannedTripDao {
    @Query("SELECT * FROM planned_trips ORDER BY plannedDate ASC")
    fun observeAll(): Flow<List<PlannedTripEntity>>

    @Query("SELECT COUNT(*) FROM planned_trips")
    suspend fun count(): Int

    @Query("SELECT * FROM planned_trips ORDER BY plannedDate ASC")
    suspend fun observeAllOnce(): List<PlannedTripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PlannedTripEntity>)

    @Query("DELETE FROM planned_trips WHERE id = :id")
    suspend fun deleteById(id: String)

    /** ★批量删除（计划页「✓ 批量管理」用） */
    @Query("DELETE FROM planned_trips WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM planned_trips")
    suspend fun deleteAll()
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE recordId = :recordId ORDER BY createdAt ASC")
    fun observeByRecord(recordId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE recordId = :recordId ORDER BY createdAt ASC")
    suspend fun byRecord(recordId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos ORDER BY createdAt ASC")
    suspend fun allOnce(): List<PhotoEntity>

    /** ★山册要按山聚合全量照片（跨记录），订阅式拿全表 */
    @Query("SELECT * FROM photos ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM photos WHERE recordId = :recordId")
    suspend fun deleteByRecord(recordId: String)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()
}

/**
 * ★★数据库迁移（2026-11-02）—— 之前两处 builder 都写的是
 *   `fallbackToDestructiveMigration()`，意思是「版本对不上就把所有表删了重建」。
 *   对一个日记本来说这是灾难：以后只要加一个字段（比如这次给计划加海拔），
 *   用户所有记录、照片、计划全部清零，而且没有任何提示。
 *
 *   现在改成显式迁移，数据一条不丢。fallback 仍保留，但只在「用户装了更高版本
 *   又降级回来」这种极端情况兜底（那时确实没有迁移路径可写）。
 */
/**
 * ★★2026-11-05 补上 1 → 2（`tools/dbaudit.py` 抓到缺口）
 *
 *   v1 只有记录表和计划表；v2 加了照片表。当时只写了 2→3，**1→2 一直是缺的** ——
 *   意味着任何还停在 v1 的安装（早期装过原生版、后来没升级的机器）一升级到 v3，
 *   Room 会找不到 1→2 的路径，直接抛
 *   `IllegalStateException: Room cannot verify the data integrity`。
 *   自己的手机每次都装最新，所以从来没复现；但这是真正会让老用户打不开的坑。
 *
 *   建表语句必须和 Room 根据 [PhotoEntity] 生成的**逐字一致**（列名、类型、
 *   NOT NULL、主键），差一点就会在打开数据库时校验失败。实体没有声明索引，
 *   所以这里也不建索引 —— 多建一个同样会 mismatch。
 */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {

        // ★为什么不直接 `CREATE TABLE IF NOT EXISTS`：
        //   我们没法百分百确定 v1 到底是"没有 photos 表"还是"有但列更少"。
        //   如果是后者，IF NOT EXISTS 会悄悄跳过 ⇒ 表还在但缺列 ⇒
        //   Room 校验失败，报的却是一个完全无关的错误，极难排查。
        //   所以先查 sqlite_master 再查 PRAGMA，两种情况都处理：
        //     · 没这张表 → 按 Room 的建表语句建
        //     · 有这张表 → 只补缺的列（已有的列不动，数据保住）
        fun hasTable(): Boolean =
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='photos'")
                .use { it.moveToFirst() }

        fun columns(): Set<String> {
            val out = HashSet<String>()
            db.query("PRAGMA table_info(`photos`)").use { c ->
                val nameIdx = c.getColumnIndex("name")
                while (c.moveToNext()) out.add(c.getString(nameIdx))
            }
            return out
        }

        if (!hasTable()) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `photos` (" +
                    "`id` TEXT NOT NULL, " +
                    "`recordId` TEXT NOT NULL, " +
                    "`fileName` TEXT NOT NULL, " +
                    "`width` INTEGER NOT NULL, " +
                    "`height` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            return
        }

        val cols = columns()
        if ("width" !in cols) db.execSQL("ALTER TABLE `photos` ADD COLUMN `width` INTEGER NOT NULL DEFAULT 0")
        if ("height" !in cols) db.execSQL("ALTER TABLE `photos` ADD COLUMN `height` INTEGER NOT NULL DEFAULT 0")
        if ("createdAt" !in cols) db.execSQL("ALTER TABLE `photos` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
        if ("fileName" !in cols) db.execSQL("ALTER TABLE `photos` ADD COLUMN `fileName` TEXT NOT NULL DEFAULT ''")
        if ("recordId" !in cols) db.execSQL("ALTER TABLE `photos` ADD COLUMN `recordId` TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE planned_trips ADD COLUMN elevation INTEGER NOT NULL DEFAULT 0")
    }
}

/** 统一入口：所有建库的地方都必须走这里，别再各写一套 */
fun buildDatabase(context: android.content.Context): AppDatabase =
    androidx.room.Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "hiking.db")
        // ★2026-11-05 两条都要加：从 v1 直接升到 v3 时 Room 会按顺序跑 1→2、2→3
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        // ★★2026-11-03 去掉 fallbackToDestructiveMigration()（用户：「没用的话就不要了」）
        //
        //   那个 API 的实际作用是：**表结构一变就把整库删了重建** —— 对日记 App 来说
        //   等于静默清空所有记录，比报错危险得多；它本身也已被 Room 标记 deprecated。
        //   现在改成：只认显式迁移。以后再改表结构，必须在这里 addMigrations 里补一条，
        //   缺了会在启动时抛 IllegalStateException（被 CrashGuard 捕获并显示"上次崩溃"），
        //   至少数据还在、也能立刻发现。
        .build()

@Database(
    entities = [RecordEntity::class, PlannedTripEntity::class, PhotoEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun plannedTripDao(): PlannedTripDao
    abstract fun photoDao(): PhotoDao
}
