@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartstudent.ui

// =========================================================================================
// 1. الاستيرادات (Imports)
// تم كتابة كل استيراد في سطر منفصل لضمان الدقة وتجنب أي تعارض.
// =========================================================================================
import android.Manifest
import android.app.Activity
import android.app.AlarmManager
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
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image as FoundationImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.smartstudent.BuildConfig
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// =========================================================================================
// 2. الإعدادات العامة (App Config)
// هنا كل الثوابت في مكان واحد: أسماء الـ SharedPreferences، قائمة الموديلات الاحتياطية،
// وخيارات شاشة الامتحانات (الصف / النظام / الشعبة).
// =========================================================================================
object AppConfig {
    const val PREFS_NAME = "smartstudent_prefs"
    const val KEY_DARK = "dark"
    const val KEY_RINGTONE = "ringtone"
    const val KEY_TASKS = "tasks_json"
    const val ALARM_CHANNEL_ID = "smartstudent_alarm_channel"
    const val EXTRA_TASK_TITLE = "title"
    const val ALARM_RING_MILLIS = 30_000L
    const val OPENAI_BASE_URL = "https://api.openai.com/v1/chat/completions"

    val FALLBACK_MODELS: List<String> = listOf(
    "gpt-4o-mini",
    "gpt-4o"
)

    val GRADES: List<String> = listOf("الأول الثانوي", "الثاني الثانوي", "الثالث الثانوي")
    val SYSTEMS: List<String> = listOf("عام", "لغات", "أزهر")
    const val GENERAL_STREAM = "عام (بدون شعبة)"
    val STREAMS: List<String> = listOf("علمي علوم", "علمي رياضة", "أدبي")
}

fun subjectsFor(stream: String): List<String> = when (stream) {
    "علمي علوم" -> listOf("أحياء", "كيمياء", "فيزياء", "لغة عربية", "لغة إنجليزية")
    "علمي رياضة" -> listOf("رياضيات", "فيزياء", "كيمياء", "لغة عربية", "لغة إنجليزية")
    "أدبي" -> listOf("تاريخ", "جغرافيا", "فلسفة ومنطق", "علم نفس واجتماع", "لغة عربية", "لغة إنجليزية")
    else -> listOf("أحياء", "كيمياء", "فيزياء", "رياضيات", "لغة عربية", "لغة إنجليزية")
}
// =========================================================================================
// 3. الكلاسات الأساسية: Application و BroadcastReceiver للمنبه
// =========================================================================================

/** كلاس التطبيق الرئيسي، يحتفظ بنسخة ثابتة من الـ Application عند الحاجة. */
class MyApp : Application() {
    companion object {
        lateinit var instance: MyApp
            private set
    }

    /** يُستدعى عند تشغيل التطبيق لأول مرة. */
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}

