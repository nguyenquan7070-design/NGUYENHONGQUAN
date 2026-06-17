package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class Repository(private val db: AppDatabase) {

    val members: Flow<List<Member>> = db.memberDao().getAllMembers()
    val tasks: Flow<List<Task>> = db.taskDao().getAllTasks()
    val checkIns: Flow<List<CheckIn>> = db.checkInDao().getAllCheckIns()
    val dutySchedules: Flow<List<DutySchedule>> = db.dutyScheduleDao().getSchedulesFlow()
    val documents: Flow<List<Document>> = db.documentDao().getAllDocuments()

    fun getMessages(channelId: String): Flow<List<Message>> = db.messageDao().getMessagesForChannel(channelId)
    suspend fun getMessagesList(channelId: String): List<Message> = db.messageDao().getMessagesForChannelList(channelId)
    val pinnedMessages: Flow<List<Message>> = db.messageDao().getPinnedMessages()

    suspend fun insertMember(member: Member) = db.memberDao().insertMember(member)
    suspend fun deleteMember(id: String) = db.memberDao().deleteMember(id)
    suspend fun insertTask(task: Task) = db.taskDao().insertTask(task)
    suspend fun updateTask(task: Task) = db.taskDao().updateTask(task)
    suspend fun deleteTask(id: Int) = db.taskDao().deleteTask(id)
    suspend fun insertMessage(message: Message) = db.messageDao().insertMessage(message)
    suspend fun deleteMessage(id: Int) = db.messageDao().deleteMessage(id)
    suspend fun insertCheckIn(checkIn: CheckIn) = db.checkInDao().insertCheckIn(checkIn)
    suspend fun insertSchedule(schedule: DutySchedule) = db.dutyScheduleDao().insertSchedule(schedule)
    suspend fun insertDocument(document: Document) = db.documentDao().insertDocument(document)
    suspend fun deleteDocument(id: Int) = db.documentDao().deleteDocument(id)
    suspend fun markChannelAsRead(channelId: String) = db.messageDao().markChannelAsRead(channelId)

    /**
     * Prepulate initial members, tasks, schedules, and chat history
     */
    suspend fun prepopulateIfEmpty() {
        val currentMembers = members.firstOrNull()
        if (currentMembers.isNullOrEmpty()) {
            val listMembers = listOf(
                // Leaders
                Member("nguyencongan", "Nguyễn Công An", "Đội trưởng", "Trung tá", "0912.345.678", "Chỉ huy phòng"),
                Member("lethanhdung", "Lê Thanh Dũng", "Đội trưởng", "Trung tá", "0987.654.321", "Ban Chỉ huy Đội"),
                Member("hoangngoctu", "Hoàng Ngọc Tú", "Đội phó", "Thiếu tá", "0905.112.233", "Ban Chỉ huy Đội"),
                Member("truongthithanhduyen", "Trương Thị Thanh Duyên", "Đội phó", "Thiếu tá", "0914.445.566", "Ban Chỉ huy Đội"),
                Member("nguyenthithanhkieu", "Nguyễn Thị Thanh Kiều", "Đội phó", "Thiếu tá", "0935.778.899", "Ban Chỉ huy Đội"),
                
                // Professional Staff
                Member("phamchihung", "Phạm Chí Hùng", "Cán bộ", "Đại úy", "0944.112.233", "Tổ Hậu cần - Tổng hợp"),
                Member("tranngochoanglong", "Trần Ngọc Hoàng Long", "Cán bộ", "Thượng úy", "0905.445.566", "Tổ Hậu cần - Tổng hợp"),
                Member("nguyenhongquan", "Nguyễn Hồng Quân", "Cán bộ", "Đại úy", "0912.889.900", "Tổ Hậu cần - Tổng hợp"),
                Member("nguyenngocthanh", "Nguyễn Ngọc Thanh", "Cán bộ", "Thượng úy", "0987.112.233", "Tổ Hậu cần - Tổng hợp"),
                Member("nguyenvanvi", "Nguyễn Văn Vĩ", "Cán bộ", "Trung úy", "0935.445.566", "Tổ Điện nước - Kỹ thuật"),
                Member("dinhthedam", "Đinh Thế Đảm", "Cán bộ", "Thiếu úy", "0944.778.899", "Tổ Điện nước - Kỹ thuật"),
                Member("doankhanhduc", "Đoàn Khánh Đức", "Cán bộ", "Đại úy", "0912.112.233", "Tổ Hậu cần - Tổng hợp"),
                Member("phamthianhtuyet", "Phạm Thị Ánh Tuyết", "Cán bộ", "Đại úy", "0987.445.566", "Tổ Hậu cần - Tổng hợp"),
                Member("trinhthithuy", "Trịnh Thị Thủy", "Cán bộ", "Đại úy", "0905.778.899", "Tổ Hậu cần - Tổng hợp"),
                
                // Labour/Support Staff
                Member("buiduyhoai", "Bùi Duy Hoài", "Lao động hợp đồng", "Nhân viên", "0912.111.222", "Tổ Phục vụ & Vệ sinh"),
                Member("tranminhkhoan", "Trần Minh Khoan", "Lao động hợp đồng", "Nhân viên", "0987.111.222", "Tổ Phục vụ & Vệ sinh"),
                Member("buithithuthao", "Bùi Thị Thu Thảo", "Lao động hợp đồng", "Nhân viên", "0905.111.222", "Tổ Phục vụ & Vệ sinh"),
                Member("lethitham", "Lê Thị Thắm", "Lao động hợp đồng", "Nhân viên", "0914.111.222", "Tổ Phục vụ & Vệ sinh"),
                Member("dangthithunga", "Đặng Thị Thu Nga", "Lao động hợp đồng", "Nhân viên", "0935.111.222", "Tổ Phục vụ & Vệ sinh"),
                Member("phamthixinhlinh", "Phạm Thị Xinh Linh", "Lao động hợp đồng", "Nhân viên", "0944.111.222", "Tổ Phục vụ & Vệ sinh"),
                
                Member("truongquangvuong", "Trương Quang Vương", "Lao động hợp đồng", "Nhân viên", "0912.222.333", "Tổ Điện nước - Kỹ thuật"),
                Member("nguyencanhtrung", "Nguyễn Cảnh Trung", "Lao động hợp đồng", "Nhân viên", "0987.222.333", "Tổ Điện nước - Kỹ thuật"),
                Member("nguyenhuutrung", "Nguyễn Hữu Trung", "Lao động hợp đồng", "Nhân viên", "0905.222.333", "Tổ Điện nước - Kỹ thuật"),
                Member("buiquyetthang", "Bùi Quyết Thắng", "Lao động hợp đồng", "Nhân viên", "0914.222.333", "Tổ Điện nước - Kỹ thuật"),
                Member("nguyencanhhieu", "Nguyễn Cảnh Hiểu", "Lao động hợp đồng", "Nhân viên", "0935.222.333", "Tổ Điện nước - Kỹ thuật"),
                
                Member("nguyenthihonghang", "Nguyễn Thị Hồng Hằng", "Lao động hợp đồng", "Nhân viên", "0944.222.333", "Tổ Phục vụ & Vệ sinh"),
                Member("tranthithuong", "Trần Thị Thương", "Lao động hợp đồng", "Nhân viên", "0912.333.444", "Tổ Phục vụ & Vệ sinh"),
                Member("dothingan", "Đỗ Thị Ngấn", "Lao động hợp đồng", "Nhân viên", "0987.333.444", "Tổ Phục vụ & Vệ sinh"),
                Member("nguyenthinhan", "Nguyễn Thị Nhàn", "Lao động hợp đồng", "Nhân viên", "0905.333.444", "Tổ Phục vụ & Vệ sinh"),
                Member("dothihoa", "Đỗ Thị Hòa", "Lao động hợp đồng", "Nhân viên", "0914.333.444", "Tổ Phục vụ & Vệ sinh"),
                Member("lamhoaiphuong", "Lâm Hoài Phương", "Lao động hợp đồng", "Nhân viên", "0935.333.444", "Tổ Phục vụ & Vệ sinh"),
                Member("hoangthiphuonghoan", "Hoàng Thị Phương Hoàn", "Lao động hợp đồng", "Nhân viên", "0944.333.444", "Tổ Phục vụ & Vệ sinh"),
                Member("lethihang", "Lê Thị Hằng", "Lao động hợp đồng", "Nhân viên", "0912.444.555", "Tổ Phục vụ & Vệ sinh"),
                Member("lethihoa", "Lê Thị Hoa", "Lao động hợp đồng", "Nhân viên", "0987.444.555", "Tổ Phục vụ & Vệ sinh"),
                Member("damhuyentram", "Đàm Huyền Trâm", "Lao động hợp đồng", "Nhân viên", "0905.444.555", "Tổ Phục vụ & Vệ sinh"),
                Member("luongthiloan", "Lương Thị Loan", "Lao động hợp đồng", "Nhân viên", "0914.444.555", "Tổ Phục vụ & Vệ sinh"),
                Member("lethihong", "Lê Thị Hồng", "Lao động hợp đồng", "Nhân viên", "0935.444.555", "Tổ Phục vụ & Vệ sinh"),
                Member("buithithanh", "Bùi Thị Thanh", "Lao động hợp đồng", "Nhân viên", "0944.444.555", "Tổ Phục vụ & Vệ sinh"),
                Member("hoangthithuy", "Hoàng Thị Thủy", "Lao động hợp đồng", "Nhân viên", "0912.555.666", "Tổ Phục vụ & Vệ sinh"),
                Member("tranthituyetvan", "Trần Thị Tuyết Vân", "Lao động hợp đồng", "Nhân viên", "0987.555.666", "Tổ Phục vụ & Vệ sinh"),
                Member("vothibichthuy", "Võ Thị Bích Thủy", "Lao động hợp đồng", "Nhân viên", "0905.555.666", "Tổ Phục vụ & Vệ sinh"),
                Member("phamthihanh", "Phạm Thị Hạnh", "Lao động hợp đồng", "Nhân viên", "0914.555.666", "Tổ Phục vụ & Vệ sinh"),
                Member("lethiyen", "Lê Thị Yến", "Lao động hợp đồng", "Nhân viên", "0935.555.666", "Tổ Phục vụ & Vệ sinh"),
                Member("nguyenthihop", "Nguyễn Thị Hợp", "Lao động hợp đồng", "Nhân viên", "0944.555.666", "Tổ Phục vụ & Vệ sinh"),
                Member("lethithuha", "Lê Thị Thu Hạ", "Lao động hợp đồng", "Nhân viên", "0912.666.777", "Tổ Phục vụ & Vệ sinh"),
                
                Member("dangngochung", "Đặng Ngọc Hưng", "Lao động hợp đồng", "Nhân viên", "0987.666.777", "Tổ Điện nước - Kỹ thuật"),
                Member("huynhvanlong", "Huỳnh Văn Long", "Lao động hợp đồng", "Nhân viên", "0905.666.777", "Tổ Điện nước - Kỹ thuật"),
                Member("lequocthang", "Lê Quốc Thắng", "Lao động hợp đồng", "Nhân viên", "0914.666.777", "Tổ Phục vụ & Vệ sinh"),
                Member("nguyenminhloc", "Nguyễn Minh Lộc", "Lao động hợp đồng", "Nhân viên", "0935.666.777", "Tổ Điện nước - Kỹ thuật"),
                Member("dangthitrucgiang", "Đặng Thị Trúc Giang", "Lao động hợp đồng", "Nhân viên", "0944.666.777", "Tổ Phục vụ & Vệ sinh"),
                Member("ngovuminhhoi", "Ngô Vũ Minh Hội", "Lao động hợp đồng", "Nhân viên", "0912.777.888", "Tổ Điện nước - Kỹ thuật"),
                Member("nguyentanhtan", "Nguyễn Thanh Tân", "Cán bộ", "Thiếu úy", "0987.777.888", "Tổ Điện nước - Kỹ thuật")
            )
            db.memberDao().insertMembers(listMembers)

            val listTasks = listOf(
                Task(
                    title = "Bảo dưỡng hệ thống điện nước cơ quan",
                    content = "Rà soát toàn bộ hệ thống chiếu sáng, bơm nước khu vực trụ sở Công an tỉnh, thay thế các thiết bị hỏng hóc trước đợt cao điểm mùa khô.",
                    assignedTo = "Nguyễn Văn Vĩ",
                    status = "Đang làm",
                    progress = 45,
                    score = 0
                ),
                Task(
                    title = "Soạn thảo báo cáo sơ kết công tác 6 tháng đầu năm",
                    content = "Tổng hợp số liệu từ các tổ kỹ thuật, quản lý xe và tổng hợp chi tiêu ngân sách hậu cần hành chính, hoàn thiện dự thảo trình lãnh đạo phòng ký duyệt.",
                    assignedTo = "Trịnh Thị Thủy",
                    status = "Đang làm",
                    progress = 80,
                    score = 0
                ),
                Task(
                    title = "Kiểm tra định kỳ đội xe ô tô chỉ huy và chở quân",
                    content = "Đưa xe đi kiểm định định kỳ, thay dầu bảo dưỡng, chạy thử trước khi có phương án huy động quân số tác chiến.",
                    assignedTo = "Đặng Ngọc Hưng",
                    status = "Đã hoàn thành",
                    progress = 100,
                    score = 95
                )
            )
            for (t in listTasks) {
                db.taskDao().insertTask(t)
            }

            val listSchedules = listOf(
                DutySchedule(
                    dateStr = "16/06/2026",
                    dutyLeader = "Trung tá Lê Thanh Dũng",
                    dutyOfficer = "Đại úy Nguyễn Hồng Quân",
                    patrolStaff = "Trung úy Nguyễn Văn Vĩ, NV Bùi Duy Hoài",
                    notes = "Trực bảo vệ an toàn tuyệt đối khu vực kho vật tư và phòng truyền thống."
                ),
                DutySchedule(
                    dateStr = "17/06/2026",
                    dutyLeader = "Thiếu tá Hoàng Ngọc Tú",
                    dutyOfficer = "Thượng úy Trần Ngọc Hoàng Long",
                    patrolStaff = "Thiếu úy Đinh Thế Đảm, NV Bùi Duy Hoài",
                    notes = "Tăng cường tuần tra đêm từ 22h00 đến 04h00 sáng hôm sau."
                )
            )
            db.dutyScheduleDao().insertSchedules(listSchedules)

            // Initial messages
            val listMessages = listOf(
                Message(
                    channelId = "toan_doi",
                    senderId = "lethanhdung",
                    senderName = "Lê Thanh Dũng",
                    senderPosition = "Đội trưởng",
                    content = "Chào toàn thể cán bộ chiến sĩ Đội HC-QT! Ứng dụng nghiệp vụ chính thức được nâng cấp lên phiên bản AI PRO thông minh cực kỳ mạnh mẽ.",
                    isPinned = true
                ),
                Message(
                    channelId = "toan_doi",
                    senderId = "hoangngoctu",
                    senderName = "Hoàng Ngọc Tú",
                    senderPosition = "Đội phó",
                    content = "Đề nghị đồng chí Quân rà soát lại tiến độ dự án mua sắm điều hòa văn phòng mới để kịp gửi lãnh đạo phê duyệt trong chiều nay."
                ),
                Message(
                    channelId = "toan_doi",
                    senderId = "nguyenhongquan",
                    senderName = "Nguyễn Hồng Quân",
                    senderPosition = "Cán bộ",
                    content = "Dạ báo cáo anh, em đã dùng AI soạn thảo Tờ trình xong rồi, đang chạy thử kiểm tra xem có đúng thể thức Nghị định 30 không ạ."
                ),
                Message(
                    channelId = "to_quan_tri",
                    senderId = "nguyenhongquan",
                    senderName = "Nguyễn Hồng Quân",
                    senderPosition = "Cán bộ",
                    content = "Nhóm Tổ quản trị họp rà soát danh sách tài sản phòng họp trực tuyến lúc 14h00 nhé các đồng chí."
                )
            )
            for (m in listMessages) {
                db.messageDao().insertMessage(m)
            }

            // Initial documents
            val listDocs = listOf(
                Document(
                    type = "Tờ trình",
                    title = "Tờ trình sắm sửa máy lọc nước văn phòng",
                    content = """
                        CÔNG AN TỈNH ĐẮK LẮK
                        PHÒNG HẬU CẦN
                        Số: 45 /TTr-HCQT
                        
                        Đắk Lắk, ngày 16 tháng 6 năm 2026
                        
                        TỜ TRÌNH
                        Về việc trang bị thêm máy lọc nước RO phục vụ tiếp dân tại sảnh Trung tâm Hành chính
                        
                        Kính gửi: Lãnh đạo Phòng Hậu cần Công an tỉnh Đắk Lắk.
                        
                        Căn cứ tình hình thời tiết Đắk Lắk nắng nóng gay gắt kéo dài và nhu cầu sử dụng nước uống sạch bảo đảm vệ sinh dịch tễ cho nhân dân khi đến liên hệ công tác tại sảnh chính cơ quan.
                        Đội Hậu cần - Quản trị kính trình Lãnh đạo phòng duyệt chủ trương mua sắm thêm 02 máy lọc nước RO nóng lạnh.
                        - Dự kiến kinh phí: 18.000.000 đồng (Mười tám triệu đồng chẵn).
                        - Nguồn vốn: Dự toán chi ngân sách thường xuyên phục vụ công tác năm 2026.
                        
                        Kính trình lãnh đạo Phòng xem xét, quyết định phê duyệt.
                        
                        NƠI NHẬN:
                        - Như kính gửi;
                        - Lưu: HC-QT.
                        
                        ĐỘI TRƯỞNG
                        Trung tá Mai Văn Ngọc
                    """.trimIndent(),
                    isAiGenerated = false
                )
            )
            for (d in listDocs) {
                db.documentDao().insertDocument(d)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        fun getInstance(context: Context): Repository {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "doi_hcqt_db_v3"
                ).fallbackToDestructiveMigration().build()
                val repo = Repository(db)
                INSTANCE = repo
                repo
            }
        }
    }
}
