@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartstudent.ui

// =========================================================================================
// 1. الاستيرادات (Imports) - تم تجميعها بدقة لتغطية كل الوظائف
// =========================================================================================
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
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


// =========================================================================================
// 2. نماذج البيانات الأساسية (Data Models)
// =========================================================================================

/**
 * كلاس يمثل درساً فردياً.
 * @param id معرف فريد.
 * @param title عنوان الدرس.
 */
data class Lesson(val id: Long, val title: String)

/**
 * كلاس يمثل فصلاً (باباً) يحتوي على مجموعة من الدروس.
 * @param id معرف فريد.
 * @param title عنوان الباب.
 * @param lessons قائمة الدروس.
 */
data class Chapter(val id: Long, val title: String, val lessons: List<Lesson>)

/**
 * يمثل رسالة في الدردشة.
 * @param id معرف فريد.
 * @param text محتوى النص.
 * @param isUser هل المرسل مستخدم أم ذكاء اصطناعي.
 * @param isError هل تحتوي على خطأ.
 * @param imageBitmap صورة مرفقة اختيارية.
 * @param timestamp وقت الإرسال.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val imageBitmap: Bitmap? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * يمثل سؤالاً في الاختبار.
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
    val description: String,
    val timeStr: String,
    val triggerAt: Long,
    val done: Boolean = false
)


// =========================================================================================
// 3. قاعدة بيانات المناهج الضخمة (Curriculum Repository)
// =========================================================================================

interface CurriculumRepository {
    suspend fun getChapters(subject: String): List<Chapter>
}

class MockCurriculumRepository : CurriculumRepository {
    override suspend fun getChapters(subject: String): List<Chapter> {
        delay(400) // محاكاة تأخير بسيط
        return when (subject) {
            "فيزياء" -> listOf(
                Chapter(1, "الباب الأول: الحركة", listOf(Lesson(1, "السرعة"), Lesson(2, "التسارع"), Lesson(3, "معادلات الحركة"), Lesson(4, "السقوط الحر"), Lesson(5, "الحركة في بعدين"))),
                Chapter(2, "الباب الثاني: الطاقة", listOf(Lesson(6, "الشغل"), Lesson(7, "الطاقة الحركية"), Lesson(8, "الطاقة الكامنة"), Lesson(9, "حفظ الطاقة"), Lesson(10, "القدرة"))),
                Chapter(3, "الباب الثالث: الكهرباء", listOf(Lesson(11, "قانون كولوم"), Lesson(12, "المجال الكهربائي"), Lesson(13, "الجهد الكهربائي"), Lesson(14, "المكثفات"), Lesson(15, "التيار الكهربي"))),
                Chapter(4, "الباب الرابع: المغناطيسية", listOf(Lesson(16, "المجال المغناطيسي"), Lesson(17, "قوة لورنتز"), Lesson(18, "الحث الكهرومغناطيسي"), Lesson(19, "المحولات"), Lesson(20, "الموجات الكهرومغناطيسية"))),
                Chapter(5, "الباب الخامس: الفيزياء الحديثة", listOf(Lesson(21, "النظرية النسبية"), Lesson(22, "ميكانيكا الكم"), Lesson(23, "الظاهرة الكهروضوئية"), Lesson(24, "الطيف الذري"), Lesson(25, "النشاط الإشعاعي")))
            )
            "كيمياء" -> listOf(
                Chapter(6, "الباب الأول: الكيمياء الحرارية", listOf(Lesson(26, "المحتوى الحراري"), Lesson(27, "حرارة التكوين"), Lesson(28, "قانون هس"), Lesson(29, "طاقة الرابطة"), Lesson(30, "العوامل المؤثرة"))),
                Chapter(7, "الباب الثاني: الاتزان", listOf(Lesson(31, "مفهوم الاتزان"), Lesson(32, "ثابت الاتزان"), Lesson(33, "قاعدة لوشاتيليه"), Lesson(34, "الاتزان الأيوني"), Lesson(35, "الذوبانية"))),
                Chapter(8, "الباب الثالث: الأحماض والقواعد", listOf(Lesson(36, "مفهوم الأحماض"), Lesson(37, "الرقم الهيدروجيني"), Lesson(38, "المعايرة"), Lesson(39, "الأملاح"), Lesson(40, "الدلائل"))),
                Chapter(9, "الباب الرابع: الكيمياء العضوية", listOf(Lesson(41, "الهيدروكربونات"), Lesson(42, "الكحولات"), Lesson(43, "الأحماض الكربوكسيلية"), Lesson(44, "الإسترات"), Lesson(45, "البوليمرات"))),
                Chapter(10, "الباب الخامس: الكيمياء الكهربية", listOf(Lesson(46, "الخلايا الجلفانية"), Lesson(47, "التحليل الكهربي"), Lesson(48, "جهد الاختزال"), Lesson(49, "التآكل"), Lesson(50, "البطاريات")))
            )
            "أحياء" -> listOf(
                Chapter(11, "الباب الأول: الدعامة والحركة", listOf(Lesson(51, "الدعامة في النبات"), Lesson(52, "الحركة في الإنسان"), Lesson(53, "الجهاز العضلي"), Lesson(54, "الجهاز الهيكلي"), Lesson(55, "المفاصل"))),
                Chapter(12, "الباب الثاني: التكاثر", listOf(Lesson(56, "التكاثر في النبات"), Lesson(57, "التكاثر في الإنسان"), Lesson(58, "التكاثر اللاجنسي"), Lesson(59, "التكاثر الجنسي"), Lesson(60, "الهرمونات"))),
                Chapter(13, "الباب الثالث: الوراثة", listOf(Lesson(61, "قوانين مندل"), Lesson(62, "الوراثة الجنسية"), Lesson(63, "الطفرات"), Lesson(64, "الهندسة الوراثية"), Lesson(65, "علم الوراثة السكاني"))),
                Chapter(14, "الباب الرابع: البيئة", listOf(Lesson(66, "النظام البيئي"), Lesson(67, "التلوث"), Lesson(68, "التنوع الحيوي"), Lesson(69, "التوازن البيئي"), Lesson(70, "الموارد الطبيعية"))),
                Chapter(15, "الباب الخامس: التطور", listOf(Lesson(71, "داروين"), Lesson(72, "الأدلة"), Lesson(73, "الانتخاب الطبيعي"), Lesson(74, "الأنواع"), Lesson(75, "شجرة الحياة")))
            )
            "رياضيات" -> listOf(
                Chapter(16, "الباب الأول: الجبر", listOf(Lesson(76, "المعادلات"), Lesson(77, "المتباينات"), Lesson(78, "المصفوفات"), Lesson(79, "المحددات"), Lesson(80, "المتتابعات"))),
                Chapter(17, "الباب الثاني: التفاضل", listOf(Lesson(81, "النهايات"), Lesson(82, "الاشتقاق"), Lesson(83, "قواعد الاشتقاق"), Lesson(84, "تطبيقات التفاضل"), Lesson(85, "القيم العظمى والصغرى"))),
                Chapter(18, "الباب الثالث: التكامل", listOf(Lesson(86, "التكامل غير المحدود"), Lesson(87, "التكامل المحدود"), Lesson(88, "المساحات"), Lesson(89, "الحجوم"), Lesson(90, "التكامل بالأجزاء"))),
                Chapter(19, "الباب الرابع: الهندسة التحليلية", listOf(Lesson(91, "الخط المستقيم"), Lesson(92, "الدائرة"), Lesson(93, "القطع المكافئ"), Lesson(94, "القطع الناقص"), Lesson(95, "القطع الزائد"))),
                Chapter(20, "الباب الخامس: الإحصاء", listOf(Lesson(96, "المتوسط"), Lesson(97, "الوسيط"), Lesson(98, "الانحراف المعياري"), Lesson(99, "الاحتمالات"), Lesson(100, "التوزيعات")))
            )
            "لغة عربية" -> listOf(
                Chapter(21, "الباب الأول: النحو", listOf(Lesson(101, "المبتدأ والخبر"), Lesson(102, "كان وأخواتها"), Lesson(103, "إن وأخواتها"), Lesson(104, "المفعول به"), Lesson(105, "الحال"))),
                Chapter(22, "الباب الثاني: البلاغة", listOf(Lesson(106, "التشبيه"), Lesson(107, "الاستعارة"), Lesson(108, "الكناية"), Lesson(109, "المجاز المرسل"), Lesson(110, "البديع"))),
                Chapter(23, "الباب الثالث: الأدب", listOf(Lesson(111, "مدارس الشعر"), Lesson(112, "النثر"), Lesson(113, "المسرح"), Lesson(114, "الرواية"), Lesson(115, "القصة القصيرة"))),
                Chapter(24, "الباب الرابع: النصوص", listOf(Lesson(116, "نصوص شعرية"), Lesson(117, "نصوص نثرية"), Lesson(118, "تحليل النصوص"), Lesson(119, "الشعر الجاهلي"), Lesson(120, "الشعر الحديث"))),
                Chapter(25, "الباب الخامس: القراءة", listOf(Lesson(121, "قراءة متحررة"), Lesson(122, "قراءة موجهة"), Lesson(123, "المفردات"), Lesson(124, "الأساليب"), Lesson(125, "التعبير")))
            )
            "لغة إنجليزية" -> listOf(
                Chapter(26, "Unit 1: Grammar", listOf(Lesson(126, "Present Tenses"), Lesson(127, "Past Tenses"), Lesson(128, "Future Tenses"), Lesson(129, "Passive Voice"), Lesson(130, "Conditionals"))),
                Chapter(27, "Unit 2: Vocabulary", listOf(Lesson(131, "Phrasal Verbs"), Lesson(132, "Collocations"), Lesson(133, "Idioms"), Lesson(134, "Synonyms"), Lesson(135, "Antonyms"))),
                Chapter(28, "Unit 3: Writing", listOf(Lesson(136, "Essay Writing"), Lesson(137, "Email Writing"), Lesson(138, "Story Writing"), Lesson(139, "Report Writing"), Lesson(140, "Article Writing"))),
                Chapter(29, "Unit 4: Reading", listOf(Lesson(141, "Comprehension"), Lesson(142, "Skimming"), Lesson(143, "Scanning"), Lesson(144, "Inference"), Lesson(145, "Main Idea"))),
                Chapter(30, "Unit 5: Translation", listOf(Lesson(146, "Arabic to English"), Lesson(147, "English to Arabic"), Lesson(148, "Idiomatic Translation"), Lesson(149, "Technical Terms"), Lesson(150, "Cultural Nuances")))
            )
            "تاريخ" -> listOf(
                Chapter(31, "الباب الأول: التاريخ القديم", listOf(Lesson(151, "مصر القديمة"), Lesson(152, "الحضارات القديمة"), Lesson(153, "الفراعنة"), Lesson(154, "اليونان"), Lesson(155, "الرومان"))),
                Chapter(32, "الباب الثاني: التاريخ الإسلامي", listOf(Lesson(156, "الفتوحات"), Lesson(157, "الدولة الأموية"), Lesson(158, "الدولة العباسية"), Lesson(159, "الأندلس"), Lesson(160, "الحروب الصليبية"))),
                Chapter(33, "الباب الثالث: التاريخ الحديث", listOf(Lesson(161, "الحملة الفرنسية"), Lesson(162, "محمد علي"), Lesson(163, "الثورة العرابية"), Lesson(164, "الاحتلال البريطاني"), Lesson(165, "ثورة 1919"))),
                Chapter(34, "الباب الرابع: التاريخ المعاصر", listOf(Lesson(166, "ثورة 1952"), Lesson(167, "حرب 1967"), Lesson(168, "حرب أكتوبر"), Lesson(169, "ثورة 2011"), Lesson(170, "الجمهورية الجديدة"))),
                Chapter(35, "الباب الخامس: الحضارة", listOf(Lesson(171, "العمارة"), Lesson(172, "الفنون"), Lesson(173, "العلوم"), Lesson(174, "الفلسفة"), Lesson(175, "الأدب")))
            )
            "جغرافيا" -> listOf(
                Chapter(36, "الباب الأول: الجغرافيا الطبيعية", listOf(Lesson(176, "المناخ"), Lesson(177, "التضاريس"), Lesson(178, "التربة"), Lesson(179, "المياه"), Lesson(180, "الغطاء النباتي"))),
                Chapter(37, "الباب الثاني: الجغرافيا البشرية", listOf(Lesson(181, "السكان"), Lesson(182, "الاقتصاد"), Lesson(183, "العمران"), Lesson(184, "الهجرة"), Lesson(185, "الثقافة"))),
                Chapter(38, "الباب الثالث: جغرافيا مصر", listOf(Lesson(186, "الموقع"), Lesson(187, "السكان"), Lesson(188, "الاقتصاد"), Lesson(189, "المناخ"), Lesson(190, "الموارد"))),
                Chapter(39, "الباب الرابع: الجغرافيا السياسية", listOf(Lesson(191, "الحدود"), Lesson(192, "ال geopolitics"), Lesson(193, "الصراعات"), Lesson(194, "التكتلات"), Lesson(195, "القانون الدولي"))),
                Chapter(40, "الباب الخامس: الخرائط", listOf(Lesson(196, "المقياس"), Lesson(197, "الإحداثيات"), Lesson(198, "الرموز"), Lesson(199, "التحليل"), Lesson(200, "نظم المعلومات")))
            )
            else -> emptyList()
        }
    }
}


// =========================================================================================
// 4. محرك الذكاء الاصطناعي (Gemini AI Engine) - القلب النابض للتطبيق
// =========================================================================================

/**
 * نموذج استجابة الذكاء الاصطناعي.
 */