/**
 * مستقبل المنبه: عند حلول وقت المهمة يشغّل النغمة المختارة ويهزّ الجهاز ويعرض إشعاراً.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        /** نحتفظ بمرجع للنغمة حتى لا يلتقطها الـ Garbage Collector ويوقف الصوت. */
        private var activeRingtone: Ringtone? = null
    }

    /** نقطة الدخول: تُستدعى من AlarmManager في الموعد المحدد. */
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(AppConfig.EXTRA_TASK_TITLE) ?: "مهمة دراسية"
        playRingtone(context)
        vibrateDevice(context)
        showNotification(context, title)
    }

    /** تشغيل النغمة المحفوظة في الإعدادات (أو نغمة المنبه الافتراضية) ثم إيقافها تلقائياً. */
    private fun playRingtone(context: Context) {
        try {
            val prefs = context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(AppConfig.KEY_RINGTONE, null)
            val uri: Uri = if (!saved.isNullOrBlank()) {
                Uri.parse(saved)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
            activeRingtone?.stop()
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            if (ringtone != null) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                activeRingtone = ringtone
                ringtone.play()
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        ringtone.stop()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, AppConfig.ALARM_RING_MILLIS)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** اهتزاز الجهاز بنمط متقطع مع مراعاة اختلاف الـ API بين الإصدارات. */
    @Suppress("DEPRECATION")
    private fun vibrateDevice(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            val pattern = longArrayOf(0, 500, 300, 500, 300, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** إنشاء قناة الإشعارات (أندرويد 8+) وعرض إشعار "حان الوقت!" عند الضغط عليه يفتح التطبيق. */
    private fun showNotification(context: Context, title: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConfig.ALARM_CHANNEL_ID,
                "منبهات المذاكرة",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "تنبيهات مواعيد المذاكرة والمهام"
            channel.setSound(null, null)
            nm.createNotificationChannel(channel)
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, AppConfig.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان الوقت!")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}

// =========================================================================================
// 4. نماذج البيانات (Data Models)
// =========================================================================================

/** درس واحد داخل باب. */
data class Lesson(val id: Long, val title: String)

/** باب كامل يحتوي على مجموعة دروس. */
data class Chapter(val id: Long, val title: String, val lessons: List<Lesson>)

/** رسالة في شاشة الدردشة (نص + صورة اختيارية + علامة خطأ). */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val imageBitmap: Bitmap? = null
)

/** سؤال اختيار من متعدد مع رقم الإجابة الصحيحة (يبدأ من 0) والشرح. */
data class QuizQuestion(
    val text: String,
    val options: List<String>,
    val correctIdx: Int,
    val explanation: String
)

/** مهمة دراسية لها موعد تنبيه. */
data class StudyTask(
    val id: Long,
    val title: String,
    val triggerAt: Long,
    val done: Boolean = false
)

/** رد محرك الذكاء الاصطناعي: نص + هل هو خطأ؟ */
data class AIResponse(val text: String, val isError: Boolean = false)

/** نتيجة جلب المنهج: إما نجاح بقائمة أبواب، أو فشل برسالة. */
sealed class CurriculumResult {
    data class Success(val chapters: List<Chapter>) : CurriculumResult()
    data class Failure(val message: String) : CurriculumResult()
}

// =========================================================================================
// 5. تخزين المهام (Task Store)
// يحفظ المهام في SharedPreferences بصيغة JSON حتى لا تضيع عند إغلاق التطبيق.
// =========================================================================================
object TaskStore {

    /** قراءة كل المهام المحفوظة، وترجع قائمة فاضية لو حصل أي خطأ في القراءة. */
    fun load(prefs: SharedPreferences): List<StudyTask> {
        val raw = prefs.getString(AppConfig.KEY_TASKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<StudyTask>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(
                    StudyTask(
                        id = o.getLong("id"),
                        title = o.getString("title"),
                        triggerAt = o.getLong("triggerAt"),
                        done = o.optBoolean("done", false)
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** حفظ قائمة المهام كاملة. */
    fun save(prefs: SharedPreferences, tasks: List<StudyTask>) {
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("triggerAt", t.triggerAt)
                    .put("done", t.done)
            )
        }
        prefs.edit().putString(AppConfig.KEY_TASKS, arr.toString()).apply()
    }
}

// =========================================================================================
// 6. حاملات الحالة (State Holders)
// تُنشأ مرة واحدة في MainScreen حتى لا تضيع المحادثة أو تقدّم الامتحان عند تغيير التبويب.
// =========================================================================================

/** حالة شاشة الدردشة. */
class ChatState {
    /** كل رسائل المحادثة. */
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())

    /** هل ننتظر رد الذكاء الاصطناعي الآن؟ */
    var loading by mutableStateOf(false)
}

/** حالة معالج الامتحان بكل خطواته. */
class ExamState {
    /** الخطوة الحالية: 0 صف، 1 نظام، 2 شعبة، 3 مادة، 4 اختيار الدروس، 5 الاختبار، 6 النتيجة. */
    var step by mutableIntStateOf(0)
    var grade by mutableStateOf("")
    var system by mutableStateOf("")
    var stream by mutableStateOf("")
    var subject by mutableStateOf("")

    /** الأبواب والدروس القادمة من الذكاء الاصطناعي. */
    var chapters by mutableStateOf<List<Chapter>>(emptyList())

    /** أرقام الدروس المحددة. */
    var selectedIds by mutableStateOf<Set<Long>>(emptySet())

    var curriculumLoading by mutableStateOf(false)
    var examLoading by mutableStateOf(false)
    var errorMsg by mutableStateOf("")

    var questions by mutableStateOf<List<QuizQuestion>>(emptyList())
    var qIndex by mutableIntStateOf(0)
    var selectedAnswer by mutableStateOf<Int?>(null)
    var score by mutableIntStateOf(0)

    /** عدّاد لإبطال الردود القديمة. */
    var requestToken = 0

    /** الرجوع للبداية بعد تصفير كل شيء. */
    fun resetAll() {
        step = 0
        grade = ""
        system = ""
        stream = ""
        subject = ""
        chapters = emptyList()
        selectedIds = emptySet()
        curriculumLoading = false
        examLoading = false
        errorMsg = ""
        questions = emptyList()
        qIndex = 0
        selectedAnswer = null
        score = 0
    }
}
// =========================================================================================
// 7. محرك الذكاء الاصطناعي (Gemini AI Engine)
// - يستخدم OkHttp للاتصال بـ Gemini.
// - نظام التبديل التلقائي (Fallback): لو موديل فشل ينتقل للتالي في حلقة حتى ينجح أحدهم.
// - مفتاح الـ API من BuildConfig.GEMINI_API_KEY.
// =========================================================================================
  class GeminiAIEngine {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    // هنا نقرا المفتاح الجديد بتاع OpenAI
    private val apiKey: String = BuildConfig.OPENAI_API_KEY.trim()

    private val chatSystemPrompt: String =
        "أنت \"الطالب الذكي\"، مساعد دراسي لطلاب الثانوية العامة في مصر. " +
            "اشرح بأسلوب بسيط ومرتب وبخطوات واضحة، وبالعربية. " +
            "التزم بالمنهج المصري، وإذا لم تكن متأكداً من معلومة فقل ذلك بوضوح."

    private suspend fun makeRequest(payload: JSONObject): AIResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            return@withContext AIResponse("مفتاح OpenAI غير موجود. تأكد من ضبط OPENAI_API_KEY.", true)
        }
        
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        var lastError = "لا توجد تفاصيل للخطأ"

        for (model in AppConfig.FALLBACK_MODELS) {
            ensureActive()
            
            // بنغير اسم الموديل جوه الـ Payload قبل ما نبعته
            payload.put("model", model)

            val request = Request.Builder()
                .url(AppConfig.OPENAI_BASE_URL)
                .header("Authorization", "Bearer $apiKey") // الهيدر بتاع OpenAI
                .post(body)
                .build()
                
            try {
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val root = JSONObject(raw)
                        val choices = root.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val text = choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty()
                            if (text.isNotBlank()) {
                                return@withContext AIResponse(text)
                            }
                        }
                        lastError = "الموديل $model رجّع رداً فارغاً"
                    } else {
                        lastError = "خطأ ${response.code} في موديل $model: ${extractErrorMessage(raw)}"
                    }
                }
            } catch (e: Exception) {
                lastError = "فشل الاتصال بموديل $model: ${e.message}"
            }
        }
        AIResponse("فشلت كل محاولات الاتصال.\n$lastError", true)
    }

    private fun extractErrorMessage(raw: String): String {
        return try {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty().ifBlank { raw.take(120) }
        } catch (e: Exception) {
            raw.take(120)
        }
    }

    suspend fun chat(history: List<ChatMessage>, imageBase64: String? = null): AIResponse {
        val usable = history.filter { !it.isError && it.text.isNotBlank() }.takeLast(10).dropWhile { !it.isUser }
        if (usable.isEmpty()) return AIResponse("لا توجد رسالة لإرسالها.", true)

        val messagesArray = JSONArray()
        
        // إضافة تعليمات النظام
        messagesArray.put(JSONObject().put("role", "system").put("content", chatSystemPrompt))

        // إضافة رسائل المحادثة
        usable.forEach { msg ->
            messagesArray.put(JSONObject().put("role", if (msg.isUser) "user" else "assistant").put("content", msg.text))
        }
        
        val payload = JSONObject().put("messages", messagesArray)
        
        // لو في صورة مرفقة
        if (imageBase64 != null && usable.last().isUser) {
             val lastMsg = messagesArray.getJSONObject(messagesArray.length() - 1)
             val contentArray = JSONArray()
             contentArray.put(JSONObject().put("type", "text").put("text", lastMsg.getString("content")))
             contentArray.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")))
             lastMsg.put("content", contentArray)
        }

        return makeRequest(payload)
    }

    suspend fun generateCurriculum(grade: String, system: String, stream: String, subject: String): AIResponse {
        val prompt = """
            أنت خبير في المناهج الرسمية لوزارة التربية والتعليم في مصر.
            اذكر أبواب ودروس مادة "$subject" للصف "$grade" بنظام "$system" والشعبة "$stream".
            أرجع JSON صارماً فقط بهذا الشكل: {"chapters":[{"title":"اسم الباب","lessons":["الدرس 1","الدرس 2"]}]}
        """.trimIndent()
        
        val messagesArray = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
        val payload = JSONObject()
            .put("messages", messagesArray)
            .put("response_format", JSONObject().put("type", "json_object")) // إجبار OpenAI على إرجاع JSON
            
        return makeRequest(payload)
    }

    suspend fun generateInteractiveExam(grade: String, system: String, stream: String, subject: String, topics: String): AIResponse {
        val prompt = """
            أنت معلم. اكتب اختباراً تفاعلياً من 5 أسئلة لمادة $subject (الصف: $grade، الشعبة: $stream).
            الدروس: $topics.
            التنسيق:
            Q: [سؤال]
            O1: [خيار]
            O2: [خيار]
            O3: [خيار]
            O4: [خيار]
            C: [رقم الصح 1-4]
            E: [الشرح]
        """.trimIndent()
        
        val messagesArray = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
        return makeRequest(JSONObject().put("messages", messagesArray))
    }
  }  

  // =========================================================================================
