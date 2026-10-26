@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartstudent.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.smartstudent.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Calendar
import java.util.concurrent.TimeUnit

// =====================================================================
//  البيانات والمنبهات (كما هي)
// =====================================================================

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

object TimeUtil {
    fun nowMillis() = System.currentTimeMillis()
    fun format(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(
            "%02d-%02d-%d %02d:%02d",
            cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR),
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )
    }
}

// =====================================================================
//  محرك الذكاء الاصطناعي (Gemini)
// =====================================================================

data class AIResponse(val candidates: List<Candidate>, val isError: Boolean = false)
data class Candidate(val content: Content)
data class Content(val parts: List<Part>)
data class Part(val text: String)

data class ChatMessage(val text: String, val isUser: Boolean, val isError: Boolean = false)

fun aiOk(text: String) = AIResponse(listOf(Candidate(Content(listOf(Part(text))))))
fun aiError(text: String) = AIResponse(listOf(Candidate(Content(listOf(Part(text))))), isError = true)
fun AIResponse.text(): String = candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()

suspend fun safeAi(block: suspend () -> AIResponse): AIResponse =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        aiError("حدث خطأ غير متوقع: ${e.javaClass.simpleName} - ${e.message}")
    }

interface AIEngine {
    suspend fun generateExam(subject: String, years: List<Int>, types: List<String>): AIResponse
    suspend fun explain(subject: String, topic: String, level: String = "highschool"): AIResponse
    suspend fun autoSchedule(schedule: String): AIResponse
    suspend fun chat(history: List<ChatMessage>): AIResponse
}

