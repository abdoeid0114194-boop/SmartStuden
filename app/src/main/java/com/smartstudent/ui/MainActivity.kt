@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartstudent.ui

import android.app.*
import android.content.*
import android.graphics.Color as AndroidColor
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.smartstudent.BuildConfig
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit

/* ── البيانات الأساسية ── */
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
        val idx = list.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) list[idx] = alarm
        _alarms.postValue(list)
    }
}

/* مناهج (فصول – دروس) */
data class Lesson(val id: Long, val title: String, val content: String = "")
data class Chapter(val id: Long, val title: String, val lessons: List<Lesson>)
interface CurriculumRepository {
    suspend fun getAllChapters(subject: String): List<Chapter>
}
class MockCurriculumRepository : CurriculumRepository {
    private val dummy = mapOf(
        "فيزياء" to listOf(
            Chapter(1, "الحركة", listOf(Lesson(1, "قانون السرعة"), Lesson(2, "قانون التسارع"))),
            Chapter(2, "الطاقة", listOf(Lesson(3, "الطاقة الحركية"), Lesson(4, "الطاقة الكامنة")))
        ),
        "رياضيات" to listOf(
            Chapter(3, "جبر", listOf(Lesson(5, "معادلات خطية"), Lesson(6, "متسلسلات"))),
            Chapter(4, "هندسة", listOf(Lesson(7, "مثلثات"), Lesson(8, "دوائر")))
        )
    )
    override suspend fun getAllChapters(subject: String): List<Chapter> = dummy[subject] ?: emptyList()
}