// 8. مستودع المنهج (Curriculum Repository)
// =========================================================================================

/** واجهة المستودع. */
interface CurriculumRepository {
    /** يرجع أبواب ودروس المادة (نجاح بقائمة، أو فشل برسالة). */
    suspend fun getChapters(grade: String, system: String, stream: String, subject: String): CurriculumResult
}

/** تنفيذ المستودع الذي يعتمد على الذكاء الاصطناعي. */
class AiCurriculumRepository(private val aiEngine: GeminiAIEngine) : CurriculumRepository {

    /** كاش بسيط في الذاكرة: المفتاح هو (الصف|النظام|الشعبة|المادة). */
    private val cache = mutableMapOf<String, List<Chapter>>()

    /** يرجع الأبواب من الكاش لو موجودة، وإلا يطلبها من الذكاء الاصطناعي ويحللها. */
    override suspend fun getChapters(
        grade: String,
        system: String,
        stream: String,
        subject: String
    ): CurriculumResult {
        val key = "$grade|$system|$stream|$subject"
        cache[key]?.let { return CurriculumResult.Success(it) }

        val response = aiEngine.generateCurriculum(grade, system, stream, subject)
        if (response.isError) return CurriculumResult.Failure(response.text)

        return try {
            val chapters = CurriculumParser.parse(response.text)
            if (chapters.isEmpty()) {
                CurriculumResult.Failure("الذكاء الاصطناعي لم يرجّع أبواباً مفهومة. جرّب مرة أخرى.")
            } else {
                cache[key] = chapters
                CurriculumResult.Success(chapters)
            }
        } catch (e: Exception) {
            CurriculumResult.Failure("تعذّر قراءة المنهج (الرد ليس JSON صحيحاً). جرّب مرة أخرى.")
        }
    }
}

/** محلل JSON الخاص بالمنهج: يحوّل رد الذكاء الاصطناعي إلى قائمة Chapters و Lessons. */
object CurriculumParser {

    /** يقصّ النص من أول { أو [ إلى آخر } أو ] لتجاهل أي كلام زائد أو علامات Markdown. */
    private fun extractJsonBlock(raw: String): String {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOfFirst { it == '{' || it == '[' }
        val end = cleaned.indexOfLast { it == '}' || it == ']' }
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
    }

    /** التحليل الرئيسي. يدعم رد على شكل {"chapters":[...]} أو مصفوفة مباشرة [...]. */
    fun parse(raw: String): List<Chapter> {
        val json = extractJsonBlock(raw)
        val chaptersArray: JSONArray = if (json.trimStart().startsWith("[")) {
            JSONArray(json)
        } else {
            JSONObject(json).optJSONArray("chapters") ?: JSONArray()
        }

        val result = mutableListOf<Chapter>()
        for (i in 0 until chaptersArray.length()) {
            val chapterObj = chaptersArray.optJSONObject(i) ?: continue
            val chapterTitle = chapterObj.optString("title", "").trim()
            val lessonsArray = chapterObj.optJSONArray("lessons") ?: continue
            val chapterId = (i + 1) * 1000L

            val lessons = mutableListOf<Lesson>()
            for (j in 0 until lessonsArray.length()) {
                val item = lessonsArray.opt(j)
                val lessonTitle: String = when (item) {
                    is String -> item
                    is JSONObject -> item.optString("title", item.optString("name", ""))
                    else -> ""
                }
                if (lessonTitle.isNotBlank()) {
                    lessons.add(Lesson(chapterId + j + 1, lessonTitle.trim()))
                }
            }
            if (chapterTitle.isNotBlank() && lessons.isNotEmpty()) {
                result.add(Chapter(chapterId, chapterTitle, lessons))
            }
        }
        return result
    }
}

// =========================================================================================
// 9. محلل الامتحان (Quiz Parser)
// =========================================================================================
// =========================================================================================
// 9. محلل الامتحان (Quiz Parser)
// يحوّل نص Gemini ذي التنسيق (Q / O1-O4 / C / E) إلى قائمة QuizQuestion.
// =========================================================================================
fun parseQuiz(text: String): List<QuizQuestion> {
    val questions = mutableListOf<QuizQuestion>()
    val qRegex = Regex("^Q\\d*\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)
    val oRegex = Regex("^O([1-4])\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)
    val cRegex = Regex("^C\\s*[:：]\\s*([1-4])", RegexOption.IGNORE_CASE)
    val eRegex = Regex("^E\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)

    var qText = ""
    var options = arrayOfNulls<String>(4)
    var correct = -1
    var explanation = ""
    var lastWasExplanation = false

    fun flush() {
        if (qText.isNotBlank() && correct in 0..3 && options.all { !it.isNullOrBlank() }) {
            questions.add(QuizQuestion(qText, options.map { it.orEmpty() }, correct, explanation.trim()))
        }
        qText = ""
        options = arrayOfNulls(4)
        correct = -1
        explanation = ""
        lastWasExplanation = false
    }

    val cleanText = text.replace("```json", "").replace("```", "").replace("**", "")
    for (rawLine in cleanText.lines()) {
        val line = rawLine.trim().trimStart('-', '•', '#', ' ')
        if (line.isEmpty()) continue

        val q = qRegex.find(line)
        val o = oRegex.find(line)
        val c = cRegex.find(line)
        val e = eRegex.find(line)
        when {
            q != null -> { flush(); qText = q.groupValues[1].trim() }
            o != null -> { options[o.groupValues[1].toInt() - 1] = o.groupValues[2].trim(); lastWasExplanation = false }
            c != null -> { correct = c.groupValues[1].toInt() - 1; lastWasExplanation = false }
            e != null -> { explanation = e.groupValues[1].trim(); lastWasExplanation = true }
            lastWasExplanation -> explanation += " " + line
            else -> Unit
        }
    }
    flush()
    return questions
}

// =========================================================================================
// 10. الثيم (Material Design 3 + RTL + Gradient + Dark Mode)
// =========================================================================================
private val Indigo = Color(0xFF4F46E5)
private val Violet = Color(0xFF8B5CF6)
private val BrandGradient = Brush.linearGradient(listOf(Violet, Indigo))

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
// 11. دوال مساعدة (Helpers)
// =========================================================================================

/** يحمّل صورة من الـ Uri ويصغّرها (أقصى ضلع 1024 بكسل) لتفادي OutOfMemory. */
fun loadScaledBitmap(ctx: Context, uri: Uri, maxSide: Int = 1024): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) {
            sample *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        val largest = maxOf(decoded.width, decoded.height)
        if (largest > maxSide) {
            val scale = maxSide.toFloat() / largest
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt(),
                (decoded.height * scale).toInt(),
                true
            )
        } else {
            decoded
        }
    } catch (e: Exception) {
        null
    }
}