class GeminiAIEngine : AIEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val apiKey: String = BuildConfig.GEMINI_API_KEY.trim()

    /** آخر موديل اشتغل بنجاح، بنجرّبه الأول في الطلبات الجاية. */
    @Volatile
    private var preferredModel: String? = null

    private sealed class Attempt {
        data class Ok(val text: String) : Attempt()
        data class Retry(val reason: String, val notFound: Boolean = false) : Attempt()
        data class Fatal(val message: String) : Attempt()
    }

    private class Failure {
        var last: String = ""
        var onlyNotFound: Boolean = true
    }

    // ---------- الواجهة العامة ----------

    override suspend fun chat(history: List<ChatMessage>): AIResponse {
        val turns = mutableListOf<Pair<String, String>>()
        history.filter { !it.isError }.takeLast(14).forEach { m ->
            val role = if (m.isUser) "user" else "model"
            if (turns.isNotEmpty() && turns.last().first == role) {
                turns[turns.lastIndex] = role to (turns.last().second + "\n" + m.text)
            } else {
                turns.add(role to m.text)
            }
        }
        while (turns.isNotEmpty() && turns.first().first != "user") turns.removeAt(0)
        if (turns.isEmpty()) return aiError("اكتب سؤالك أولًا.")
        return generate(CHAT_SYSTEM, turns)
    }

    override suspend fun generateExam(subject: String, years: List<Int>, types: List<String>): AIResponse {
        val yearsTxt = if (years.isEmpty()) "" else " على نمط امتحانات الثانوية العامة للسنوات ${years.sorted().joinToString("، ")}"
        val typesTxt = if (types.isEmpty()) "نوّع في أنواع الأسئلة." else "أنواع الأسئلة المطلوبة: ${types.joinToString("، ")}."
        val prompt = "اكتب امتحانًا في مادة $subject$yearsTxt. $typesTxt " +
            "اجعله من 10 إلى 15 سؤالًا متدرجة الصعوبة، وابتكر أسئلة جديدة بنفس المستوى والنمط دون نسخ أسئلة حقيقية. " +
            "اكتب في النهاية قسمًا بعنوان «الإجابات النموذجية»."
        return generate(EXAM_SYSTEM, listOf("user" to prompt))
    }

    override suspend fun explain(subject: String, topic: String, level: String): AIResponse =
        generate(CHAT_SYSTEM, listOf("user" to "اشرح «$topic» في مادة $subject ببساطة وبالعربية مع مثال."))

    override suspend fun autoSchedule(schedule: String): AIResponse =
        generate(
            SCHEDULE_SYSTEM,
            listOf("user" to "دي مهامي الدراسية:\n$schedule\n\nاقترح لي جدولًا دراسيًا متوازنًا لليوم بالعربية.")
        )

    // ---------- التنفيذ ----------

    private suspend fun generate(system: String, turns: List<Pair<String, String>>): AIResponse =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
                return@withContext aiError(
                    "مفتاح Gemini غير موجود داخل التطبيق.\n" +
                        "تأكد إن الـ Secret اسمه مظبوط وإن ملف الـ workflow بيمرّره وقت البناء."
                )
            }
            val body = buildBody(system, turns)
            val order = (listOfNotNull(preferredModel) + MODELS).distinct()
            val failure = Failure()

            tryModels(order, body, failure)?.let { return@withContext it }

            // لو كل الأسماء طلعت 404، نسأل Google عن الموديلات المتاحة فعلًا لمفتاحك
            if (failure.onlyNotFound) {
                val extra = discoverModels().filter { it !in order }.take(3)
                tryModels(extra, body, failure)?.let { return@withContext it }
            }

            aiError(
                "تعذّر الحصول على رد من Gemini.\n${failure.last}\n" +
                    "جرّب تاني بعد شوية، ولو فضلت المشكلة افتح الإنترنت من شبكة تانية."
            )
        }

    private fun tryModels(models: List<String>, body: String, failure: Failure): AIResponse? {
        for (model in models) {
            when (val r = callModel(model, body)) {
                is Attempt.Ok -> {
                    preferredModel = model
                    return aiOk(r.text)
                }
                is Attempt.Fatal -> return aiError(r.message)
                is Attempt.Retry -> {
                    failure.last = r.reason
                    if (!r.notFound) failure.onlyNotFound = false
                }
            }
        }
        return null
    }

    private fun buildBody(system: String, turns: List<Pair<String, String>>): String {
        val contents = JSONArray()
        turns.forEach { (role, text) ->
            contents.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .toString()
    }

    private fun callModel(model: String, body: String): Attempt {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> parse(raw)
                    resp.code == 400 && (raw.contains("API key", true) || raw.contains("API_KEY")) ->
                        Attempt.Fatal("مفتاح Gemini غير صالح (400). أنشئ مفتاحًا جديدًا من Google AI Studio وحدّث الـ Secret.")
                    resp.code == 400 ->
                        Attempt.Fatal("الطلب غير مقبول (400): ${errorMessage(raw)}")
                    resp.code == 401 || resp.code == 403 ->
                        Attempt.Fatal("المفتاح مرفوض (${resp.code}): ${errorMessage(raw)}")
                    resp.code == 404 ->
                        Attempt.Retry("الموديل $model غير متاح (404).", notFound = true)
                    resp.code == 429 ->
                        Attempt.Retry("وصلت للحد المسموح من الطلبات على $model (429). استنى دقيقة وجرّب.")
                    else ->
                        Attempt.Retry("الخادم مشغول (${resp.code}) على $model.")
                }
            }
        } catch (e: SocketTimeoutException) {
            Attempt.Retry("الرد اتأخر أكتر من 30 ثانية على $model (timeout).")
        } catch (e: UnknownHostException) {
            Attempt.Fatal("مفيش اتصال بالإنترنت. تأكد من الواي فاي أو الداتا.")
        } catch (e: IOException) {
            Attempt.Retry("خطأ في الاتصال: ${e.javaClass.simpleName}.")
        }
    }

    private fun parse(raw: String): Attempt {
        val root = JSONObject(raw)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val block = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            return if (block.isNotEmpty()) Attempt.Fatal("تم حجب الطلب ($block). جرّب صياغة تانية للسؤال.")
            else Attempt.Retry("الخادم رجّع رد فاضي.")
        }
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (!p.optBoolean("thought", false)) sb.append(p.optString("text", ""))
            }
        }
        val text = sb.toString().trim()
        return if (text.isEmpty()) Attempt.Retry("الرد وصل فاضي (${candidate.optString("finishReason")}).")
        else Attempt.Ok(text)
    }

    private fun errorMessage(raw: String): String =
        try {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty().ifEmpty { raw.take(160) }
        } catch (e: Exception) {
            raw.take(160)
        }

    private fun discoverModels(): List<String> =
        try {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
                .header("x-goog-api-key", apiKey)
                .build()
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
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.getString(j) == "generateContent") canGenerate = true
                        }
                    }
                    if (canGenerate && name.startsWith("gemini-") && name.contains("flash") &&
                        EXCLUDED.none { name.contains(it) }
                    ) out.add(name)
                }
                out.sortedDescending()
            }
        } catch (e: Exception) {
            emptyList()
        }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** بالترتيب: الأحدث أولًا، وبعده بدائل أسرع. لو اسم اتقفل بيجرّب اللي بعده تلقائيًا. */
        val MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite")

        val EXCLUDED = listOf(
            "image", "tts", "live", "audio", "embedding", "robotics",
            "vision", "thinking", "cyber", "exp", "computer", "native"
        )

        const val CHAT_SYSTEM =
            "أنت «الطالب الذكي»، مساعد دراسي لطلاب المرحلة الثانوية في مصر (المنهج المصري). " +
                "أجب بالعربية الواضحة، وبلهجة مصرية بسيطة لو كتب الطالب بالعامية. " +
                "اشرح خطوة بخطوة مع مثال عند الحاجة، وكن مختصرًا ودقيقًا. " +
                "لو السؤال خارج الدراسة فأجب باختصار ثم شجّع الطالب على المذاكرة. " +
                "استخدم عناوين قصيرة ونقاطًا فقط، ولا تستخدم الجداول."

        const val EXAM_SYSTEM =
            "أنت معلم خبير في المنهج المصري للثانوية العامة. اكتب الأسئلة مرقمة وواضحة، " +
                "ثم الإجابات النموذجية في النهاية. لا تستخدم الجداول."

        const val SCHEDULE_SYSTEM =
            "أنت مرشد دراسي للمرحلة الثانوية. اقترح جدولًا واقعيًا بفترات مذاكرة قصيرة وراحات، " +
                "وابدأ بالأصعب أو الأقرب موعدًا. استخدم نقاطًا قصيرة بدون جداول."
    }
}

