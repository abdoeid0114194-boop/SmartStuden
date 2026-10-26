@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartstudent.ui

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.Application
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smartstudent.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

// =========================================================================================
// 1. كلاسات البيانات (Data Classes) - تم تفصيلها بالكامل
// =========================================================================================

/**
 * يمثل درساً واحداً داخل باب معين في المنهج الدراسي.
 */
data class Lesson(
    val id: Long,
    val title: String
)

/**
 * يمثل فصلاً (أو باباً) في مادة دراسية.
 */
data class Chapter(
    val id: Long,
    val title: String,
    val lessons: List<Lesson>
)

/**
 * يمثل رسالة دردشة بين المستخدم والذكاء الاصطناعي.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val imageBitmap: Bitmap? = null
)

/**
 * يمثل سؤالاً في الاختبار التفاعلي.
 */
data class QuizQuestion(
    val text: String,
    val options: List<String>,
    val correctIdx: Int,
    val explanation: String
)

/**
 * يمثل مهمة دراسية أو منبهاً.
 */
data class StudyTask(
    val id: Long,
    val title: String,
    val timeStr: String,
    val triggerAt: Long,
    val done: Boolean = false
)

// =========================================================================================
// 2. مستودع المناهج (Curriculum Repository) - بيانات تجريبية شاملة
// =========================================================================================

interface CurriculumRepository {
    suspend fun getChapters(subject: String): List<Chapter>
}

class MockCurriculumRepository : CurriculumRepository {
    override suspend fun getChapters(subject: String): List<Chapter> {
        delay(300) // محاكاة وقت التحميل
        return when (subject) {
            "فيزياء" -> listOf(
                Chapter(1, "الباب الأول: الحركة", listOf(Lesson(1, "السرعة"), Lesson(2, "التسارع"), Lesson(3, "معادلات الحركة"))),
                Chapter(2, "الباب الثاني: الطاقة", listOf(Lesson(4, "الشغل"), Lesson(5, "الطاقة الحركية"), Lesson(6, "الطاقة الكامنة"))),
                Chapter(3, "الباب الثالث: الكهرباء", listOf(Lesson(7, "التيار الكهربي"), Lesson(8, "قانون أوم"), Lesson(9, "المكثفات")))
            )
            "كيمياء" -> listOf(
                Chapter(4, "الباب الأول: الكيمياء الحرارية", listOf(Lesson(10, "المحتوى الحراري"), Lesson(11, "حرارة التكوين"))),
                Chapter(5, "الباب الثاني: الاتزان", listOf(Lesson(12, "الاتزان الكيميائي"), Lesson(13, "ثابت الاتزان"))),
                Chapter(6, "الباب الثالث: الأحماض والقواعد", listOf(Lesson(14, "مفهوم الأحماض"), Lesson(15, "الرقم الهيدروجيني")))
            )
            "أحياء" -> listOf(
                Chapter(7, "الباب الأول: الدعامة والحركة", listOf(Lesson(16, "الدعامة في النبات"), Lesson(17, "الحركة في الإنسان"))),
                Chapter(8, "الباب الثاني: التكاثر", listOf(Lesson(18, "التكاثر في النبات"), Lesson(19, "التكاثر في الإنسان"))),
                Chapter(9, "الباب الثالث: الوراثة", listOf(Lesson(20, "قوانين مندل"), Lesson(21, "الوراثة الجنسية")))
            )
            "رياضيات" -> listOf(
                Chapter(10, "الباب الأول: الجبر", listOf(Lesson(22, "المعادلات"), Lesson(23, "المتباينات"))),
                Chapter(11, "الباب الثاني: التفاضل", listOf(Lesson(24, "النهايات"), Lesson(25, "الاشتقاق"))),
                Chapter(12, "الباب الثالث: التكامل", listOf(Lesson(26, "التكامل غير المحدود"), Lesson(27, "التكامل المحدود")))
            )
            "لغة عربية" -> listOf(
                Chapter(13, "الباب الأول: النحو", listOf(Lesson(28, "المبتدأ والخبر"), Lesson(29, "كان وأخواتها"))),
                Chapter(14, "الباب الثاني: البلاغة", listOf(Lesson(30, "التشبيه"), Lesson(31, "الاستعارة"))),
                Chapter(15, "الباب الثالث: الأدب", listOf(Lesson(32, "مدارس الشعر"), Lesson(33, "النثر")))
            )
            "لغة إنجليزية" -> listOf(
                Chapter(16, "Unit 1: Grammar", listOf(Lesson(34, "Present Simple"), Lesson(35, "Past Simple"))),
                Chapter(17, "Unit 2: Vocabulary", listOf(Lesson(36, "Phrasal Verbs"), Lesson(37, "Collocations")))
            )
            else -> emptyList()
        }
    }
}