/** يحوّل الصورة إلى Base64 بصيغة JPEG لإرسالها إلى الموديل. */
fun bitmapToBase64(bitmap: Bitmap): String {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

/** يبني الـ PendingIntent الخاص بمهمة معينة (نفس الدالة تُستخدم للضبط والإلغاء). */
fun buildAlarmPendingIntent(ctx: Context, task: StudyTask): PendingIntent {
    val intent = Intent(ctx, AlarmReceiver::class.java).putExtra(AppConfig.EXTRA_TASK_TITLE, task.title)
    return PendingIntent.getBroadcast(
        ctx, task.id.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** هل يسمح النظام بالمنبهات الدقيقة؟ (دائماً true قبل أندرويد 12). */
fun canUseExactAlarms(ctx: Context): Boolean {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
}

/** ضبط المنبه. لو الصلاحية ممنوحة نستخدم setExactAndAllowWhileIdle، وإلا نستخدم setAndAllowWhileIdle. */
fun scheduleAlarm(ctx: Context, task: StudyTask): Boolean {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pending = buildAlarmPendingIntent(ctx, task)
    if (canUseExactAlarms(ctx)) {
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, pending)
            return true
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, pending)
    return false
}

/** إلغاء منبه مهمة (عند حذفها أو إنهائها). */
fun cancelAlarm(ctx: Context, task: StudyTask) {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    am.cancel(buildAlarmPendingIntent(ctx, task))
}

/** تنسيق موعد المهمة للعرض، مثال: 2026/10/27 - 08:30 م. */
fun formatTaskTime(millis: Long): String {
    val formatter = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar", "EG"))
    return formatter.format(Date(millis))
}

// =========================================================================================
// 12. المكونات المشتركة (Shared UI Components)
// =========================================================================================

/** بطاقة بخلفية متدرجة تعرض عنواناً ونصاً فرعياً. */
@Composable
fun GradientCard(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        Column(Modifier.background(BrandGradient).padding(20.dp)) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

/** بطاقة خيار قابلة للضغط تُستخدم في خطوات معالج الامتحان. */
@Composable
fun OptionCard(text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** عنوان قسم موحّد. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

// =========================================================================================
// 13. النشاط الرئيسي (MainActivity) - جزء من الإعداد
// =========================================================================================
class MainActivity : ComponentActivity() {

    /** محرك الذكاء الاصطناعي (نسخة واحدة لكل التطبيق). */
    private val aiEngine = GeminiAIEngine()

    /** مستودع المنهج المعتمد على الذكاء الاصطناعي بدل البيانات الثابتة. */
    private val repo: CurriculumRepository = AiCurriculumRepository(aiEngine)

    /** طلب صلاحية الإشعارات (أندرويد 13+). */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** نقطة بداية التطبيق: يطلب صلاحية الإشعارات ثم يعرض الواجهة بالثيم المحفوظ. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val prefs = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        setContent {
            var dark by remember { mutableStateOf(prefs.getBoolean(AppConfig.KEY_DARK, false)) }
            SmartStudentTheme(darkTheme = dark) {
                MainScreen(
                    aiEngine = aiEngine,
                    repo = repo,
                    darkTheme = dark,
                    onDarkThemeChange = {
                        dark = it
                        prefs.edit().putBoolean(AppConfig.KEY_DARK, it).apply()
                    },
                    prefs = prefs
                )
            }
        }
    }
}
// =========================================================================================
// 14. واجهة التنقل الرئيسية (Main Screen & Navigation)
// =========================================================================================

/**
 * الشاشة الرئيسية: شريط علوي متدرج + شريط تنقل سفلي بأربعة أقسام.
 * يتم الاحتفاظ بحالة الدردشة والامتحانات هنا حتى لا تضيع عند التنقل بين التبويبات.
 */
@Composable
fun MainScreen(
    aiEngine: GeminiAIEngine,
    repo: CurriculumRepository,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    prefs: SharedPreferences
) {
    // متغير لتحديد التبويب النشط حالياً (0: الرئيسية، 1: الدردشة، 2: الامتحانات، 3: حسابي)
    var selectedTab by remember { mutableIntStateOf(0) }

    // سكوب للكوروتينات مرتبط بدورة حياة هذه الشاشة
    val appScope = rememberCoroutineScope()

    // حاملات الحالة المحفوظة (State Holders) للدردشة والامتحانات
    val chatState = remember { ChatState() }
    val examState = remember { ExamState() }

    // قوائم التبويبات والأيقونات الخاصة بها
    val tabs = listOf("الرئيسية", "دردشة AI", "الامتحانات", "حسابي")
    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Chat,
        Icons.Filled.Description,
        Icons.Filled.Person
    )

    // استخدام Scaffold لإنشاء الهيكل الأساسي (شريط علوي + شريط سفلي + محتوى)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "الطالب الذكي",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, // شفاف ليأخذ لون التدرج من الموديفاير
                    titleContentColor = Color.White
                ),
                modifier = Modifier.background(BrandGradient)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                icons[index],
                                contentDescription = label
                            )
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        // توزيع الشاشات بناءً على التبويب المختار
        when (selectedTab) {
            0 -> HomeTab(
                prefs = prefs,
                modifier = Modifier.padding(paddingValues)
            )
            1 -> AIChatTab(
                aiEngine = aiEngine,
                state = chatState,
                scope = appScope,
                modifier = Modifier.padding(paddingValues)
            )
            2 -> ExamWizardTab(
                aiEngine = aiEngine,
                repo = repo,
                state = examState,
                scope = appScope,
                modifier = Modifier.padding(paddingValues)
            )
            else -> ProfileTab(
                dark = darkTheme,
                onDark = onDarkThemeChange,
                prefs = prefs,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

// =========================================================================================
// 15. الشاشة الرئيسية: المهام والمنبهات (Home Tab) - تم إصلاح الزرار العائم
// =========================================================================================

/**
 * شاشة المهام الرئيسية.
 * تم استخدام Box بدلاً من Scaffold داخلي لتجنب تداخل الأبعاد، مما يضمن ظهور الزرار العائم (FAB) بشكل صحيح.
 */
@Composable
fun HomeTab(
    prefs: SharedPreferences,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current

    // قائمة المهام القابلة للتحديث (MutableStateList) لتعكس أي إضافة أو حذف فوراً
    val tasks = remember {
        mutableStateListOf<StudyTask>().apply {
            addAll(TaskStore.load(prefs))
        }
    }

    // حالة إظهار نافذة إضافة مهمة جديدة
    var showDialog by remember { mutableStateOf(false) }

    // حقول الإدخال في النافذة
    var title by remember { mutableStateOf("") }
    var pickedDate by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var pickedTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    /** دالة داخلية لحفظ المهام في الذاكرة الدائمة (SharedPreferences) */
    fun persist() {
        TaskStore.save(prefs, tasks.toList())
    }

    // عند فتح التطبيق، نقوم بإعادة جدولة المنبهات القادمة التي لم تنتهِ بعد
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        tasks.filter { !it.done && it.triggerAt > now }.forEach { task ->
            scheduleAlarm(ctx, task)
        }
    }

    /** تصفير الحقول عند إغلاق النافذة */
    fun resetDialogFields() {
        title = ""
        pickedDate = null
        pickedTime = null
    }

    /** فتح نافذة اختيار التاريخ */
    fun openDatePicker() {
        val now = Calendar.getInstance()
        val dialog = DatePickerDialog(
            ctx,
            { _, year, month, day ->
                pickedDate = Triple(year, month, day)
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        )
        // منع اختيار تاريخ في الماضي
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        dialog.show()
    }

    /** فتح نافذة اختيار الوقت */
    fun openTimePicker() {
        val now = Calendar.getInstance()
        TimePickerDialog(
            ctx,
            { _, hour, minute ->
                pickedTime = Pair(hour, minute)
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            false // تنسيق 12 ساعة
        ).show()
    }

    /** إضافة المهمة بعد التحقق من صحة البيانات وضبط المنبه */
    fun addTask() {
        val date = pickedDate
        val time = pickedTime

        if (title.isBlank() || date == null || time == null) {
            Toast.makeText(ctx, "أكمل البيانات أولاً (الاسم والتاريخ والوقت)", Toast.LENGTH_SHORT).show()
            return
        }

        val cal = Calendar.getInstance()
        cal.set(date.first, date.second, date.third, time.first, time.second, 0)
        cal.set(Calendar.MILLISECOND, 0)

        if (cal.timeInMillis <= System.currentTimeMillis()) {
            Toast.makeText(ctx, "الموعد لازم يكون في المستقبل", Toast.LENGTH_SHORT).show()
            return
        }

        val task = StudyTask(
            id = System.currentTimeMillis(),
            title = title.trim(),
            triggerAt = cal.timeInMillis
        )

        tasks.add(task)
        tasks.sortBy { it.triggerAt } // ترتيب المهام حسب الأقرب
        persist()

        val exact = scheduleAlarm(ctx, task)
        if (!exact) {
            Toast.makeText(
                ctx,
                "تم ضبط منبه تقريبي. فعّل المنبهات الدقيقة من تبويب حسابي لدقة أعلى.",
                Toast.LENGTH_LONG
            ).show()
        }

        showDialog = false
        resetDialogFields()
    }

    // نافذة إضافة مهمة جديدة
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("مهمة جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("اسم المهمة (مثال: درس الفيزياء)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openDatePicker() }) {
                            val d = pickedDate
                            Text(
                                if (d != null) {
                                    String.format(Locale.US, "%04d/%02d/%02d", d.first, d.second + 1, d.third)
                                } else {
                                    "التاريخ"
                                }
                            )
                        }
                        OutlinedButton(onClick = { openTimePicker() }) {
                            val t = pickedTime
                            Text(
                                if (t != null) {
                                    val hour12 = if (t.first % 12 == 0) 12 else t.first % 12
                                    val suffix = if (t.first < 12) "ص" else "م"
                                    String.format(Locale.US, "%02d:%02d %s", hour12, t.second, suffix)
                                } else {
                                    "الوقت"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { addTask() }) { Text("إضافة") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    resetDialogFields()
                }) { Text("إلغاء") }
            }
        )
    }

    // ---------------------------------------------------------------------------------
    // إصلاح مشكلة اختفاء الزرار العائم: استخدام Box كحاوية رئيسية
    // ---------------------------------------------------------------------------------
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "أهلاً بك 👋",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))

            val doneCount = tasks.count { it.done }
            GradientCard(
                title = "مساعدك الذكي",
                subtitle = if (tasks.isEmpty()) {
                    "ذاكر بذكاء ووفر وقتك. أضف أول موعد مذاكرة بزر +"
                } else {
                    "أنجزت $doneCount من ${tasks.size} مهمة"
                }
            )
            Spacer(Modifier.height(16.dp))
            SectionTitle("مهامك القادمة")
            Spacer(Modifier.height(8.dp))

            if (tasks.isEmpty()) {
                Text(
                    "لا توجد مهام بعد.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            // مسافة سفلية في نهاية القائمة حتى لا يغطي الزرار العائم على آخر مهمة
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { checked ->
                            val index = tasks.indexOfFirst { it.id == task.id }
                            if (index >= 0) {
                                val updated = task.copy(done = checked)
                                tasks[index] = updated
                                persist()
                                if (checked) {
                                    cancelAlarm(ctx, updated)
                                } else if (updated.triggerAt > System.currentTimeMillis()) {
                                    scheduleAlarm(ctx, updated)
                                }
                            }
                        },
                        onDelete = {
                            cancelAlarm(ctx, task)
                            tasks.remove(task)
                            persist()
                        }
                    )
                }
            }
        }

        // الزرار العائم (FAB) في الركن السفلي الأيمن
        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "مهمة جديدة")
        }
    }
}