// =====================================================================
//  الثيم
// =====================================================================

private val Indigo = Color(0xFF4F46E5)
private val Violet = Color(0xFF8B5CF6)
private val BrandGradient = Brush.linearGradient(listOf(Violet, Indigo))

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFFEC4899),
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF312E81),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F2F6),
    onSurfaceVariant = Color(0xFF6B7280),
    outlineVariant = Color(0xFFE5E7EB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFF472B6),
    secondaryContainer = Color(0xFF3730A3),
    onSecondaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF181B24),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF232733),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outlineVariant = Color(0xFF2C3140)
)

@Composable
fun SmartStudentTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography(),
            content = content
        )
    }
}

// =====================================================================
//  النشاط الرئيسي
// =====================================================================

class MainActivity : ComponentActivity() {
    private val alarmRepo: AlarmRepository = MockAlarmRepository()
    private val aiEngine: AIEngine = GeminiAIEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("smartstudent_prefs", Context.MODE_PRIVATE)
        setContent {
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
            SmartStudentTheme(darkTheme = dark) {
                MainScreen(
                    alarmRepository = alarmRepo,
                    aiEngine = aiEngine,
                    darkTheme = dark,
                    onDarkThemeChange = {
                        dark = it
                        prefs.edit().putBoolean("dark", it).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    alarmRepository: AlarmRepository,
    aiEngine: AIEngine,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
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
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(BrandGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Text("الطالب الذكي", fontWeight = FontWeight.ExtraBold)
                    }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Notifications, contentDescription = "التنبيهات") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground,
                    actionIconContentColor = cs.onSurfaceVariant
                )
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
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = cs.onSecondaryContainer,
                            selectedTextColor = cs.primary,
                            indicatorColor = cs.secondaryContainer
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
                else -> ProfileTab(darkTheme, onDarkThemeChange, Modifier.padding(padding))
            }
        }
    }
}

