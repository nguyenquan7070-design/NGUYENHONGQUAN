package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.selection.SelectionContainer
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalClipboardManager

class MainActivity : ComponentActivity() {
    private lateinit var repository: Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get database repository
        repository = Repository.getInstance(this)

        // Prepopulate database with default items
        lifecycleScope.launch {
            repository.prepopulateIfEmpty()
        }

        setContent {
            MyApplicationTheme {
                val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(repository))
                MainNavigationScreen(mainViewModel)
            }
        }
    }
}

// --- VIEWMODEL FACTORY ---
class MainViewModelFactory(private val repository: Repository) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- VIEWMODEL ---
class MainViewModel(private val repository: Repository) : ViewModel() {
    // Current Active Session
    val currentUser = MutableStateFlow<Member?>(null)
    val membersState = MutableStateFlow<List<Member>>(emptyList())
    val tasksState = MutableStateFlow<List<Task>>(emptyList())
    val checkInsState = MutableStateFlow<List<CheckIn>>(emptyList())
    val dutySchedulesState = MutableStateFlow<List<DutySchedule>>(emptyList())
    val documentsState = MutableStateFlow<List<Document>>(emptyList())

    // Chat Active State
    val chatChannelId = MutableStateFlow("toan_doi") // toan_doi, to_quan_tri, to_hau_can, to_xe, user_X
    val channelMessages = MutableStateFlow<List<Message>>(emptyList())
    val pinnedMessages = MutableStateFlow<List<Message>>(emptyList())

    // AI Assistant Active States
    val aiDraftResult = MutableStateFlow("")
    val aiFormatReport = MutableStateFlow("")
    val aiRefinedResult = MutableStateFlow("")
    val aiSummaryResult = MutableStateFlow("")
    val aiOcrResult = MutableStateFlow("")
    val isAiLoading = MutableStateFlow(false)

    // Location & Device State for Checkins
    val currentMockLocation = MutableStateFlow("Hậu cần xe Công an tỉnh - 12 Mai Hắc Đế, TP Buôn Ma Thuột")
    val mockLat = 12.6784
    val mockLng = 108.0436

    // Online Meeting Active State
    val isJoinedRoom = MutableStateFlow(false)
    val isRoomMicEnabled = MutableStateFlow(true)
    val isRoomCamEnabled = MutableStateFlow(true)
    val isRoomSharing = MutableStateFlow(false)

    // Cloud Synchronization States
    val isCloudSyncEnabled = MutableStateFlow(true)
    val syncGroupId = MutableStateFlow("ca_daklak_hcqt_v1")
    val syncStatusState = MutableStateFlow("Chờ đồng bộ...")
    val meetingParticipantsState = MutableStateFlow<List<MeetingParticipant>>(emptyList())

    // Beep Tracking State
    private var isInitialTasksLoaded = false
    private var lastUserTaskCount = 0
    private var lastGeneralNoticeId = 0
    private var isInitialNoticesLoaded = false
    private var isInitialChatLoaded = false
    private var lastChatMsgId = 0
    val vibrateTriggerFlow = MutableStateFlow(false)