/* أدوات الوقت */
object TimeUtil {
    fun nowMillis() = System.currentTimeMillis()
    fun format(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format("%02d-%02d-%d %02d:%02d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
    fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)
}

/* ── Gemini AI Engine ── */
data class AIResponse(val candidates: List<Candidate>, val isError: Boolean = false)
data class Candidate(val content: Content)
data class Content(val parts: List<Part>)
data class Part(val text: String)

data class ChatMessage(val text: String, val isUser: Boolean, val isError: Boolean = false)

fun aiOk(text: String) = AIResponse(listOf(Candidate(Content(listOf(Part(text)))))
fun aiError(text: String) = AIResponse(listOf(Candidate(Content(listOf(Part(text))))), isError = true)
fun AIResponse.text(): String = candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()

suspend fun safeAi(block: suspend () -> AIResponse): AIResponse =
    try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { aiError("خطأ غير متوقع: ${e.javaClass.simpleName} - ${e.message}") }

interface AIEngine {
    suspend fun generateExam(subject: String, years: List<Int>, types: List<String>, currentYear: Int): AIResponse
    suspend fun explain(subject: String, topic: String, level: String = "highschool"): AIResponse
    suspend fun autoSchedule(schedule: String): AIResponse
    suspend fun chat(history: List<ChatMessage>, currentYear: Int): AIResponse
    suspend fun generateInteractiveQuiz(subject: String, scopeText: String, currentYear: Int): AIResponse
}

class GeminiAIEngine : AIEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val apiKey: String = BuildConfig.GEMINI_API_KEY.trim()
    @Volatile private var preferredModel: String? = null

    override suspend fun chat(history: List<ChatMessage>, currentYear: Int): AIResponse {
        val turns = mutableListOf<Pair<String, String>>()
        history.filter { !it.isError }.takeLast(14).forEach { m ->
            val role = if (m.isUser) "user" else "model"
            if (turns.isNotEmpty() && turns.last().first == role) {
                turns[turns.lastIndex] = role to (turns.last().second + "\n" + m.text)
            } else turns.add(role to m.text)
        }
        while (turns.isNotEmpty() && turns.first().first != "user") turns.removeAt(0)
        if (turns.isEmpty()) return aiError("اكتب سؤالك أولًا.")
        return generate("$CHAT_SYSTEM (السنة الحالية $currentYear)", turns)
    }

    override suspend fun generateExam(subject: String, years: List<Int>, types: List<String>, currentYear: Int): AIResponse {
        val yearsTxt = if (years.isEmpty()) "" else " على نمط امتحانات الثانوية العامة للسنوات ${years.sorted().joinToString("، ")}"
        val typesTxt = if (types.isEmpty()) "نوّع في أنواع الأسئلة." else "أنواع الأسئلة المطلوبة: ${types.joinToString("، ")}."
        val prompt = "اكتب امتحانًا في مادة $subject$yearsTxt. $typesTxt اجعله من 10 إلى 15 سؤالًا متدرجة الصعوبة. اكتب في النهاية قسمًا بعنوان «الإجابات النموذجية». السنة الحالية هي $currentYear."
        return generate(EXAM_SYSTEM, listOf("user" to prompt))
    }

    override suspend fun explain(subject: String, topic: String, level: String): AIResponse =
        generate(CHAT_SYSTEM, listOf("user" to "اشرح «$topic» في مادة $subject ببساطة وبالعربية مع مثال."))

    override suspend fun autoSchedule(schedule: String): AIResponse =
        generate(SCHEDULE_SYSTEM, listOf("user" to "دي مهامي الدراسية:\n$schedule\n\nاقترح لي جدولًا دراسيًا متوازنًا لليوم بالعربية."))

    override suspend fun generateInteractiveQuiz(subject: String, scopeText: String, currentYear: Int): AIResponse {
        val prompt = "اكتب اختبارًا تفاعليًا (Multiple‑Choice) باللغة العربية لمادة $subject حول: $scopeText. لكل سؤال قدّم 4 خيارات، ضع الإجابة الصحيحة داخل النص كالتالي: **(الإجابة الصحيحة)**. السنة الحالية: $currentYear."
        return generate(CHAT_SYSTEM, listOf("user" to prompt))
    }

    private suspend fun generate(system: String, turns: List<Pair<String, String>>): AIResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") return@withContext aiError("مفتاح Gemini غير موجود داخل التطبيق.")
        val body = buildBody(system, turns)
        val order = (listOfNotNull(preferredModel) + MODELS).distinct()
        val failure = Failure()

        tryModels(order, body, failure)?.let { return@withContext it }
        if (failure.onlyNotFound) {
            val extra = discoverModels().filter { it !in order }.take(3)
            tryModels(extra, body, failure)?.let { return@withContext it }
        }
        aiError("تعذّر الحصول على رد.\n${failure.last}\nجرّب تاني بعد شوية.")
    }

    private fun tryModels(models: List<String>, body: String, failure: Failure): AIResponse? {
        for (model in models) {
            when (val r = callModel(model, body)) {
                is Attempt.Ok -> { preferredModel = model; return aiOk(r.text) }
                is Attempt.Fatal -> return aiError(r.message)
                is Attempt.Retry -> { failure.last = r.reason; if (!r.notFound) failure.onlyNotFound = false }
            }
        }
        return null
    }