// =====================================================================
//  مكونات مشتركة
// =====================================================================

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null
    )
}

@Composable
fun ResultCard(title: String, text: String, isError: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) cs.errorContainer else cs.surface,
            contentColor = if (isError) cs.onErrorContainer else cs.onSurface
        ),
        border = BorderStroke(1.dp, if (isError) cs.error.copy(alpha = 0.3f) else cs.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isError) "تعذّر التنفيذ" else title,
                fontWeight = FontWeight.Bold,
                color = if (isError) cs.error else cs.primary
            )
            SelectionContainer {
                MarkdownText(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp))
            }
        }
    }
}

// ---------- عرض نص Markdown بشكل بسيط (عناوين، نقاط، خط عريض) ----------

private val HEADER_RE = Regex("^#{1,6}\\s+(.*)$")
private val BULLET_RE = Regex("^[*\\-•]\\s+(.*)$")
private val NUMBERED_RE = Regex("^(\\d+)[.)]\\s+(.*)$")
private val RULE_RE = Regex("^([-*_]\\s*){3,}$")

private fun AnnotatedString.Builder.appendInline(line: String) {
    val parts = line.split("**")
    if (parts.size % 2 == 0) {
        append(line.replace("**", ""))
        return
    }
    parts.forEachIndexed { i, seg ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(seg) } else append(seg)
    }
}

