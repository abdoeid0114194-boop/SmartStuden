@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartstudent.ui

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Base64
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
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

/* ── البيانات الأساسية ── */
data class Lesson(val id: Long, val title: String)
data class Chapter(val id: Long, val title: String, val lessons: List<Lesson>)
data class ChatMessage(val text: String, val isUser: Boolean, val isError: Boolean = false, val imageBitmap: Bitmap? = null)
data class QuizQuestion(val text: String, val options: List<String>, val correctIdx: Int, val explanation: String)

interface CurriculumRepository { suspend fun getChapters(subject: String): List<Chapter> }
class MockCurriculumRepository : CurriculumRepository {
    override suspend fun getChapters(subject: String): List<Chapter> {
        delay(300)
        return when (subject) {
            "فيزياء" -> listOf(
                Chapter(1, "الباب الأول: الحركة", listOf(Lesson(1, "السرعة"), Lesson(2, "التسارع"))),
                Chapter(2, "الباب الثاني: الطاقة", listOf(Lesson(3, "الشغل"), Lesson(4, "الطاقة الحركية")))
            )
            "رياضيات" -> listOf(
                Chapter(3, "الباب الأول: الجبر", listOf(Lesson(5, "المعادلات"), Lesson(6, "المتباينات"))),
                Chapter(4, "الباب الثاني: التفاضل", listOf(Lesson(7, "النهايات"), Lesson(8, "الاشتقاق")))
            )
            else -> emptyList()
        }
    }
}

/* ── محرك الذكاء الاصطناعي ── */
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

    // دردشة مع دعم الصور
    suspend fun chat(history: List<ChatMessage>, imageBase64: String? = null): AIResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext aiError("مفتاح API غير موجود.")
        val contents = JSONArray()
        
        history.takeLast(4).forEach { msg ->
            val parts = JSONArray().put(JSONObject().put("text", msg.text))
            contents.put(JSONObject().put("role", if (msg.isUser) "user" else "model").put("parts", parts))
        }

        if (imageBase64 != null) {
            val parts = JSONArray().apply {
                put(JSONObject().put("text", "حلل هذه الصورة وساعدني في فهمها."))
                put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", imageBase64)))
            }
            contents.put(JSONObject().put("role", "user").put("parts", parts))
        }

        val body = JSONObject().put("contents", contents).toString()
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent").header("x-goog-api-key", apiKey).post(body.toRequestBody("application/json".toMediaType())).build()

        try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val txt = JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    aiOk(txt)
                } else {
                    aiError("خطأ من الخادم: ${resp.code}")
                }
            }
        } catch (e: Exception) { aiError("فشل الاتصال: ${e.message}") }
    }

    // توليد الامتحانات
    suspend fun generateInteractiveExam(subject: String, selectedTopics: String): AIResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext aiError("مفتاح API غير موجود.")
        val prompt = "أنت معلم خبير. اكتب اختباراً تفاعلياً من 5 أسئلة (اختيار من متعدد) لمادة $subject. المواضيع: $selectedTopics.\nالتنسيق:\nQ: [سؤال]\nO1: [خيار1]\nO2: [خيار2]\nO3: [خيار3]\nO4: [خيار4]\nC: [رقم الصح 1-4]\nE: [شرح]"
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))).toString()
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent").header("x-goog-api-key", apiKey).post(body.toRequestBody("application/json".toMediaType())).build()
        try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val txt = JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    aiOk(txt)
                } else aiError("خطأ: ${resp.code}")
            }
        } catch (e: Exception) { aiError("فشل الاتصال") }
    }
}

/* ── الثيم ── */
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

/* ── التطبيق ── */
class MainActivity : ComponentActivity() {
    private val aiEngine = GeminiAIEngine()
    private val curriculumRepo = MockCurriculumRepository()

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
                MainScreen(aiEngine, curriculumRepo, dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() }, prefs)
            }
        }
    }
}