/**
 * بطاقة مهمة واحدة: خانة إنجاز + العنوان + الموعد + زر حذف.
 */
@Composable
fun TaskCard(
    task: StudyTask,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.done,
                onCheckedChange = onToggle
            )
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null
                )
                Text(
                    formatTaskTime(task.triggerAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "حذف",
                    tint = Color(0xFFE53935)
                )
            }
        }
    }
}

// =========================================================================================
// 16. شاشة الدردشة (AI Chat Tab)
// =========================================================================================

/**
 * فقاعة رسالة واحدة (لون مختلف للمستخدم والذكاء الاصطناعي والخطأ).
 */
@Composable
fun ChatBubble(msg: ChatMessage) {
    val scheme = MaterialTheme.colorScheme

    // تحديد الخلفية الأمامية بناءً على مرسل الرسالة وحالتها
    val background = when {
        msg.isError -> scheme.errorContainer
        msg.isUser -> scheme.primary
        else -> scheme.surface
    }
    val foreground = when {
        msg.isError -> scheme.onErrorContainer
        msg.isUser -> scheme.onPrimary
        else -> scheme.onSurface
    }

    // عرض الرسالة: المستخدم على اليسار (Start)، والبوت على اليمين (End) بسبب RTL
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.Start else Arrangement.End
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = background)
        ) {
            Column(Modifier.padding(12.dp)) {
                // إذا كانت الرسالة تحتوي على صورة مرفقة، يتم عرضها
                msg.imageBitmap?.let { bmp ->
                    FoundationImage(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "الصورة المرفقة",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(msg.text, color = foreground)
            }
        }
    }
}

