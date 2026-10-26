@OptIn(ExperimentalMaterial3Api::class)
package com.smartstudent.ui

// ────────────────────────────────────────────────────────
// 1. الاستيرادات (Imports)
// ────────────────────────────────────────────────────────
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Base64
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smartstudent.BuildConfig
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.TimeUnit

// ────────────────────────────────────────────────────────
// 2. كلاسات البيانات (Data Models)
// ────────────────────────────────────────────────────────
data class Lesson(val id: Long, val title: String)
data class Chapter(val id: Long, val title: String, val lessons: List<Lesson>)
data class ChatMessage(val text: String, val isUser: Boolean, val isError: Boolean = false, val imageBitmap: Bitmap? = null)
data class QuizQuestion(val text: String, val options: List<String>, val correctIdx: Int, val explanation: String)
data class StudyTask(val id: Long, val title: String, val timeStr: String, val triggerAt: Long, val done: Boolean = false)
data class StudentProfile(val grade: String, val system: String, val stream: String)

// ────────────────────────────────────────────────────────
// 3. المناهج والمواد (Curriculum Data)
// ────────────────────────────────────────────────────────
interface CurriculumRepository { suspend fun getChapters(subject: String): List<Chapter> }
class MockCurriculumRepository : CurriculumRepository {
    override suspend fun getChapters(subject: String): List<Chapter> {
        delay(300)
        return when (subject) {
            "فيزياء" -> listOf(
                Chapter(1, "الباب الأول: الحركة", listOf(Lesson(1, "السرعة"), Lesson(2, "التسارع"), Lesson(3, "معادلات الحركة"))),
                Chapter(2, "الباب الثاني: الطاقة", listOf(Lesson(4, "الشغل"), Lesson(5, "الطاقة الحركية"), Lesson(6, "الطاقة الكامنة"))),
                Chapter(3, "الباب الثالث: الكهرباء", listOf(Lesson(7, "التيار الكهربي"), Lesson(8, "قانون أوم"), Lesson(9, "المكثفات")))
            )
            "كيمياء" -> listOf(
                Chapter(4, "الباب الأول: الكيمياء الحرارية", listOf(Lesson(10, "المحتوى الحراري"), Lesson(11, "حرارة التكوين"))),
                Chapter(5, "الباب الثاني: الاتزان", listOf(Lesson(12, "الاتزان الكيميائي"), Lesson(13, "ثابت الاتزان"))),
                Chapter(6, "الباب الثالث: الأحماض والقواعد", listOf(Lesson(14, "مفهوم الأحماض"), listOf(Lesson(15, "الرقم الهيدروجيني"))).flatten())
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

// ────────────────────────────────────────────────────────
// 4. محرك الذكاء الاصطناعي (Gemini AI)
// ────────────────────────────────────────────────────────
data class AIResponse(val candidates: List<Candidate>, val isError: Boolean = false)
data class Candidate(val content: Content)
data class Content(val parts: List<Part>)
data class Part(val text: String)

fun aiOk(text: String) = AIResponse(listOf(Candidate(Content(listOf(Part(text))))))
fun aiError(text: String) = AIResponse(listOf(Candidate(Content(listOf(Part(text))))), isError = true)
fun AIResponse.text(): String = candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
suspend fun safeAi(block: suspend () -> AIResponse): AIResponse = try { block() } catch (e: Exception) { aiError("خطأ: ${e.message}") }

class GeminiAIEngine {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val apiKey: String = BuildConfig.GEMINI_API_KEY.trim()
    
    private val fallbackModels = listOf("gemini-1.5-flash-latest", "gemini-1.5-pro-latest", "gemini-1.5-flash", "gemini-pro")

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
                            val txt = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
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

    suspend fun chat(history: List<ChatMessage>, profile: StudentProfile, imageBase64: String? = null): AIResponse {
        val contents = JSONArray()
        val systemPrompt = "أنت مساعد تعليمي ذكي وخبير في المنهج المصري للثانوية العامة. بيانات الطالب: الصف (${profile.grade})، النظام (${profile.system})، الشعبة (${profile.stream}). ساعد الطالب بدقة واحترافية."
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))

        history.takeLast(6).forEach { msg ->
            contents.put(JSONObject().put("role", if (msg.isUser) "user" else "model").put("parts", JSONArray().put(JSONObject().put("text", msg.text))))
        }
        if (imageBase64 != null) {
            val parts = JSONArray().apply {
                put(JSONObject().put("text", "حلل هذه الصورة وساعدني بناءً على كوني طالباً في ${profile.grade} (${profile.system} - ${profile.stream})."))
                put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", imageBase64)))
            }
            contents.put(JSONObject().put("role", "user").put("parts", parts))
        }
        return makeRequest(JSONObject().put("contents", contents))
    }

    suspend fun generateInteractiveExam(profile: StudentProfile, subject: String, topics: String): AIResponse {
        val prompt = """أنت معلم خبير في المنهج المصري. اكتب اختباراً تفاعلياً من 5 أسئلة لمادة $subject (الصف: ${profile.grade}، النظام: ${profile.system}، الشعبة: ${profile.stream}). المواضيع المطلوبة: $topics.
التنسيق حصرياً كالتالي لكل سؤال بدون أي زيادة:
Q: [نص السؤال بدقة]
O1: [الخيار الأول]
O2: [الخيار الثاني]
O3: [الخيار الثالث]
O4: [الخيار الرابع]
C: [رقم الإجابة الصحيحة رقماً من 1 إلى 4]
E: [شرح وافي ومبسط لسبب الإجابة ولماذا باقي الخيارات خاطئة]"""
        val payload = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        return makeRequest(payload)
    }
}

// ────────────────────────────────────────────────────────
// 5. الثيم (Theme)
// ────────────────────────────────────────────────────────
private val Indigo = Color(0xFF4F46E5)
private val BrandGradient = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Indigo))
private val LightColors = lightColorScheme(primary = Indigo, secondary = Color(0xFFEC4899), background = Color(0xFFF5F6FA), surface = Color.White)
private val DarkColors = darkColorScheme(primary = Color(0xFF818CF8), secondary = Color(0xFFF472B6), background = Color(0xFF0F1117), surface = Color(0xFF181B24))

