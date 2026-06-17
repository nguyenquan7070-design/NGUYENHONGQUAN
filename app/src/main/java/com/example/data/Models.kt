package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "members")
data class Member(
    @PrimaryKey val id: String,
    val name: String,
    val position: String, // Đội trưởng, Đội phó, Cán bộ, Lao động hợp đồng
    val rank: String, // Đại úy, Thiếu tá, Thượng úy, etc.
    val phone: String,
    val unit: String, // Hậu cần, Quản trị, Tổ xe, Tổ điện nước...
    val password: String = "123456"
) : Serializable

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val assignedTo: String, // Member name, or comma-separated list of names
    val dateAssigned: Long = System.currentTimeMillis(),
    val status: String, // Đang làm, Đã hoàn thành, Quá hạn
    val progress: Int = 0, // 0 to 100
    val score: Int = 0, // Điểm thi đua chấm cho công việc
    val attachedImageUrl: String? = null, // Admin's attached image URL/Uri
    val dateCompleted: Long? = null, // Completion date/time
    val evaluation: String? = null, // Text evaluation of completion
    val ratingStars: Int = 0, // Rating (e.g. 1-5 stars)
    val dueDate: String? = null // Target completion date
) : Serializable

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val channelId: String, // "toan_doi", "to_quan_tri", "to_hau_can", or direct chat recipient "user_X"
    val senderId: String,
    val senderName: String,
    val senderPosition: String,
    val content: String,
    val attachmentPath: String? = null, // image or file
    val isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) : Serializable

@Entity(tableName = "check_ins")
data class CheckIn(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val staffName: String,
    val location: String,
    val latitude: Double,
    val longitude: Double,
    val imagePath: String? = null,
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "duty_schedules")
data class DutySchedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateStr: String, // dd/MM/yyyy
    val dutyLeader: String, // Đội trưởng/Đội phó trực chỉ huy
    val dutyOfficer: String, // Cán bộ trực chỉ huy ban ngày/đêm
    val patrolStaff: String, // Cán bộ tuần tra bảo vệ
    val notes: String = ""
) : Serializable

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // Công văn, Báo cáo, Kế hoạch, Quyết định, Biên bản, Thông báo, Tờ trình...
    val title: String,
    val content: String,
    val originalText: String? = null,
    val isAiGenerated: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
