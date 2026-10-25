package com.smartstudent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Calendar

// ... (نفس كلاسات البيانات والمحركات زي ما هي) ...
data class AlarmEntity(
    val id: Long = 0L,
    val title: String,
    val description: String? = null,
    val triggerAt: Long,
    val repeatInterval: Long? = null,
    val isActive: Boolean = true
)

interface AlarmRepository {
    val activeAlarms: LiveData<List<AlarmEntity>>
    suspend fun addAlarm(alarm: AlarmEntity)
    suspend fun removeAlarm(alarm: AlarmEntity)
    suspend fun updateAlarm(alarm: AlarmEntity)
    suspend fun getById(id: Long): AlarmEntity?
}

class MockAlarmRepository : AlarmRepository {
    private val _alarms = MutableLiveData<List<AlarmEntity>>(emptyList())
    override val activeAlarms: LiveData<List<AlarmEntity>> get() = _alarms
    override suspend fun addAlarm(alarm: AlarmEntity) {
        val list = _alarms.value?.toMutableList() ?: mutableListOf()
        list.add(alarm.copy(id = System.currentTimeMillis()))
        _alarms.postValue(list)
    }
    override suspend fun removeAlarm(alarm: AlarmEntity) {
        val list = _alarms.value?.toMutableList() ?: mutableListOf()
        list.removeAll { it.id == alarm.id }
        _alarms.postValue(list)
    }
    override suspend fun updateAlarm(alarm: AlarmEntity) {
        val list = _alarms.value?.toMutableList() ?: mutableListOf()
        val index = list.indexOfFirst { it.id == alarm.id }
        if (index >= 0) list[index] = alarm
        _alarms.postValue(list)
    }
    override suspend fun getById(id: Long): AlarmEntity? = _alarms.value?.firstOrNull { it.id == id }
}

data class AIResponse(val candidates: List<Candidate>)
data class Candidate(val content: Content)
data class Content(val parts: List<Part>)
data class Part(val text: String)

interface AIEngine {
    suspend fun generateExam(subject: String, years: List<Int>, types: List<String>): AIResponse
    suspend fun explain(subject: String, topic: String, level: String = "highschool"): AIResponse
    suspend fun autoSchedule(schedule: String): AIResponse
}

class MockAIEngine : AIEngine {
    override suspend fun generateExam(subject: String, years: List<Int>, types: List<String>) =
        AIResponse(listOf(Candidate(Content(listOf(Part("امتحان $subject\nالسنوات: ${years.joinToString(", ")}\n\n1. السؤال الأول…\n2. السؤال الثاني…"))))))
    override suspend fun explain(subject: String, topic: String, level: String) =
        AIResponse(listOf(Candidate(Content(listOf(Part("شرح $topic:\n- التعريف…\n- مثال…"))))))
    override suspend fun autoSchedule(schedule: String) =
        AIResponse(listOf(Candidate(Content(listOf(Part("الجدول المقترح:\n08:00 رياضيات\n09:15 فيزياء"))))))
}

object TimeUtil {
    fun nowMillis(): Long = System.currentTimeMillis()
    fun format(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format("%02d-%02d-%d %02d:%02d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
}

class MainActivity : ComponentActivity() {
    private val alarmRepo: AlarmRepository = MockAlarmRepository()
    private val aiEngine: AIEngine = MockAIEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartStudentTheme {
                MainScreen(alarmRepository = alarmRepo, aiEngine = aiEngine)
            }
        }
    }
}

@Composable
fun SmartStudentTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Color(0xFF1E88E5), // أزرق احترافي
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE3F2FD),
                onPrimaryContainer = Color(0xFF0D47A1),
                secondary = Color(0xFF43A047), // أخضر حيوي
                background = Color(0xFFF4F7F6), // خلفية فاتحة مريحة للعين
                surface = Color.White,
                onSurface = Color(0xFF1A1A1A)
            ),
            typography = Typography(),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(alarmRepository: AlarmRepository, aiEngine: AIEngine) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("المنبهات", "دردشة AI", "الامتحانات")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الطالب الذكي", fontWeight = FontWeight.ExtraBold, fontSize = MaterialTheme.typography.headlineSmall.fontSize) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Settings, contentDescription = "الإعدادات")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) // حواف دائرية للشريط السفلي
            ) {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                when (index) {
                                    0 -> if (selectedTab == 0) Icons.Filled.Alarm else Icons.Outlined.Alarm
                                    1 -> if (selectedTab == 1) Icons.Filled.Chat else Icons.Outlined.Chat
                                    else -> if (selectedTab == 2) Icons.Filled.Description else Icons.Outlined.Description
                                },
                                contentDescription = label
                            )
                        },
                        label = { Text(label, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        // أنيميشن ناعم عند التنقل بين الشاشات
        AnimatedContent(targetState = selectedTab, label = "TabTransition") { targetTab ->
            when (targetTab) {
                0 -> AlarmTab(alarmRepository, aiEngine, Modifier.padding(padding))
                1 -> AIChatTab(aiEngine, Modifier.padding(padding))
                else -> ExamTab(aiEngine, Modifier.padding(padding))
            }
        }
    }
}