    private fun buildBody(system: String, turns: List<Pair<String, String>>): String {
        val contents = JSONArray()
        turns.forEach { (role, text) -> contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text)))) }
        return JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))).put("contents", contents).toString()
    }

    private fun callModel(model: String, body: String): Attempt {
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").header("x-goog-api-key", apiKey).post(body.toRequestBody(JSON_MEDIA)).build()
        return try {
            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> parse(raw)
                    resp.code == 400 && (raw.contains("API key", true) || raw.contains("API_KEY")) -> Attempt.Fatal("مفتاح Gemini غير صالح (400).")
                    resp.code == 400 -> Attempt.Fatal("الطلب غير مقبول (400): ${errorMessage(raw)}")
                    resp.code == 401 || resp.code == 403 -> Attempt.Fatal("المفتاح مرفوض (${resp.code}): ${errorMessage(raw)}")
                    resp.code == 404 -> Attempt.Retry("الموديل $model غير متاح (404).", notFound = true)
                    resp.code == 429 -> Attempt.Retry("وصلت للحد المسموح من الطلبات (429).")
                    else -> Attempt.Retry("الخادم مشغول (${resp.code}).")
                }
            }
        } catch (e: SocketTimeoutException) { Attempt.Retry("الرد اتأخر (timeout).") } 
        catch (e: UnknownHostException) { Attempt.Fatal("مفيش اتصال بالإنترنت.") } 
        catch (e: IOException) { Attempt.Retry("خطأ في الاتصال: ${e.javaClass.simpleName}.") }
    }

    private fun parse(raw: String): Attempt {
        val root = JSONObject(raw)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val block = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            return if (block.isNotEmpty()) Attempt.Fatal("تم حجب الطلب ($block).") else Attempt.Retry("رد فاضي.")
        }
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text", ""))
        }
        val text = sb.toString().trim()
        return if (text.isEmpty()) Attempt.Retry("الرد وصل فاضي.") else Attempt.Ok(text)
    }

    private fun errorMessage(raw: String): String = try { JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty().ifEmpty { raw.take(160) } } catch (e: Exception) { raw.take(160) }

    private fun discoverModels(): List<String> = try {
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200").header("x-goog-api-key", apiKey).build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@use emptyList<String>()
            val arr = JSONObject(raw).optJSONArray("models") ?: return@use emptyList<String>()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").removePrefix("models/")
                val methods = o.optJSONArray("supportedGenerationMethods")
                var canGenerate = false
                if (methods != null) for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") canGenerate = true
                if (canGenerate && name.startsWith("gemini-") && name.contains("flash") && EXCLUDED.none { name.contains(it) }) out.add(name)
            }
            out.sortedDescending()
        }
    } catch (e: Exception) { emptyList() }

    private sealed class Attempt {
        data class Ok(val text: String) : Attempt()
        data class Retry(val reason: String, val notFound: Boolean = false) : Attempt()
        data class Fatal(val message: String) : Attempt()
    }
    private class Failure { var last = ""; var onlyNotFound = true }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite")
        val EXCLUDED = listOf("image", "tts", "live", "audio", "embedding", "robotics", "vision", "thinking", "cyber", "exp", "computer", "native")
        const val CHAT_SYSTEM = "أنت «الطالب الذكي»، مساعد دراسي لطلاب المرحلة الثانوية في مصر. أجب بالعربية الواضحة. استخدم عناوين قصيرة ونقاطًا فقط، ولا تستخدم الجداول."
        const val EXAM_SYSTEM = "أنت معلم خبير في المنهج المصري. اكتب الأسئلة مرقمة وواضحة، ثم الإجابات النموذجية في النهاية. لا تستخدم الجداول."
        const val SCHEDULE_SYSTEM = "أنت مرشد دراسي. اقترح جدولًا واقعيًا بفترات مذاكرة قصيرة وراحات. استخدم نقاطًا قصيرة بدون جداول."
    }
}

/* ── Theme ── */
private val Indigo = Color(0xFF4F46E5)
private val Violet = Color(0xFF8B5CF6)
private val BrandGradient = Brush.linearGradient(listOf(Violet, Indigo))

private val LightColors = lightColorScheme(
    primary = Indigo, onPrimary = Color.White, primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFFEC4899), secondaryContainer = Color(0xFFE0E7FF), onSecondaryContainer = Color(0xFF312E81),
    background = Color(0xFFF5F6FA), onBackground = Color(0xFF111827), surface = Color.White, onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F2F6), onSurfaceVariant = Color(0xFF6B7280), outlineVariant = Color(0xFFE5E7EB)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8), onPrimary = Color(0xFF1E1B4B), primaryContainer = Color(0xFF3730A3), onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFF472B6), secondaryContainer = Color(0xFF3730A3), onSecondaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF0F1117), onBackground = Color(0xFFE5E7EB), surface = Color(0xFF181B24), onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF232733), onSurfaceVariant = Color(0xFF9CA3AF), outlineVariant = Color(0xFF2C3140)
)