/**
 * تحويل رمز خطأ SpeechRecognizer إلى رسالة مفهومة للمستخدم.
 */
fun speechErrorMessage(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "الإدخال الصوتي يحتاج اتصال إنترنت"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحية الميكروفون غير ممنوحة"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "خدمة التعرف على الصوت مشغولة، حاول بعد لحظات"
    else -> "تعذّر التعرف على الصوت (رمز $code)"
}

/**
 * شاشة الدردشة الكاملة: قائمة الرسائل + شريط الإدخال (صورة / نص / صوت / إرسال).
 * الحالة (الرسائل والتحميل) محفوظة في ChatState حتى لا تضيع عند تغيير التبويب.
 */
@Composable
fun AIChatTab(
    aiEngine: GeminiAIEngine,
    state: ChatState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()

    // حالة الإدخال المحلية
    var input by remember { mutableStateOf("") }
    var imgB64 by remember { mutableStateOf<String?>(null) }
    var imgBmp by remember { mutableStateOf<Bitmap?>(null) }
    var listening by remember { mutableStateOf(false) }

    // ---------------- الإدخال الصوتي ----------------
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(ctx) }
    val recognizer = remember { if (speechAvailable) SpeechRecognizer.createSpeechRecognizer(ctx) else null }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    // ربط المستمع بالـ recognizer، وتنظيفه عند مغادرة الشاشة
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onError(error: Int) {
                listening = false
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Toast.makeText(ctx, speechErrorMessage(error), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    input = text
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    input = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    // بديل لو الجهاز ما فيهوش خدمة SpeechRecognizer
    val speechFallbackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                input = text
            }
        }
    }

    // طلب صلاحية الميكروفون
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            listening = true
            recognizer?.startListening(speechIntent)
        } else {
            Toast.makeText(ctx, "لازم تسمح بصلاحية الميكروفون عشان الإدخال الصوتي", Toast.LENGTH_SHORT).show()
        }
    }

    /** زر الميكروفون: يبدأ الاستماع أو يوقفه */
    fun toggleMic() {
        if (recognizer == null) {
            try {
                speechFallbackLauncher.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                )
            } catch (e: Exception) {
                Toast.makeText(ctx, "الإدخال الصوتي غير مدعوم على جهازك", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (listening) {
            recognizer.stopListening()
            listening = false
            return
        }

        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            listening = true
            recognizer.startListening(speechIntent)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ---------------- اختيار صورة من المعرض ----------------
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { loadScaledBitmap(ctx, uri) }
                if (bmp != null) {
                    val b64 = withContext(Dispatchers.IO) { bitmapToBase64(bmp) }
                    imgBmp = bmp
                    imgB64 = b64
                } else {
                    Toast.makeText(ctx, "تعذّر تحميل الصورة", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** إرسال الرسالة (نص و/أو صورة) واستقبال رد الذكاء الاصطناعي */
    fun sendMessage() {
        if ((input.isBlank() && imgB64 == null) || state.loading) return
        if (listening) {
            recognizer?.stopListening()
            listening = false
        }

        val question = if (input.isBlank()) "حلل الصورة وساعدني في حلها" else input.trim()
        val updated = state.messages + ChatMessage(text = question, isUser = true, imageBitmap = imgBmp)
        state.messages = updated

        val imageToSend = imgB64
        input = ""
        imgBmp = null
        imgB64 = null
        state.loading = true

        scope.launch {
            val res = aiEngine.chat(updated, imageToSend)
            state.messages = state.messages + ChatMessage(text = res.text, isUser = false, isError = res.isError)
            state.loading = false
        }
    }

    // تمرير تلقائي لآخر رسالة عند أي تغيير
    LaunchedEffect(state.messages.size, state.loading) {
        val count = state.messages.size + if (state.loading) 1 else 0
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    // ---------------- واجهة الشاشة ----------------
    Column(modifier.fillMaxSize().imePadding().padding(16.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.messages.isEmpty()) {
                item {
                    GradientCard(
                        title = "اسألني في أي مادة",
                        subtitle = "اكتب سؤالك، أو صوّره، أو قوله بصوتك وأنا هساعدك."
                    )
                }
            }

            items(state.messages, key = { it.id }) { msg ->
                ChatBubble(msg)
            }

            if (state.loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("جاري التفكير...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // معاينة الصورة المختارة قبل الإرسال
        val previewBitmap = imgBmp
        if (previewBitmap != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FoundationImage(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "معاينة الصورة",
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    imgBmp = null
                    imgB64 = null
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "إلغاء الصورة", tint = Color(0xFFE53935))
                }
            }
        }

        // شريط الإدخال: صورة / نص / ميكروفون / إرسال
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { imageLauncher.launch("image/*") }) {
                Icon(Icons.Filled.Image, contentDescription = "صورة", tint = MaterialTheme.colorScheme.primary)
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (listening) "جاري الاستماع..." else "اكتب سؤالك...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            IconButton(onClick = { toggleMic() }) {
                Icon(
                    if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = "تسجيل صوتي",
                    tint = if (listening) Color(0xFFE53935) else MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = { sendMessage() }, enabled = !state.loading) {
                Icon(Icons.Filled.Send, contentDescription = "إرسال", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
// =========================================================================================
// 17. منطق معالج الامتحان (Exam Wizard Logic)
// =========================================================================================

/**
 * يجلب أبواب ودروس المادة الحالية من Gemini (JSON ثم Parser).
 * نستخدم requestToken لتجاهل أي رد قديم لو المستخدم رجع أو غيّر المادة أثناء الانتظار.
 */
fun loadCurriculum(state: ExamState, repo: CurriculumRepository, scope: CoroutineScope) {
    state.requestToken += 1
    val token = state.requestToken
    state.curriculumLoading = true
    state.errorMsg = ""
    state.chapters = emptyList()
    state.selectedIds = emptySet()
    scope.launch {
        val result = repo.getChapters(state.grade, state.system, state.stream, state.subject)
        if (token == state.requestToken) {
            when (result) {
                is CurriculumResult.Success -> state.chapters = result.chapters
                is CurriculumResult.Failure -> state.errorMsg = result.message
            }
            state.curriculumLoading = false
        }
    }
}

/**
 * يولّد امتحان الـ 5 أسئلة من الدروس المحددة، وينتقل لشاشة الاختبار عند النجاح.
 */
fun generateExam(state: ExamState, aiEngine: GeminiAIEngine, scope: CoroutineScope) {
    val topics = state.chapters
        .flatMap { it.lessons }
        .filter { state.selectedIds.contains(it.id) }
        .joinToString("، ") { it.title }
    if (topics.isBlank()) return

    state.requestToken += 1
    val token = state.requestToken
    state.examLoading = true
    state.errorMsg = ""
    scope.launch {
        val response = aiEngine.generateInteractiveExam(state.grade, state.system, state.stream, state.subject, topics)
        if (token == state.requestToken) {
            if (response.isError) {
                state.errorMsg = response.text
            } else {
                val parsed = parseQuiz(response.text)
                if (parsed.isNotEmpty()) {
                    state.questions = parsed
                    state.qIndex = 0
                    state.score = 0
                    state.selectedAnswer = null
                    state.step = 5
                } else {
                    state.errorMsg = "رد الذكاء الاصطناعي غير مفهوم. جرّب تاني."
                }
            }
            state.examLoading = false
        }
    }
}

/**
 * زر/إيماءة الرجوع: يرجع خطوة واحدة ويلغي أي طلب جارٍ.
 */
fun examGoBack(state: ExamState) {
    state.requestToken += 1
    state.curriculumLoading = false
    state.examLoading = false
    state.errorMsg = ""
    state.step = when (state.step) {
        5, 6 -> 4
        in 1..4 -> state.step - 1
        else -> 0
    }
}

// =========================================================================================
// 18. شاشة الامتحانات (Exam Wizard Tab)
// الخطوات: الصف ← النظام ← الشعبة ← المادة ← (جلب المنهج) ← اختيار الدروس ← الاختبار ← النتيجة
// =========================================================================================

/** رأس الخطوة: زر رجوع (لو متاح) + العنوان. */
@Composable
fun WizardHeader(title: String, showBack: Boolean, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            // في الاتجاه RTL سهم "الرجوع" يشير لليمين، لذلك نستخدم ArrowForward.
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** خطوة اختيار بسيطة: عنوان + قائمة بطاقات. */
@Composable
fun WizardOptionStep(
    title: String,
    options: List<String>,
    showBack: Boolean,
    onBack: () -> Unit,
    onPick: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WizardHeader(title, showBack, onBack)
        options.forEach { option -> OptionCard(option) { onPick(option) } }
    }
}

/** بطاقة باب: خانة لتحديد الباب كله + زر لفرد دروسه وتحديد درس معين. */
@Composable
fun ChapterCard(chapter: Chapter, selectedIds: Set<Long>, onSelectionChange: (Set<Long>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val chapterLessonIds = chapter.lessons.map { it.id }.toSet()
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selectedIds.containsAll(chapterLessonIds),
                    onCheckedChange = { checked ->
                        onSelectionChange(if (checked) selectedIds + chapterLessonIds else selectedIds - chapterLessonIds)
                    }
                )
                Text(
                    chapter.title,
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = "عرض الدروس"
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 24.dp, bottom = 8.dp)) {
                    chapter.lessons.forEach { lesson ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedIds.contains(lesson.id),
                                onCheckedChange = { checked ->
                                    onSelectionChange(if (checked) selectedIds + lesson.id else selectedIds - lesson.id)
                                }
                            )
                            Text(lesson.title, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

/** خطوة اختيار الدروس (درس معين / باب كامل / المنهج كله) مع حالات التحميل والخطأ. */
@Composable
fun LessonPickerStep(
    state: ExamState,
    repo: CurriculumRepository,
    aiEngine: GeminiAIEngine,
    scope: CoroutineScope
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WizardHeader("اختر الدروس", true) { examGoBack(state) }
        Text(
            "مادة ${state.subject} - ${state.grade}",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        when {
            // 1) جاري جلب المنهج من الذكاء الاصطناعي
            state.curriculumLoading -> {
                Column(
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        "جاري جلب أبواب ودروس المنهج...",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // 2) فشل الجلب: نعرض الرسالة وزر إعادة المحاولة
            state.chapters.isEmpty() -> {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            state.errorMsg.ifBlank { "لم يتم العثور على دروس." },
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(onClick = { loadCurriculum(state, repo, scope) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("إعادة المحاولة")
                        }
                    }
                }
            }

            // 3) المنهج جاهز: قائمة الأبواب والدروس
            else -> {
                if (state.errorMsg.isNotBlank()) {
                    Text(state.errorMsg, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        state.selectedIds = state.chapters.flatMap { it.lessons }.map { it.id }.toSet()
                    }) { Text("المنهج كله") }
                    OutlinedButton(onClick = { state.selectedIds = emptySet() }) { Text("مسح التحديد") }
                }
                Text(
                    "تم اختيار ${state.selectedIds.size} درس. حدّد باباً كاملاً أو افرده لاختيار درس معين.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                LazyColumn(Modifier.weight(1f)) {
                    items(state.chapters, key = { it.id }) { chapter ->
                        ChapterCard(chapter, state.selectedIds) { newSelection -> state.selectedIds = newSelection }
                    }
                }
                Button(
                    onClick = { generateExam(state, aiEngine, scope) },
                    enabled = state.selectedIds.isNotEmpty() && !state.examLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.examLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text("توليد الامتحان (5 أسئلة)")
                    }
                }
            }
        }
    }
}

/** شاشة الاختبار: سؤال واحد، الصحيح أخضر والخطأ أحمر، ثم مربع الشرح وزر التالي. */
@Composable
fun QuizStep(state: ExamState) {
    val question = state.questions.getOrNull(state.qIndex) ?: return
    val answered = state.selectedAnswer != null
    val isLast = state.qIndex + 1 >= state.questions.size

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "سؤال ${state.qIndex + 1} من ${state.questions.size}",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        LinearProgressIndicator(
            progress = { (state.qIndex + 1f) / state.questions.size },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            question.text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        question.options.forEachIndexed { index, option ->
            val isCorrect = index == question.correctIdx
            val isWrongChoice = answered && index == state.selectedAnswer && !isCorrect
            val background = when {
                answered && isCorrect -> Color(0xFFD1FAE5)
                isWrongChoice -> Color(0xFFFEE2E2)
                else -> MaterialTheme.colorScheme.surface
            }
            // نحدد لون النص صراحة حتى يظهر بوضوح فوق الأخضر/الأحمر الفاتح في الوضع الليلي.
            val foreground = when {
                answered && isCorrect -> Color(0xFF065F46)
                isWrongChoice -> Color(0xFF991B1B)
                else -> MaterialTheme.colorScheme.onSurface
            }
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !answered) {
                        state.selectedAnswer = index
                        if (isCorrect) state.score += 1
                    },
                colors = CardDefaults.cardColors(containerColor = background)
            ) {
                Text(option, Modifier.padding(16.dp), color = foreground)
            }
        }

        AnimatedVisibility(visible = answered) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    "الشرح: " + question.explanation,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }

        if (answered) {
            Button(
                onClick = {
                    if (isLast) {
                        state.step = 6
                    } else {
                        state.qIndex += 1
                        state.selectedAnswer = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isLast) "عرض النتيجة" else "التالي") }
        }
    }
}

/** شاشة النتيجة النهائية مع خيارات إعادة الاختبار. */
@Composable
fun ResultStep(state: ExamState, aiEngine: GeminiAIEngine, scope: CoroutineScope) {
    val total = state.questions.size.coerceAtLeast(1)
    val percent = state.score * 100 / total
    val message = when {
        percent >= 80 -> "ممتاز! مستواك رائع 🎉"
        percent >= 50 -> "كويس، بس محتاج مراجعة بسيطة 👍"
        else -> "محتاج تراجع الدروس دي تاني 💪"
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GradientCard(title = "النتيجة: ${state.score} / ${state.questions.size}", subtitle = message)
        if (state.errorMsg.isNotBlank()) {
            Text(state.errorMsg, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { generateExam(state, aiEngine, scope) },
            enabled = !state.examLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.examLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text("امتحان جديد في نفس الدروس")
            }
        }
        OutlinedButton(onClick = { state.step = 4 }, modifier = Modifier.fillMaxWidth()) {
            Text("اختيار دروس أخرى")
        }
        OutlinedButton(onClick = { state.resetAll() }, modifier = Modifier.fillMaxWidth()) {
            Text("البدء من جديد")
        }
    }
}

/** الشاشة الرئيسية للامتحانات: توزّع الخطوات حسب state.step. */
@Composable
fun ExamWizardTab(
    aiEngine: GeminiAIEngine,
    repo: CurriculumRepository,
    state: ExamState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    // زر الرجوع في الهاتف يرجع خطوة داخل المعالج بدل الخروج من التطبيق.
    BackHandler(enabled = state.step in 1..6) { examGoBack(state) }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state.step) {
            0 -> WizardOptionStep("اختر الصف الدراسي", AppConfig.GRADES, false, {}) { grade ->
                state.grade = grade
                state.step = 1
            }

            1 -> WizardOptionStep("اختر النظام", AppConfig.SYSTEMS, true, { examGoBack(state) }) { system ->
                state.system = system
                state.step = 2
            }

            2 -> {
                // الصف الأول الثانوي ليس فيه شعب، لذلك نعرض خياراً واحداً.
                val streams = if (state.grade == AppConfig.GRADES.first()) {
                    listOf(AppConfig.GENERAL_STREAM)
                } else {
                    AppConfig.STREAMS
                }
                WizardOptionStep("اختر الشعبة", streams, true, { examGoBack(state) }) { stream ->
                    state.stream = stream
                    state.step = 3
                }
            }

            3 -> WizardOptionStep("اختر المادة", subjectsFor(state.stream), true, { examGoBack(state) }) { subject ->
                state.subject = subject
                state.step = 4
                loadCurriculum(state, repo, scope)
            }

            4 -> LessonPickerStep(state, repo, aiEngine, scope)
            5 -> QuizStep(state)
            else -> ResultStep(state, aiEngine, scope)
        }
    }
}

// =========================================================================================
// 19. شاشة الإعدادات (Profile Tab)
// - تشغيل/إيقاف الوضع الليلي.
// - اختيار نغمة المنبه (RingtonePicker) وحفظها في SharedPreferences.
// - فتح إعدادات النظام للسماح بالمنبهات الدقيقة.
// =========================================================================================

/** اسم النغمة المحفوظة للعرض (أو "الافتراضية" لو لم تُختر نغمة). */
fun ringtoneTitle(ctx: Context, uriString: String?): String {
    if (uriString.isNullOrBlank()) return "الافتراضية"
    return try {
        RingtoneManager.getRingtone(ctx, Uri.parse(uriString))?.getTitle(ctx) ?: "نغمة مخصصة"
    } catch (e: Exception) {
        "نغمة مخصصة"
    }
}

/** قراءة الـ Uri الذي اختاره المستخدم من نتيجة RingtonePicker (مع مراعاة اختلاف الـ API). */
@Suppress("DEPRECATION")
fun extractPickedUri(data: Intent): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }
}

/** يفتح شاشة النظام الخاصة بالسماح بالمنبهات الدقيقة (أندرويد 12+ فقط). */
fun openExactAlarmSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:" + ctx.packageName))
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "تعذّر فتح إعدادات المنبهات الدقيقة", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(ctx, "جهازك لا يحتاج هذه الصلاحية", Toast.LENGTH_SHORT).show()
    }
}

/** شاشة الإعدادات: الوضع الليلي + نغمة المنبه + صلاحية المنبهات الدقيقة. */
@Composable
fun ProfileTab(
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    prefs: SharedPreferences,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var ringtoneName by remember { mutableStateOf(ringtoneTitle(ctx, prefs.getString(AppConfig.KEY_RINGTONE, null))) }
    var exactAllowed by remember { mutableStateOf(canUseExactAlarms(ctx)) }

    // نحدّث حالة صلاحية المنبهات الدقيقة كلما رجع المستخدم للتطبيق من شاشة الإعدادات.
    DisposableEffect(Unit) {
        val lifecycle = (ctx as? ComponentActivity)?.lifecycle
        val observer = object : LifecycleEventObserver {
            // عند عودة المستخدم للتطبيق نعيد فحص صلاحية المنبهات الدقيقة.
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_RESUME) exactAllowed = canUseExactAlarms(ctx)
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val picked = res.data?.let { extractPickedUri(it) }
            if (picked != null) {
                prefs.edit().putString(AppConfig.KEY_RINGTONE, picked.toString()).apply()
            } else {
                prefs.edit().remove(AppConfig.KEY_RINGTONE).apply()
            }
            ringtoneName = ringtoneTitle(ctx, prefs.getString(AppConfig.KEY_RINGTONE, null))
        }
    }

    /** يفتح شاشة اختيار النغمة مع تحديد النغمة الحالية. */
    fun openRingtonePicker() {
        val saved = prefs.getString(AppConfig.KEY_RINGTONE, null)
        val current: Uri = if (!saved.isNullOrBlank()) {
            Uri.parse(saved)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختر نغمة المنبه")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
        }
        ringtonePicker.launch(intent)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "الإعدادات",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // ---- الوضع الليلي ----
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("الوضع الليلي", color = MaterialTheme.colorScheme.onSurface)
                Switch(checked = dark, onCheckedChange = onDark)
            }
        }

        // ---- نغمة المنبه ----
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("نغمة المنبه", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "الحالية: $ringtoneName",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Button(onClick = { openRingtonePicker() }) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("اختيار نغمة")
                }
            }
        }

        // ---- صلاحية المنبهات الدقيقة ----
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("المنبهات الدقيقة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (exactAllowed) {
                        "الحالة: مفعّلة ✅ المنبه يرن في الثانية المحددة بالضبط."
                    } else {
                        "الحالة: غير مفعّلة ⚠️ المنبه قد يتأخر قليلاً. فعّلها لدقة أعلى."
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                OutlinedButton(onClick = { openExactAlarmSettings(ctx) }) {
                    Icon(Icons.Filled.Alarm, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("فتح إعدادات المنبهات الدقيقة")
                }
            }
        }
    }
}
