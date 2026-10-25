package com.smartstudent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Calendar
import com.smartstudent.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.JsonParser

// --- كلاسات البيانات والمحركات (كما هي بدون تغيير) ---
data class AlarmEntity(
    val id: Long = 0L, val title: String, val description: String? = null,
    val triggerAt: Long, val repeatInterval: Long? = null, val isActive: Boolean = true
)

interface AlarmRepository {
    val activeAlarms: LiveData<List<AlarmEntity>>
    suspend fun addAlarm(alarm: AlarmEntity)
    suspend fun removeAlarm(alarm: AlarmEntity)
    suspend fun updateAlarm(alarm: AlarmEntity)
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

class GeminiAIEngine : AIEngine {
    private val client = OkHttpClient()
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private suspend fun callGemini(prompt: String): AIResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            return@withContext AIResponse(listOf(Candidate(Content(listOf(Part("المفتاح السري غير موجود."))))))
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val jsonPayload = """{"contents": [{"parts":[{"text": "$prompt"}]}]}""".trimIndent()
        val request = Request.Builder().url(url).post(jsonPayload.toRequestBody("application/json".toMediaType())).build()
        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext AIResponse(listOf(Candidate(Content(listOf(Part("خطأ ($response.code): $responseBody"))))))
            val jsonObject = JsonParser.parseString(responseBody).asJsonObject
            val candidates = jsonObject.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val text = candidates[0].asJsonObject.getAsJsonObject("content").getAsJsonArray("parts")[0].asJsonObject.get("text").asString
                return@withContext AIResponse(listOf(Candidate(Content(listOf(Part(text))))))
            }
            return@withContext AIResponse(listOf(Candidate(Content(listOf(Part("لم يتم تلقي رد."))))))
        } catch (e: Exception) {
            return@withContext AIResponse(listOf(Candidate(Content(listOf(Part("فشل الاتصال: ${e.localizedMessage}"))))))
        }
    }

    override suspend fun generateExam(subject: String, years: List<Int>, types: List<String>) =
        callGemini("أنت معلم. امتحان في $subject للسنوات ${years.joinToString()}، أنواع: ${types.joinToString()}. بالعربية.")

    override suspend fun explain(subject: String, topic: String, level: String) =
        callGemini("أنت مساعد ذكي. اشرح '$topic' في '$subject' ببساطة وبالعربية.")

    override suspend fun autoSchedule(schedule: String) =
        callGemini("اقترح جدولاً دراسياً متوازناً بناءً على: $schedule. بالعربية.")
}

object TimeUtil {
    fun nowMillis() = System.currentTimeMillis()
    fun format(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format("%02d-%02d-%d %02d:%02d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
}

// --- التصميم الجديد والاحترافي ---

class MainActivity : ComponentActivity() {
    private val alarmRepo: AlarmRepository = MockAlarmRepository()
    private val aiEngine: AIEngine = GeminiAIEngine()

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
                primary = Color(0xFF4F46E5), // أزرق بنفسجي احترافي
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE0E7FF),
                onPrimaryContainer = Color(0xFF312E81),
                secondary = Color(0xFFEC4899), // وردي حيوي
                background = Color(0xFFF3F4F6), // رمادي فاتح مريح
                surface = Color.White,
                onSurface = Color(0xFF111827)
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
    val tabs = listOf("الرئيسية", "دردشة AI", "الامتحانات", "حسابي")
    val tabIcons = listOf(Icons.Filled.Home, Icons.Filled.Chat, Icons.Filled.Description, Icons.Filled.Person)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الطالب الذكي", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Notifications, contentDescription = "التنبيهات") }
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
                tonalElevation = 12.dp,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tabIcons[index], contentDescription = label) },
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
        AnimatedContent(targetState = selectedTab, label = "TabTransition") { targetTab ->
            when (targetTab) {
                0 -> HomeTab(alarmRepository, aiEngine, Modifier.padding(padding))
                1 -> AIChatTab(aiEngine, Modifier.padding(padding))
                2 -> ExamTab(aiEngine, Modifier.padding(padding))
                else -> ProfileTab(Modifier.padding(padding))
            }
        }
    }
}