@Composable
fun SmartStudentTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography(), content = content)
    }
}

/* ── MainActivity ── */
class MainActivity : ComponentActivity() {
    private val alarmRepo: AlarmRepository = MockAlarmRepository()
    private val aiEngine: AIEngine = GeminiAIEngine()
    private val curriculumRepo: CurriculumRepository = MockCurriculumRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(AlarmReceiver.CHANNEL_ID, "منبهات ذكي‑ستودنت", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "إشعارات تنبيه للمهام الدراسية"
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val prefs = getSharedPreferences("smartstudent_prefs", Context.MODE_PRIVATE)
        setContent {
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
            SmartStudentTheme(darkTheme = dark) {
                MainScreen(alarmRepo, aiEngine, curriculumRepo, dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() })
            }
        }
    }
}

/* ── UI رئيسية (Tabs) ── */
@Composable
fun MainScreen(alarmRepository: AlarmRepository, aiEngine: AIEngine, curriculumRepository: CurriculumRepository, darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("الرئيسية", "دردشة AI", "الامتحانات", "حسابي")
    val tabIcons = listOf(Icons.Filled.Home, Icons.Filled.Chat, Icons.Filled.Description, Icons.Filled.Person)
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(BrandGradient), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Text("الطالب الذكي", fontWeight = FontWeight.ExtraBold)
                    }
                },
                actions = { IconButton(onClick = { }) { Icon(Icons.Outlined.Notifications, contentDescription = "التنبيهات") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background, titleContentColor = cs.onBackground, actionIconContentColor = cs.onSurfaceVariant)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = cs.surface, tonalElevation = 3.dp) {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tabIcons[index], contentDescription = label) },
                        label = { Text(label, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = cs.onSecondaryContainer, selectedTextColor = cs.primary, indicatorColor = cs.secondaryContainer)
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(targetState = selectedTab, label = "TabTransition") { targetTab ->
            when (targetTab) {
                0 -> HomeTab(alarmRepository, aiEngine, Modifier.padding(padding))
                1 -> AIChatTab(aiEngine, Modifier.padding(padding))
                2 -> ExamTab(aiEngine, curriculumRepository, Modifier.padding(padding))
                else -> ProfileTab(darkTheme, onDarkThemeChange, Modifier.padding(padding))
            }
        }
    }
}

/* ── مكوّنات مشتركة ── */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content)
}
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = if (selected) { { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null)
}
private val HEADER_RE = Regex("^#{1,6}\\s+(.*)$")
private val BULLET_RE = Regex("^[*\\-•]\\s+(.*)$")
private val NUMBERED_RE = Regex("^(\\d+)[.)]\\s+(.*)$")
private val RULE_RE = Regex("^([-*_]\\s*){3,}$")
private fun AnnotatedString.Builder.appendInline(line: String) {
    val parts = line.split("**")
    if (parts.size % 2 == 0) { append(line.replace("**", "")); return }
    parts.forEachIndexed { i, seg -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(seg) } else append(seg) }
}
fun markdownToAnnotated(src: String): AnnotatedString = buildAnnotatedString {
    var first = true
    for (rawLine in src.lines()) {
        val line = rawLine.trim()
        if (line.startsWith("```") || RULE_RE.matches(line)) continue
        if (!first) append("\n")
        first = false
        val h = HEADER_RE.matchEntire(line); val b = BULLET_RE.matchEntire(line); val n = NUMBERED_RE.matchEntire(line)
        when {
            h != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) { appendInline(h.groupValues[1]) }
            b != null -> { append("•  "); appendInline(b.groupValues[1]) }
            n != null -> { append("${n.groupValues[1]}.  "); appendInline(n.groupValues[2]) }
            else -> appendInline(line)
        }
    }
}
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    Text(remember(text) { markdownToAnnotated(text) }, modifier = modifier, style = style)
}
@Composable
fun ResultCard(title: String, text: String, isError: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isError) cs.errorContainer else cs.surface, contentColor = if (isError) cs.onErrorContainer else cs.onSurface),
        border = BorderStroke(1.dp, if (isError) cs.error.copy(alpha = 0.3f) else cs.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (isError) "تعذّر التنفيذ" else title, fontWeight = FontWeight.Bold, color = if (isError) cs.error else cs.primary)
            SelectionContainer { MarkdownText(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp)) }
        }
    }
}