data class AIResponse(val candidates: List<Candidate>, val isError: Boolean = false)
data class Candidate(val content: Content)
data class Content(val parts: List<Part>)
data class Part(val text: String)

fun aiOk(text: String) = AIResponse(listOf(Candidate(Content(listOf(Part(text))))))
fun aiError(text: String) = AIResponse(listOf(Candidate(Content(listOf(Part(text))))), isError = true)
fun AIResponse.text(): String = candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()

suspend fun safeAi(block: suspend () -> AIResponse): AIResponse = try {
    block()
} catch (e: Exception) {
    aiError("حدث خطأ غير متوقع: ${e.localizedMessage}")
}

/**
 * محرك الذكاء الاصطناعي الرئيسي.
 * يقوم بإدارة الاتصال مع Google Gemini API، ويحتوي على نظام تبديل تلقائي
 * (Fallback Models) في حال فشل أحد النماذج، لضمان استمرارية الخدمة.
 */
class GeminiAIEngine {
    
    // إعداد عميل HTTP مع تحديد أوقات الانتظار (Timeouts) لضمان عدم تعليق التطبيق
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // استخراج مفتاح API من ملف الإعدادات (BuildConfig)
    private val apiKey: String = BuildConfig.GEMINI_API_KEY.trim()
    