// --- الشاشة الرئيسية (Dashboard) ---
@Composable
fun HomeTab(alarmRepository: AlarmRepository, aiEngine: AIEngine, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var scheduleResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // الترحيب
        item {
            Column {
                Text("أهلاً بك مجدداً 👋", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Text("الطالب الذكي", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
        }

        // إحصائيات سريعة
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("ساعات الدراسة", "12h", Icons.Filled.Timer, Color(0xFF10B981), Modifier.weight(1f))
                StatCard("الامتحانات", "3", Icons.Filled.School, Color(0xFFF59E0B), Modifier.weight(1f))
            }
        }

        // كارت الذكاء الاصطناعي
        item {
            Card(
                Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    Modifier.background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF4F46E5)))).padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                            Text("مساعدك الذكي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("دع الذكاء الاصطناعي ينظم جدولك بأفضل طريقة.", color = Color.White.copy(alpha = 0.9f))
                        Button(
                            onClick = {
                                isLoading = true
                                scope.launch {
                                    scheduleResult = aiEngine.autoSchedule("جدول").candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
                                    isLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading
                        ) {
                            if(isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = Color(0xFF4F46E5))
                            else {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("جدولة تلقائية الآن", fontWeight = FontWeight.Bold)
                            }
                        }
                        AnimatedVisibility(visible = scheduleResult.isNotBlank()) {
                            Text(scheduleResult, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // قائمة المهام / المنبهات
        item {
            Text("مهامك القادمة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
        }

        item {
            Card(
                Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFFE0E7FF)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Book, contentDescription = null, tint = Color(0xFF4F46E5))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("مراجعة درس الفيزياء", fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                        Text("اليوم - 05:00 مساءً", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Filled.CheckCircle, contentDescription = "مكتمل", tint = Color(0xFF10B981))
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Card(
        modifier.shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
            Text(title, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

// --- شاشة حسابي (Profile) ---
@Composable
fun ProfileTab(modifier: Modifier = Modifier) {
    var isDarkTheme by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    Column(modifier.fillMaxSize().background(Color(0xFFF3F4F6)).verticalScroll(rememberScrollState())) {
        // هيدر البروفايل
        Box(
            Modifier.fillMaxWidth().height(200.dp).background(
                Brush.verticalGradient(listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6))),
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(Color.White).padding(4.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFE0E7FF)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(50.dp), tint = Color(0xFF4F46E5))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("عبد الرحمن", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text("طالب ثانوي", color = Color.White.copy(alpha = 0.8f))
            }
        }

        // الإعدادات
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileItem(Icons.Outlined.Settings, "الإعدادات العامة", "تعديل بيانات الحساب") {}
            ProfileItem(Icons.Outlined.Notifications, "التنبيهات", "تفعيل إشعارات المهام") {
                Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
            }
            ProfileItem(Icons.Outlined.DarkMode, "الوضع الليلي", "تغيير مظهر التطبيق") {
                Switch(checked = isDarkTheme, onCheckedChange = { isDarkTheme = it })
            }
            ProfileItem(Icons.Outlined.Info, "عن التطبيق", "الإصدار 1.0.0") {}
        }
    }
}

@Composable
fun ProfileItem(icon: ImageVector, title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    Card(
        Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(12.dp)).clickable { },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF3F4F6)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xFF4F46E5))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.Gray)
            }
        }
    }
}

// --- شاشة الدردشة (كما هي مع تحسينات بسيطة) ---
data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun AIChatTab(aiEngine: AIEngine, modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            Modifier.fillMaxWidth().weight(1f).shadow(4.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages) { msg -> ChatBubble(msg) }
                if (loading) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Card(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF4F46E5))
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
                value = input, onValueChange = { input = it }, placeholder = { Text("اكتب سؤالك هنا...") },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.LightGray, focusedBorderColor = Color(0xFF4F46E5))
            )
            FloatingActionButton(
                onClick = {
                    val query = input.trim()
                    if (query.isNotEmpty() && !loading) {
                        messages = messages + ChatMessage(query, true)
                        input = ""; loading = true
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
                containerColor = Color(0xFF4F46E5), contentColor = Color.White, shape = CircleShape
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
                containerColor = if (message.isUser) Color(0xFF4F46E5) else Color(0xFFF3F4F6),
                contentColor = if (message.isUser) Color.White else Color.Black
            )
        ) { Text(message.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge) }
    }
}

// --- شاشة الامتحانات (كما هي مع تحسينات بسيطة) ---
@Composable
fun ExamTab(aiEngine: AIEngine, modifier: Modifier = Modifier) {
    var subject by remember { mutableStateOf("") }
    var years by remember { mutableStateOf<List<Int>>(emptyList()) }
    var types by remember { mutableStateOf<List<String>>(emptyList()) }
    var result by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("المادة", fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("فيزياء", "رياضيات", "كيمياء", "أحياء", "إنجليزي")) { s ->
                        FilterChip(
                            selected = subject == s, onClick = { subject = s }, label = { Text(s) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF4F46E5), selectedLabelColor = Color.White)
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
            enabled = subject.isNotBlank() && !generating,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
        ) {
            if (generating) { CircularProgressIndicator(Modifier.size(24.dp), color = Color.White) } 
            else { Text("توليد الامتحان", fontWeight = FontWeight.Bold) }
        }

        if (result.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("النتيجة", fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                    Text(result, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
