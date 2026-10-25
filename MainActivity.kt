package com.smartstudent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Calendar

/* --------------------------------------------------------------
 * Mock data layer – replace with a persistent database later.
 * -------------------------------------------------------------- */
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

    override suspend fun getById(id: Long): AlarmEntity? =
        _alarms.value?.firstOrNull { it.id == id }
}

/* --------------------------------------------------------------
 * Mock AI engine. Replace with a real Gemini/API implementation.
 * -------------------------------------------------------------- */
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
        AIResponse(listOf(Candidate(Content(listOf(Part(
            "Exam for $subject\nYears: ${years.joinToString(", ")}\nTypes: ${types.joinToString(", ")}\n\n1. Question 1…\n2. Question 2…\n3. Question 3…"
        ))))))

    override suspend fun explain(subject: String, topic: String, level: String) =
        AIResponse(listOf(Candidate(Content(listOf(Part(
            "Explanation for $topic in $subject:\n- Definition…\n- Key concepts…\n- Example…"
        ))))))

    override suspend fun autoSchedule(schedule: String) =
        AIResponse(listOf(Candidate(Content(listOf(Part(
            "Optimized schedule based on your input:\n08:00-09:00 Study Math\n09:15-10:15 Physics\n11:00-12:00 Chemistry\n..."
        ))))))
}

object TimeUtil {
    fun nowMillis(): Long = System.currentTimeMillis()
    fun format(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(
            "%02d-%02d-%d %02d:%02d",
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
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
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF4285F4),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD2E3FC),
            onPrimaryContainer = Color(0xFF174EA6),
            secondary = Color(0xFF34A853),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFA8F0C0),
            onSecondaryContainer = Color(0xFF0D6525),
            background = Color(0xFFF8F9FA),
            onBackground = Color(0xFF202124),
            surface = Color.White,
            onSurface = Color(0xFF202124),
            surfaceVariant = Color(0xFFF1F3F5),
            onSurfaceVariant = Color(0xFF555555)
        ),
        typography = Typography(),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(alarmRepository: AlarmRepository, aiEngine: AIEngine) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Alarms", "AI Chat", "Exams")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartStudent", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                when (index) {
                                    0 -> Icons.Default.Alarm
                                    1 -> Icons.Default.Chat
                                    else -> Icons.Default.Description
                                },
                                contentDescription = label
                            )
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> AlarmTab(alarmRepository, Modifier.padding(padding))
            1 -> AIChatTab(aiEngine, Modifier.padding(padding))
            else -> ExamTab(aiEngine, Modifier.padding(padding))
        }
    }
}

@Composable
fun AlarmTab(alarmRepository: AlarmRepository, modifier: Modifier = Modifier) {
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Smart Alarms", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Alarm")
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("AI Schedule Suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text("Let AI optimize your study timetable and avoid conflicts.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Button(
                    onClick = {
                        scope.launch {
                            scheduleResult = aiEngine.autoSchedule("My study schedule").candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-Schedule")
                }
                if (scheduleResult.isNotBlank()) Text(scheduleResult)
            }
        }

        if (alarms.isEmpty()) {
            EmptyStateCard()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm,
                        onToggle = { updated -> scope.launch(Dispatchers.IO) { alarmRepository.updateAlarm(updated) } },
                        onDelete = { deleted -> scope.launch(Dispatchers.IO) { alarmRepository.removeAlarm(deleted) } }
                    )
                }
            }
        }
    }

    if (showDialog) {
        AddAlarmDialog(
            onDismiss = { showDialog = false },
            onConfirm = { alarm ->
                scope.launch(Dispatchers.IO) { alarmRepository.addAlarm(alarm) }
                showDialog = false
            }
        )
    }
}

@Composable
fun AlarmCard(alarm: AlarmEntity, onToggle: (AlarmEntity) -> Unit, onDelete: (AlarmEntity) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isActive) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(alarm.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    alarm.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Switch(checked = alarm.isActive, onCheckedChange = { onToggle(alarm.copy(isActive = it)) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(TimeUtil.format(alarm.triggerAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { onDelete(alarm) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Text("No active alarms", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tap the + button to add a new alarm.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun AddAlarmDialog(onDismiss: () -> Unit, onConfirm: (AlarmEntity) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Alarm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        AlarmEntity(
                            title = title.trim(),
                            description = description.ifBlank { null },
                            triggerAt = TimeUtil.nowMillis() + 3_600_000L
                        )
                    )
                },
                enabled = title.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
        Text("AI Chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Ask any study question, and the AI will respond.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth().weight(1f), elevation = CardDefaults.cardElevation(2.dp)) {
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { msg -> ChatBubble(msg) }
                if (loading) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("Thinking...")
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Type your question...") },
                leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null) },
                modifier = Modifier.weight(1f),
                maxLines = 3
            )
            IconButton(
                onClick = {
                    val query = input.trim()
                    if (query.isNotEmpty() && !loading) {
                        messages = messages + ChatMessage(query, true)
                        input = ""
                        loading = true
                        scope.launch {
                            try {
                                val response = aiEngine.explain("General", query)
                                val reply = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                    ?: "Sorry, I couldn't generate a response."
                                messages = messages + ChatMessage(reply, false)
                            } catch (e: Exception) {
                                messages = messages + ChatMessage("Something went wrong: ${e.localizedMessage ?: "unknown error"}", false)
                            } finally {
                                loading = false
                            }
                        }
                    }
                },
                modifier = Modifier.size(56.dp)
            ) { Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary) }
        }

        Text("Quick topics:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickChip("Physics") { input = "Explain Newton's laws" }
            QuickChip("Math") { input = "Solve quadratic equation example" }
            QuickChip("Chemistry") { input = "Describe periodic table trends" }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start) {
        Card(
            Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(message.text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun QuickChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null) }
    )
}

@Composable
fun ExamTab(aiEngine: AIEngine, modifier: Modifier = Modifier) {
    var subject by remember { mutableStateOf("") }
    var years by remember { mutableStateOf<List<Int>>(emptyList()) }
    var types by remember { mutableStateOf<List<String>>(emptyList()) }
    var result by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val subjectOptions = listOf("Physics", "Mathematics", "Chemistry", "Biology", "English", "Arabic")
    val yearOptions = listOf(2020, 2021, 2022, 2023, 2024)
    val typeOptions = listOf("Multiple Choice", "Short Answer", "Analytical")

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Exam Generator", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Select criteria and generate a practice exam.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Subject", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subjectOptions) { s ->
                        FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(s) })
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Past Years", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    yearOptions.forEach { year ->
                        FilterChip(
                            selected = years.contains(year),
                            onClick = { years = if (year in years) years - year else years + year },
                            label = { Text(year.toString()) }
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Question Types", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                typeOptions.forEach { type ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = type in types,
                            onCheckedChange = { checked -> types = if (checked) types + type else types - type }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(type)
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
                        result = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No result."
                    } catch (e: Exception) {
                        result = "Failed to generate exam: ${e.localizedMessage ?: "unknown error"}"
                    } finally {
                        generating = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = subject.isNotBlank() && years.isNotEmpty() && types.isNotEmpty() && !generating
        ) {
            if (generating) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Generating...")
            } else {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate Exam")
            }
        }

        if (result.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Generated Exam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { /* TODO: implement sharing */ }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                    Text(result, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