    /**
     * قائمة النماذج الاحتياطية (Fallback Models).
     * مرتبة من الأحدث إلى الأقدم. النظام سيحاول استخدام النموذج الأول،
     * وإذا فشل (بسبب خطأ 404، أو 429، أو أي خطأ آخر)، سينتقل تلقائياً للنموذج التالي.
     */
    private val fallbackModels = listOf(
        "gemini-1.5-flash-latest",  // النموذج الأساسي السريع
        "gemini-1.5-pro-latest",    // النموذج البديل الأكثر ذكاءً
        "gemini-1.5-flash",         // نموذج مستقر
        "gemini-pro"                // نموذج قديم جداً كخيار أخير
    )

    /**
     * دالة داخلية لإرسال الطلب إلى Gemini ومحاولة النماذج بالترتيب.
     * @param payload بيانات الطلب بصيغة JSON.
     * @return استجابة الذكاء الاصطناعي أو رسالة خطأ مفصلة.
     */
    private suspend fun makeRequest(payload: JSONObject): AIResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            return@withContext aiError("خطأ: مفتاح API غير موجود. تأكد من إعداده في GitHub Secrets.")
        }
        
        var lastErrorMessage = ""
        
        // المرور على كل نموذج في القائمة
        for (model in fallbackModels) {
            Log.d("GeminiAIEngine", "Attempting with model: $model")
            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            
            val request = Request.Builder()
                .url(requestUrl)
                .header("x-goog-api-key", apiKey)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    
                    if (response.isSuccessful) {
                        val jsonObject = JSONObject(responseBody)
                        val candidatesArray = jsonObject.optJSONArray("candidates")
                        
                        if (candidatesArray != null && candidatesArray.length() > 0) {
                            val firstCandidate = candidatesArray.getJSONObject(0)
                            val contentObject = firstCandidate.optJSONObject("content")
                            val partsArray = contentObject?.optJSONArray("parts")
                            
                            if (partsArray != null && partsArray.length() > 0) {
                                val text = partsArray.getJSONObject(0).optString("text").orEmpty()
                                if (text.isNotBlank()) {
                                    Log.d("GeminiAIEngine", "Success with model: $model")
                                    return@withContext aiOk(text)
                                }
                            }
                        }
                    } else {
                        lastErrorMessage = "خطأ ${response.code} في الموديل $model"
                        Log.e("GeminiAIEngine", "Error $response.code with model $model: $responseBody")
                    }
                }
            } catch (e: Exception) {
                lastErrorMessage = "فشل الاتصال بالموديل $model: ${e.localizedMessage}"
                Log.e("GeminiAIEngine", "Exception with model $model", e)
            }
        }
        
        return@withContext aiError("فشل الاتصال بالذكاء الاصطناعي بعدة محاولات.\n$lastErrorMessage")
    }

    /**
     * إرسال رسالة دردشة (مع دعم الصور).
     */
    suspend fun chat(history: List<ChatMessage>, imageBase64: String? = null): AIResponse {
        val contentsArray = JSONArray()
        
        // بناء سياق المحادثة (آخر 10 رسائل فقط لتوفير الذاكرة)
        history.takeLast(10).forEach { message ->
            val partsArray = JSONArray().put(JSONObject().put("text", message.text))
            val messageObject = JSONObject().apply {
                put("role", if (message.isUser) "user" else "model")
                put("parts", partsArray)
            }
            contentsArray.put(messageObject)
        }
        
        // إضافة صورة إذا وجدت
        if (imageBase64 != null) {
            val imagePartsArray = JSONArray().apply {
                put(JSONObject().put("text", "حلل هذه الصورة وساعدني في فهم محتواها."))
                put(JSONObject().put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", imageBase64)
                }))
            }
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", imagePartsArray)
            })
        }
        
        val payload = JSONObject().put("contents", contentsArray)
        return makeRequest(payload)
    }

    /**
     * توليد امتحان تفاعلي بناءً على المعايير.
     */
    suspend fun generateInteractiveExam(
        grade: String, 
        system: String, 
        stream: String, 
        subject: String, 
        topics: String
    ): AIResponse {
        val prompt = """
            أنت معلم خبير في المناهج المصرية.
            المطلوب: توليد اختبار تفاعلي من 5 أسئلة (اختيار من متعدد).
            بيانات الاختبار:
            - الصف: $grade
            - النظام: $system
            - الشعبة: $stream
            - المادة: $subject
            - المواضيع المحددة: $topics
            
            يجب أن تتبع التنسيق التالي بدقة شديدة لكل سؤال:
            Q: [نص السؤال]
            O1: [الخيار الأول]
            O2: [الخيار الثاني]
            O3: [الخيار الثالث]
            O4: [الخيار الرابع]
            C: [رقم الإجابة الصحيحة من 1 إلى 4]
            E: [شرح بسيط لسبب صحة الإجابة]
            
            لا تكتب أي مقدمات أو نهايات، فقط الأسئلة بالتنسيق المطلوب.
        """.trimIndent()
        
        val payload = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        return makeRequest(payload)
    }
}