// =========================================================================================
// 3. محرك الذكاء الاصطناعي (Gemini AI Engine) - مع النماذج الاحتياطية
// =========================================================================================

data class AIResponse(
    val candidates: List<Candidate>,
    val isError: Boolean = false
)

data class Candidate(
    val content: Content
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String
)

fun aiOk(text: String) = AIResponse(listOf(Candidate(Content(listOf(Part(text))))))
fun aiError(text: String) = AIResponse(listOf(Candidate(Content(listOf(Part(text))))), isError = true)
fun AIResponse.text(): String = candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()

suspend fun safeAi(block: suspend () -> AIResponse): AIResponse = try {
    block()
} catch (e: Exception) {
    aiError("حدث خطأ غير متوقع: ${e.message}")
}

class GeminiAIEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey: String = BuildConfig.GEMINI_API_KEY.trim()
    
    private val fallbackModels = listOf(
        "gemini-1.5-flash-latest",
        "gemini-1.5-pro-latest",
        "gemini-1.5-flash",
        "gemini-pro"
    )

    private suspend fun makeRequest(payload: JSONObject): AIResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext aiError("مفتاح API غير موجود.")
        
        var lastError = ""
        
        for (model in fallbackModels) {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val root = JSONObject(raw)
                        val candidates = root.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val txt = candidates.getJSONObject(0)
                                .optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.optJSONObject(0)
                                ?.optString("text").orEmpty()
                            if (txt.isNotBlank()) return@withContext aiOk(txt)
                        }
                    } else {
                        lastError = "خطأ ${resp.code} في موديل $model"
                    }
                }
            } catch (e: Exception) {
                lastError = "فشل الاتصال بموديل $model: ${e.message}"
            }
        }
        
        return@withContext aiError("فشل الاتصال بعدة محاولات.\n$lastError")
    }

    suspend fun chat(history: List<ChatMessage>, imageBase64: String? = null): AIResponse {
        val contents = JSONArray()
        
        history.takeLast(4).forEach { msg ->
            val parts = JSONArray().put(JSONObject().put("text", msg.text))
            val messageObject = JSONObject()
                .put("role", if (msg.isUser) "user" else "model")
                .put("parts", parts)
            contents.put(messageObject)
        }
        
        if (imageBase64 != null) {
            val parts = JSONArray().apply {
                put(JSONObject().put("text", "حلل هذه الصورة وساعدني."))
                put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", imageBase64)))
            }
            contents.put(JSONObject().put("role", "user").put("parts", parts))
        }
        
        return makeRequest(JSONObject().put("contents", contents))
    }

    suspend fun generateInteractiveExam(grade: String, system: String, stream: String, subject: String, topics: String): AIResponse {
        val prompt = """
            أنت معلم خبير.
            اكتب اختباراً تفاعلياً من 5 أسئلة لمادة $subject.
            (الصف: $grade، النظام: $system، الشعبة: $stream).
            المواضيع المحددة: $topics.
            
            التنسيق المطلوب:
            Q: [سؤال]
            O1: [خيار1]
            O2: [خيار2]
            O3: [خيار3]
            O4: [خيار4]
            C: [رقم الصح 1-4]
            E: [شرح]
        """.trimIndent()
        
        val payload = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        return makeRequest(payload)
    }
}

// =========================================================================================
// 4. الثيم والألوان (Theme)
// =========================================================================================

private val Indigo = Color(0xFF4F46E5)
private val BrandGradient = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Indigo))

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Color(0xFFEC4899),
    background = Color(0xFFF5F6FA),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    secondary = Color(0xFFF472B6),
    background = Color(0xFF0F1117),
    surface = Color(0xFF181B24)
)

@Composable
fun SmartStudentTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}

// =========================================================================================
// 5. نظام المنبهات في الخلفية (Alarms)
// =========================================================================================

class MyApp : Application() {
    companion object {
        lateinit var instance: MyApp
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "مهمة"
        val prefs = context.getSharedPreferences("smartstudent_prefs", Context.MODE_PRIVATE)
        val uri = Uri.parse(prefs.getString("ringtone", null) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString())
        
        try {
            RingtoneManager.getRingtone(context, uri).play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel("alarm", "منبه", NotificationManager.IMPORTANCE_HIGH))
        }
        