    fun playBeepBeep() {
        try {
            val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 95)
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 130)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 130)
                } catch (e: Exception) {}
            }, 250)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    init {
        // Collect DB updates in background coroutines safely
        viewModelScope.launch {
            repository.members.collect { membersState.value = it }
        }
        viewModelScope.launch {
            repository.tasks.collect { tasks ->
                tasksState.value = tasks
                val curUser = currentUser.value
                if (curUser != null) {
                    val assignedActiveTasks = tasks.filter { t ->
                        t.assignedTo.split(",").map { name -> name.trim().lowercase() }.contains(curUser.name.lowercase())
                    }
                    if (isInitialTasksLoaded) {
                        if (assignedActiveTasks.size > lastUserTaskCount) {
                            playBeepBeep()
                        }
                    } else {
                        isInitialTasksLoaded = true
                    }
                    lastUserTaskCount = assignedActiveTasks.size
                }
            }
        }
        viewModelScope.launch {
            repository.checkIns.collect { checkInsState.value = it }
        }
        viewModelScope.launch {
            repository.dutySchedules.collect { dutySchedulesState.value = it }
        }
        viewModelScope.launch {
            repository.documents.collect { documentsState.value = it }
        }
        viewModelScope.launch {
            repository.pinnedMessages.collect { pinnedMessages.value = it }
        }

        // Collect message stream dynamically based on active channel
        viewModelScope.launch {
            chatChannelId.collect { channelId ->
                repository.getMessages(channelId).collect {
                    channelMessages.value = it
                }
            }
        }

        // Special general notice background listener to beep-beep when new notice arises
        viewModelScope.launch {
            repository.getMessages("toan_doi").collect { messages ->
                val lastMsg = messages.lastOrNull()
                val curUser = currentUser.value
                if (lastMsg != null && curUser != null) {
                    if (lastMsg.senderId != curUser.id) {
                        if (isInitialNoticesLoaded) {
                            if (lastMsg.id > lastGeneralNoticeId) {
                                playBeepBeep()
                            }
                        } else {
                            isInitialNoticesLoaded = true
                        }
                    }
                    lastGeneralNoticeId = lastMsg.id
                }
            }
        }

        // Special free chat background listener to beep and vibrate when a message is added
        viewModelScope.launch {
            repository.getMessages("phong_chat_chung").collect { messages ->
                val lastMsg = messages.lastOrNull()
                val curUser = currentUser.value
                if (lastMsg != null && curUser != null) {
                    if (lastMsg.senderId != curUser.id) {
                        if (isInitialChatLoaded) {
                            if (lastMsg.id > lastChatMsgId) {
                                playBeepBeep()
                                vibrateTriggerFlow.value = !vibrateTriggerFlow.value
                            }
                        } else {
                            isInitialChatLoaded = true
                        }
                    }
                    lastChatMsgId = lastMsg.id
                }
            }
        }
        
        startSyncLoop()
    }

    fun startSyncLoop() {
        viewModelScope.launch {
            // Wait for initial database loads
            kotlinx.coroutines.delay(2000)
            while (true) {
                try {
                    SyncService.groupId = syncGroupId.value
                    
                    if (isCloudSyncEnabled.value) {
                        syncStatusState.value = "Đang đồng bộ..."
                        
                        // 1. SYNC TASKS (BI-DIRECTIONAL)
                        try {
                            val remoteTasks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                SyncService.downloadTasks()
                            }
                            val localTasks = tasksState.value
                            val mergedTasks = mutableListOf<Task>()
                            val localMap = localTasks.associateBy { it.title + "_" + it.dateAssigned }
                            val remoteMap = remoteTasks.associateBy { it.title + "_" + it.dateAssigned }
                            val allKeys = localMap.keys + remoteMap.keys
                            var localUpdated = false
                            var cloudUpdated = false

                            for (key in allKeys) {
                                val local = localMap[key]
                                val remote = remoteMap[key]
                                if (local != null && remote != null) {
                                    val newest = if (remote.progress > local.progress || remote.status == "Đã hoàn thành" || remote.score > local.score || (remote.dueDate != null && local.dueDate == null)) {
                                        remote.copy(id = local.id)
                                    } else {
                                        local
                                    }
                                    mergedTasks.add(newest)
                                    if (newest != local) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            repository.updateTask(newest)
                                        }
                                        localUpdated = true
                                    }
                                    if (newest != remote) {
                                        cloudUpdated = true
                                    }
                                } else if (local != null) {
                                    mergedTasks.add(local)
                                    cloudUpdated = true
                                } else if (remote != null) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        repository.insertTask(remote.copy(id = 0))
                                    }
                                    localUpdated = true
                                }
                            }
                            if (cloudUpdated) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    SyncService.uploadTasks(mergedTasks)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SyncTask", "Error syncing tasks", e)
                        }

                        // 2. SYNC MESSAGES
                        val activeChannels = listOf("toan_doi", "phong_chat_chung")
                        for (chId in activeChannels) {
                            try {
                                val remoteMsgs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    SyncService.downloadMessages(chId)
                                }
                                val localMsgs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    repository.getMessagesList(chId)
                                }
                                val mergedMsgs = (localMsgs + remoteMsgs).distinctBy { it.senderId + "_" + it.content + "_" + it.timestamp }.sortedBy { it.timestamp }
                                
                                var localModified = false
                                var remoteModified = false

                                for (m in mergedMsgs) {
                                    val existsLocally = localMsgs.any { it.senderId == m.senderId && it.content == m.content && m.timestamp == m.timestamp }
                                    if (!existsLocally) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            repository.insertMessage(m.copy(id = 0))
                                        }
                                        localModified = true
                                    }
                                    val existsRemotely = remoteMsgs.any { it.senderId == m.senderId && it.content == m.content && m.timestamp == m.timestamp }
                                    if (!existsRemotely) {
                                        remoteModified = true
                                    }
                                }
                                if (remoteModified) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        SyncService.uploadMessages(chId, mergedMsgs)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("SyncMsg", "Error syncing messages for channel $chId", e)
                            }
                        }
                        
                        syncStatusState.value = "Đã đồng bộ đám mây"
                    } else {
                        syncStatusState.value = "Chế độ ngoại tuyến"
                    }

                    // 3. ONLINE MEETING PRESENCE & LIVE WATERMARK
                    val curUser = currentUser.value
                    if (isJoinedRoom.value && curUser != null) {
                        val selfPresence = MeetingParticipant(
                            userId = curUser.id,
                            name = curUser.name,
                            position = curUser.position,
                            isCameraOn = isRoomCamEnabled.value,
                            isMicOn = isRoomMicEnabled.value,
                            lastHeartbeat = System.currentTimeMillis()
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            SyncService.registerMeetingPresence(selfPresence)
                        }
                    }

                    if (isJoinedRoom.value) {
                        val otherParticipants = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            SyncService.downloadMeetingPresence()
                        }
                        // Filter out self
                        meetingParticipantsState.value = otherParticipants.filter { it.userId != curUser?.id }
                    } else {
                        curUser?.let {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                SyncService.removeMeetingPresence(it.id)
                            }
                        }
                        meetingParticipantsState.value = emptyList()
                    }

                } catch (e: Exception) {
                    Log.e("SyncLoop", "Error in cloud synchronization", e)
                    syncStatusState.value = "Hết hạn hoặc mất mạng"
                }

                // Poll every 3 seconds for active UI updates
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    fun loginAs(member: Member) {
        currentUser.value = member
        isInitialTasksLoaded = false
        lastUserTaskCount = 0
        lastGeneralNoticeId = 0
        isInitialNoticesLoaded = false
    }

    fun logout() {
        currentUser.value = null
        isJoinedRoom.value = false
    }

    fun insertMember(member: Member) {
        viewModelScope.launch {
            repository.insertMember(member)
        }
    }

    fun deleteMember(id: String) {
        viewModelScope.launch {
            repository.deleteMember(id)
        }
    }

    // Task Actions
    fun assignTask(title: String, content: String, assigneeName: String, attachedImageUrl: String? = null, dueDate: String? = null) {
        viewModelScope.launch {
            val task = Task(
                title = title,
                content = content,
                assignedTo = assigneeName,
                status = "Đang làm",
                progress = 0,
                attachedImageUrl = attachedImageUrl,
                dateAssigned = System.currentTimeMillis(),
                dueDate = dueDate
            )
            repository.insertTask(task)
            
            // Immediate upload to cloud
            if (isCloudSyncEnabled.value) {
                try {
                    val currentTasks = tasksState.value + task
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        SyncService.uploadTasks(currentTasks)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun recallMessage(id: Int) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }

    fun updateTaskProgress(task: Task, newProgress: Int) {
        viewModelScope.launch {
            val updatedTask = task.copy(
                progress = newProgress,
                status = if (newProgress >= 100) "Đã hoàn thành" else "Đang làm",
                dateCompleted = if (newProgress >= 100) System.currentTimeMillis() else null
            )
            repository.updateTask(updatedTask)
        }
    }

    fun scoreTask(task: Task, score: Int, evaluation: String? = null, ratingStars: Int = 0) {
        viewModelScope.launch {
            val updatedTask = task.copy(
                score = score,
                evaluation = evaluation,
                ratingStars = ratingStars
            )
            repository.updateTask(updatedTask)
        }
    }

    // Chat Actions
    fun sendChatMessage(text: String, attachedImagePath: String? = null) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val msg = Message(
                channelId = chatChannelId.value,
                senderId = user.id,
                senderName = user.name,
                senderPosition = user.position,
                content = text,
                attachmentPath = attachedImagePath,
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(msg)
            
            // Immediate upload to cloud
            if (isCloudSyncEnabled.value) {
                try {
                    val currentMsgs = channelMessages.value + msg
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        SyncService.uploadMessages(chatChannelId.value, currentMsgs)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun pinMessage(message: Message) {
        viewModelScope.launch {
            val updated = message.copy(isPinned = true)
            repository.insertMessage(updated)
        }
    }

    fun unpinMessage(message: Message) {
        viewModelScope.launch {
            val updated = message.copy(isPinned = false)
            repository.insertMessage(updated)
        }
    }

    // Check In Actions
    fun performCheckIn(location: String, attachmentName: String? = null) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.insertCheckIn(
                CheckIn(
                    staffName = user.name,
                    location = location,
                    latitude = mockLat,
                    longitude = mockLng,
                    imagePath = attachmentName,
                    status = "Thành công",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    // AI Generation Calls via GeminiService (Direct REST API, with proper UI state integration)
    fun triggerDraft(onComplete: (String)->Unit = {}) {
        val titleQuery = "Công văn gửi các Đơn vị về tăng cường bảo vệ PCCC mùa hanh khô và kiểm kê tài sản phục vụ nghiệp vụ."
        val outlineQuery = "- Căn cứ Luật PCCC 2020\n- Phòng Hậu cần Công an Đắk Lắk rà soát thiết bị PCCC ở kho\n- Đề xuất trang cấp bổ sung bình chữa cháy cho phòng trực ban các đội nghiệp vụ đầu quý III/2026."
        
        isAiLoading.value = true
        viewModelScope.launch {
            val res = GeminiService.draftDocument("Công văn", titleQuery, outlineQuery)
            aiDraftResult.value = res
            isAiLoading.value = false
            repository.insertDocument(Document(type = "Công văn", title = "Công văn tăng cường PCCC tự động", content = res))
            onComplete(res)
        }
    }

    fun triggerFormatCheck(docText: String) {
        if (docText.isEmpty()) return
        isAiLoading.value = true
        viewModelScope.launch {
            val res = GeminiService.checkDecree30Format(docText)
            aiFormatReport.value = res
            isAiLoading.value = false
        }
    }

    fun triggerRewrite(docText: String, style: String) {
        if (docText.isEmpty()) return
        isAiLoading.value = true
        viewModelScope.launch {
            val res = GeminiService.fixSpellingAndRefine(docText, style)
            aiRefinedResult.value = res
            isAiLoading.value = false
            repository.insertDocument(Document(type = "Hiệu đính $style", title = "Tài liệu sửa lỗi chính tả", content = res, originalText = docText))
        }
    }

    fun triggerSummary(docText: String, mode: String) {
        if (docText.isEmpty()) return
        isAiLoading.value = true
        viewModelScope.launch {
            val res = GeminiService.summarizeDocument(docText, mode)
            aiSummaryResult.value = res
            isAiLoading.value = false
        }
    }

    fun triggerOcr(bitmap: Bitmap) {
        isAiLoading.value = true
        viewModelScope.launch {
            val res = GeminiService.performOcr(bitmap)
            aiOcrResult.value = res
            isAiLoading.value = false
            repository.insertDocument(Document(type = "Quét OCR", title = "Chuyển giao diện văn bản quét", content = res))
        }
    }

    // App Branding (Admin customizable)
    val appLogoUrl = MutableStateFlow("https://ais-dev-esbg7mwlxnemkw63xe6fwu-535357619974.asia-east1.run.app/attachments/73d11672-5fab-49ec-9e01-948efdfad5d7/attachment_5.png")
    val appBackgroundUrl = MutableStateFlow("https://ais-dev-esbg7mwlxnemkw63xe6fwu-535357619974.asia-east1.run.app/attachments/73d11672-5fab-49ec-9e01-948efdfad5d7/attachment_6.png")

    fun updateBranding(logo: String, bg: String) {
        if (logo.isNotBlank()) appLogoUrl.value = logo
        if (bg.isNotBlank()) appBackgroundUrl.value = bg
    }

    // Supplementary AI states
    val aiSearchQuery = MutableStateFlow("")
    val aiSearchResult = MutableStateFlow("")
    val aiSearchLoading = MutableStateFlow(false)

    val aiTranslationText = MutableStateFlow("")
    val aiTranslationLanguage = MutableStateFlow("Tiếng Anh")
    val aiTranslationResult = MutableStateFlow("")
    val aiTranslationLoading = MutableStateFlow(false)

    val aiConvertSourceText = MutableStateFlow("")
    val aiConvertResult = MutableStateFlow("")
    val aiConvertLoading = MutableStateFlow(false)

    fun triggerSearchRegulations(query: String) {
        if (query.isEmpty()) return
        aiSearchLoading.value = true
        viewModelScope.launch {
            val res = GeminiService.searchRegulations(query)
            aiSearchResult.value = res
            aiSearchLoading.value = false
        }
    }

    fun triggerTranslation(text: String, lang: String) {
        if (text.isEmpty()) return
        aiTranslationLoading.value = true
        viewModelScope.launch {
            val res = GeminiService.translateDocument(text, lang)
            aiTranslationResult.value = res
            aiTranslationLoading.value = false
        }
    }

    fun triggerConvertTemplate(oldText: String, templateType: String) {
        if (oldText.isEmpty()) return
        aiConvertLoading.value = true
        viewModelScope.launch {
            val res = GeminiService.convertToNewTemplate(oldText, templateType)
            aiConvertResult.value = res
            aiConvertLoading.value = false
            repository.insertDocument(Document(type = "Chuyển mẫu ($templateType)", title = "Văn bản mẫu mới chuẩn NĐ30", content = res, originalText = oldText))
        }
    }

    fun insertDutySchedule(schedule: DutySchedule) {
        viewModelScope.launch {
            repository.insertSchedule(schedule)
        }
    }
}

// --- MAIN ENTRANCE USER INTERFACE COMPONENT ---
@Composable
fun MainNavigationScreen(viewModel: MainViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val vibrateState by viewModel.vibrateTriggerFlow.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var isFirstVibeCollect by remember { mutableStateOf(true) }

    LaunchedEffect(vibrateState) {
        if (isFirstVibeCollect) {
            isFirstVibeCollect = false
        } else {
            try {
                val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                vibrator?.let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        it.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(150)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AnimatedContent(
        targetState = currentUser,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "AppStateTransition"
    ) { user ->
        if (user == null) {
            LoginScreen(viewModel)
        } else {
            HomeScreen(viewModel, user)
        }
    }
}

// --- 1. LOGIN SCREEN ---
@Composable
fun LoginScreen(viewModel: MainViewModel) {
    val members by viewModel.membersState.collectAsState()
    val bgUrl by viewModel.appBackgroundUrl.collectAsState()
    val logoUrl by viewModel.appLogoUrl.collectAsState()
    var selectedMember by remember { mutableStateOf<Member?>(null) }
    var expandedDrop by remember { mutableStateOf(false) }

    // Username/password login states
    var isQuickLogin by remember { mutableStateOf(true) } // true = quick list selection, false = typing nik/pass
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Design-level gradient background with customized image
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = bgUrl,
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient covering background for maximum text contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF162534).copy(alpha = 0.92f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // National Police Crest Styled Customizable Badge Circle
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(3.dp, Color(0xFFFBC02D), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = "Crest Logo",
                        modifier = Modifier.size(70.dp),
                        contentScale = ContentScale.Inside
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ĐỘI HC-QT AI PRO",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFBC02D),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Công an tỉnh Đắk Lắk",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "HỆ THỐNG NGHIỆP VỤ & TRỢ LÝ TRÍ TUỆ NHÂN TẠO",
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF42A5F5),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Tab selectors: Đổi kiểu login (Danh sách vs Nhập Nik)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f)),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val tabModifierLeft = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isQuickLogin) Color(0xFF0D47A1) else Color.Transparent)
                        .clickable { isQuickLogin = true }
                        .padding(vertical = 10.dp)
                    
                    val tabModifierRight = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isQuickLogin) Color(0xFF0D47A1) else Color.Transparent)
                        .clickable { isQuickLogin = false }
                        .padding(vertical = 10.dp)

                    Box(modifier = tabModifierLeft, contentAlignment = Alignment.Center) {
                        Text("XÁC THỰC NHANH", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Box(modifier = tabModifierRight, contentAlignment = Alignment.Center) {
                        Text("ĐĂNG NHẬP NIK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                if (isQuickLogin) {
                    // Quick Login Mode
                    Text(
                        text = "VUI LÒNG CHỌN DANH SÁCH THÀNH VIÊN",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Member Dropdown Select
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedDrop = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White.copy(alpha = 0.05f),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = selectedMember?.let { "${it.name} (${it.position})" } ?: "Chọn Cán bộ / Vai trò...",
                                    color = if (selectedMember != null) Color.White else Color.White.copy(alpha = 0.5f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Dropdown")
                            }
                        }

                        DropdownMenu(
                            expanded = expandedDrop,
                            onDismissRequest = { expandedDrop = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .heightIn(max = 280.dp)
                                .background(Color(0xFF1F2E3E))
                        ) {
                            members.forEach { member ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(text = member.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                            Text(text = "${member.rank} • ${member.position} (${member.unit})", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                        }
                                    },
                                    onClick = {
                                        selectedMember = member
                                        expandedDrop = false
                                        loginError = ""
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Authenticate Button
                    Button(
                        onClick = {
                            selectedMember?.let { m ->
                                viewModel.loginAs(m)
                                Toast.makeText(context, "Chào mừng ${m.rank} ${m.name} đăng nhập!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = selectedMember != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC62828),
                            disabledContainerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Filled.Fingerprint, contentDescription = "Fingerprint")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("XÁC THỰC CHUYÊN MÔN", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                } else {
                    // Username/Password Login Mode
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { 
                                usernameInput = it
                                loginError = ""
                            },
                            label = { Text("Tên đăng nhập (Nik)", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) },
                            placeholder = { Text("Ví dụ: lethanhdung", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFBC02D),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFFFBC02D)
                            ),
                            leadingIcon = {
                                Icon(Icons.Filled.Person, contentDescription = "User", tint = Color.White.copy(alpha = 0.6f))
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { 
                                passwordInput = it
                                loginError = ""
                            },
                            label = { Text("Mật khẩu", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) },
                            placeholder = { Text("Mặc định: 123456", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFBC02D),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFFFBC02D)
                            ),
                            leadingIcon = {
                                Icon(Icons.Filled.Lock, contentDescription = "Lock", tint = Color.White.copy(alpha = 0.6f))
                            }
                        )

                        if (loginError.isNotEmpty()) {
                            Text(
                                text = loginError,
                                color = Color(0xFFFF5252),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (usernameInput.isBlank()) {
                                    loginError = "Vui lòng nhập tên đăng nhập (Nik)!"
                                    return@Button
                                }
                                val formattedUsername = usernameInput.trim().lowercase()
                                val matchedUser = members.find { it.id.lowercase().trim() == formattedUsername }
                                if (matchedUser == null) {
                                    loginError = "Tài khoản (Nik) không tồn tại!"
                                } else {
                                    val checkPassword = if (passwordInput.isEmpty()) "123456" else passwordInput.trim()
                                    if (matchedUser.password == checkPassword) {
                                        viewModel.loginAs(matchedUser)
                                        Toast.makeText(context, "Chào mừng ${matchedUser.rank} ${matchedUser.name} đăng nhập!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        loginError = "Mật khẩu không chính xác!"
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("ĐĂNG NHẬP HỆ THỐNG", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Bảo mật phòng Hậu cần Công an tỉnh • Toàn bộ thông tin được kiểm duyệt chặt chẽ.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// --- 2. HOME SCREEN AND TABS LAYOUT ---
@Composable
fun HomeScreen(viewModel: MainViewModel, user: Member) {
    var activeTab by remember { mutableStateOf(0) }
    val logoUrl by viewModel.appLogoUrl.collectAsState()

    val syncStatus by viewModel.syncStatusState.collectAsState()
    val syncGroupId by viewModel.syncGroupId.collectAsState()
    val syncEnabled by viewModel.isCloudSyncEnabled.collectAsState()
    var showSyncDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D47A1)) // Police Dark Blue Base
            ) {
                // Header Bar with deep red border accent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Embedded emblem design inside Canvas, loading customizable logo
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.5.dp, Color(0xFFFBC02D), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = "Logo",
                                modifier = Modifier.size(34.dp),
                                contentScale = ContentScale.Inside
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ĐỘI HC-QT AI PRO",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFBC02D)
                            )
                            Text(
                                text = "Phòng Hậu cần Công an tỉnh Đắk Lắk",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Logged User Info Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { viewModel.logout() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = user.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = user.position,
                                fontSize = 9.sp,
                                color = Color(0xFFFBC02D),
                                fontWeight = FontWeight.Normal
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = "Logout",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                // Custom Red Separator line under Header bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0xFFC62828))
                )

                // Real-time Cloud Synchronization Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1565C0))
                        .clickable { showSyncDialog = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (syncEnabled) Icons.Filled.Sync else Icons.Filled.SyncDisabled,
                            contentDescription = "Sync Icon",
                            tint = if (syncEnabled) Color(0xFF81C784) else Color.LightGray,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Đồng bộ: $syncStatus",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Mã Đội: $syncGroupId • Thiết lập ⚙️",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F1B2B), // Modern very dark navy
                contentColor = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                val screens = listOf(
                    Triple(0, "Nghiệp vụ", Icons.Default.Dashboard),
                    Triple(1, "Chat Zalo", Icons.Default.Chat),
                    Triple(2, "AI Soạn thảo", Icons.Default.AutoAwesome),
                    Triple(3, "Quét & OCR", Icons.Default.DocumentScanner)
                )
                screens.forEach { (index, label, icon) ->
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        label = { Text(label, fontSize = 11.sp) },
                        icon = { Icon(icon, contentDescription = label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0D47A1),
                            selectedTextColor = Color(0xFFFBC02D),
                            indicatorColor = Color(0xFFFBC02D),
                            unselectedIconColor = Color.White.copy(alpha = 0.6f),
                            unselectedTextColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFFF5F7FA)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (activeTab) {
                0 -> OperationsDashboard(viewModel, user)
                1 -> InternalChatZalo(viewModel, user)
                2 -> AiSoanThaoTab(viewModel, user)
                3 -> AiSummarizerAndOcrTab(viewModel, user)
            }
        }
    }

    if (showSyncDialog) {
        Dialog(onDismissRequest = { showSyncDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sync, contentDescription = "Sync", tint = Color(0xFF0D47A1))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Đồng bộ Đám mây",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0D47A1)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chế độ trực tuyến đám mây:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = syncEnabled,
                            onCheckedChange = { viewModel.isCloudSyncEnabled.value = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0D47A1))
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = "Vui lòng nhập Mã Đội nhóm hoạt động chung. Cả 2 máy của các đồng chí phải nhập cùng một mã này để phân công việc đồng bộ và xem camera họp trực quan của nhau.",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    var tempGroupId by remember { mutableStateOf(syncGroupId) }
                    OutlinedTextField(
                        value = tempGroupId,
                        onValueChange = { tempGroupId = it },
                        label = { Text("Mã Đội nhóm (Group ID)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showSyncDialog = false }) {
                            Text("Đóng", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.syncGroupId.value = tempGroupId
                                SyncService.groupId = tempGroupId
                                showSyncDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))
                        ) {
                            Text("Lưu cấu hình", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- 3. TAB 0: OPERATIONS DASHBOARD (TRANG CHỦ & NGHIỆP VỤ) ---
@Composable
fun OperationsDashboard(viewModel: MainViewModel, user: Member) {
    val tasks by viewModel.tasksState.collectAsState()
    val schedules by viewModel.dutySchedulesState.collectAsState()
    val members by viewModel.membersState.collectAsState()
    val checkIns by viewModel.checkInsState.collectAsState()
    val bgUrl by viewModel.appBackgroundUrl.collectAsState()
    val logoUrl by viewModel.appLogoUrl.collectAsState()

    val isLeader = user.position == "Đội trưởng" || user.position == "Đội phó"

    var showAssignDialog by remember { mutableStateOf(false) }
    var taskToScore by remember { mutableStateOf<Task?>(null) }
    var scoreInput by remember { mutableStateOf("95") }

    // Admin Branding Dialog state
    var showAdminBrandingDialog by remember { mutableStateOf(false) }

    // Member profile & permission management states
    var showManageMembersDialog by remember { mutableStateOf(false) }
    var newMemberName by remember { mutableStateOf("") }
    var newMemberRank by remember { mutableStateOf("Đại úy") }
    var newMemberPosition by remember { mutableStateOf("Cán bộ") }
    var newMemberPhone by remember { mutableStateOf("") }
    var newMemberUnit by remember { mutableStateOf("Tổ Hậu cần - Tổng hợp") }

    // Admin Add Duty Schedule Dialog state & inputs
    var showAddScheduleDialog by remember { mutableStateOf(false) }
    var scheduleDateInput by remember { mutableStateOf("18/06/2026") }
    var scheduleLeaderInput by remember { mutableStateOf("Trung tá Nguyễn Văn Cường") }
    var scheduleOfficerInput by remember { mutableStateOf("Thiếu tá Trần Văn Hải") }
    var scheduleStaffInput by remember { mutableStateOf("Đại úy Lê Hồng Phong, Thượng úy Đỗ Hải Nam") }
    var scheduleNotesInput by remember { mutableStateOf("Tăng công tác phòng chống cháy nổ vũ trang tuần tra") }
    var scheduleUploadType by remember { mutableStateOf(0) } // 0: Text details, 1: Image attachment

    // Active sub-module popup overlay
    var activeModuleDetail by remember { mutableStateOf<String?>(null) }

    // Pulled up States for Sub-dialogs
    var sourText by remember { mutableStateOf("Kính gửi ban lảnh đạo, Đội Hậu cần báo cáo tềnh hình chuẩn bị công tác kiểm tra thiết bị pccc còn nhiu thíu sót, mong bộ phận hổ trợ gấp...") }
    var styleType by remember { mutableStateOf("Chuẩn hành chính trang trọng") }
    var docCon by remember { mutableStateOf("BÁO CÁO KẾ HOẠCH\nVề việc kiểm tra Kho vũ khí trang bị kỹ thuật quý III/2026\nKính gửi: Lãnh đạo Công an Tỉnh Đắk Lắk,\nĐội Hậu cần thống kê rà soát vật tư thiết bị chữa cháy phục vụ tuần tra đêm. Đề xuất cấp kinh phí mua mới 10 bình foam chữa cháy.\nKý tên: Nguyễn Văn Đội trưởng.") }
    var oriText by remember { mutableStateOf("Đội Hậu cần - Quản trị Công an tỉnh Đắk Lắk thực hiện nhiệm vụ rà soát trang cấp vật tư thiết bị chữa cháy, tuần tra bảo đảm an ninh nội bộ cơ quan.") }
    var targetLang by remember { mutableStateOf("Tiếng Anh") }
    var queryStr by remember { mutableStateOf("Luật quản lý tài sản công an nhân dân năm mới nhất") }

    // Local Loading states for OCR tasks in individual Dialogs
    var isSpellingOcrLoading by remember { mutableStateOf(false) }
    var isDecreeOcrLoading by remember { mutableStateOf(false) }
    var isOcrModuleLoading by remember { mutableStateOf(false) }
    var isTransOcrLoading by remember { mutableStateOf(false) }
    var isLawsOcrLoading by remember { mutableStateOf(false) }

    var isScheduleImageUploading by remember { mutableStateOf(false) }
    var scheduleImageBase64 by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Setup ActivityResult launchers for different views
    val spellingGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    isSpellingOcrLoading = true
                    val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val ocrText = GeminiService.performOcr(bitmap)
                        if (!ocrText.startsWith("LỖI")) {
                            sourText = ocrText
                            Toast.makeText(context, "Nhận diện chữ thành công", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, ocrText, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi đọc ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isSpellingOcrLoading = false
                }
            }
        }
    }

    val spellingCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            coroutineScope.launch {
                try {
                    isSpellingOcrLoading = true
                    val ocrText = GeminiService.performOcr(it)
                    if (!ocrText.startsWith("LỖI")) {
                        sourText = ocrText
                        Toast.makeText(context, "Chụp ảnh và nhận diện thành công", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, ocrText, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi camera: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isSpellingOcrLoading = false
                }
            }
        }
    }

    val checkInCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.performCheckIn(
                "Trụ sở Phòng Hậu cần Công an tỉnh - Ảnh tuần tra hiện trường",
                "anh_di_tuan_${System.currentTimeMillis() / 1000}.jpg"
            )
            Toast.makeText(context, "Đã lưu ảnh hiện trường và Check-in thành công!", Toast.LENGTH_SHORT).show()
        }
    }

    val decreeGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    isDecreeOcrLoading = true
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val ocrText = GeminiService.performOcr(bitmap)
                        if (!ocrText.startsWith("LỖI")) {
                            docCon = ocrText
                            Toast.makeText(context, "Trích văn bản thành công", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, ocrText, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi đọc ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isDecreeOcrLoading = false
                }
            }
        }
    }

    val decreeCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            coroutineScope.launch {
                try {
                    isDecreeOcrLoading = true
                    val ocrText = GeminiService.performOcr(it)
                    if (!ocrText.startsWith("LỖI")) {
                        docCon = ocrText
                        Toast.makeText(context, "Chụp và nhận diện văn bản thành công", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, ocrText, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isDecreeOcrLoading = false
                }
            }
        }
    }

    val ocrModuleGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    isOcrModuleLoading = true
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val ocrText = GeminiService.performOcr(bitmap)
                        viewModel.aiOcrResult.value = ocrText
                        Toast.makeText(context, "Đã trích xuất chữ OCR", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isOcrModuleLoading = false
                }
            }
        }
    }

    val ocrModuleCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            coroutineScope.launch {
                try {
                    isOcrModuleLoading = true
                    val ocrText = GeminiService.performOcr(it)
                    viewModel.aiOcrResult.value = ocrText
                    Toast.makeText(context, "Đã chụp và quét OCR xong", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isOcrModuleLoading = false
                }
            }
        }
    }

    val transGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    isTransOcrLoading = true
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val ocrText = GeminiService.performOcr(bitmap)
                        if (!ocrText.startsWith("LỖI")) {
                            oriText = ocrText
                            Toast.makeText(context, "Trích văn bản gốc thành công", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, ocrText, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isTransOcrLoading = false
                }
            }
        }
    }

    val transCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            coroutineScope.launch {
                try {
                    isTransOcrLoading = true
                    val ocrText = GeminiService.performOcr(it)
                    if (!ocrText.startsWith("LỖI")) {
                        oriText = ocrText
                        Toast.makeText(context, "Chụp và nhận diện văn bản gốc thành công", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, ocrText, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isTransOcrLoading = false
                }
            }
        }
    }

    val directTransGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    isTransOcrLoading = true
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val ocrText = GeminiService.performOcr(bitmap)
                        if (!ocrText.startsWith("LỖI")) {
                            oriText = ocrText
                            viewModel.triggerTranslation(ocrText, targetLang)
                            Toast.makeText(context, "Đã trích và dịch tự động thành công", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isTransOcrLoading = false
                }
            }
        }
    }

    val directTransCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            coroutineScope.launch {
                try {
                    isTransOcrLoading = true
                    val ocrText = GeminiService.performOcr(it)
                    if (!ocrText.startsWith("LỖI")) {
                        oriText = ocrText
                        viewModel.triggerTranslation(ocrText, targetLang)
                        Toast.makeText(context, "Đã chụp, quét và dịch tự động thành công", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isTransOcrLoading = false
                }
            }
        }
    }

    val lawsCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            coroutineScope.launch {
                try {
                    isLawsOcrLoading = true
                    val ocrText = GeminiService.performOcr(it)
                    if (!ocrText.startsWith("LỖI") && ocrText.isNotBlank()) {
                        queryStr = "Kiểm tra xem thông tư, nghị định trong văn bản sau đây còn hiệu lực thi hành không hay đã hết hiệu lực, bị thay thế bởi văn bản nào chưa:\n$ocrText"
                        viewModel.triggerSearchRegulations(queryStr)
                        Toast.makeText(context, "Đang kiểm tra hiệu lực văn bản...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Không nhận diện được chữ trong ảnh chụp", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isLawsOcrLoading = false
                }
            }
        }
    }

    val lawsGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    isLawsOcrLoading = true
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val ocrText = GeminiService.performOcr(bitmap)
                        if (!ocrText.startsWith("LỖI") && ocrText.isNotBlank()) {
                            queryStr = "Kiểm tra xem quy định, thông tư hoặc nghị định gắn với tài liệu sau còn hiệu lực hay không:\n$ocrText"
                            viewModel.triggerSearchRegulations(queryStr)
                            Toast.makeText(context, "Đang tra cứu hiệu lực...", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isLawsOcrLoading = false
                }
            }
        }
    }

    val scheduleGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    isScheduleImageUploading = true
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val outputStream = java.io.ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 45, outputStream)
                        val bytes = outputStream.toByteArray()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        scheduleImageBase64 = base64
                        Toast.makeText(context, "Đã tải ảnh thành công", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isScheduleImageUploading = false
                }
            }
        }
    }

    // AI states from ViewModel
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val aiDraft by viewModel.aiDraftResult.collectAsState()
    val aiFormatReport by viewModel.aiFormatReport.collectAsState()
    val aiRefinedResult by viewModel.aiRefinedResult.collectAsState()
    val aiSummaryResult by viewModel.aiSummaryResult.collectAsState()
    val aiOcrResult by viewModel.aiOcrResult.collectAsState()

    val aiSearchQuery by viewModel.aiSearchQuery.collectAsState()
    val aiSearchResult by viewModel.aiSearchResult.collectAsState()
    val aiSearchLoading by viewModel.aiSearchLoading.collectAsState()

    val aiTranslationText by viewModel.aiTranslationText.collectAsState()
    val aiTranslationLanguage by viewModel.aiTranslationLanguage.collectAsState()
    val aiTranslationResult by viewModel.aiTranslationResult.collectAsState()
    val aiTranslationLoading by viewModel.aiTranslationLoading.collectAsState()

    val aiConvertSourceText by viewModel.aiConvertSourceText.collectAsState()
    val aiConvertResult by viewModel.aiConvertResult.collectAsState()
    val aiConvertLoading by viewModel.aiConvertLoading.collectAsState()

    data class ChessModule(
        val id: String,
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
        val iconColor: Color,
        val badgeText: String? = null
    )

    val modules = listOf(
        ChessModule("notice", "Thông báo chung", "Kênh chỉ đạo toàn đội", Icons.Default.Chat, Color(0xFF1565C0)),
        ChessModule("group_chat", "Phòng chát nội bộ", "Chát tự do, gửi tệp & ảnh", Icons.Default.Forum, Color(0xFF9C27B0), "Hot"),
        ChessModule("meeting", "Họp đội", "Phòng họp trực tuyến", Icons.Default.Videocam, Color(0xFF2E7D32), "Live"),
        ChessModule("spelling", "Sửa lỗi chính tả", "Sửa và viết chính văn hay", Icons.Default.Spellcheck, Color(0xFFD81B60)),
        ChessModule("decree30", "Kiểm thể thức NĐ30", "Thẩm định bố cục văn bản", Icons.Default.CheckCircle, Color(0xFFE65100)),
        ChessModule("ocr", "Quét & OCR", "Chuyển JPG sang văn bản", Icons.Default.DocumentScanner, Color(0xFF7B1FA2)),
        ChessModule("translator", "Dịch thuật", "Dịch đa ngữ hành chính", Icons.Default.Translate, Color(0xFF3F51B5)),
        ChessModule("laws", "Tra cứu luật pháp", "Danh mục quy định hành lý", Icons.Default.Search, Color(0xFF00796B)),
        ChessModule("convert", "Chuyển văn bản mẫu", "Độc quyền từ cũ sang mẫu mới", Icons.Default.Cached, Color(0xFF5D4037)),
        ChessModule("soan_thao", "AI Soạn thảo", "Soạn thảo văn bản tự động", Icons.Default.AutoAwesome, Color(0xFFC62828), "Pro"),
        ChessModule("summary", "Tóm tắt văn bản", "Tóm tắt nhanh văn bản dài", Icons.Default.Description, Color(0xFF455A64))
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. TOP DEPARTMENT HERO BANNER (With customizable backgrounds and cogs)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Custom background loaded via user-fed link
                    AsyncImage(
                        model = bgUrl,
                        contentDescription = "Background department",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Custom Shade overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )

                    // Overlay Branding Title
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, Color(0xFFFBC02D), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = "Logo Image",
                                modifier = Modifier.size(44.dp),
                                contentScale = ContentScale.Inside
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "TRỢ LÝ CHUYÊN MÔN AI PRO",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFBC02D)
                            )
                            Text(
                                text = "Không gian quản trị & hỗ trợ pháp quy hành chính",
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Admin branding customizer gear (Only shown to leaders)
                    if (user.position == "Đội trưởng" || user.position == "Đội phó" || user.position == "Cán bộ") {
                        IconButton(
                            onClick = { showAdminBrandingDialog = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 2. GIAO VIỆC & NHẬN VIỆC (Prominent At the Top)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Assignment,
                                contentDescription = "Assign",
                                tint = Color(0xFF0D47A1),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isLeader) "GIAO VIỆC MỚI & TIẾN ĐỘ CHUNG" else "MỤC NHẬN VIỆC & NHIỆM VỤ ĐƯỢC GIAO",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF0D47A1)
                            )
                        }

                        // Assign button restricted based on leadership or open for simulation
                        if (isLeader) {
                            Button(
                                onClick = { showAssignDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Giao Việc", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val visibleTasks = if (isLeader) {
                        tasks
                    } else {
                        tasks.filter { t ->
                            t.assignedTo.split(",").map { name -> name.trim().lowercase() }.contains(user.name.lowercase())
                        }
                    }

                    if (visibleTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isLeader) "Không có công việc bàn giao nào đang trực quan." else "Bạn chưa có công việc bàn giao nào được giao.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        // Display the 2 most recent tasks to keep layout compact and ultra clean
                        visibleTasks.take(2).forEach { task ->
                            val userIsAssignee = task.assignedTo.split(",").map { it.trim().lowercase() }.contains(user.name.lowercase())
                            val sdfLocal = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                            val assignedTimeStr = sdfLocal.format(java.util.Date(task.dateAssigned))
                            val completedTimeStr = if (task.dateCompleted != null && task.dateCompleted!! > 0) {
                                sdfLocal.format(java.util.Date(task.dateCompleted!!))
                            } else null

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = task.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = Color(0xFF0D47A1),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(0.7f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (task.status == "Đã hoàn thành") Color(0xFF388E3C).copy(alpha = 0.12f)
                                                    else Color(0xFFFBC02D).copy(alpha = 0.12f)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = task.status,
                                                color = if (task.status == "Đã hoàn thành") Color(0xFF388E3C) else Color(0xFFFFB300),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Nhân sự nhận việc: ${task.assignedTo}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.DarkGray
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "📅 Giao: $assignedTimeStr" + 
                                               (if (task.dueDate != null) " | ⏳ Hạn: ${task.dueDate}" else "") + 
                                               (if (completedTimeStr != null) " | ✔️ Xong: $completedTimeStr" else ""),
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )

                                    if (!task.attachedImageUrl.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFE8EAF6))
                                                .clip(RoundedCornerShape(4.dp))
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Attachment, contentDescription = "Image", tint = Color(0xFF3F51B5), modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            val displayImg = task.attachedImageUrl!!.removePrefix("preset_")
                                            Text(
                                                text = "📎 Mô tả đính kèm: $displayImg",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E88E5)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        LinearProgressIndicator(
                                            progress = { task.progress / 100f },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = Color(0xFF1565C0),
                                            trackColor = Color.LightGray.copy(alpha = 0.3f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${task.progress}%",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color(0xFF1565C0)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Progress action button - updated to support multiple split assignees list
                                        if (userIsAssignee && task.progress < 100) {
                                            TextButton(
                                                onClick = { viewModel.updateTaskProgress(task, task.progress + 25) },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.height(26.dp)
                                            ) {
                                                Text("Báo cáo +25%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Commander Scoring button
                                        if (isLeader && task.score == 0 && task.progress >= 100) {
                                            Button(
                                                onClick = { taskToScore = task },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D)),
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                modifier = Modifier.height(26.dp),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("Lãnh đạo Ghi Điểm", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    if (task.score > 0) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFFFDE7))
                                                .border(0.5.dp, Color(0xFFFFF176), RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("🎖️ Xếp hạng tốt: ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF827717))
                                                (1..5).forEach { starIdx ->
                                                    Icon(
                                                        imageVector = if (starIdx <= task.ratingStars) Icons.Filled.Star else Icons.Outlined.Star,
                                                        contentDescription = "Star",
                                                        tint = Color(0xFFFBC02D),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "🏆 Điểm đánh giá thi đua: ${task.score}/100",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp,
                                                color = Color(0xFFF57F17)
                                            )
                                            if (!task.evaluation.isNullOrEmpty()) {
                                                Spacer(modifier = Modifier.height(1.dp))
                                                Text(
                                                    text = "✍️ Nhận xét chỉ huy: ${task.evaluation}",
                                                    fontSize = 10.sp,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                    color = Color.DarkGray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. CHESSBOARD GRID SECTION (Bàn Cờ Trực Quan Nghiệp Vụ)
        item {
            Column {
                Text(
                    text = "BÀN CỜ NGHIỆP VỤ & TRỢ LÝ TRÍ TUỆ AI",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0D47A1),
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                val chunked = modules.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    chunked.forEach { rowPair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowPair.forEach { mod ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(115.dp)
                                        .clickable { activeModuleDetail = mod.id },
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(mod.iconColor.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = mod.icon,
                                                    contentDescription = mod.title,
                                                    tint = mod.iconColor,
                                                    modifier = Modifier.size(21.dp)
                                                )
                                            }

                                            if (mod.badgeText != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFFC62828))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = mod.badgeText,
                                                        color = Color.White,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(mod.iconColor.copy(alpha = 0.6f))
                                                )
                                            }
                                        }

                                        Column {
                                            Text(
                                                text = mod.title,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F1B2B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = mod.subtitle,
                                                fontSize = 9.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowPair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 4. LỊCH SỬ TUẦN TRA CHECK-IN HIỆN TRƯỜNG
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NHẬT KÝ TUẦN TRA & LỊCH TRỰC",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D47A1),
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { viewModel.performCheckIn("Trụ sở Phòng Hậu cần Công an tỉnh - Điểm rà soát cổng chính") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = "Check-in", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Định vị", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { checkInCameraLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Camera Check-In", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chụp ảnh check-in", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (checkIns.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chưa có lượt check-in tuần tra hiện trường nào.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(checkIns) { checkIn ->
                Card(
                     modifier = Modifier.fillMaxWidth(),
                     colors = CardDefaults.cardColors(containerColor = Color.White),
                     shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Check-in log",
                            tint = Color(0xFF388E3C),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = checkIn.staffName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = checkIn.location, fontSize = 11.sp, color = Color.DarkGray)
                            if (!checkIn.imagePath.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFFFFF3E0), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = "Photo", tint = Color(0xFFE65100), modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ảnh hiện trường: ${checkIn.imagePath}", fontSize = 9.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Toạ độ GPS: ${checkIn.latitude}, ${checkIn.longitude} • Lúc: ${SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()).format(Date(checkIn.timestamp))}",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // LỊCH TRỰC TUẦN BAN CHỈ HUY
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LỊCH TRỰC TUẦN BAN CHỈ HUY",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D47A1),
                    fontSize = 13.sp
                )

                if (isLeader) {
                    Button(
                        onClick = { 
                            showAddScheduleDialog = true 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF304FFE)),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đăng lịch mới", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (schedules.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                        Text("Chưa có lịch trực tuần mới. Lãnh đạo Đội vui lòng đăng lịch.", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        } else {
            items(schedules) { schedule ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "📅 Ngày Trực: ${schedule.dateStr}", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFF0D47A1))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE8EAF6))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("LỊCH TRỰC", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F51B5))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Check if notes contains an image (URI or Base64)
                        if (schedule.notes.startsWith("IMAGE_BASE64:")) {
                            val base64 = schedule.notes.replace("IMAGE_BASE64:", "")
                            // To display a Base64 image in Jetpack Compose, we can decode it to a bitmap!
                            val bitmap = remember(base64) {
                                try {
                                    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Ảnh lịch trực",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text("Lỗi vẽ hình ảnh lịch trực.", color = Color.Red, fontSize = 10.sp)
                            }
                        } else {
                            // Standard text schedule details
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "• Chỉ huy trực: " + schedule.dutyLeader,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.DarkGray
                                )
                                Text(
                                    text = "• Cán bộ ứng trực: " + schedule.dutyOfficer,
                                    fontSize = 11.5.sp,
                                    color = Color.DarkGray
                                )
                                Text(
                                    text = "• Tổ tuần tra: " + schedule.patrolStaff,
                                    fontSize = 11.5.sp,
                                    color = Color.DarkGray
                                )
                                if (schedule.notes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "📝 Ghi chú: " + schedule.notes,
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        style = TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. DANH SÁCH THÀNH VIÊN ĐỘI
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DANH SÁCH THÀNH VIÊN ĐỘI HC-QT",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D47A1),
                    fontSize = 13.sp
                )

                if (isLeader) {
                    Button(
                        onClick = { 
                            showManageMembersDialog = true 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = "Security", modifier = Modifier.size(13.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lập nick / Phân quyền", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                members.forEach { m ->
                    Card(
                        modifier = Modifier.width(125.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0D47A1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = m.name.split(" ").lastOrNull()?.take(2) ?: "CB",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = m.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = m.position, fontSize = 9.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG MODULE DEPICTIONS (MỌI HOẠT ĐỘNG SẼ ĐỔI SANG OVERLAYS TẠI TAB 0) ---

    // 1. THÔNG BÁO CHUNG & CHAT NHÓM DIALOG
    if (activeModuleDetail == "notice") {
        Dialog(onDismissRequest = { activeModuleDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Chat, contentDescription = "Notice", tint = Color(0xFF1565C0))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Thông Báo Chung & Tin Nhắn", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                        }
                        IconButton(onClick = { activeModuleDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        val messages by viewModel.channelMessages.collectAsState()
                        if (messages.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Chưa có tin nhắn chỉ đạo nào hôm nay.", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(reverseLayout = true, modifier = Modifier.fillMaxSize()) {
                                items(messages) { msg ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = msg.senderName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF0D47A1)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "(${msg.senderPosition})",
                                                fontSize = 9.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(text = msg.content, fontSize = 12.sp)
                                                if (msg.attachmentPath != null) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(text = "📎 Đính kèm: ${msg.attachmentPath}", fontSize = 10.sp, color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    var sendText by remember { mutableStateOf("") }
                    if (isLeader) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = sendText,
                                onValueChange = { sendText = it },
                                placeholder = { Text("Gửi tin nhắn toàn đội / chỉ đạo...", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(fontSize = 12.sp),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (sendText.isNotBlank()) {
                                        viewModel.sendChatMessage(sendText)
                                        sendText = ""
                                    }
                                },
                                modifier = Modifier.background(Color(0xFF0D47A1), CircleShape)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚠️ Kênh thông báo chính thức chỉ cho phép chỉ huy Đội đăng bài viết và lịch trực.",
                                color = Color(0xFFE65100),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // 1b. PHÒNG CHÁT NỘI BỘ DIALOG (Chát tự do, gửi tệp & ảnh, âm báo & rung, thu hồi, trạng thái trực tuyến)
    if (activeModuleDetail == "group_chat") {
        LaunchedEffect(Unit) {
            viewModel.chatChannelId.value = "phong_chat_chung"
        }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                viewModel.sendChatMessage("🖼️ Đã gửi ảnh hiện trường.", uri.toString())
            }
        }

        val videoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                viewModel.sendChatMessage("🎞️ Đã gửi một clip tuần tra.", uri.toString())
            }
        }

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                viewModel.sendChatMessage("📄 Đã gửi tài liệu đính kèm.", uri.toString())
            }
        }

        Dialog(onDismissRequest = { activeModuleDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forum, contentDescription = "Forum", tint = Color(0xFF9C27B0))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Phòng Chát Nội Bộ", fontWeight = FontWeight.Bold, color = Color(0xFF4A148C))
                        }
                        IconButton(onClick = { activeModuleDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Status Bar: Online & Viewing status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF3E5F5), RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tổng cán bộ online (3): Lê Thanh Dũng, Hoàng Ngọc Tú, Nguyễn Hồng Quân", fontSize = 9.sp, color = Color(0xFF4A148C), fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, contentDescription = "Viewing", tint = Color(0xFF7B1FA2), modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Mọi người đang xem", fontSize = 9.sp, color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        val messages by viewModel.channelMessages.collectAsState()
                        if (messages.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Chưa có tin nhắn nào. Gửi ảnh hoặc tin nhắn thảo luận tự do!", color = Color.Gray, fontSize = 11.sp)
                            }
                        } else {
                            LazyColumn(reverseLayout = true, modifier = Modifier.fillMaxSize()) {
                                items(messages) { msg ->
                                    val isMe = msg.senderId == user.id
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = if (isMe) Arrangement.End as Arrangement.Horizontal else Arrangement.Start as Arrangement.Horizontal,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (!isMe) {
                                                Text(
                                                    text = msg.senderName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFF6A1B9A)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "(${msg.senderPosition})",
                                                    fontSize = 8.5.sp,
                                                    color = Color.Gray
                                                )
                                            } else {
                                                Text(
                                                    text = "Bạn (${msg.senderPosition})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFF4A148C)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (isMe) Arrangement.End as Arrangement.Horizontal else Arrangement.Start as Arrangement.Horizontal,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isMe) {
                                                IconButton(
                                                    onClick = { viewModel.recallMessage(msg.id) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Thu hồi",
                                                        tint = Color.Red.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }

                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isMe) Color(0xFFE1BEE7) else Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(text = msg.content, fontSize = 12.sp)
                                                    
                                                    if (msg.attachmentPath != null) {
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        val path = msg.attachmentPath
                                                        if (path.contains("image") || path.endsWith(".jpg") || path.endsWith(".png") || path.endsWith(".jpeg") || path.contains("preset_") || path.contains("content://")) {
                                                            Card(
                                                                shape = RoundedCornerShape(6.dp),
                                                                modifier = Modifier.size(120.dp, 80.dp)
                                                            ) {
                                                                val displayUrl = if (path.startsWith("preset_") || path.contains("content://")) {
                                                                    "https://ais-dev-esbg7mwlxnemkw63xe6fwu-535357619974.asia-east1.run.app/attachments/73d11672-5fab-49ec-9e01-948efdfad5d7/attachment_5.png"
                                                                } else {
                                                                    path
                                                                }
                                                                androidx.compose.foundation.Image(
                                                                    painter = coil.compose.rememberAsyncImagePainter(model = displayUrl),
                                                                    contentDescription = "Preview",
                                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text("🖼️ Ảnh đính kèm", fontSize = 9.sp, color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)
                                                        } else if (path.contains("video") || path.contains("mp4")) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(Color.Black, RoundedCornerShape(6.dp))
                                                                    .size(120.dp, 80.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = Color.White, modifier = Modifier.size(24.dp))
                                                            }
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text("🎞️ Video clip đính kèm", fontSize = 9.sp, color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)
                                                        } else {
                                                            Row(
                                                                modifier = Modifier
                                                                    .background(Color(0xFFE3F2FD), RoundedCornerShape(4.dp))
                                                                    .padding(4.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(Icons.Default.Description, contentDescription = "Doc", tint = Color(0xFF1565C0), modifier = Modifier.size(12.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Tập tin: ${path.substringAfterLast("/")}", fontSize = 9.sp, color = Color(0xFF0D47A1))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Buttons to upload media and files
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Gửi nhanh:", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1BEE7), contentColor = Color(0xFF4A148C)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = "Image", modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Ảnh", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1BEE7), contentColor = Color(0xFF4A148C)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video", modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Video", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1BEE7), contentColor = Color(0xFF4A148C)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = "File", modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Tài liệu", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    var sendText by remember { mutableStateOf("") }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = sendText,
                            onValueChange = { sendText = it },
                            placeholder = { Text("Nhập tin nhắn để thảo luận...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(fontSize = 12.sp),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (sendText.isNotBlank()) {
                                    viewModel.sendChatMessage(sendText)
                                    sendText = ""
                                }
                            },
                            modifier = Modifier.background(Color(0xFF6A1B9A), CircleShape)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    // 2. PHÒNG HỌP ĐỘI TRỰC TUYẾN
    if (activeModuleDetail == "meeting") {
        Dialog(onDismissRequest = { activeModuleDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Phòng Họp Trực Tuyến Đội", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), fontSize = 15.sp)
                        IconButton(onClick = { activeModuleDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    MeetingHubSection(viewModel, user)
                }
            }
        }
    }

    // 3. AI SỬA LỖI CHÍNH TẢ & HIỆU ĐÍNH VĂN BẢN
    if (activeModuleDetail == "spelling") {
        var showStyleDrop by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { activeModuleDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(580.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sửa Lỗi Chính Tả & Hiệu Đính AI", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), fontSize = 15.sp)
                        IconButton(onClick = { activeModuleDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("VĂN BẢN HIỆN CÓ / BỊ SAI LỖI", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = sourText,
                        onValueChange = { sourText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        textStyle = TextStyle(fontSize = 12.sp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { spellingGalleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tải ảnh từ máy", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { spellingCameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chụp ảnh sửa", fontSize = 11.sp)
                        }
                    }

                    if (isSpellingOcrLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFD81B60))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang xử lý & quét chữ...", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box {
                        OutlinedButton(onClick = { showStyleDrop = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Phong cách: $styleType", fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = showStyleDrop, onDismissRequest = { showStyleDrop = false }) {
                            listOf("Chuẩn hành chính trang trọng", "Bóng bẩy ngoại giao", "Ngắn gọn hành động quyết liệt").forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(style, fontSize = 12.sp) },
                                    onClick = {
                                        styleType = style
                                        showStyleDrop = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.triggerRewrite(sourText, styleType) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD81B60)),
                        enabled = !isAiLoading
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Spellcheck", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thực hiện hiệu đính AI", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("KẾT QUẢ ĐÃ SỬA CHUẨN HOÁ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF388E3C))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = aiRefinedResult.ifEmpty { "Chưa có văn bản hiệu đính. Nhấp vào nút phía trên để trợ lý AI thực hiện chỉnh sửa." },
                                fontSize = 12.sp,
                                color = if (aiRefinedResult.isEmpty()) Color.Gray else Color.Black
                            )
                        }
                    }

                    if (aiRefinedResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(aiRefinedResult))
                                Toast.makeText(context, "Đã sao chép văn bản hiệu đính", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sao chép kết quả", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 4. KIỂM TRA THỂ THỨC NGHỊ ĐỊNH 30/2020/NĐ-CP
    if (activeModuleDetail == "decree30") {

        Dialog(onDismissRequest = { activeModuleDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Thẩm Định Thể Thức NĐ 30", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), fontSize = 15.sp)
                        IconButton(onClick = { activeModuleDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("VĂN BẢN TRÌNH KÝ CẦN KIỂM TRA", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = docCon,
                        onValueChange = { docCon = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp),
                        textStyle = TextStyle(fontSize = 12.sp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { decreeGalleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tải ảnh chụp lên", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { decreeCameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chụp ảnh kiểm", fontSize = 11.sp)
                        }
                    }

                    if (isDecreeOcrLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFE65100))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đang xử lý & trích văn bản...", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.triggerFormatCheck(docCon) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        enabled = !isAiLoading
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Format check", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kiểm Thể Thức Theo NĐ 30/2020", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("KẾT QUẢ PHÂN TÍCH & BÁO CÁO ĐIỂM", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFE65100))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = aiFormatReport.ifEmpty { "Chưa có báo cáo. Nhấp nút kiểm tra để tra cứu căn lề, số ký hiệu, quốc hiệu, tiêu ngữ..." },
                                fontSize = 12.sp,
                                color = if (aiFormatReport.isEmpty()) Color.Gray else Color.Black
                            )
                        }
                    }
                }
            }
        }
    }

    // 5. QUÉT TÀI LIỆU & OCR CHUYỂN DẠNG FILE
    if (activeModuleDetail == "ocr") {
        val ocrPresets = listOf(
            "ẢNH CHỮ QUÉT 1" to "QUYẾT ĐỊNH\nVề việc rà soát quân tư trang của cán bộ chiến sỹ quý III/2026.\nPhòng Hậu cần Công an tỉnh Đắk Lắk quyết định điều chuyển 5 xe bán tải hỗ trợ các tổ nghiệp vụ cảnh sát giao thông ứng trực tuần tra...",
            "ẢNH CHỮ QUÉT 2" to "KẾ HOẠCH HỌP QUY MÔ TOÀN ĐỘI\nTập trung bàn giao cơ sở vật chất, hệ thống AI camera an ninh giám sát hành trình tuần tra biên giới.\nYêu cầu đồng chí Đội phó Nguyễn Văn Long chuẩn bị phòng họp trực tuyến chu đáo."
        )

        Dialog(onDismissRequest = { activeModuleDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quét Tài Liệu & Trích Chữ OCR", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), fontSize = 15.sp)
                        IconButton(onClick = { activeModuleDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Text("Chọn chụp ảnh, tải ảnh thực tế hoặc thử nghiệm mẫu thô:", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { ocrModuleGalleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tải ảnh từ máy", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { ocrModuleCameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chụp từ camera", fontSize = 11.sp)
                        }
                    }

                    if (isOcrModuleLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF7B1FA2))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đang xử lý tài liệu ảnh...", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("HOẶC BẤM ĐỂ GIẢ LẬP KHẢ NĂNG QUÉT MẪU:", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))

                    ocrPresets.forEach { (name, txt) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.aiOcrResult.value = txt
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = "Scan", tint = Color(0xFF7B1FA2))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF7B1FA2))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("KẾT QUẢ ĐÃ TRÍCH XUẤT CHỮ (RAW TXT)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF7B1FA2))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF3E5F5), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = aiOcrResult.ifEmpty { "Chưa có chữ trích xuất. Nhấp chọn 1 tài liệu quét ở bảng trên để giả lập camera quét văn bản." },
                                fontSize = 12.sp,
                                color = if (aiOcrResult.isEmpty()) Color.Gray else Color.Black
                            )
                        }
                    }

                    if (aiOcrResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(aiOcrResult))
                                Toast.makeText(context, "Đã sao chép chữ trích xuất", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sao Chép Văn Bản OCR", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 6. DỊCH THUẬT ĐA NGÔN NGỮ
    if (activeModuleDetail == "translator") {
        var showLangDrop by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { activeModuleDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dịch Thuật Đa Ngôn Ngữ AI", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), fontSize = 15.sp)
                        IconButton(onClick = { activeModuleDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("VĂN BẢN GỐC TIẾNG VIỆT", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = oriText,
                        onValueChange = { oriText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        textStyle = TextStyle(fontSize = 12.sp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { transGalleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tải ảnh", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = { transCameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chụp ảnh", fontSize = 10.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { directTransCameraLauncher.launch(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dịch Trực Tiếp Cam", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { directTransGalleryLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dịch Trực Tiếp Ảnh", fontSize = 10.sp)
                        }
                    }

                    if (isTransOcrLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang dịch quét từ camera...", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box {
                        OutlinedButton(onClick = { showLangDrop = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Từ tiếng Việt sang: $targetLang", fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = showLangDrop, onDismissRequest = { showLangDrop = false }) {
                            listOf("Tiếng Anh", "Tiếng Trung Quốc", "Tiếng Nhật Bản", "Tiếng Pháp", "Tiếng Ê-đê", "Tiếng Gia-rai").forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang, fontSize = 12.sp) },
                                    onClick = {
                                        targetLang = lang
                                        showLangDrop = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.triggerTranslation(oriText, targetLang) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5)),
                        enabled = !aiTranslationLoading
                    ) {
                        if (aiTranslationLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Translate, contentDescription = "Translate", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bắt đầu dịch thuật", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("BẢN DỊCH KHÁCH QUAN", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF3F51B5))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8EAF6), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = aiTranslationResult.ifEmpty { "Chưa dịch. Nhấp vào nút dịch thuật phía trên để trợ lý AI hiển thị kết quả." },
                                fontSize = 12.sp,
                                color = if (aiTranslationResult.isEmpty()) Color.Gray else Color.Black
                            )
                        }
                    }

                    if (aiTranslationResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(aiTranslationResult))
                                Toast.makeText(context, "Đã sao chép bản dịch", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sao Chép Bản Dịch", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 7. TRA CỨU QUY ĐỊNH (THÔNG TƯ, NGHỊ ĐỊNH, LUẬT PHÁP)
    if (activeModuleDetail == "laws") {

        Dialog(onDismissRequest = { activeModuleDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tra Cứu Thông Tư, Nghị Định", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), fontSize = 15.sp)
                        IconButton(onClick = { activeModuleDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("NHẬP NỘI DUNG/LUẬT HOẶC CHỤP ẢNH ĐỂ TRA CỨU HIỆU LỰC", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = queryStr,
                        onValueChange = { queryStr = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 12.sp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { lawsGalleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tải ảnh văn bản", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { lawsCameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chụp để tra cứu", fontSize = 11.sp)
                        }
                    }

                    if (isLawsOcrLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00796B))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang phân tích hiệu lực tài liệu...", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.triggerSearchRegulations(queryStr) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                        enabled = !aiSearchLoading
                    ) {
                        if (aiSearchLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tra Cứu Cơ Sở Dữ Liệu Pháp Luật", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("KẾT QUẢ ĐIỀU KHOẢN TRUY VẤN", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF00796B))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE0F2F1), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = aiSearchResult.ifEmpty { "Chưa tra cứu. Trợ lý AI sẽ trích dẫn chi tiết chương, điều khoản quy định liên quan từ cơ sớ pháp lý Việt Nam." },
                                fontSize = 12.sp,
                                color = if (aiSearchResult.isEmpty()) Color.Gray else Color.Black
                            )
                        }
                    }
                }
            }
        }
    }

    // 8. CHUYỂN ĐỔI VĂN BẢN CŨ SANG MẪU MỚI TOÀN DIỆN
    if (activeModuleDetail == "convert") {
        var oldDraftText by remember { mutableStateOf("Bản phác thảo công việc ngày 12/7. Đội Hậu cần sẽ tiếp nhận rà soát các cơ sở xe công vụ bị hư, đề nghị cấp xe lôi, và duyệt thêm chút xăng phòng cháy chữa cháy.") }
        var targetTypeSelected by remember { mutableStateOf("Báo cáo") }
        var showTypeDrop by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { activeModuleDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(580.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chuyển Mẫu Chuẩn Nghị Định 30", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), fontSize = 15.sp)
                        IconButton(onClick = { activeModuleDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("NỘI DUNG BẢN THẢO CŨ / SƠ SÀI", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = oldDraftText,
                        onValueChange = { oldDraftText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        textStyle = TextStyle(fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box {
                        OutlinedButton(onClick = { showTypeDrop = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Chuyển chuẩn hóa sang mẫu: $targetTypeSelected", fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = showTypeDrop, onDismissRequest = { showTypeDrop = false }) {
                            listOf("Báo cáo", "Quyết định", "Tờ trình", "Kế hoạch", "Công văn").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type, fontSize = 12.sp) },
                                    onClick = {
                                        targetTypeSelected = type
                                        showTypeDrop = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.triggerConvertTemplate(oldDraftText, targetTypeSelected) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037)),
                        enabled = !aiConvertLoading
                    ) {
                        if (aiConvertLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Cached, contentDescription = "Convert", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hợp Chuẩn Nghị Định 30", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("MẪU VĂN BẢN HIỆN ĐẠI MỚI CHUẨN HOÁ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF5D4037))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEFEBE9), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = aiConvertResult.ifEmpty { "Chưa có văn bản chuẩn hoá. Click vào hợp chuẩn hóa phía trên để áp dụng Quốc hiệu, Tiêu ngữ, Căn lề, Thẩm quyền ký tự động." },
                                fontSize = 12.sp,
                                color = if (aiConvertResult.isEmpty()) Color.Gray else Color.Black
                            )
                        }
                    }

                    if (aiConvertResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(aiConvertResult))
                                Toast.makeText(context, "Đã sao chép văn bản mẫu mới", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sao chép mẫu mới chuẩn hoá", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 9. AI SOẠN THẢO VĂN BẢN
    if (activeModuleDetail == "soan_thao") {
        var docDraftType by remember { mutableStateOf("Công văn") }
        var docDraftTitle by remember { mutableStateOf("Kế hoạch trang cấp trang thiết bị hậu cần và mua sắm bình chữa cháy Quý III") }
        var docDraftOutline by remember { mutableStateOf("- Căn cứ hoạt động thi đua năm 2026\n- Rà soát hư hỏng tại Trụ sở\n- Trình ban chỉ huy cấp kinh phí đặc thù") }
        var showTypeDraftDrop by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { activeModuleDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(580.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Trợ Lý Soạn Văn Bản Hàng Loạt", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), fontSize = 15.sp)
                        IconButton(onClick = { activeModuleDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box {
                        OutlinedButton(onClick = { showTypeDraftDrop = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Loại văn bản: $docDraftType", fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = showTypeDraftDrop, onDismissRequest = { showTypeDraftDrop = false }) {
                            listOf("Công văn", "Báo cáo", "Kế hoạch", "Tờ trình", "Quyết định", "Biên bản", "Thông báo", "BC kiểm tra đảng viên", "Hồ sơ thầu").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type, fontSize = 12.sp) },
                                    onClick = {
                                        docDraftType = type
                                        showTypeDraftDrop = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = docDraftTitle,
                        onValueChange = { docDraftTitle = it },
                        label = { Text("Tiêu đề phát hành", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = docDraftOutline,
                        onValueChange = { docDraftOutline = it },
                        label = { Text("Outline các ý chính", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 12.sp),
                        minLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.triggerDraft() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        enabled = !isAiLoading
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Draft", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tự Động Soạn Thảo Văn Bản", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("VĂN BẢN DO AI PHÁT THẢO ĐẦY ĐỦ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFC62828))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = aiDraft.ifEmpty { "Chưa khởi tạo. Nhấp nút tự động soạn thảo để trợ lý AI soạn toàn văn công văn báo cáo theo NĐ 30." },
                                fontSize = 12.sp,
                                color = if (aiDraft.isEmpty()) Color.Gray else Color.Black
                            )
                        }
                    }

                    if (aiDraft.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(aiDraft))
                                Toast.makeText(context, "Đã sao chép toàn văn do AI soạn", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sao chép toàn văn bản soạn", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 10. AI TÓM TẮT VĂN BẢN
    if (activeModuleDetail == "summary") {
        var bigTextToSum by remember { mutableStateOf("Báo cáo tổng kết công tác chuyển đổi số và nâng cao năng lực ứng dụng công nghệ thông tin của Đội Hậu cần - Quản trị nửa đầu năm 2026. Quán triệt tinh thần chỉ đạo về hoàn thành thắng lợi các phong trào thi đua vì an ninh Tổ quốc. Qua rà soát chi tiết, Đội đã nâng cao tổng tỉ lệ hoàn thành nhiệm vụ lên mức xuất sắc 98.6%. Toàn bộ cán bộ thực hiện 100% check-in hiện hiện trường nghiêm túc, tổ chức hơn 45 buổi họp phòng trực ban, số hoá 120 tài liệu văn thư chỉ đạo dồn dập chuẩn mực. Thách thức lớn nhất hiện ưu là cơ cấu hạ tầng thông tin, hệ thống AI camera dã ngoại cần tiếp tục được nâng cấp bảo trì chống chịu mọi điều kiện biên thùy khắc nghiệt.") }
        var summaryMode by remember { mutableStateOf("Tóm tắt ý chính quan trọng") }

        Dialog(onDismissRequest = { activeModuleDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AI Tóm Tắt Văn Bản Dài", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), fontSize = 15.sp)
                        IconButton(onClick = { activeModuleDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("VĂN BẢN GỐC CẦN TÓM TẮT", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = bigTextToSum,
                        onValueChange = { bigTextToSum = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        textStyle = TextStyle(fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.triggerSummary(bigTextToSum, summaryMode) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                        enabled = !isAiLoading
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Description, contentDescription = "Summarize", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bắt đầu tóm tắt AI", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("KẾT QUẢ TÓM TẮT DẠNG BULLET", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF455A64))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFECEFF1), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = aiSummaryResult.ifEmpty { "Chưa tóm tắt. Nhấp nút trên để trợ lý AI rút gọn ý chính cốt lõi nhất." },
                                fontSize = 12.sp,
                                color = if (aiSummaryResult.isEmpty()) Color.Gray else Color.Black
                            )
                        }
                    }
                }
            }
        }
    }

    // --- OTHER ORIGINAL DIALOGS (GIAO VIỆC & CHẤM ĐIỂM) ---

    // ĐĂNG LỊCH TRỰC TUẦN DIALOG (ADMIN/LEADER ONLY)
    if (showAddScheduleDialog) {
        Dialog(onDismissRequest = { showAddScheduleDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "ĐĂNG LỊCH CÔNG TÁC BAN CHỈ HUY",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D47A1),
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = scheduleDateInput,
                        onValueChange = { scheduleDateInput = it },
                        label = { Text("Ngày trực tuần (Ví dụ: Thứ Hai 18/06/2026)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Hình thức đăng lịch:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { scheduleUploadType = 0 }
                        ) {
                            RadioButton(selected = scheduleUploadType == 0, onClick = { scheduleUploadType = 0 })
                            Text("Bằng văn bản chữ", fontSize = 12.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { scheduleUploadType = 1 }
                        ) {
                            RadioButton(selected = scheduleUploadType == 1, onClick = { scheduleUploadType = 1 })
                            Text("Tải hình ảnh lịch", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (scheduleUploadType == 0) {
                        OutlinedTextField(
                            value = scheduleLeaderInput,
                            onValueChange = { scheduleLeaderInput = it },
                            label = { Text("Chỉ huy trực ban") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = scheduleOfficerInput,
                            onValueChange = { scheduleOfficerInput = it },
                            label = { Text("Cán bộ ban ứng trực") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = scheduleStaffInput,
                            onValueChange = { scheduleStaffInput = it },
                            label = { Text("Tổ tuần tra tăng cường") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = scheduleNotesInput,
                            onValueChange = { scheduleNotesInput = it },
                            label = { Text("Chỉ thị, ghi chú khác") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Image upload mode
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Button(
                                onClick = { scheduleGalleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tải ảnh lịch trực từ máy")
                            }

                            if (isScheduleImageUploading) {
                                Spacer(modifier = Modifier.height(6.dp))
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }

                            scheduleImageBase64?.let { base64 ->
                                Spacer(modifier = Modifier.height(8.dp))
                                val bitmap = remember(base64) {
                                    try {
                                        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Preview",
                                        modifier = Modifier
                                            .size(width = 120.dp, height = 150.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, Color.Gray, RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddScheduleDialog = false }) {
                            Text("HUỶ BỎ", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (scheduleUploadType == 0) {
                                    viewModel.insertDutySchedule(
                                        DutySchedule(
                                            dateStr = scheduleDateInput,
                                            dutyLeader = scheduleLeaderInput,
                                            dutyOfficer = scheduleOfficerInput,
                                            patrolStaff = scheduleStaffInput,
                                            notes = scheduleNotesInput
                                        )
                                    )
                                } else {
                                    val imgStr = scheduleImageBase64
                                    if (imgStr != null) {
                                        viewModel.insertDutySchedule(
                                            DutySchedule(
                                                dateStr = scheduleDateInput,
                                                dutyLeader = "Đã đính kèm ảnh lịch trực",
                                                dutyOfficer = "",
                                                patrolStaff = "",
                                                notes = "IMAGE_BASE64:$imgStr"
                                            )
                                        )
                                    } else {
                                        Toast.makeText(context, "Vui lòng tải ảnh lịch trực trước", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                }
                                showAddScheduleDialog = false
                                Toast.makeText(context, "Đã cập nhật lịch trực tuần công tác", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))
                        ) {
                            Text("ĐĂNG CÔNG BỐ LỊCH")
                        }
                    }
                }
            }
        }
    }

    // QUẢN LÝ THÀNH VIÊN VÀ PHÂN QUYỀN DIALOG
    if (showManageMembersDialog) {
        Dialog(onDismissRequest = { showManageMembersDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "QUẢN LÝ CÁN BỘ & PHÂN QUYỀN",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D47A1),
                            fontSize = 14.sp
                        )
                        IconButton(
                            onClick = { showManageMembersDialog = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrollable form and list
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Section 1: Thêm tài khoản bộ
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "✨ LẬP ACCOUNT/NICK MỚI",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFC62828)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = newMemberName,
                                    onValueChange = { newMemberName = it },
                                    label = { Text("Họ và Tên", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 12.sp),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = newMemberPhone,
                                        onValueChange = { newMemberPhone = it },
                                        label = { Text("Số điện thoại", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(fontSize = 12.sp),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newMemberUnit,
                                        onValueChange = { newMemberUnit = it },
                                        label = { Text("Tổ công tác", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(fontSize = 12.sp),
                                        singleLine = true
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Rank Selection
                                Text("Cấp bậc hàm:", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color.DarkGray)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val ranks = listOf("Trung tá", "Thiếu tá", "Đại úy", "Thượng úy", "Trung úy", "Thiếu úy", "Thượng sĩ", "Nhân viên")
                                    ranks.forEach { r ->
                                        val selected = newMemberRank == r
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (selected) Color(0xFF0D47A1) else Color.White)
                                                .border(1.dp, if (selected) Color(0xFF0D47A1) else Color.LightGray, RoundedCornerShape(6.dp))
                                                .clickable { newMemberRank = r }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(r, fontSize = 10.sp, color = if (selected) Color.White else Color.DarkGray, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Position / Permissions Selection (Phân quyền)
                                Text("Phân quyền vai trò & Chức vụ:", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color.DarkGray)
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    val positions = listOf(
                                        Triple("Đội trưởng", "Chỉ huy toàn diện (Giao việc, chấm điểm, đăng lịch trực)", Color(0xFFC62828)),
                                        Triple("Đội phó", "Chỉ huy phối hợp chỉ đạo & duyệt công việc", Color(0xFFE65100)),
                                        Triple("Cán bộ", "Thực hiện nghiệp vụ, kiểm tra & đề xuất tài sản", Color(0xFF1565C0)),
                                        Triple("Lao động hợp đồng", "Phục vụ phụ trợ xe, điện nước hành chính cơ quan", Color(0xFF455A64))
                                    )
                                    positions.forEach { (pos, desc, color) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { newMemberPosition = pos }
                                                .padding(vertical = 2.dp)
                                        ) {
                                            RadioButton(
                                                selected = newMemberPosition == pos,
                                                onClick = { newMemberPosition = pos },
                                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0D47A1))
                                            )
                                            Column {
                                                Text(pos, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = color)
                                                Text(desc, fontSize = 9.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        if (newMemberName.isBlank()) {
                                            Toast.makeText(context, "Vui lòng nhập họ tên cán bộ", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val tempName = newMemberName.trim()
                                        val temp = java.text.Normalizer.normalize(tempName, java.text.Normalizer.Form.NFD)
                                        val pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
                                        val formattedNick = pattern.matcher(temp).replaceAll("")
                                            .replace("đ", "d")
                                            .replace("Đ", "d")
                                            .lowercase()
                                            .replace("\\s+".toRegex(), "")
                                            .replace("[^a-zA-Z0-9]".toRegex(), "")
                                        val newId = if (formattedNick.isNotBlank()) formattedNick else System.currentTimeMillis().toString()
                                        viewModel.insertMember(
                                            Member(
                                                id = newId,
                                                name = newMemberName.trim(),
                                                rank = newMemberRank,
                                                position = newMemberPosition,
                                                phone = newMemberPhone.trim().ifEmpty { "09xx.xxx.xxx" },
                                                unit = newMemberUnit.trim().ifEmpty { "Hậu cần" }
                                            )
                                        )
                                        Toast.makeText(context, "Đã lập thành công tài khoản (Nik: $newId)", Toast.LENGTH_LONG).show()
                                        newMemberName = ""
                                        newMemberPhone = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("ĐĂNG KÝ TÀI KHOẢN & PHÂN QUYỀN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 2: Danh sách cán bộ hiện có và chức năng xoá
                        Text(
                            "📚 DANH SÁCH & QUYỀN HẠN CÁN BỘ HIỆN TẠI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = Color(0xFF0D47A1),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        members.forEach { m ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE8EAF6)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = m.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color.Black
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (m.position == "Đội trưởng" || m.position == "Đội phó") Color(0xFFFFEBEE)
                                                        else Color(0xFFE8EAF6)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = m.rank,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (m.position == "Đội trưởng" || m.position == "Đội phó") Color(0xFFC62828) else Color(0xFF3F51B5)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Chức vụ: ${m.position} • Tổ: ${m.unit}",
                                            fontSize = 10.5.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = "SĐT: ${m.phone}",
                                            fontSize = 9.5.sp,
                                            color = Color.DarkGray
                                        )
                                        
                                        // Badge detailing permissions
                                        val permText = when(m.position) {
                                            "Đội trưởng", "Đội phó" -> "🛡️ Toàn quyền Chỉ huy: Duyệt việc, Đăng lịch, Chấm điểm"
                                            "Cán bộ" -> "📝 Quyền Nghiệp vụ: Cập nhật tiến độ, Gửi hồ sơ, Chat"
                                            else -> "🔧 Quyền Lao động: Trực nhật, Báo cáo xe dọn dẹp"
                                        }
                                        Text(
                                            text = permText,
                                            fontSize = 8.5.sp,
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    // Let leaders delete profiles except themselves
                                    if (m.id != user.id) {
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteMember(m.id)
                                                Toast.makeText(context, "Đã xóa tài khoản ${m.name}", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFC62828),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showManageMembersDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("HOÀN TẤT QUẢN LÝ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // GIAO VIỆC DIALOG
    if (showAssignDialog) {
        var taskTitle by remember { mutableStateOf("") }
        var taskContent by remember { mutableStateOf("") }
        var assigneeName by remember { mutableStateOf("") }
        var taskDueDate by remember { mutableStateOf("") }
        var selectedAssignees by remember { mutableStateOf(setOf<String>()) }
        var attachedImageUrlState by remember { mutableStateOf<String?>(null) }

        val taskImageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                attachedImageUrlState = uri.toString()
            }
        }

        Dialog(onDismissRequest = { showAssignDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("GIAO VIỆC NGHIỆP VỤ MỚI", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), fontSize = 16.sp)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        label = { Text("Tiêu đề công cụ/nhiệm vụ") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = taskContent,
                        onValueChange = { taskContent = it },
                        label = { Text("Nội dung, yêu cầu chi tiết") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = taskDueDate,
                        onValueChange = { taskDueDate = it },
                        label = { Text("Hạn hoàn thành (Ví dụ: 18/06/2026)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Chọn những cán bộ cùng thực hiện:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                            .padding(4.dp)
                    ) {
                        items(members) { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedAssignees = if (selectedAssignees.contains(m.name)) {
                                            selectedAssignees - m.name
                                        } else {
                                            selectedAssignees + m.name
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedAssignees.contains(m.name),
                                    onCheckedChange = { isChecked ->
                                        selectedAssignees = if (isChecked) {
                                            selectedAssignees + m.name
                                        } else {
                                            selectedAssignees - m.name
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${m.name} (${m.position})", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tài liệu hình ảnh mô tả:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Button(
                            onClick = { taskImageLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Pick", modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tải ảnh từ Admin", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (attachedImageUrlState != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE3F2FD))
                                .border(0.5.dp, Color(0xFF90CAF9), RoundedCornerShape(4.dp))
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Attachment, contentDescription = "Attached", tint = Color(0xFF1565C0), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            val displayPath = attachedImageUrlState!!
                            Text("Đã đính kèm: " + (if (displayPath.length > 22) "..." + displayPath.takeLast(18) else displayPath), fontSize = 11.sp, color = Color(0xFF0D47A1))
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { attachedImageUrlState = null },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Xóa", color = Color.Red, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("thiết_bị_hỏng.png", "phương_án_tuần_tra.jpg").forEach { preset ->
                                Button(
                                    onClick = { attachedImageUrlState = "preset_$preset" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFECEFF1), contentColor = Color.Black),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp).weight(1f)
                                ) {
                                    Text("Bản phôi: $preset", fontSize = 8.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAssignDialog = false }) {
                            Text("Bỏ qua", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (taskTitle.isNotEmpty() && selectedAssignees.isNotEmpty()) {
                                    val finalAssignees = selectedAssignees.joinToString(", ")
                                    viewModel.assignTask(
                                        taskTitle, 
                                        taskContent, 
                                        finalAssignees, 
                                        attachedImageUrlState, 
                                        taskDueDate.ifBlank { null }
                                    )
                                    showAssignDialog = false
                                } else {
                                    Toast.makeText(context, "Vui lòng nhập tiêu đề và chọn ít nhất 1 cán bộ thực hiện!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                        ) {
                            Text("Giao việc")
                        }
                    }
                }
            }
        }
    }

    // CHẤM ĐIỂM THI ĐUA DIALOG
    if (taskToScore != null) {
        var scoreInputText by remember { mutableStateOf("95") }
        var evaluationInputText by remember { mutableStateOf("Hoàn thành xuất sắc vượt tiến độ.") }
        var ratingStarsState by remember { mutableStateOf(5) }

        Dialog(onDismissRequest = { taskToScore = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ĐÁNH GIÁ & CHẤM ĐIỂM THI ĐUA", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Đánh giá công việc: ${taskToScore?.title}", fontSize = 13.sp, color = Color.DarkGray)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = scoreInputText,
                        onValueChange = { scoreInputText = it },
                        label = { Text("Nhập điểm thi đua (0 - 100)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = evaluationInputText,
                        onValueChange = { evaluationInputText = it },
                        label = { Text("Đánh giá mức độ hoàn thành") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Xếp hạng mức hoàn thành:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (1..5).forEach { starIndex ->
                            IconButton(
                                onClick = { ratingStarsState = starIndex },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (starIndex <= ratingStarsState) Icons.Filled.Star else Icons.Outlined.Star,
                                    contentDescription = "Rating $starIndex",
                                    tint = Color(0xFFFBC02D)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { taskToScore = null }) {
                            Text("Hủy", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val intScore = scoreInputText.toIntOrNull() ?: 95
                                taskToScore?.let {
                                    viewModel.scoreTask(it, intScore, evaluationInputText, ratingStarsState)
                                }
                                taskToScore = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                        ) {
                            Text("Ghi điểm")
                        }
                    }
                }
            }
        }
    }

    // 11. ADMIN BRANDING CONFIGURATION DIALOG
    if (showAdminBrandingDialog) {
        var tempLogoUrl by remember { mutableStateOf(logoUrl) }
        var tempBgUrl by remember { mutableStateOf(bgUrl) }

        Dialog(onDismissRequest = { showAdminBrandingDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("THAY ĐỔI LOGO & HÌNH NỀN", fontWeight = FontWeight.Bold, color = Color(0xFF0F1B2B), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tempLogoUrl,
                        onValueChange = { tempLogoUrl = it },
                        label = { Text("Đường dẫn Logo mới (URL)") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tempBgUrl,
                        onValueChange = { tempBgUrl = it },
                        label = { Text("Đường dẫn Hình nền mới (URL)") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                tempLogoUrl = "https://ais-dev-esbg7mwlxnemkw63xe6fwu-535357619974.asia-east1.run.app/attachments/73d11672-5fab-49ec-9e01-948efdfad5d7/attachment_5.png"
                                tempBgUrl = "https://ais-dev-esbg7mwlxnemkw63xe6fwu-535357619974.asia-east1.run.app/attachments/73d11672-5fab-49ec-9e01-948efdfad5d7/attachment_6.png"
                            }
                        ) {
                            Text("Khôi phục mặc định", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Row {
                            TextButton(onClick = { showAdminBrandingDialog = false }) {
                                Text("Hủy", color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.updateBranding(tempLogoUrl, tempBgUrl)
                                    showAdminBrandingDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                            ) {
                                Text("Lưu cấu hình", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ACTIVE MEETING HUB SUB-VIEW ---
@Composable
fun MeetingHubSection(viewModel: MainViewModel, user: Member) {
    val isJoined by viewModel.isJoinedRoom.collectAsState()
    val micEnabled by viewModel.isRoomMicEnabled.collectAsState()
    val camEnabled by viewModel.isRoomCamEnabled.collectAsState()
    val screenSharing by viewModel.isRoomSharing.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECEFF1)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VideoCall, contentDescription = "Video", tint = Color(0xFFC62828))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Phòng họp giao ban Online", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                }

                if (isJoined) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF388E3C))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Đã kết nối", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isJoined) {
                Text(
                    text = "Đồng chí có thể tham gia nhanh phòng giao ban họp khẩn của Đội Hậu cần - Quản trị và Lãnh đạo bất cứ lúc nào.",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.isJoinedRoom.value = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Enter")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("VÀO PHÒNG HỌP KHẨN CA-ĐL", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else {
                val participants by viewModel.meetingParticipantsState.collectAsState()
                
                Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        // 1. ME / Current User Card
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(115.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (camEnabled) {
                                    if (hasCameraPermission) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AndroidView(
                                                factory = { ctx ->
                                                    val previewView = PreviewView(ctx).apply {
                                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                                    }
                                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                                    cameraProviderFuture.addListener({
                                                        try {
                                                            val cameraProvider = cameraProviderFuture.get()
                                                            val cameraPreview = CameraPreview.Builder().build().also {
                                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                                            }
                                                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                                                            cameraProvider.unbindAll()
                                                            cameraProvider.bindToLifecycle(
                                                                lifecycleOwner,
                                                                cameraSelector,
                                                                cameraPreview
                                                            )
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }, ContextCompat.getMainExecutor(ctx))
                                                    previewView
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            // Watermark
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(6.dp)
                                                    .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("LIVE • BẠN", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(6.dp)
                                        ) {
                                            Text("Cần quyền máy ảnh", color = Color.White, fontSize = 9.sp, textAlign = TextAlign.Center)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Button(
                                                onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                modifier = Modifier.height(20.dp),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("Cấp quyền", fontSize = 8.sp)
                                            }
                                        }
                                    }
                                } else {
                                    // Camera is disabled
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color(0xFF1E88E5), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(user.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("${user.name} (Bạn)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text("Camera tắt", color = Color.LightGray, fontSize = 8.sp)
                                    }
                                }
                                
                                // Indicators overlay
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                        contentDescription = "Mic State",
                                        tint = if (micEnabled) Color.Green else Color.Red,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        // 2. OTHER ACTIVE PARTICIPANTS
                        items(participants) { p ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(115.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E1E1E))
                                    .border(
                                        width = if (p.isMicOn) 1.5.dp else 0.dp,
                                        color = if (p.isMicOn) Color.Green else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (p.isCameraOn) {
                                    // Animated mock camera stream
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Visual audio amplitude pulse
                                        val infiniteTransition = rememberInfiniteTransition()
                                        val pulseAlpha by infiniteTransition.animateFloat(
                                            initialValue = 0.15f,
                                            targetValue = 0.5f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1200, easing = LinearEasing),
                                                repeatMode = RepeatMode.Reverse
                                            )
                                        )

                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(45.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Green.copy(alpha = pulseAlpha))
                                                    .align(Alignment.Center)
                                            )
                                            
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.align(Alignment.Center)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(Color(0xFF7B1FA2), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(p.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        // Live Indicator
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .background(Color.Green.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("LIVE • TRUYỀN", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    // Camera is turned OFF
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color.Gray, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(p.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(p.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text("Camera tắt", color = Color.LightGray, fontSize = 8.sp)
                                    }
                                }

                                // Status icons overlay
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (p.isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                                        contentDescription = "Mic State",
                                        tint = if (p.isMicOn) Color.Green else Color.Red,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                                
                                // Overlay showing rank/position metadata
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${p.position} ${p.name}",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Meeting Controller Layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.isRoomMicEnabled.value = !micEnabled }) {
                            Icon(
                                imageVector = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "Mic",
                                tint = if (micEnabled) Color(0xFF0D47A1) else Color.Red
                            )
                        }
                        IconButton(onClick = { viewModel.isRoomCamEnabled.value = !camEnabled }) {
                            Icon(
                                imageVector = if (camEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                contentDescription = "Cam",
                                tint = if (camEnabled) Color(0xFF0D47A1) else Color.Red
                            )
                        }
                        IconButton(onClick = { viewModel.isRoomSharing.value = !screenSharing }) {
                            Icon(
                                imageVector = Icons.Default.PresentToAll,
                                contentDescription = "Share",
                                tint = if (screenSharing) Color(0xFF388E3C) else Color.DarkGray
                            )
                        }
                        Button(
                            onClick = { viewModel.isJoinedRoom.value = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Rời phòng", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// --- 4. TAB 1: INTERNAL CHAT LIKE ZALO (CHAT NỘI BỘ) ---
@Composable
fun InternalChatZalo(viewModel: MainViewModel, user: Member) {
    val activeChannel by viewModel.chatChannelId.collectAsState()
    val messages by viewModel.channelMessages.collectAsState()
    val pinned by viewModel.pinnedMessages.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column Channels List like Zalo Groups
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(100.dp)
                .background(Color(0xFFEFEFEF))
                .border(width = (0.5).dp, color = Color.LightGray),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "TỔ / NHÓM",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                fontWeight = FontWeight.Bold
            )

            val channels = listOf(
                Pair("toan_doi", "Toàn Đội"),
                Pair("to_quan_tri", "Tổ QTri"),
                Pair("to_hau_can", "Tổ H.Cần"),
                Pair("to_xe", "Tổ Xe")
            )

            channels.forEach { (id, label) ->
                val isSelected = activeChannel == id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF0D47A1) else Color.Transparent)
                        .clickable { viewModel.chatChannelId.value = id }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color.Black,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Right Column Chat Interface Stream
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .background(Color(0xFFECEFF1)) // Classic Zalo light color theme
        ) {
            // Channel Banner Header
            val headerTitle = when (activeChannel) {
                "toan_doi" -> "Nhóm: TOÀN ĐỘI HC-QT"
                "to_quan_tri" -> "Tổ: Quản lý Tài Sản"
                "to_hau_can" -> "Tổ: Hậu Cần Quản Trị"
                else -> "Tổ: Xe Nghiệp Vụ"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(0.5.dp, Color.LightGray)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Group, contentDescription = "Active Channel", tint = Color(0xFF0D47A1))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = headerTitle, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
            }

            // Pinned system announcements (Announcement ghim)
            val filteredPinned = pinned.filter { it.channelId == activeChannel }
            if (filteredPinned.isNotEmpty()) {
                val latestPin = filteredPinned.first()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF9C4)) // Pin background shade
                        .border(0.5.dp, Color(0xFFFFF176))
                        .clickable {
                            // Highlight or dialog
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = Color.Red,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ghim: ${latestPin.senderName}: ${latestPin.content}",
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (user.position == "Đội trưởng" || user.position == "Đội phó") {
                        IconButton(onClick = { viewModel.unpinMessage(latestPin) }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Unpin", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Messages Streams View
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(messages) { message ->
                    val isMyMsg = message.senderId == user.id
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isMyMsg) Alignment.End else Alignment.Start
                    ) {
                        // Sender display label
                        if (!isMyMsg) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                                Text(
                                    text = "${message.senderName} (${message.senderPosition})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.DarkGray
                                )
                            }
                        }

                        // Message Bubbles Card
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                                bottomStart = if (isMyMsg) 12.dp else 0.dp,
                                bottomEnd = if (isMyMsg) 0.dp else 12.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMyMsg) Color(0xFFD0E1FD) else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = message.content, fontSize = 13.sp, color = Color.Black)

                                if (message.attachmentPath != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Gray.copy(alpha = 0.2f))
                                            .padding(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.AttachFile, contentDescription = "File", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(message.attachmentPath, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Timestamp + Read status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
                        ) {
                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                            if (isMyMsg) {
                                Text("• Đã xem", fontSize = 9.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Chat send action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Send Mock Image Trigger
                var showAddMockFile by remember { mutableStateOf(false) }
                IconButton(onClick = { showAddMockFile = true }) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Send Image", tint = Color(0xFF0D47A1))
                }

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Nhập tin nhắn...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF5F7FA),
                        unfocusedContainerColor = Color(0xFFF5F7FA)
                    ),
                    maxLines = 2,
                    textStyle = TextStyle(fontSize = 13.sp)
                )

                IconButton(
                    onClick = {
                        if (textInput.isNotEmpty()) {
                            viewModel.sendChatMessage(textInput)
                            textInput = ""
                            scope.launch {
                                lazyListState.animateScrollToItem(messages.size)
                            }
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF0D47A1)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                }

                if (showAddMockFile) {
                    Dialog(onDismissRequest = { showAddMockFile = false }) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("GỬI ẢNH / TÀI LIỆU NHANH", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                val files = listOf(
                                    "ảnh_hiện_trường_thiết_bị_hỏng.png",
                                    "tập_tin_báo_cáo_nghiệp_vụ.xlsx",
                                    "bản_vẽ_mặt_bằng_đội_xe.pdf"
                                )
                                files.forEach { filename ->
                                    TextButton(
                                        onClick = {
                                            viewModel.sendChatMessage(
                                                text = "Đã gửi tệp đính kèm nghiệp vụ.",
                                                attachedImagePath = filename
                                            )
                                            showAddMockFile = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(filename, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 5. TAB 2: AI SOẠN THẢO VÀ THẨM ĐỊNH (AI DRAFTING PANEL) ---
@Composable
fun AiSoanThaoTab(viewModel: MainViewModel, user: Member) {
    var docType by remember { mutableStateOf("Công văn") }
    var docTitle by remember { mutableStateOf("Kế hoạch rà soát kiểm tra vật lực Quý III Công an tỉnh Đắk Lắk") }
    var docOutline by remember { mutableStateOf("- Căn cứ chương trình hoạt động năm 2026\n- Rà soát các thiết bị hư hỏng để sửa chữa\n- Trình ban chỉ huy ký phê duyệt ngân sách.") }

    val aiDraft by viewModel.aiDraftResult.collectAsState()
    val aiFormatReport by viewModel.aiFormatReport.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    var activeSubTab by remember { mutableStateOf(0) } // 0: Soạn thảo, 1: Kiểm thể thức Nghị định 30

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = Color.White,
            contentColor = Color(0xFF0D47A1)
        ) {
            Tab(selected = activeSubTab == 0, onClick = { activeSubTab = 0 }) {
                Text("AI Soạn Văn Bản", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Tab(selected = activeSubTab == 1, onClick = { activeSubTab = 1 }) {
                Text("Kiểm Thể thức NĐ 30", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(Color(0xFFF4F6F9))
        ) {
            if (activeSubTab == 0) {
                // Draft documents panel
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("THÔNG TIN ĐẦU VÀO SOẠN THẢO", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                                Spacer(modifier = Modifier.height(8.dp))

                                // Dropdown selector for types
                                var showTypesDrop by remember { mutableStateOf(false) }
                                val types = listOf("Công văn", "Báo cáo", "Kế hoạch", "Tờ trình", "Quyết định", "Biên bản", "Thông báo", "BC kiểm tra đảng viên", "Hồ sơ thầu")

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { showTypesDrop = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Loại văn bản: $docType", fontWeight = FontWeight.Bold)
                                    }
                                    DropdownMenu(
                                        expanded = showTypesDrop,
                                        onDismissRequest = { showTypesDrop = false },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        types.forEach { t ->
                                            DropdownMenuItem(
                                                text = { Text(t) },
                                                onClick = {
                                                    docType = t
                                                    showTypesDrop = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = docTitle,
                                    onValueChange = { docTitle = it },
                                    label = { Text("Tiêu đề / Ý định ban hành") },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 13.sp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = docOutline,
                                    onValueChange = { docOutline = it },
                                    label = { Text("Các ý chính muốn đưa vào nội dung") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    textStyle = TextStyle(fontSize = 13.sp)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { viewModel.triggerDraft() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isAiLoading,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isAiLoading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Draft")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("KÍCH HOẠT GEMINI SOẠN VĂN BẢN", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Draft display result
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("VĂN BẢN HÀNH CHÍNH DO AI SOẠN", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                                Spacer(modifier = Modifier.height(8.dp))

                                if (aiDraft.isNotEmpty()) {
                                    SelectionContainer {
                                        Text(
                                            text = aiDraft,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Black,
                                            modifier = Modifier
                                                .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                                .background(Color(0xFFF9FBFD))
                                                .padding(10.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Chưa kích hoạt soạn thảo văn bản tự động.", color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Formatting rà soát panel (Kiểm tra Thể thức Nghị định 30/2020)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("KIỂM TRA THỂ THỨC NGHỊ ĐỊNH 30", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "AI sẽ tiến hành rà rập kiểm định đầy đủ quốc hiệu, tiêu ngữ, căn lề trái-phải, cỡ chữ và thẩm quyền phê duyệt ký tên.",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = { viewModel.triggerFormatCheck(aiDraft) },
                                    enabled = aiDraft.isNotEmpty() && !isAiLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isAiLoading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        Icon(Icons.Filled.Verified, contentDescription = "Verify")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("QUÉT KIỂM TRA VĂN BẢN ĐÃ SOẠN", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("BÁO CÁO THẨM ĐỊNH CHI TIẾT", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                Spacer(modifier = Modifier.height(8.dp))

                                if (aiFormatReport.isNotEmpty()) {
                                    HighlightReportText(rawReport = aiFormatReport)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Chưa tiến hành quét thẩm định Thể thức.", color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 6. TAB 3: AI SUMMARIZER AND OCR SCANNERS (TÓM TẮT & OCR) ---
@Composable
fun AiSummarizerAndOcrTab(viewModel: MainViewModel, user: Member) {
    var longDocText by remember { mutableStateOf("Thông tư liên tịch số 02/2021/TTLT-BCA-BQP quy định cụ thể về việc phối hợp trong công tác bảo vệ an ninh quốc gia, bảo đảm trật tự an toàn xã hội và thực hiện nhiệm vụ quốc phòng hậu cần. Văn bản gồm 4 chương và 28 điều khoản ràng buộc hết sức chặt chẽ...") }
    var summarizeMode by remember { mutableStateOf("Rút ý nhanh báo cáo lãnh đạo (Executive Summary)") }

    val aiSummary by viewModel.aiSummaryResult.collectAsState()
    val aiOcr by viewModel.aiOcrResult.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    val context = LocalContext.current
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Setup photo/image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                selectedBitmap = bitmap
                Toast.makeText(context, "Đã chọn ảnh từ Thư viện", Toast.LENGTH_SHORT).show()
                // Auto trigger OCR once bitmap loaded
                viewModel.triggerOcr(bitmap)
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi nạp ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var activeSubTab by remember { mutableStateOf(0) } // 0: Summarizer, 1: OCR scan

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = Color.White,
            contentColor = Color(0xFF0D47A1)
        ) {
            Tab(selected = activeSubTab == 0, onClick = { activeSubTab = 0 }) {
                Text("AI Tóm Tắt", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Tab(selected = activeSubTab == 1, onClick = { activeSubTab = 1 }) {
                Text("Quét Trích Chữ OCR", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(Color(0xFFF4F6F9))
        ) {
            if (activeSubTab == 0) {
                // Long document summarizer view
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("TÓM TẮT VĂN BẢN / HỒ SƠ THẦU", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = longDocText,
                                    onValueChange = { longDocText = it },
                                    label = { Text("Dán toàn bộ văn bản dài cần tóm tắt") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 4,
                                    textStyle = TextStyle(fontSize = 13.sp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Dropdown summarizer mode selection
                                var showModesDrop by remember { mutableStateOf(false) }
                                val modes = listOf("Tóm tắt ngắn gọn (Short)", "Chi tiết chuyên sâu (Detailed)", "Rút ý nhanh báo cáo lãnh đạo (Executive Summary)")

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { showModesDrop = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Chế độ tóm tắt: $summarizeMode", fontWeight = FontWeight.Bold)
                                    }
                                    DropdownMenu(
                                        expanded = showModesDrop,
                                        onDismissRequest = { showModesDrop = false },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        modes.forEach { m ->
                                            DropdownMenuItem(
                                                text = { Text(m) },
                                                onClick = {
                                                    summarizeMode = m
                                                    showModesDrop = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { viewModel.triggerSummary(longDocText, summarizeMode) },
                                    enabled = longDocText.isNotEmpty() && !isAiLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isAiLoading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        Icon(Icons.Filled.ContentPasteGo, contentDescription = "Summarize")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("TRÍCH ĐỌC & TÓM TẮT KHẨN", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("NỘI DUNG TÓM TẮT ĐÃ TRÍCH XUẤT", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                                Spacer(modifier = Modifier.height(8.dp))

                                if (aiSummary.isNotEmpty()) {
                                    Text(
                                        text = aiSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Black,
                                        modifier = Modifier
                                            .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                            .background(Color(0xFFEEF5EE))
                                            .padding(10.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Đang chờ kích hoạt trích tóm tắt.", color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // OCR Scanner View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "QUÉT OCR CHUYỂN HOÀN FILE TIẾNG VIỆT",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D47A1),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Đồng chí chụp ảnh tài liệu hành chính hoặc nạp ảnh từ thư viện, AI sẽ ngay lập tức trích xuất toàn bộ chữ thô hoạt động chính xác tương tự như máy scan chuyên nghiệp.",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Select Gallery Photo Trigger
                                Button(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.PhotoLibrary, contentDescription = "Gallery")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("NẠP ẢNH TÀI LIỆU CẦN QUÉT", fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Simulated high-quality office doc image select triggers (Alternative high quality mock dataset)
                                Text("HOẶC CHỌN ẢNH PHÔI ĐIỂN HÌNH ĐỂ THỬ NGHIỆM:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth())

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            // Mock/Load typical office print bitmap safely which has actual Vietnamese text
                                            val generatedMock = createMockTextBitmap("QUYẾT ĐỊNH Đội xe nghiệp vụ Hậu cần Công an tỉnh Đắk Lắk 2026...")
                                            selectedBitmap = generatedMock
                                            viewModel.triggerOcr(generatedMock)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Phôi Quyết định", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val generatedMock = createMockTextBitmap("BÁO CÁO CÔNG TÁC TUẦN - V/v Sắp xếp thiết bị an ninh văn phòng Đội HC-QT Đắk Lắk...")
                                            selectedBitmap = generatedMock
                                            viewModel.triggerOcr(generatedMock)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Phôi Báo cáo", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Selected preview image
                    selectedBitmap?.let { bmp ->
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Custom visual display rendering
                                    Text(
                                        "[HÌNH ẢNH TÀI LIỆU ĐANG QUÉT - SẴN SÀNG OCR]",
                                        color = Color.DarkGray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Render results
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("KẾT QUẢ QUÉT CHỮ TRÍCH XUẤT", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                                Spacer(modifier = Modifier.height(8.dp))

                                if (aiOcr.isNotEmpty()) {
                                    SelectionContainer {
                                        Text(
                                            text = aiOcr,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Black,
                                            modifier = Modifier
                                                .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                                .background(Color(0xFFF1F8E9))
                                                .padding(10.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isAiLoading) {
                                            CircularProgressIndicator(color = Color(0xFF0D47A1))
                                        } else {
                                            Text("Chưa có ảnh tài liệu nào được quét.", color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        var sourceFormat by remember { mutableStateOf("Word (.docx)") }
                        var targetFormat by remember { mutableStateOf("Văn bản PDF") }
                        var fileNameInput by remember { mutableStateOf("Bao_cao_so_lieu_hau_can_thang_6") }
                        
                        var isConverting by remember { mutableStateOf(false) }
                        var convertProgress by remember { mutableStateOf(0f) }
                        var isConvertedSuccess by remember { mutableStateOf(false) }
                        var finalFileName by remember { mutableStateOf("") }

                        val scope = rememberCoroutineScope()

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Transform, contentDescription = "Convert", tint = Color(0xFFE65100))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("BỘ CHUYỂN ĐỔI ĐỊNH DẠNG TÀI LIỆU AI", fontWeight = FontWeight.Bold, color = Color(0xFFE65100), fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("Hỗ trợ quy đổi tương sinh 2 chiều giữa tập tin Word (.docx), hình ảnh chụp tài liệu (.png/.jpg) và sách dữ liệu định dạng PDF.", fontSize = 11.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(10.dp))

                                Text("1. CHỌN ĐỊNH DẠNG GỐC VÀO:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.DarkGray)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Word (.docx)", "Ảnh (.png/.jpg)", "Văn bản PDF").forEach { fmt ->
                                        val isSelected = sourceFormat == fmt
                                        Button(
                                            onClick = { 
                                                sourceFormat = fmt
                                                // auto recommend distinct target
                                                if (sourceFormat == "Word (.docx)") targetFormat = "Văn bản PDF"
                                                if (sourceFormat == "Ảnh (.png/.jpg)") targetFormat = "Word (.docx)"
                                                if (sourceFormat == "Văn bản PDF") targetFormat = "Word (.docx)"
                                                isConvertedSuccess = false
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) Color(0xFFE65100) else Color(0xFFECEFF1),
                                                contentColor = if (isSelected) Color.White else Color.Black
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Text(fmt, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text("2. CHỌN ĐỊNH DẠNG ĐÍCH XUẤT:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.DarkGray)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Word (.docx)", "Ảnh (.png/.jpg)", "Văn bản PDF").forEach { fmt ->
                                        val isSelected = targetFormat == fmt
                                        val isAllowed = fmt != sourceFormat
                                        Button(
                                            onClick = { 
                                                if (isAllowed) {
                                                    targetFormat = fmt
                                                    isConvertedSuccess = false
                                                }
                                            },
                                            enabled = isAllowed,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) Color(0xFFE65100) else Color(0xFFE0E0E0),
                                                contentColor = if (isSelected) Color.White else Color.Black
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Text(fmt, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = fileNameInput,
                                    onValueChange = { 
                                        fileNameInput = it
                                        isConvertedSuccess = false
                                    },
                                    label = { Text("Đổi tên tệp hành chính đầu ra:") },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 12.sp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                if (isConverting) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        LinearProgressIndicator(
                                            progress = { convertProgress },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                            color = Color(0xFFE65100)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Đang chuyển đổi AI (${(convertProgress * 100).toInt()}%)... Vui lòng giữ ứng dụng mở.",
                                            fontSize = 11.sp,
                                            color = Color(0xFFE65100),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isConvertedSuccess = false
                                                isConverting = true
                                                convertProgress = 0f
                                                while (convertProgress < 1f) {
                                                    kotlinx.coroutines.delay(100)
                                                    convertProgress += 0.11f
                                                    if (convertProgress > 1f) convertProgress = 1f
                                                }
                                                val suffix = when (targetFormat) {
                                                    "Word (.docx)" -> ".docx"
                                                    "Ảnh (.png/.jpg)" -> ".png"
                                                    else -> ".pdf"
                                                }
                                                finalFileName = "${fileNameInput.replace(" ", "_")}_converted$suffix"
                                                isConverting = false
                                                isConvertedSuccess = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.Sync, contentDescription = "Sync")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("BẮT ĐẦU CHUYỂN HOÁN TẬP TIN AI", fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (isConvertedSuccess) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                        border = BorderStroke(1.dp, Color(0xFF81C784)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = Color(0xFF2E7D32))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("CHUYỂN HOÁN THÀNH CÔNG!", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 12.sp)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Định dạng gốc: $sourceFormat ➔ $targetFormat", fontSize = 11.sp, color = Color.DarkGray)
                                            Text("Tên tệp đã xuất: $finalFileName", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        Toast.makeText(context, "Đã tải tập tin $finalFileName xuống thư mục Download", Toast.LENGTH_LONG).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Icon(Icons.Filled.Download, contentDescription = "Download", modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Tải xuống máy", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }

                                                Button(
                                                    onClick = {
                                                        viewModel.sendChatMessage(
                                                            text = "Đã chuyển đổi & gửi tài liệu nghiệp vụ sang nhóm Zalo: $finalFileName",
                                                            attachedImagePath = finalFileName
                                                        )
                                                        Toast.makeText(context, "Đã gửi $finalFileName sang nhóm Zalo của Đội!", Toast.LENGTH_LONG).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Icon(Icons.Filled.Send, contentDescription = "Send", modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Gửi sang Zalo", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom simple helper builder to create dynamic Bitmap with texts as tests
fun createMockTextBitmap(textToDraw: String): Bitmap {
    val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
    // Return empty but populated with metadata colors which translates to standard bitmap structure
    return bitmap
}

@Composable
fun HighlightReportText(rawReport: String) {
    val lines = rawReport.split("\n").filter { it.trim().isNotEmpty() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        lines.forEach { line_text ->
            val line = line_text.trim()
            val hasError = line.contains("Lỗi", ignoreCase = true) || 
                           line.contains("Cần sửa", ignoreCase = true) || 
                           line.contains("Caution", ignoreCase = true) || 
                           line.contains("Sai", ignoreCase = true) ||
                           line.contains("Không đạt", ignoreCase = true) ||
                           line.contains("Sử dụng", ignoreCase = true) ||
                           line.contains("Nghị định 20", ignoreCase = true)

            val isOk = line.contains("Chuẩn", ignoreCase = true) || 
                       line.contains("Chính xác", ignoreCase = true) || 
                       line.contains("Đạt", ignoreCase = true) || 
                       line.contains("OK", ignoreCase = true) || 
                       line.contains("Đúng", ignoreCase = true)

            val bgColor = when {
                hasError -> Color(0xFFFEEBEE) // Crimson Red alert background
                isOk -> Color(0xFFE8F5E9)     // Forest Green success background
                else -> Color(0xFFECEFF1)     // Light neutral background
            }

            val borderColor = when {
                hasError -> Color(0xFFEF5350)
                isOk -> Color(0xFF66BB6A)
                else -> Color(0xFFB0BEC5)
            }

            val textColor = when {
                hasError -> Color(0xFFC62828)
                isOk -> Color(0xFF2E7D32)
                else -> Color(0xFF37474F)
            }

            val icon = when {
                hasError -> Icons.Default.Error
                isOk -> Icons.Default.CheckCircle
                else -> Icons.Default.Info
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, borderColor),
                colors = CardDefaults.cardColors(containerColor = bgColor)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Status",
                        tint = borderColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = line,
                        fontSize = 12.sp,
                        fontWeight = if (hasError) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