/* ── الصفحة الرئيسية ── */
data class StudyTask(val id: Long, val title: String, val time: String, val triggerAt: Long, val done: Boolean = false)

@Composable
fun HomeTab(alarmRepository: AlarmRepository, aiEngine: AIEngine, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val tasks = remember { mutableStateListOf<StudyTask>() }
    var showAdd by remember { mutableStateOf(false) }
    var scheduleText by remember { mutableStateOf("") }
    var scheduleError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when { hour < 12 -> "صباح الخير ☀️"; hour < 18 -> "أهلاً بك 👋"; else -> "مساء النور 🌙" }

    if (showAdd) {
        AddTaskDialog(onDismiss = { showAdd = false }, onAdd = { title, dateMillis, timeTxt ->
            val task = StudyTask(System.currentTimeMillis(), title, timeTxt, dateMillis, false)
            tasks.add(0, task)
            scheduleAlarm(task)
            showAdd = false
        })
    }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Text(greeting, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                Text("خلّينا نذاكر النهارده", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val pending = tasks.count { !it.done }; val done = tasks.size - pending
                StatCard("مهام متبقية", pending.toString(), Icons.Filled.Timer, Color(0xFFF59E0B), Modifier.weight(1f))
                StatCard("مهام منجزة", done.toString(), Icons.Filled.CheckCircle, Color(0xFF10B981), Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Box(Modifier.background(BrandGradient).padding(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White)
                            Text("مساعدك الذكي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("خلّي الذكاء الاصطناعي يرتّب مهامك في جدول مناسب.", color = Color.White.copy(alpha = 0.92f))
                        Button(
                            onClick = {
                                val pending = tasks.filter { !it.done }
                                if (pending.isEmpty()) { scheduleError = false; scheduleText = "أضف مهامك أولًا." }
                                else {
                                    isLoading = true
                                    val list = pending.joinToString("\n") { "- ${it.title} (${it.time})" }
                                    scope.launch {
                                        val r = safeAi { aiEngine.autoSchedule(list) }
                                        scheduleText = r.text(); scheduleError = r.isError; isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Indigo),
                            shape = RoundedCornerShape(12.dp), enabled = !isLoading
                        ) {
                            if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Indigo)
                            else {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("جدولة تلقائية الآن", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        if (scheduleText.isNotBlank()) item { ResultCard("جدولك المقترح", scheduleText, scheduleError) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("مهامك القادمة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("إضافة") }
            }
        }
        if (tasks.isEmpty()) {
            item { Text("مفيش مهام لسه. اضغط «إضافة» وابدأ بأول مهمة.", color = cs.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), textAlign = TextAlign.Center) }
        }
        items(tasks, key = { it.id }) { task ->
            TaskRow(task = task, onToggle = {
                val idx = tasks.indexOfFirst { it.id == task.id }
                if (idx >= 0) tasks[idx] = tasks[idx].copy(done = !tasks[idx].done)
            }, onDelete = { tasks.removeAll { it.id == task.id } })
        }
    }
}
@Composable
fun StatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = color) }
            Column {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable
fun TaskRow(task: StudyTask, onToggle: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.SemiBold, textDecoration = if (task.done) TextDecoration.LineThrough else null, color = if (task.done) cs.onSurfaceVariant else cs.onSurface)
                Text(task.time, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = cs.onSurfaceVariant) }
        }
    }
}

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String, Long, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    var timeText by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val calendar = Calendar.getInstance()

    val datePicker = remember {
        DatePickerDialog(ctx, { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            dateMillis = calendar.timeInMillis
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    }

    val timePicker = remember {
        TimePickerDialog(ctx, { _, hourOfDay, minute ->
            val isPm = hourOfDay >= 12
            var hour12 = hourOfDay % 12; if (hour12 == 0) hour12 = 12
            timeText = String.format("%02d:%02d %s", hour12, minute, if (isPm) "مساءً" else "صباحًا")
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay); calendar.set(Calendar.MINUTE, minute); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
            dateMillis = calendar.timeInMillis
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مهمة جديدة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("اسم المهمة") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { datePicker.show() }) {
                        Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        // هنا كان الخطأ وصلحناه
                        val dateStr = dateMillis?.let { android.text.format.DateFormat.format("dd/MM/yyyy", it) } ?: "اختر التاريخ"
                        Text("التاريخ: $dateStr")
                    }
                    OutlinedButton(onClick = { timePicker.show() }) {
                        Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (timeText.isNotBlank()) "الوقت: $timeText" else "اختر الوقت")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ts = dateMillis ?: System.currentTimeMillis()
                onAdd(title.trim(), ts, timeText.ifBlank { "بدون توقيت" })
            }, enabled = title.isNotBlank() && dateMillis != null) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun scheduleAlarm(task: StudyTask) {
    val ctx = MyApp.instance
    val intent = Intent(ctx, AlarmReceiver::class.java).apply { putExtra(AlarmReceiver.EXTRA_TITLE, task.title) }
    val pending = PendingIntent.getBroadcast(ctx, task.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    val alarmMgr = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, pending)
    else alarmMgr.setExact(AlarmManager.RTC_WAKEUP, task.triggerAt, pending)
}

class MyApp : Application() {
    companion object { lateinit var instance: MyApp; private set }
    override fun onCreate() { super.onCreate(); instance = this }
}

class AlarmReceiver : BroadcastReceiver() {
    companion object { const val CHANNEL_ID = "smartstudent_alarm_channel"; const val EXTRA_TITLE = "alarm_title" }
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "مهمة"
        val tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        RingtoneManager.getRingtone(context, tone).play()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // أيقونة افتراضية لتجنب الخطأ
            .setContentTitle("منبه")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }
}

/* ── دردشة AI ── */
@Composable
fun AIChatTab(aiEngine: AIEngine, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val currentYear = remember { TimeUtil.currentYear() }

    val ask: () -> Unit = {
        loading = true
        val history = messages
        scope.launch {
            val r = safeAi { aiEngine.chat(history, currentYear) }
            messages = messages + ChatMessage(r.text(), isUser = false, isError = r.isError)
            loading = false
        }
    }
    val send: (String) -> Unit = { raw ->
        val q = raw.trim()
        if (q.isNotEmpty() && !loading) {
            messages = messages + ChatMessage(q, isUser = true)
            input = ""
            ask()
        }
    }
    val retry: () -> Unit = {
        if (!loading) {
            messages = messages.dropLastWhile { !it.isUser }
            ask()
        }
    }

    LaunchedEffect(messages.size, loading) {
        val count = messages.size + if (loading) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (messages.isEmpty()) item { EmptyChatState(onPick = send) }
            itemsIndexed(messages) { index, msg ->
                ChatBubble(message = msg, onRetry = if (msg.isError && index == messages.lastIndex && !loading) retry else null)
            }
            if (loading) item { TypingBubble() }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, placeholder = { Text("اكتب سؤالك هنا...") },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(26.dp), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = cs.primary, unfocusedBorderColor = cs.outlineVariant, focusedContainerColor = cs.surface, unfocusedContainerColor = cs.surface)
            )
            val canSend = input.isNotBlank() && !loading
            Box(Modifier.size(52.dp).clip(CircleShape).background(if (canSend) cs.primary else cs.outlineVariant).clickable(enabled = canSend) { send(input) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Send, contentDescription = "إرسال", tint = if (canSend) cs.onPrimary else cs.onSurfaceVariant, modifier = Modifier.size(22.dp).graphicsLayer { scaleX = -1f })
            }
        }
    }
}