        NotificationManagerCompat.from(context).notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(context, "alarm")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("حان الوقت!")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }
}

// =========================================================================================
// 6. التطبيق الرئيسي والواجهة (Main Activity & UI)
// =========================================================================================

class MainActivity : ComponentActivity() {
    private val aiEngine = GeminiAIEngine()
    private val repo = MockCurriculumRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        val prefs = getSharedPreferences("smartstudent_prefs", Context.MODE_PRIVATE)
        
        setContent {
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
            SmartStudentTheme(darkTheme = dark) {
                MainScreen(
                    aiEngine = aiEngine,
                    repo = repo,
                    darkTheme = dark,
                    onDarkThemeChange = {
                        dark = it
                        prefs.edit().putBoolean("dark", it).apply()
                    },
                    prefs = prefs
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    aiEngine: GeminiAIEngine,
    repo: MockCurriculumRepository,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    prefs: SharedPreferences
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("الرئيسية", "دردشة AI", "الامتحانات", "حسابي")
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Chat, Icons.Filled.Description, Icons.Filled.Person)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("الطالب الذكي", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icons[index], contentDescription = title) },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeTab(prefs)
                1 -> AIChatTab(aiEngine)
                2 -> ExamWizardTab(aiEngine, repo)
                else -> ProfileTab(darkTheme, onDarkThemeChange, prefs)
            }
        }
    }
}

@Composable
fun HomeTab(prefs: SharedPreferences) {
    val ctx = LocalContext.current
    val tasks = remember { mutableStateListOf<StudyTask>() }
    var showDialog by remember { mutableStateOf(false) }
    
    var title by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    var timeText by remember { mutableStateOf("") }
    val calendar = Calendar.getInstance()

    val datePicker = remember {
        DatePickerDialog(ctx, { _, y, m, d ->
            calendar.set(y, m, d)
            dateMillis = calendar.timeInMillis
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    }
    
    val timePicker = remember {
        TimePickerDialog(ctx, { _, h, min ->
            calendar.set(Calendar.HOUR_OF_DAY, h)
            calendar.set(Calendar.MINUTE, min)
            dateMillis = calendar.timeInMillis
            timeText = String.format("%02d:%02d", h, min)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("مهمة جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("اسم المهمة") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { datePicker.show() }) { Text(if (dateMillis != null) "التاريخ: تم" else "التاريخ") }
                        OutlinedButton(onClick = { timePicker.show() }) { Text(if (timeText.isNotBlank()) "الوقت: $timeText" else "الوقت") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank() && dateMillis != null) {
                        val t = StudyTask(System.currentTimeMillis(), title, "يوم $timeText", dateMillis!!)
                        tasks.add(t)
                        setAlarm(ctx, t)
                        showDialog = false
                    } else {
                        Toast.makeText(ctx, "أكمل البيانات أولاً", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("إضافة") }
            }
        )
    }

    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = { showDialog = true }) { Icon(Icons.Filled.Add, null) } },
        containerColor = Color.Transparent
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("أهلاً بك 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            
            Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
                Column(Modifier.background(BrandGradient).padding(20.dp)) {
                    Text("مساعدك الذكي", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("ذاكر بذكاء ووفر وقتك.", color = Color.White.copy(alpha = 0.9f))
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Text("مهامك القادمة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { task ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = task.done, onCheckedChange = { checked ->
                                val index = tasks.indexOfFirst { it.id == task.id }
                                if (index >= 0) tasks[index] = task.copy(done = checked)
                            })
                            Column(Modifier.weight(1f)) {
                                Text(task.title, fontWeight = FontWeight.Bold, textDecoration = if (task.done) TextDecoration.LineThrough else null)
                                Text(task.timeStr, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            IconButton(onClick = { tasks.remove(task) }) { Icon(Icons.Filled.Delete, null, tint = Color.Red) }
                        }
                    }
                }
            }
        }
    }
}

fun setAlarm(ctx: Context, task: StudyTask) {
    val intent = Intent(ctx, AlarmReceiver::class.java).putExtra("title", task.title)
    val pending = PendingIntent.getBroadcast(ctx, task.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, pending)
    } else {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, pending)
    }
}