@Composable
fun MainScreen(aiEngine: GeminiAIEngine, curriculumRepo: MockCurriculumRepository, darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit, prefs: SharedPreferences) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("الرئيسية", "دردشة AI", "الامتحانات", "حسابي")
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Chat, Icons.Filled.Description, Icons.Filled.Person)
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.background,
        topBar = { TopAppBar(title = { Text("الطالب الذكي", fontWeight = FontWeight.Bold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)) },
        bottomBar = {
            NavigationBar(containerColor = cs.surface) {
                tabs.forEachIndexed { i, l ->
                    NavigationBarItem(selected = selectedTab == i, onClick = { selectedTab = i }, icon = { Icon(icons[i], null) }, label = { Text(l) })
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeTab(Modifier.padding(padding))
            1 -> AIChatTab(aiEngine, Modifier.padding(padding))
            2 -> ExamWizardTab(aiEngine, curriculumRepo, Modifier.padding(padding))
            else -> ProfileTab(darkTheme, onDarkThemeChange, prefs, Modifier.padding(padding))
        }
    }
}

/* ── الشاشة الرئيسية ── */
@Composable
fun HomeTab(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("أهلاً بك 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
            Column(Modifier.background(BrandGradient).padding(20.dp)) {
                Text("مساعدك الذكي", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("اسأل بالصوت أو ارفع صورة لحل مسألة.", color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

/* ── الدردشة بالصوت والصور ── */
@Composable
fun AIChatTab(aiEngine: GeminiAIEngine, modifier: Modifier) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) input = spokenText
        }
    }

    var pendingImageBase64 by remember { mutableStateOf<String?>(null) }
    var pendingImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = ctx.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(stream)
            pendingImageBitmap = bitmap
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            pendingImageBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }

    fun sendMessage() {
        if ((input.isNotBlank() || pendingImageBase64 != null) && !loading) {
            val text = if (input.isBlank()) "حلل هذه الصورة" else input
            val userMsg = ChatMessage(text = text, isUser = true, imageBitmap = pendingImageBitmap)
            messages = messages + userMsg
            
            input = ""
            val imgToSend = pendingImageBase64
            pendingImageBitmap = null
            pendingImageBase64 = null
            loading = true

            scope.launch {
                val res = safeAi { aiEngine.chat(messages, imgToSend) }
                messages = messages + ChatMessage(res.text(), false, res.isError)
                loading = false
            }
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { msg ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start) {
                    Card(colors = CardDefaults.cardColors(containerColor = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(12.dp)) {
                            msg.imageBitmap?.let {
                                androidx.compose.foundation.Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "صورة",
                                    modifier = Modifier.size(150.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(msg.text, color = if (msg.isUser) Color.White else Color.Black)
                        }
                    }
                }
            }
            if (loading) item { CircularProgressIndicator(Modifier.size(24.dp)) }
        }

        if (pendingImageBitmap != null) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Start) {
                androidx.compose.foundation.Image(
                    bitmap = pendingImageBitmap!!.asImageBitmap(),
                    contentDescription = "معاينة",
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))
                )
                IconButton(onClick = { pendingImageBitmap = null; pendingImageBase64 = null }) {
                    Icon(Icons.Filled.Close, null)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { imageLauncher.launch("image/*") }) {
                Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.primary)
            }
            
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("اكتب أو سجل...") })
            
            IconButton(onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                }
                speechLauncher.launch(intent)
            }) {
                Icon(Icons.Filled.Mic, null, tint = MaterialTheme.colorScheme.secondary)
            }

            IconButton(onClick = { sendMessage() }) {
                Icon(Icons.Filled.Send, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/* ── شاشة الامتحانات ── */
@Composable
fun ExamWizardTab(aiEngine: GeminiAIEngine, curriculumRepo: MockCurriculumRepository, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var subject by remember { mutableStateOf("") }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedLessonIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var questions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var currentQ by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }

    when (step) {
        0 -> {
            Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("اختر المادة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                listOf("فيزياء", "رياضيات").forEach { s ->
                    Card(Modifier.fillMaxWidth().clickable {
                        subject = s
                        scope.launch {
                            isLoading = true
                            chapters = curriculumRepo.getChapters(s)
                            isLoading = false
                            step = 1
                        }
                    }) { Text(s, Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge) }
                }
                if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        }
        1 -> {
            Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("اختر الدروس", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { selectedLessonIds = chapters.flatMap { it.lessons }.map { it.id }.toSet() }, modifier = Modifier.fillMaxWidth()) { Text("تحديد المنهج كله") }

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chapters) { chapter ->
                        var expanded by remember { mutableStateOf(false) }
                        val chapterLessonIds = chapter.lessons.map { it.id }.toSet()
                        val isChapterSelected = selectedLessonIds.containsAll(chapterLessonIds)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isChapterSelected, onCheckedChange = { checked -> selectedLessonIds = if (checked) selectedLessonIds + chapterLessonIds else selectedLessonIds - chapterLessonIds })
                                    Text(chapter.title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null) }
                                }
                                if (expanded) {
                                    chapter.lessons.forEach { lesson ->
                                        Row(Modifier.fillMaxWidth().padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = selectedLessonIds.contains(lesson.id), onCheckedChange = { checked -> selectedLessonIds = if (checked) selectedLessonIds + lesson.id else selectedLessonIds - lesson.id })
                                            Text(lesson.title)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(onClick = {
                    isLoading = true
                    val selectedNames = chapters.flatMap { it.lessons }.filter { selectedLessonIds.contains(it.id) }.joinToString(", ") { it.title }
                    scope.launch {
                        val res = aiEngine.generateInteractiveExam(subject, selectedNames)
                        if (!res.isError) {
                            questions = parseQuiz(res.text())
                            step = 2; currentQ = 0; score = 0
                        }
                        isLoading = false
                    }
                }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = selectedLessonIds.isNotEmpty() && !isLoading) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White) else Text("توليد الاختبار الآن")
                }
            }
        }
        2 -> {
            if (questions.isNotEmpty() && currentQ < questions.size) {
                val q = questions[currentQ]
                Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LinearProgressIndicator(progress = { (currentQ + 1).toFloat() / questions.size }, modifier = Modifier.fillMaxWidth())
                    Text(q.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    q.options.forEachIndexed { idx, option ->
                        val isSelected = selectedAnswer == idx; val isCorrect = idx == q.correctIdx
                        val color = when { selectedAnswer == null -> MaterialTheme.colorScheme.surface; isCorrect -> Color(0xFFD1FAE5); isSelected && !isCorrect -> Color(0xFFFEE2E2); else -> MaterialTheme.colorScheme.surface }
                        Card(Modifier.fillMaxWidth().clickable(enabled = selectedAnswer == null) { selectedAnswer = idx }, colors = CardDefaults.cardColors(containerColor = color)) { Text(option, Modifier.padding(16.dp)) }
                    }
                    if (selectedAnswer != null) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Text("الشرح: ${q.explanation}", Modifier.padding(16.dp)) }
                        Button(onClick = { if (selectedAnswer == q.correctIdx) score++; if (currentQ + 1 < questions.size) { currentQ++; selectedAnswer = null } else step = 3 }, modifier = Modifier.fillMaxWidth()) { Text("التالي") }
                    }
                }
            }
        }
        3 -> {
            Column(modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("انتهى الاختبار!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("نتيجتك: $score من ${questions.size}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { step = 0; selectedLessonIds = emptySet() }) { Text("اختبار جديد") }
            }
        }
    }
}