@Composable
fun EmptyChatState(onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val suggestions = listOf("اشرح لي قانون نيوتن الثاني ببساطة", "لخّص لي درس الوراثة في الأحياء", "إزاي أنظّم مذاكرتي قبل الامتحانات؟", "اديني أسئلة على الاتزان الكيميائي")
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Text("اسألني أي حاجة في مذاكرتك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("بشرح لك الدروس، وأحل معاك الأسئلة، وأساعدك تنظّم وقتك.", color = cs.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        suggestions.forEach { s -> AppCard(Modifier.fillMaxWidth().clickable { onPick(s) }) { Text(s, Modifier.padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyMedium) } }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, onRetry: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val container = when { message.isUser -> cs.primary; message.isError -> cs.errorContainer; else -> cs.surface }
    val content = when { message.isUser -> cs.onPrimary; message.isError -> cs.onErrorContainer; else -> cs.onSurface }
    val shape = if (message.isUser) RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp) else RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End) {
        Column(Modifier.widthIn(max = 310.dp)) {
            Card(shape = shape, colors = CardDefaults.cardColors(containerColor = container, contentColor = content), border = if (!message.isUser && !message.isError) BorderStroke(1.dp, cs.outlineVariant) else null) {
                if (message.isUser) Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
                else SelectionContainer { MarkdownText(message.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)) }
            }
            if (onRetry != null) TextButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("إعادة المحاولة") }
        }
    }
}