@Composable
fun AIChatTab(aiEngine: GeminiAIEngine) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var imgB64 by remember { mutableStateOf<String?>(null) }
    var imgBmp by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == ComponentActivity.RESULT_OK) {
            input = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
        }
    }
    
    val imgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bmp = BitmapFactory.decodeStream(ctx.contentResolver.openInputStream(it))
            imgBmp = bmp
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
            imgB64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { msg ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start) {
                    Card(colors = CardDefaults.cardColors(containerColor = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(12.dp)) {
                            msg.imageBitmap?.let {
                                Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            }
                            Text(msg.text, color = if (msg.isUser) Color.White else Color.Black)
                        }
                    }
                }
            }
            if (loading) {
                item { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
        }
        
        if (imgBmp != null) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(bitmap = imgBmp!!.asImageBitmap(), contentDescription = "Preview", modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { imgBmp = null; imgB64 = null }) { Icon(Icons.Filled.Close, "إلغاء", tint = Color.Red) }
            }
        }
        
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { imgLauncher.launch("image/*") }) { Icon(Icons.Filled.Image, "صورة", tint = MaterialTheme.colorScheme.primary) }
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("اكتب...") }, shape = RoundedCornerShape(24.dp))
            IconButton(onClick = { speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")) }) { Icon(Icons.Filled.Mic, "تسجيل", tint = MaterialTheme.colorScheme.secondary) }
            IconButton(onClick = {
                if ((input.isNotBlank() || imgB64 != null) && !loading) {
                    val q = if (input.isBlank()) "حلل الصورة" else input
                    messages = messages + ChatMessage(text = q, isUser = true, imageBitmap = imgBmp)
                    val base64ToSend = imgB64
                    input = ""; imgBmp = null; imgB64 = null; loading = true
                    scope.launch {
                        val res = safeAi { aiEngine.chat(messages, base64ToSend) }
                        messages = messages + ChatMessage(text = res.text(), isUser = false, isError = res.isError)
                        loading = false
                    }
                }
            }) { Icon(Icons.Filled.Send, "إرسال") }
        }
    }
}

@Composable
fun ExamWizardTab(aiEngine: GeminiAIEngine, repo: MockCurriculumRepository) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var grade by remember { mutableStateOf("") }
    var system by remember { mutableStateOf("") }
    var stream by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var questions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var qIndex by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (errorMsg.isNotBlank()) {
            Text(errorMsg, color = Color.Red)
            TextButton(onClick = { errorMsg = "" }) { Text("إغلاق") }
        }
        
        when (step) {
            0 -> {
                Text("اختر الصف الدراسي", style = MaterialTheme.typography.headlineSmall)
                listOf("الأول الثانوي", "الثاني الثانوي", "الثالث الثانوي").forEach { g ->
                    Card(Modifier.fillMaxWidth().clickable { grade = g; step = 1 }) { Text(g, Modifier.padding(16.dp)) }
                }
            }
            1 -> {
                Text("اختر النظام", style = MaterialTheme.typography.headlineSmall)
                listOf("عام", "لغات", "أزهر").forEach { s ->
                    Card(Modifier.fillMaxWidth().clickable { system = s; step = 2 }) { Text(s, Modifier.padding(16.dp)) }
                }
            }
            2 -> {
                Text("اختر الشعبة", style = MaterialTheme.typography.headlineSmall)
                listOf("علمي علوم", "علمي رياضة", "أدبي").forEach { st ->
                    Card(Modifier.fillMaxWidth().clickable { stream = st; step = 3 }) { Text(st, Modifier.padding(16.dp)) }
                }
            }
            3 -> {
                Text("اختر المادة", style = MaterialTheme.typography.headlineSmall)
                listOf("فيزياء", "كيمياء", "أحياء", "رياضيات", "لغة عربية", "لغة إنجليزية").forEach { sub ->
                    Card(Modifier.fillMaxWidth().clickable {
                        subject = sub
                        scope.launch {
                            isLoading = true
                            chapters = repo.getChapters(sub)
                            isLoading = false
                            step = 4
                        }
                    }) { Text(sub, Modifier.padding(16.dp)) }
                }
                if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
            4 -> {
                Text("اختر الدروس", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { selectedIds = chapters.flatMap { it.lessons }.map { it.id }.toSet() }, modifier = Modifier.fillMaxWidth()) {
                    Text("تحديد المنهج كله")
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(chapters) { ch ->
                        var expanded by remember { mutableStateOf(false) }
                        val chIds = ch.lessons.map { it.id }.toSet()
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = selectedIds.containsAll(chIds), onCheckedChange = { c ->
                                        selectedIds = if (c) selectedIds + chIds else selectedIds - chIds
                                    })
                                    Text(ch.title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null) }
                                }
                                if (expanded) {
                                    ch.lessons.forEach { l ->
                                        Row(Modifier.padding(start = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = selectedIds.contains(l.id), onCheckedChange = { c ->
                                                selectedIds = if (c) selectedIds + l.id else selectedIds - l.id
                                            })
                                            Text(l.title)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick = {
                    isLoading = true
                    errorMsg = ""
                    val topics = chapters.flatMap { it.lessons }.filter { selectedIds.contains(it.id) }.joinToString(", ") { it.title }
                    scope.launch {
                        val r = aiEngine.generateInteractiveExam(grade, system, stream, subject, topics)
                        if (!r.isError) {
                            questions = parseQuiz(r.text())
                            if (questions.isNotEmpty()) {
                                step = 5; qIndex = 0; score = 0
                            } else {
                                errorMsg = "الرد غير مفهوم. جرب تاني."
                            }
                        } else {
                            errorMsg = r.text()
                        }
                        isLoading = false
                    }
                }, enabled = selectedIds.isNotEmpty() && !isLoading, modifier = Modifier.fillMaxWidth()) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(24.dp)) else Text("توليد الامتحان")
                }
            }
            5 -> {
                if (questions.isNotEmpty() && qIndex < questions.size) {
                    val q = questions[qIndex]
                    LinearProgressIndicator(progress = { (qIndex + 1f) / questions.size }, modifier = Modifier.fillMaxWidth())
                    Text(q.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                    q.options.forEachIndexed { i, opt ->
                        val color = when {
                            selectedAnswer == null -> MaterialTheme.colorScheme.surface
                            i == q.correctIdx -> Color(0xFFD1FAE5)
                            i == selectedAnswer -> Color(0xFFFEE2E2)
                            else -> MaterialTheme.colorScheme.surface
                        }
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = selectedAnswer == null) { selectedAnswer = i }, colors = CardDefaults.cardColors(containerColor = color)) {
                            Text(opt, Modifier.padding(16.dp))
                        }
                    }
                    if (selectedAnswer != null) {
                        Text(text = "الشرح: " + q.explanation, modifier = Modifier.padding(8.dp), color = Color.Gray)
                        Button(onClick = {
                            if (selectedAnswer == q.correctIdx) {
                                score++
                            }
                            if (qIndex + 1 < questions.size) {
                                qIndex++; selectedAnswer = null
                            } else {
                                step = 6
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("التالي") }
                    }
                }
            }
            6 -> {
                Text("النتيجة: $score / ${questions.size}", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = { step = 0 }) { Text("اختبار جديد") }
            }
        }
    }
}

