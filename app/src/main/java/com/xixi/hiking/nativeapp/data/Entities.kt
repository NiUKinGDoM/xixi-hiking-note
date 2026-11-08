package com.xixi.hiking.nativeapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ★记录实体 —— 字段与 Web 版 records[] 的 JSON 一一对应，方便以后直接导入备份。
 *   照片先整体存成 JSON 字符串（photosJson），骨架期够用；
 *   后续会拆成独立表 + 文件系统存储（Web 版是 IndexedDB）。
 */
@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey val id: String,
    val name: String,
    val distance: Double,
    val duration: Int,
    val elevation: Int,
    val difficulty: Int,
    val mood: String,
    val weather: String,
    val companions: String = "",   // 同行人（Web 版同名字段）
    val notes: String = "",        // 小日记（Web 版字段名是 notes，不是 note）
    val createdAt: String,
    val updatedAt: String,
    val photosJson: String = "[]"
)

/** 计划实体 —— 与 Web 版 plannedTrips[] 对齐 */
@Entity(tableName = "planned_trips")
data class PlannedTripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val plannedDate: String,
    val location: String,
    val note: String,
    val difficulty: Int,
    val createdAt: String,
    /** 目标海拔（Web 版有这个字段，之前漏了 —— 「完成」转记录时要带过去） */
    val elevation: Int = 0
)