@Composable
fun SmartStudentTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}

// ────────────────────────────────────────────────────────
// 6. التطبيق الرئيسي والنشاط (Main Activity)
// ────────────────────────────────────────────────────────
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
            var isProfileSet by remember { mutableStateOf(prefs.contains("grade")) }

            SmartStudentTheme(darkTheme = dark) {
                if (!isProfileSet) {
                    ProfileSetupScreen(prefs) { isProfileSet = true }
                } else {
                    MainScreen(aiEngine, repo, dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() }, prefs)
                }
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(prefs: SharedPreferences, onCompleted: () -> Unit) {
    var grade by remember { mutableStateOf("الثالث الثانوي") }
    var system by remember { mutableStateOf("عام") }
    var stream by remember { mutableStateOf("علمي علوم") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("مرحباً بك في الطالب الذكي 🎓", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("برجاء تحديد بياناتك الدراسية لكي نخصص لك التجربة بدقة:", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(Modifier.height(24.dp))
            
            Text("الصف الدراسي:", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("الأول الثانوي", "الثاني الثانوي", "الثالث الثانوي").forEach { g ->
                    FilterChip(selected = grade == g, onClick = { grade = g }, label = { Text(g) })
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("نظام التعليم:", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("عام", "لغات", "أزهر").forEach { s ->
                    FilterChip(selected = system == s, onClick = { system = s }, label = { Text(s) })
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("الشعبة / التخصص:", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("علمي علوم", "علمي رياضة", "أدبي").forEach { st ->
                    FilterChip(selected = stream == st, onClick = { stream = st }, label = { Text(st) })
                }
            }
            Spacer(Modifier.height(32.dp))

            Button(onClick = {
                prefs.edit().putString("grade", grade).putString("system", system).putString("stream", stream).apply()
                onCompleted()
            }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("حفظ والبدء", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun MainScreen(aiEngine: GeminiAIEngine, repo: MockCurriculumRepository, darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit, prefs: SharedPreferences) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("الرئيسية", "دردشة AI", "الامتحانات", "حسابي")
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Chat, Icons.Filled.Description, Icons.Filled.Person)
    
    val profile = StudentProfile(
        prefs.getString("grade", "الثالث الثانوي") ?: "الثالث الثانوي",
        prefs.getString("system", "عام") ?: "عام",
        prefs.getString("stream", "علمي علوم") ?: "علمي علوم"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("الطالب الذكي (${profile.grade} - ${profile.stream})", fontWeight = FontWeight.Bold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, l ->
                    NavigationBarItem(selected = selectedTab == i, onClick = { selectedTab = i }, icon = { Icon(icons[i], null) }, label = { Text(l) })
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeTab(prefs, Modifier.padding(padding))
            1 -> AIChatTab(aiEngine, profile, Modifier.padding(padding))
            2 -> ExamWizardTab(aiEngine, profile, repo, Modifier.padding(padding))
            else -> ProfileTab(darkTheme, onDarkThemeChange, prefs, Modifier.padding(padding))
        }
    }
}

// ────────────────────────────────────────────────────────
// 7. الشاشة الأولى: الرئيسية والمنبهات (Home & Alarms)
// ────────────────────────────────────────────────────────
@Composable
fun HomeTab(prefs: SharedPreferences, modifier: Modifier) {
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
            calendar.set(Calendar.SECOND, 0)
            dateMillis = calendar.timeInMillis
            val amPm = if (h >= 12) "مساءً" else "صباحاً"
            val displayH = if (h > 12) h - 12 else if (h == 0) 12 else h
            timeText = String.format("%02d:%02d %s", displayH, min, amPm)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("منبه ومهمة دراسية جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("اسم المادة أو المهمة (مثال: درس فيزياء)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { datePicker.show() }, modifier = Modifier.weight(1f)) { Text(if (dateMillis != null) "التاريخ محدد" else "اختر التاريخ") }
                        OutlinedButton(onClick = { timePicker.show() }, modifier = Modifier.weight(1f)) { Text(if (timeText.isNotBlank()) timeText else "اختر الوقت (AM/PM)") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank() && dateMillis != null) {
                        val t = StudyTask(System.currentTimeMillis(), title, timeText, dateMillis!!)
                        tasks.add(t)
                        setExactAlarm(ctx, t)
                        showDialog = false
                        title = ""
                        timeText = ""
                        dateMillis = null
                        Toast.makeText(ctx, "تم ضبط المنبه بنجاح ⏰", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "الرجاء إدخال العنوان وتحديد الوقت بدقة", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("ضبط المنبه") }
            }
        )
    }

    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = { showDialog = true }) { Icon(Icons.Filled.Add, null) } },
        containerColor = Color.Transparent
    ) { padding ->
        Column(modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("أهلاً بك يا بطل 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
                Column(Modifier.background(BrandGradient).padding(20.dp)) {
                    Text("منبه ومنظم المذاكرة الذكي", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("نظم مواعيد دروسك وسيعلمك المنبه في موعده تماماً.", color = Color.White.copy(alpha = 0.9f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("المنبهات والمواعيد القادمة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { task ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = task.done, onCheckedChange = { checked ->
                                val idx = tasks.indexOfFirst { it.id == task.id }
                                if (idx >= 0) tasks[idx] = task.copy(done = checked)
                            })
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(task.title, fontWeight = FontWeight.Bold, textDecoration = if (task.done) TextDecoration.LineThrough else null)
                                Text("الموعد: ${task.timeStr}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { tasks.remove(task) }) { Icon(Icons.Filled.Delete, null, tint = Color.Red) }
                        }
                    }
                }
            }
        }
    }
}

fun setExactAlarm(ctx: Context, task: StudyTask) {
    val intent = Intent(ctx, AlarmReceiver::class.java).putExtra("title", task.title)
    val pending = PendingIntent.getBroadcast(ctx, task.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, pending)
    } else {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, pending)
    }
}

// ────────────────────────────────────────────────────────
// 8. الشاشة الثانية: الدردشة بالصوت والصور (AI Chat)
// ────────────────────────────────────────────────────────
@Composable
fun AIChatTab(aiEngine: GeminiAIEngine, profile: StudentProfile, modifier: Modifier) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var imgB64 by remember { mutableStateOf<String?>(null) }
    var imgBmp by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) input = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
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

    Column(modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { msg ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start) {
                    Card(colors = CardDefaults.cardColors(containerColor = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(12.dp)) {
                            msg.imageBitmap?.let { androidx.compose.foundation.Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp))) }
                            Text(msg.text, color = if (msg.isUser) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (loading) item { CircularProgressIndicator(Modifier.size(24.dp).padding(8.dp)) }
        }
        if (imgBmp != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("تم إرفاق صورة للسؤال", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { imgBmp = null; imgB64 = null }) { Text("إلغاء الصورة", color = Color.Red) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { imgLauncher.launch("image/*") }) { Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.primary) }
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("اسأل الذكاء الاصطناعي في منهجك...") })
            IconButton(onClick = { speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")) }) { Icon(Icons.Filled.Mic, null, tint = MaterialTheme.colorScheme.secondary) }
            IconButton(onClick = {
                if ((input.isNotBlank() || imgB64 != null) && !loading) {
                    val q = if (input.isBlank()) "اشرح لي هذه المسألة أو السؤال في الصورة" else input
                    messages = messages + ChatMessage(q, true, imageBitmap = imgBmp)
                    val base64 = imgB64; input = ""; imgBmp = null; imgB64 = null; loading = true
                    scope.launch {
                        val r = safeAi { aiEngine.chat(messages, profile, base64) }
                        messages = messages + ChatMessage(r.text(), false, r.isError)
                        loading = false
                    }
                }
            }) { Icon(Icons.Filled.Send, null, tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

// ────────────────────────────────────────────────────────
// 9. الشاشة الثالثة: الامتحانات التفاعلية (Exam Wizard & Quiz UI)
// ────────────────────────────────────────────────────────
@Composable
fun ExamWizardTab(aiEngine: GeminiAIEngine, profile: StudentProfile, repo: MockCurriculumRepository, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) } 
    var subject by remember { mutableStateOf("") }
    
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    var questions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var qIndex by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (errorMsg.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(errorMsg, color = Color.Red, modifier = Modifier.weight(1f))
                    TextButton(onClick = { errorMsg = "" }) { Text("إغلاق", color = Color.Red) }
                }
            }
        }
        
        when (step) {
            0 -> {
                Text("اختر المادة الدراسية للابتعاث والاختبار 📚", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                listOf("فيزياء", "كيمياء", "أحياء", "رياضيات", "لغة عربية", "لغة إنجليزية").forEach { sub ->
                    Card(Modifier.fillMaxWidth().clickable { 
                        subject = sub
                        scope.launch {
                            isLoading = true
                            chapters = repo.getChapters(sub)
                            isLoading = false
                            step = 1
                        }
                    }, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Book, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text(sub, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (isLoading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            1 -> {
                Text("اختر الفصول أو الدروس للإختبار 🎯", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Button(onClick = { selectedIds = chapters.flatMap { it.lessons }.map { it.id }.toSet() }, modifier = Modifier.fillMaxWidth()) {
                    Text("تحديد المنهج بالكامل (شامل)")
                }
                
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chapters) { ch ->
                        var expanded by remember { mutableStateOf(false) }
                        val chIds = ch.lessons.map { it.id }.toSet()
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        val r = aiEngine.generateInteractiveExam(profile, subject, topics)
                        if (!r.isError) {
                            questions = parseQuiz(r.text())
                            if (questions.isNotEmpty()) {
                                step = 2; qIndex = 0; score = 0
                            } else {
                                errorMsg = "عذراً، لم يقم الذكاء الاصطناعي بتوليد الأسئلة بشكل صحيح. جرب مرة أخرى."
                            }
                        } else {
                            errorMsg = r.text()
                        }
                        isLoading = false
                    }
                }, enabled = selectedIds.isNotEmpty() && !isLoading, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White) else Text("توليد واختبار الذكاء الاصطناعي", fontWeight = FontWeight.Bold)
                }
            }
            2 -> {
                if (questions.isNotEmpty() && qIndex < questions.size) {
                    val q = questions[qIndex]
                    val optionLetters = listOf("A", "B", "C", "D")

                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("السؤال ${qIndex + 1} من ${questions.size}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            LinearProgressIndicator(progress = { (qIndex + 1f) / questions.size }, modifier = Modifier.width(120.dp).height(8.dp).clip(RoundedCornerShape(4.dp)))
                        }

                        Text(q.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(q.options.indices) { i ->
                                val optText = q.options[i]
                                val letter = optionLetters.getOrElse(i) { "A" }
                                
                                val cardColor = when {
                                    selectedAnswer == null -> MaterialTheme.colorScheme.surface
                                    i == q.correctIdx -> Color(0xFFD1FAE5) // أخضر للإجابة الصحيحة
                                    i == selectedAnswer -> Color(0xFFFEE2E2) // أحمر للإجابة الخطأ المختارة
                                    else -> MaterialTheme.colorScheme.surface
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = selectedAnswer == null) {
                                        selectedAnswer = i
                                        if (i == q.correctIdx) score++
                                    },
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("$letter. ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(optText, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            
                            if (selectedAnswer != null) {
                                item {
                                    Spacer(Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = if (selectedAnswer == q.correctIdx) Color(0xFFECFDF5) else Color(0xFFFEF2F2)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(
                                                if (selectedAnswer == q.correctIdx) "✅ إجابة صحيحة برافو!" else "❌ إجابة خاطئة.",
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedAnswer == q.correctIdx) Color(0xFF047857) else Color(0xFFB91C1C)
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text("الشرح والتوضيح:\n${q.explanation}", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = {
                                        if (qIndex + 1 < questions.size) {
                                            qIndex++
                                            selectedAnswer = null
                                        } else {
                                            step = 3
                                        }
                                    }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                                        Text(if (qIndex + 1 < questions.size) "السؤال التالي" : "عرض النتيجة النهائية", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉 انتهى الاختبار!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("نتيجتك النهائية هي: $score من ${questions.size}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { step = 0; selectedIds = emptySet() }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("اختبار جديد في مادة أخرى", fontWeight = FontWeight.Bold)
                    }
                }
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
        if (opts.size >= 4 && correct in 0..3) {
            list.add(QuizQuestion(qText, opts.take(4), correct, exp))
        }
    }
    return list
}

// ────────────────────────────────────────────────────────
// 10. الشاشة الرابعة: الحساب والإعدادات (Profile & Settings)
// ────────────────────────────────────────────────────────
@Composable
fun ProfileTab(dark: Boolean, onDark: (Boolean) -> Unit, prefs: SharedPreferences, modifier: Modifier) {
    val ctx = LocalContext.current
    var ringtoneName by remember { mutableStateOf("افتراضية الجهاز") }
    
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            prefs.edit().putString("ringtone", uri?.toString()).apply()
            ringtoneName = RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) ?: "تم التحديد"
            Toast.makeText(ctx, "تم تحديث نغمة المنبه بنجاح", Toast.LENGTH_SHORT).show()
        }
    }
    
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("حسابي والإعدادات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("البيانات الدراسية:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("الصف: ${prefs.getString("grade", "الثالث الثانوي")}")
                Text("النظام: ${prefs.getString("system", "عام")}")
                Text("الشعبة: ${prefs.getString("stream", "علمي علوم")}")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("الوضع الليلي (Dark Mode)", fontWeight = FontWeight.Bold)
                Switch(checked = dark, onCheckedChange = onDark)
            }
        }

        Card(Modifier.fillMaxWidth().clickable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!am.canScheduleExactAlarms()) {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            }
            picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM))
        }) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("نغمة المنبه", fontWeight = FontWeight.Bold)
                Text(ringtoneName, color = Color.Gray)
            }
        }
    }
}

// ────────────────────────────────────────────────────────
// 11. نظام المنبه الكامل والفعلي (Alarm Receiver & Alert Activity)
// ────────────────────────────────────────────────────────
class MyApp : Application() {
    companion object { lateinit var instance: MyApp }
    override fun onCreate() { super.onCreate(); instance = this }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "مهمة دراسية"
        
        // تشغيل شاشة التنبيه الرنانة فوق الشاشة مقفلة
        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("title", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(alertIntent)
        
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel("alarm_channel", "منبه المذاكرة", NotificationManager.IMPORTANCE_HIGH))
        }
        val notif = NotificationCompat.Builder(context, "alarm_channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان موعد المذاكرة! ⏰")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(2026, notif)
    }
}

class AlarmAlertActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_SCREEN_RELEASE or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        val title = intent.getStringExtra("title") ?: "موعد الدرس"
        val prefs = getSharedPreferences("smartstudent_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("ringtone", null)
        val uri = if (uriStr != null) Uri.parse(uriStr) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        
        try {
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
        } catch (e: Exception) {}

        setContent {
            SmartStudentTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Alarm, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("حان وقت المذاكرة! ⏰", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(48.dp))
                        Button(onClick = {
                            ringtone?.stop()
                            finish()
                        }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                            Text("إيقاف المنبه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtone?.stop()
    }
}