fun parseQuiz(text: String): List<QuizQuestion> {
    val list = mutableListOf<QuizQuestion>()
    val cleanText = text.replace("```json", "").replace("```", "").trim()
    val blocks = cleanText.split("Q:").drop(1)
    for (block in blocks) {
        val lines = block.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) continue
        val qText = lines[0].trim()
        val opts = mutableListOf<String>()
        var correct = -1
        var exp = ""
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("O1:")) opts.add(trimmed.removePrefix("O1:").trim())
            if (trimmed.startsWith("O2:")) opts.add(trimmed.removePrefix("O2:").trim())
            if (trimmed.startsWith("O3:")) opts.add(trimmed.removePrefix("O3:").trim())
            if (trimmed.startsWith("O4:")) opts.add(trimmed.removePrefix("O4:").trim())
            if (trimmed.startsWith("C:")) correct = (trimmed.removePrefix("C:").trim().toIntOrNull() ?: 1) - 1
            if (trimmed.startsWith("E:")) exp = trimmed.removePrefix("E:").trim()
        }
        if (opts.size == 4 && correct >= 0 && correct <= 3) {
            list.add(QuizQuestion(qText, opts, correct, exp))
        }
    }
    return list
}

@Composable
fun ProfileTab(dark: Boolean, onDark: (Boolean) -> Unit, prefs: SharedPreferences) {
    val ctx = LocalContext.current
    var ringtone by remember { mutableStateOf(prefs.getString("ringtone", "افتراضية")) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == ComponentActivity.RESULT_OK) {
            prefs.edit().putString("ringtone", res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString()).apply()
            ringtone = "تم التغيير"
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("الإعدادات", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("الوضع الليلي")
            Switch(checked = dark, onCheckedChange = onDark)
        }
        Card(Modifier.fillMaxWidth().clickable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!am.canScheduleExactAlarms()) {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    Toast.makeText(ctx, "الرجاء السماح بالمنبهات الدقيقة", Toast.LENGTH_SHORT).show()
                }
            }
            picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM))
        }) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("نغمة المنبه")
                Text(ringtone ?: "افتراضية")
            }
        }
    }
}