@Composable
fun AlarmTab(alarmRepository: AlarmRepository, aiEngine: AIEngine, modifier: Modifier = Modifier) {
    var alarms by remember { mutableStateOf<List<AlarmEntity>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var scheduleResult by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, alarmRepository) {
        val observer = androidx.lifecycle.Observer<List<AlarmEntity>> { alarms = it ?: emptyList() }
        alarmRepository.activeAlarms.observe(lifecycleOwner, observer)
        onDispose { alarmRepository.activeAlarms.removeObserver(observer) }
    }

    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("المنبهات الذكية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة")
            }
        }

        // كارت الذكاء الاصطناعي بتصميم متدرج (Gradient) احترافي
        Card(
            Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                Modifier.background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF42A5F5), Color(0xFF1E88E5))
                    )
                ).padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                        Text("مساعدك الذكي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text("دع الذكاء الاصطناعي ينظم جدولك بأفضل طريقة ممكنة.", color = Color.White.copy(alpha = 0.9f))
                    Button(
                        onClick = {
                            scope.launch {
                                scheduleResult = aiEngine.autoSchedule("جدول").candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("جدولة تلقائية", fontWeight = FontWeight.Bold)
                    }
                    AnimatedVisibility(visible = scheduleResult.isNotBlank()) {
                        Text(scheduleResult, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (alarms.isEmpty()) {
            EmptyStateCard()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(alarm, 
                        onToggle = { updated -> scope.launch(Dispatchers.IO) { alarmRepository.updateAlarm(updated) } },
                        onDelete = { deleted -> scope.launch(Dispatchers.IO) { alarmRepository.removeAlarm(deleted) } }
                    )
                }
            }
        }
    }

    if (showDialog) {
        AddAlarmDialog(onDismiss = { showDialog = false }, onConfirm = { alarm ->
            scope.launch(Dispatchers.IO) { alarmRepository.addAlarm(alarm) }
            showDialog = false
        })
    }
}

@Composable
fun AlarmCard(alarm: AlarmEntity, onToggle: (AlarmEntity) -> Unit, onDelete: (AlarmEntity) -> Unit) {
    Card(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (alarm.isActive) Color.White else Color(0xFFF0F0F0))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(alarm.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                alarm.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(TimeUtil.format(alarm.triggerAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Switch(checked = alarm.isActive, onCheckedChange = { onToggle(alarm.copy(isActive = it)) })
            IconButton(onClick = { onDelete(alarm) }) {
                Icon(Icons.Outlined.Delete, contentDescription = "حذف", tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun EmptyStateCard() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(100.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE3F2FD)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AlarmOff, contentDescription = null, modifier = Modifier.size(50.dp), tint = Color(0xFF90CAF9))
        }
        Text("لا توجد منبهات نشطة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text("اضغط على زر + لإضافة منبه جديد وبدء يومك بنشاط.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
    }
}

@Composable
fun AddAlarmDialog(onDismiss: () -> Unit, onConfirm: (AlarmEntity) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("إضافة منبه جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("الوصف (اختياري)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(AlarmEntity(title = title.trim(), description = description.ifBlank { null }, triggerAt = TimeUtil.nowMillis() + 3600000L)) }, enabled = title.isNotBlank(), shape = RoundedCornerShape(12.dp)) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = Color.Gray) } }
    )
}

data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun AIChatTab(aiEngine: AIEngine, modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("دردشة الذكاء الاصطناعي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
        
        Card(
            Modifier.fillMaxWidth().weight(1f).shadow(4.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages) { msg -> ChatBubble(msg) }
                if (loading) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Card(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    Text("جاري التفكير...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("اكتب سؤالك هنا...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.LightGray, focusedBorderColor = MaterialTheme.colorScheme.primary)
            )
            FloatingActionButton(
                onClick = {
                    val query = input.trim()
                    if (query.isNotEmpty() && !loading) {
                        messages = messages + ChatMessage(query, true)
                        input = ""
                        loading = true
                        scope.launch {
                            try {
                                val response = aiEngine.explain("عام", query)
                                val reply = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "عذراً، لم أفهم."
                                messages = messages + ChatMessage(reply, false)
                            } catch (e: Exception) {
                                messages = messages + ChatMessage("حدث خطأ.", false)
                            } finally { loading = false }
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) { Icon(Icons.Default.Send, contentDescription = "إرسال") }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start) {
        Card(
            Modifier.widthIn(max = 280.dp).shadow(2.dp, RoundedCornerShape(16.dp)),
            shape = if (message.isUser) RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) MaterialTheme.colorScheme.primary else Color.White,
                contentColor = if (message.isUser) Color.White else Color.Black
            )
        ) {
            Text(message.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ExamTab(aiEngine: AIEngine, modifier: Modifier = Modifier) {
    var subject by remember { mutableStateOf("") }
    var years by remember { mutableStateOf<List<Int>>(emptyList()) }
    var types by remember { mutableStateOf<List<String>>(emptyList()) }
    var result by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("مولد الامتحانات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))

        Card(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("المادة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("فيزياء", "رياضيات", "كيمياء", "أحياء", "إنجليزي", "عربي")) { s ->
                        FilterChip(
                            selected = subject == s, onClick = { subject = s }, label = { Text(s) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("السنوات", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2022, 2023, 2024).forEach { year ->
                        FilterChip(
                            selected = years.contains(year), onClick = { years = if (year in years) years - year else years + year }, label = { Text(year.toString()) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                generating = true
                scope.launch {
                    try {
                        val response = aiEngine.generateExam(subject, years, types)
                        result = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "لا توجد نتيجة."
                    } catch (e: Exception) { result = "فشل التوليد." } finally { generating = false }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp).shadow(8.dp, RoundedCornerShape(16.dp)),
            enabled = subject.isNotBlank() && years.isNotEmpty() && !generating,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (generating) { CircularProgressIndicator(Modifier.size(24.dp), color = Color.White) } 
            else { Icon(Icons.Default.Description, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("توليد الامتحان", fontWeight = FontWeight.Bold) }
        }

        if (result.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("النتيجة", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text(result, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