fun markdownToAnnotated(src: String): AnnotatedString = buildAnnotatedString {
    var first = true
    for (rawLine in src.lines()) {
        val line = rawLine.trim()
        if (line.startsWith("```") || RULE_RE.matches(line)) continue
        if (!first) append("\n")
        first = false
        val h = HEADER_RE.matchEntire(line)
        val b = BULLET_RE.matchEntire(line)
        val n = NUMBERED_RE.matchEntire(line)
        when {
            h != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) { appendInline(h.groupValues[1]) }
            b != null -> { append("•  "); appendInline(b.groupValues[1]) }
            n != null -> { append("${n.groupValues[1]}.  "); appendInline(n.groupValues[2]) }
            else -> appendInline(line)
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val annotated = remember(text) { markdownToAnnotated(text) }
    Text(annotated, modifier = modifier, style = style)
}

// =====================================================================
//  الرئيسية
// =====================================================================

data class StudyTask(val id: Long, val title: String, val time: String, val done: Boolean = false)

@Composable
fun HomeTab(alarmRepository: AlarmRepository, aiEngine: AIEngine, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val tasks = remember { mutableStateListOf(StudyTask(1L, "مراجعة درس الفيزياء", "اليوم - 05:00 مساءً")) }
    var showAdd by remember { mutableStateOf(false) }
    var scheduleText by remember { mutableStateOf("") }
    var scheduleError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val doneCount = tasks.count { it.done }
    val pendingCount = tasks.size - doneCount
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "صباح الخير ☀️"
        hour < 18 -> "أهلاً بك 👋"
        else -> "مساء النور 🌙"
    }

    if (showAdd) {
        AddTaskDialog(
            onDismiss = { showAdd = false },
            onAdd = { title, time ->
                tasks.add(0, StudyTask(System.currentTimeMillis(), title, time))
                showAdd = false
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(greeting, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                Text(
                    "خلّينا نذاكر النهارده",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.onBackground
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("مهام متبقية", pendingCount.toString(), Icons.Filled.Timer, Color(0xFFF59E0B), Modifier.weight(1f))
                StatCard("مهام منجزة", doneCount.toString(), Icons.Filled.CheckCircle, Color(0xFF10B981), Modifier.weight(1f))
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
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
                                if (pending.isEmpty()) {
                                    scheduleError = false
                                    scheduleText = "أضف مهامك أولًا، وبعدها اضغط على الجدولة التلقائية."
                                } else {
                                    isLoading = true
                                    val list = pending.joinToString("\n") { t ->
                                        "- ${t.title}" + (if (t.time.isNotBlank()) " (${t.time})" else "")
                                    }
                                    scope.launch {
                                        val r = safeAi { aiEngine.autoSchedule(list) }
                                        scheduleText = r.text()
                                        scheduleError = r.isError
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Indigo),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Indigo)
                            } else {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("جدولة تلقائية الآن", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (scheduleText.isNotBlank()) {
            item { ResultCard("جدولك المقترح", scheduleText, scheduleError) }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "مهامك القادمة",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة")
                }
            }
        }

        if (tasks.isEmpty()) {
            item {
                Text(
                    "مفيش مهام لسه. اضغط «إضافة» وابدأ بأول مهمة.",
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        items(tasks, key = { it.id }) { task ->
            TaskRow(
                task = task,
                onToggle = {
                    val i = tasks.indexOfFirst { it.id == task.id }
                    if (i >= 0) tasks[i] = tasks[i].copy(done = !tasks[i].done)
                },
                onDelete = { tasks.removeAll { it.id == task.id } }
            )
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = color) }
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
                Text(
                    task.title,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) cs.onSurfaceVariant else cs.onSurface
                )
                if (task.time.isNotBlank()) {
                    Text(task.time, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مهمة جديدة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("اسم المهمة") }, singleLine = true)
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("الموعد (اختياري)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(title.trim(), time.trim()) }, enabled = title.isNotBlank()) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

// =====================================================================
//  الدردشة
// =====================================================================

@Composable
fun AIChatTab(aiEngine: AIEngine, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val ask: () -> Unit = {
        loading = true
        val history = messages
        scope.launch {
            val r = safeAi { aiEngine.chat(history) }
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
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item { EmptyChatState(onPick = send) }
            }
            itemsIndexed(messages) { index, msg ->
                ChatBubble(
                    message = msg,
                    onRetry = if (msg.isError && index == messages.lastIndex && !loading) retry else null
                )
            }
            if (loading) item { TypingBubble() }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("اكتب سؤالك هنا...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = cs.primary,
                    unfocusedBorderColor = cs.outlineVariant,
                    focusedContainerColor = cs.surface,
                    unfocusedContainerColor = cs.surface
                )
            )
            val canSend = input.isNotBlank() && !loading
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (canSend) cs.primary else cs.outlineVariant)
                    .clickable(enabled = canSend) { send(input) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "إرسال",
                    tint = if (canSend) cs.onPrimary else cs.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).graphicsLayer { scaleX = -1f }
                )
            }
        }
    }
}

@Composable
fun EmptyChatState(onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val suggestions = listOf(
        "اشرح لي قانون نيوتن الثاني ببساطة",
        "لخّص لي درس الوراثة في الأحياء",
        "إزاي أنظّم مذاكرتي قبل الامتحانات؟",
        "اديني أسئلة على الاتزان الكيميائي"
    )
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Text("اسألني أي حاجة في مذاكرتك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "بشرح لك الدروس، وأحل معاك الأسئلة، وأساعدك تنظّم وقتك.",
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        suggestions.forEach { s ->
            AppCard(Modifier.fillMaxWidth().clickable { onPick(s) }) {
                Text(s, Modifier.padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, onRetry: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val container = when {
        message.isUser -> cs.primary
        message.isError -> cs.errorContainer
        else -> cs.surface
    }
    val content = when {
        message.isUser -> cs.onPrimary
        message.isError -> cs.onErrorContainer
        else -> cs.onSurface
    }
    // في الواجهة العربية (RTL): رسالة المستخدم يمين، ورد الذكاء الاصطناعي يسار
    val shape = if (message.isUser) RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp) else RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End
    ) {
        Column(Modifier.widthIn(max = 310.dp)) {
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
                border = if (!message.isUser && !message.isError) BorderStroke(1.dp, cs.outlineVariant) else null
            ) {
                if (message.isUser) {
                    Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
                } else {
                    SelectionContainer {
                        MarkdownText(
                            message.text,
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
                        )
                    }
                }
            }
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إعادة المحاولة")
                }
            }
        }
    }
}

@Composable
fun TypingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        AppCard {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("جاري التفكير...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// =====================================================================
//  الامتحانات
// =====================================================================

@Composable
fun ExamTab(aiEngine: AIEngine, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var subject by remember { mutableStateOf("") }
    var years by remember { mutableStateOf<List<Int>>(emptyList()) }
    var types by remember { mutableStateOf<List<String>>(emptyList()) }
    var resultText by remember { mutableStateOf("") }
    var resultError by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("مولّد الامتحانات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                "اختار المادة وشكل الأسئلة، وهنجهّز لك امتحان بإجاباته النموذجية.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        }

        OptionCard("المادة") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("فيزياء", "رياضيات", "كيمياء", "أحياء", "إنجليزي", "عربي")) { s ->
                    SelectChip(s, subject == s) { subject = s }
                }
            }
        }

        OptionCard("السنوات (اختياري)") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(2026, 2025, 2024, 2023, 2022)) { y ->
                    SelectChip(y.toString(), y in years) { years = if (y in years) years - y else years + y }
                }
            }
        }

        OptionCard("نوع الأسئلة (اختياري)") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("اختيار من متعدد", "صح أو خطأ", "أكمل", "مقالي")) { t ->
                    SelectChip(t, t in types) { types = if (t in types) types - t else types + t }
                }
            }
        }

        Button(
            onClick = {
                generating = true
                resultText = ""
                scope.launch {
                    val r = safeAi { aiEngine.generateExam(subject, years, types) }
                    resultText = r.text()
                    resultError = r.isError
                    generating = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            enabled = subject.isNotBlank() && !generating,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (generating) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text("جاري تجهيز الامتحان...", fontWeight = FontWeight.Bold)
            } else {
                Text("توليد الامتحان", fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(visible = resultText.isNotBlank()) {
            ResultCard("الامتحان", resultText, resultError)
        }
    }
}

@Composable
fun OptionCard(title: String, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

// =====================================================================
//  حسابي
// =====================================================================

@Composable
fun ProfileTab(darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var notificationsEnabled by remember { mutableStateOf(true) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth().height(190.dp).background(
                BrandGradient,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(Color.White).padding(4.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFE0E7FF)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(48.dp), tint = Indigo)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("عبد الرحمن", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text("طالب ثانوي", color = Color.White.copy(alpha = 0.85f))
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileItem(Icons.Outlined.Settings, "الإعدادات العامة", "تعديل بيانات الحساب")
            ProfileItem(Icons.Outlined.Notifications, "التنبيهات", "تفعيل إشعارات المهام") {
                Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
            }
            ProfileItem(Icons.Outlined.DarkMode, "الوضع الليلي", "تغيير مظهر التطبيق") {
                Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
            }
            ProfileItem(Icons.Outlined.Info, "عن التطبيق", "الإصدار 1.0.0")
        }
    }
}

@Composable
fun ProfileItem(icon: ImageVector, title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
        val cs = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = cs.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (trailing != null) trailing()
            else Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null, tint = cs.onSurfaceVariant)
        }
    }
}