@Composable
fun TypingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        AppCard {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("جاري التفكير...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/* ── شاشة الامتحانات ── */
@Composable
fun ExamTab(aiEngine: AIEngine, curriculumRepository: CurriculumRepository, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf("فيزياء") }
    var years by remember { mutableStateOf<List<Int>>(emptyList()) }
    var resultText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val currentYear = remember { TimeUtil.currentYear() }
    val subjects = listOf("فيزياء", "رياضيات", "كيمياء", "أحياء", "إنجليزي", "عربي")
    val yearsList = listOf(currentYear - 1, currentYear - 2, currentYear - 3)

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("مولد الامتحانات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("المادة", fontWeight = FontWeight.Bold, color = cs.primary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subjects) { s ->
                        FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(s) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = cs.primary, selectedLabelColor = Color.White))
                    }
                }
                Text("السنوات السابقة", fontWeight = FontWeight.Bold, color = cs.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    yearsList.forEach { y ->
                        FilterChip(selected = years.contains(y), onClick = { years = if (y in years) years - y else years + y }, label = { Text(y.toString()) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = cs.primary, selectedLabelColor = Color.White))
                    }
                }
            }
        }

        Button(
            onClick = {
                loading = true
                scope.launch {
                    val r = safeAi { aiEngine.generateExam(subject, years, emptyList(), currentYear) }
                    resultText = r.text(); isError = r.isError; loading = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !loading
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
            else { Icon(Icons.Filled.Description, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("توليد الامتحان", fontWeight = FontWeight.Bold) }
        }

        if (resultText.isNotBlank()) {
            ResultCard("الامتحان المقترح", resultText, isError, Modifier.weight(1f))
        }
    }
}

/* ── شاشة حسابي ── */
@Composable
fun ProfileTab(darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(160.dp).background(BrandGradient, shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(Color.White).padding(4.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFE0E7FF)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(50.dp), tint = Color(0xFF4F46E5))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("عبد الرحمن", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileSettingItem(Icons.Outlined.Person, "بيانات الحساب") {}
            ProfileSettingItem(Icons.Outlined.DarkMode, "الوضع الليلي") {
                Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
            }
            ProfileSettingItem(Icons.Outlined.Info, "عن التطبيق (الإصدار 1.0)") {}
        }
    }
}

@Composable
fun ProfileSettingItem(icon: ImageVector, title: String, trailing: @Composable (() -> Unit)? = null) {
    AppCard(Modifier.fillMaxWidth().clickable { }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (trailing != null) trailing()
            else Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.Gray)
        }
    }
}