// =========================================================================================
// 5. الثيم والألوان (Theme & Colors)
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
// 6. التطبيق الرئيسي (MainActivity)
// =========================================================================================

class MainActivity : ComponentActivity() {
    private val aiEngine = GeminiAIEngine()
    private val repo = MockCurriculumRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // طلب إذن الإشعارات لأندرويد 13+
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

// =========================================================================================
// 7. التنقل الرئيسي (Main Screen)
// =========================================================================================

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
    val icons = listOf(
        Icons.Filled.Home, 
        Icons.Filled.Chat, 
        Icons.Filled.Description, 
        Icons.Filled.Person
    )
    
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


// =========================================================================================
// 8. الشاشة الرئيسية (Dashboard)
// =========================================================================================

@Composable
fun HomeTab(prefs: SharedPreferences) {
    val ctx = LocalContext.current
    val tasks = remember { mutableStateListOf<StudyTask>() }
    var showDialog by remember { mutableStateOf(false) }
    
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    var timeText by remember { mutableStateOf("") }
    val calendar = Calendar.getInstance()

    val datePicker = remember {
        DatePickerDialog(ctx, { _, year, month, day ->
            calendar.set(year, month, day)
            dateMillis = calendar.timeInMillis
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    }
    
    val timePicker = remember {
        TimePickerDialog(ctx, { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            dateMillis = calendar.timeInMillis
            timeText = String.format("%02d:%02d", hour, minute)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("مهمة جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("اسم المهمة") })
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("الوصف (اختياري)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { datePicker.show() }) { Text(if (dateMillis != null) "التاريخ: تم" else "اختر التاريخ") }
                        OutlinedButton(onClick = { timePicker.show() }) { Text(if (timeText.isNotBlank()) "الوقت: $timeText" else "اختر الوقت") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank() && dateMillis != null) {
                        val task = StudyTask(System.currentTimeMillis(), title, description, "يوم $timeText", dateMillis!!)
                        tasks.add(task)
                        setAlarm(ctx, task)
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

/**
 * دالة مساعدة لضبط المنبه في نظام أندرويد.
 * تقوم بفحص الصلاحيات في الإصدارات الحديثة لتجنب انهيار التطبيق.
 */
fun setAlarm(ctx: Context, task: StudyTask) {
    val intent = Intent(ctx, AlarmReceiver::class.java).putExtra("title", task.title)
    val pendingIntent = PendingIntent.getBroadcast(ctx, task.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, pendingIntent)
    } else {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, pendingIntent)
    }
}


// =========================================================================================
// 9. شاشة الدردشة (AI Chat)
// =========================================================================================

@Composable
fun AIChatTab(aiEngine: GeminiAIEngine) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // أداة التعرف على الصوت
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            input = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
        }
    }
    
    // أداة اختيار الصور
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bitmap = BitmapFactory.decodeStream(ctx.contentResolver.openInputStream(it))
            imageBitmap = bitmap
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            imageBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }

    // التمرير التلقائي لأسفل عند وصول رسالة جديدة
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f), 
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            msg.imageBitmap?.let { 
                                androidx.compose.foundation.Image(
                                    bitmap = it.asImageBitmap(), 
                                    contentDescription = null, 
                                    modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                ) 
                            }
                            Text(
                                msg.text, 
                                color = if (msg.isUser) Color.White else Color.Black,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            if (loading) item { CircularProgressIndicator(Modifier.size(24.dp)) }
        }
        
        // معاينة الصورة قبل الإرسال
        if (imageBitmap != null) { 
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap!!.asImageBitmap(),
                    contentDescription = "Preview",
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { imageBitmap = null; imageBase64 = null }) { 
                    Icon(Icons.Filled.Close, "إلغاء", tint = Color.Red) 
                } 
            }
        }
        
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { imageLauncher.launch("image/*") }) { 
                Icon(Icons.Filled.Image, "صورة", tint = MaterialTheme.colorScheme.primary) 
            }
            OutlinedTextField(
                value = input, 
                onValueChange = { input = it }, 
                modifier = Modifier.weight(1f), 
                placeholder = { Text("اكتب أو سجل...") },
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(onClick = { 
                speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")) 
            }) { 
                Icon(Icons.Filled.Mic, "تسجيل", tint = MaterialTheme.colorScheme.secondary) 
            }
            IconButton(onClick = {
                if ((input.isNotBlank() || imageBase64 != null) && !loading) {
                    val query = if (input.isBlank()) "حلل الصورة" else input
                    messages = messages + ChatMessage(text = query, isUser = true, imageBitmap = imageBitmap)
                    val base64ToSend = imageBase64
                    input = ""; imageBitmap = null; imageBase64 = null; loading = true
                    scope.launch {
                        val response = safeAi { aiEngine.chat(messages, base64ToSend) }
                        messages = messages + ChatMessage(text = response.text(), isUser = false, isError = response.isError)
                        loading = false
                    }
                }
            }) { 
                Icon(Icons.Filled.Send, "إرسال") 
            }
        }
    }
}


// =========================================================================================
// 10. شاشة الامتحانات (Exam Wizard)
// =========================================================================================

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
                listOf("فيزياء", "كيمياء", "أحياء", "رياضيات", "لغة عربية", "لغة إنجليزية", "تاريخ", "جغرافيا").forEach { sub ->
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
                                    IconButton(onClick = { expanded = !expanded }) { 
                                        Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null) 
                                    }
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
                            if (selectedAnswer == q.correctIdx) { score++ }
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

/**
 * دالة لتحويل النص المستلم من الذكاء الاصطناعي إلى قائمة أسئلة مفهومة.
 * تقوم بالبحث عن الكلمات المفتاحية (Q, O1, C, E).
 */
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


// =========================================================================================
// 11. شاشة الإعدادات (Profile Settings)
// =========================================================================================

@Composable
fun ProfileTab(dark: Boolean, onDark: (Boolean) -> Unit, prefs: SharedPreferences) {
    val ctx = LocalContext.current
    var ringtone by remember { mutableStateOf(prefs.getString("ringtone", "افتراضية")) }
    
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) { 
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


// =========================================================================================
// 12. نظام المنبهات في الخلفية (A