fun parseQuiz(text: String): List<QuizQuestion> {
    val list = mutableListOf<QuizQuestion>()
    val blocks = text.replace("```", "").split("Q:").drop(1)
    for (block in blocks) {
        try {
            val lines = block.lines()
            val qText = lines[0].trim()
            val opts = mutableListOf<String>(); var correct = 0; var exp = ""
            for (line in lines) {
                if (line.startsWith("O1:")) opts.add(line.removePrefix("O1:").trim())
                if (line.startsWith("O2:")) opts.add(line.removePrefix("O2:").trim())
                if (line.startsWith("O3:")) opts.add(line.removePrefix("O3:").trim())
                if (line.startsWith("O4:")) opts.add(line.removePrefix("O4:").trim())
                if (line.startsWith("C:")) correct = (line.removePrefix("C:").trim().toIntOrNull() ?: 1) - 1
                if (line.startsWith("E:")) exp = line.removePrefix("E:").trim()
            }
            if (opts.size == 4) list.add(QuizQuestion(qText, opts, correct, exp))
        } catch (e: Exception) { }
    }
    return list
}

/* ── شاشة الإعدادات (الحساب) ── */
@Composable
fun ProfileTab(darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit, prefs: SharedPreferences, modifier: Modifier) {
    val ctx = LocalContext.current
    var currentRingtone by remember { mutableStateOf(prefs.getString("alarm_sound_name", "الافتراضية")) }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            prefs.edit().putString("alarm_sound_uri", uri?.toString()).apply()
            currentRingtone = "تم التغيير"
            prefs.edit().putString("alarm_sound_name", currentRingtone).apply()
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("الإعدادات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الوضع الليلي")
                Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
            }
        }
        Card(Modifier.fillMaxWidth().clickable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    ctx.startActivity(intent)
                    Toast.makeText(ctx, "من فضلك اسمح للمنبهات الدقيقة", Toast.LENGTH_LONG).show()
                }
            }
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            }
            ringtoneLauncher.launch(intent)
        }) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("نغمة المنبه")
                Text(currentRingtone ?: "افتراضية", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/* ── المنبهات ── */
class MyApp : Application() {
    companion object { lateinit var instance: MyApp; private set }
    override fun onCreate() { super.onCreate(); instance = this }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("alarm_title") ?: "مهمة"
        val prefs = context.getSharedPreferences("smartstudent_prefs", Context.MODE_PRIVATE)
        val soundUriStr = prefs.getString("alarm_sound_uri", null)
        val soundUri = if (soundUriStr != null) Uri.parse(soundUriStr) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try { RingtoneManager.getRingtone(context, soundUri).play() } catch (e: Exception) { }
        
        val channelId = "alarm_channel"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { nm.createNotificationChannel(NotificationChannel(channelId, "منبهات", NotificationManager.IMPORTANCE_HIGH)) }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("حان وقت المذاكرة!").setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(1, notification)
    }
}
