package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.localization.LanguageManager
import com.example.ui.components.BottomNavBar
import com.example.ui.screens.DuasAdhkarScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PrayerTimesScreen
import com.example.ui.screens.QiblaScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StrictLockScreen
import com.example.ui.screens.TasbihScreen
import com.example.ui.theme.MusallaTheme
import com.example.viewmodel.MusallaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MusallaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val userSettings by viewModel.userSettings.collectAsState()
            val activeTab by viewModel.activeTab.collectAsState()
            val strictLockActive by viewModel.strictLockActive.collectAsState()

            val isRtl = LanguageManager.isRtl(userSettings.languageCode)

            var quickToolTarget by remember { mutableStateOf<String?>(null) }

            MusallaTheme(isRtl = isRtl) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomNavBar(
                            selectedTab = activeTab,
                            onTabSelected = {
                                quickToolTarget = null
                                viewModel.setActiveTab(it)
                            },
                            languageCode = userSettings.languageCode
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (quickToolTarget != null) {
                            when (quickToolTarget) {
                                "qibla" -> QiblaScreen(viewModel = viewModel)
                                "tasbih" -> TasbihScreen(viewModel = viewModel)
                                "duas", "adhkar" -> DuasAdhkarScreen(viewModel = viewModel)
                            }
                        } else {
                            when (activeTab) {
                                0 -> HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = { viewModel.setActiveTab(3) },
                                    onOpenTool = { tool -> quickToolTarget = tool }
                                )
                                1 -> PrayerTimesScreen(viewModel = viewModel)
                                2 -> TasbihScreen(viewModel = viewModel)
                                3 -> SettingsScreen(viewModel = viewModel)
                            }
                        }

                        // Strict Lock Camera Verification Overlay
                        if (strictLockActive) {
                            StrictLockScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AppLanguage(val code: String, val nativeName: String, val englishName: String, val isRtl: Boolean) {
    ENGLISH("en", "English", "English", false),
    URDU("ur", "اردو", "Urdu", true),
    ARABIC("ar", "العربية", "Arabic", true),
    TURKISH("tr", "Türkçe", "Turkish", false),
    FRENCH("fr", "Français", "French", false),
    SPANISH("es", "Español", "Spanish", false),
    HINDI("hi", "हिन्दी", "Hindi", false),
    INDONESIAN("id", "Bahasa Indonesia", "Indonesian", false),
    BENGALI("bn", "বাংলা", "Bengali", false),
    RUSSIAN("ru", "Русский", "Russian", false)
}

enum class PrayerType(val displayNameKey: String) {
    FAJR("Fajr"),
    SUNRISE("Sunrise"),
    DHUHR("Dhuhr"),
    ASR("Asr"),
    MAGHRIB("Maghrib"),
    ISHA("Isha")
}

@Entity(tableName = "prayer_logs")
data class PrayerLog(
    @PrimaryKey val date: String, // YYYY-MM-DD
    val fajrDone: Boolean = false,
    val dhuhrDone: Boolean = false,
    val asrDone: Boolean = false,
    val maghribDone: Boolean = false,
    val ishaDone: Boolean = false,
    val isPeriodPausedDay: Boolean = false
)

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1,
    val languageCode: String = "en",
    val cityName: String = "London, UK",
    val latitude: Double = 51.5074,
    val longitude: Double = -0.1278,
    val isAutoGps: Boolean = false,
    val calculationMethod: String = "MWL",
    val isFemaleMode: Boolean = false,
    val isPeriodPauseActive: Boolean = false,
    val periodPauseStartDate: String? = null,
    val isStrictLockEnabled: Boolean = true,
    val currentStreakDays: Int = 12,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

@Entity(tableName = "tasbih_counters")
data class TasbihCounter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val arabicText: String,
    val transliteration: String,
    val translationEn: String,
    val currentCount: Int = 0,
    val targetCount: Int = 33,
    val totalLaps: Int = 0
)

data class PrayerTimeInfo(
    val type: PrayerType,
    val timeFormatted: String,
    val timestampMs: Long,
    val isNext: Boolean = false,
    val isCompleted: Boolean = false
)

data class DuaItem(
    val id: String,
    val category: String,
    val titleKey: String,
    val arabic: String,
    val transliteration: String,
    val translationKey: String,
    val isFavorite: Boolean = false
)

data class AdhkarItem(
    val id: String,
    val isMorning: Boolean,
    val titleKey: String,
    val arabic: String,
    val transliteration: String,
    val translationKey: String,
    val targetCount: Int = 3,
    val currentCount: Int = 0
)

package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calculator.PrayerCalculator
import com.example.data.local.MusallaDatabase
import com.example.data.model.PrayerLog
import com.example.data.model.PrayerTimeInfo
import com.example.data.model.PrayerType
import com.example.data.model.TasbihCounter
import com.example.data.model.UserSettings
import com.example.qibla.QiblaCompassEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MusallaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MusallaDatabase.getDatabase(application, viewModelScope)
    private val prayerDao = db.prayerDao()
    private val settingsDao = db.userSettingsDao()
    private val tasbihDao = db.tasbihDao()

    private val _userSettings = MutableStateFlow(UserSettings())
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    private val _prayerTimes = MutableStateFlow<List<PrayerTimeInfo>>(emptyList())
    val prayerTimes: StateFlow<List<PrayerTimeInfo>> = _prayerTimes.asStateFlow()

    private val _nextPrayer = MutableStateFlow<PrayerTimeInfo?>(null)
    val nextPrayer: StateFlow<PrayerTimeInfo?> = _nextPrayer.asStateFlow()

    private val _todayPrayerLog = MutableStateFlow(PrayerLog(getTodayDateString()))
    val todayPrayerLog: StateFlow<PrayerLog> = _todayPrayerLog.asStateFlow()

    private val _tasbihList = MutableStateFlow<List<TasbihCounter>>(emptyList())
    val tasbihList: StateFlow<List<TasbihCounter>> = _tasbihList.asStateFlow()

    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private val _strictLockActive = MutableStateFlow(false)
    val strictLockActive: StateFlow<Boolean> = _strictLockActive.asStateFlow()

    private val _qiblaAzimuth = MutableStateFlow(0f)
    val qiblaAzimuth: StateFlow<Float> = _qiblaAzimuth.asStateFlow()

    private val _qiblaBearing = MutableStateFlow(0f)
    val qiblaBearing: StateFlow<Float> = _qiblaBearing.asStateFlow()

    private val _isQiblaAligned = MutableStateFlow(false)
    val isQiblaAligned: StateFlow<Boolean> = _isQiblaAligned.asStateFlow()

    private val _makkahDistanceKm = MutableStateFlow(4520)
    val makkahDistanceKm: StateFlow<Int> = _makkahDistanceKm.asStateFlow()

    private var qiblaEngine: QiblaCompassEngine? = null

    init {
        loadSettings()
        loadTasbihs()
        loadTodayLog()
        initQiblaEngine()
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsDao.getUserSettingsFlow().collect { settings ->
                val current = settings ?: UserSettings()
                _userSettings.value = current
                recalculatePrayers(current)
            }
        }
    }

    private fun loadTasbihs() {
        viewModelScope.launch {
            tasbihDao.getAllTasbihs().collect { list ->
                _tasbihList.value = list
            }
        }
    }

    private fun loadTodayLog() {
        viewModelScope.launch {
            val today = getTodayDateString()
            val log = prayerDao.getPrayerLog(today) ?: PrayerLog(today)
            _todayPrayerLog.value = log
        }
    }

    fun recalculatePrayers(settings: UserSettings = _userSettings.value) {
        val method = when (settings.calculationMethod) {
            "ISNA" -> PrayerCalculator.Method.ISNA
            "Egypt" -> PrayerCalculator.Method.EGYPT
            "Makkah" -> PrayerCalculator.Method.MAKKAH
            "Karachi" -> PrayerCalculator.Method.KARACHI
            "Tehran" -> PrayerCalculator.Method.TEHRAN
            else -> PrayerCalculator.Method.MWL
        }

        val calculated = PrayerCalculator.calculateDailyPrayers(
            lat = settings.latitude,
            lng = settings.longitude,
            calendar = Calendar.getInstance(),
            method = method
        )

        val log = _todayPrayerLog.value
        val updated = calculated.map { p ->
            val done = when (p.type) {
                PrayerType.FAJR -> log.fajrDone
                PrayerType.DHUHR -> log.dhuhrDone
                PrayerType.ASR -> log.asrDone
                PrayerType.MAGHRIB -> log.maghribDone
                PrayerType.ISHA -> log.ishaDone
                else -> false
            }
            p.copy(isCompleted = done)
        }

        _prayerTimes.value = updated
        _nextPrayer.value = updated.firstOrNull { it.isNext } ?: updated.firstOrNull { !it.isCompleted && it.type != PrayerType.SUNRISE } ?: updated.firstOrNull()
    }

    fun togglePrayerCompleted(type: PrayerType) {
        viewModelScope.launch {
            val today = getTodayDateString()
            val current = _todayPrayerLog.value
            val newLog = when (type) {
                PrayerType.FAJR -> current.copy(fajrDone = !current.fajrDone)
                PrayerType.DHUHR -> current.copy(dhuhrDone = !current.dhuhrDone)
                PrayerType.ASR -> current.copy(asrDone = !current.asrDone)
                PrayerType.MAGHRIB -> current.copy(maghribDone = !current.maghribDone)
                PrayerType.ISHA -> current.copy(ishaDone = !current.ishaDone)
                else -> current
            }
            _todayPrayerLog.value = newLog
            prayerDao.insertOrUpdatePrayerLog(newLog)
            recalculatePrayers()
        }
    }

    fun toggleFemaleMode(enabled: Boolean) {
        val updated = _userSettings.value.copy(isFemaleMode = enabled)
        _userSettings.value = updated
        viewModelScope.launch { settingsDao.saveUserSettings(updated) }
    }

    fun togglePeriodPause(active: Boolean) {
        val today = getTodayDateString()
        val updated = _userSettings.value.copy(
            isPeriodPauseActive = active,
            periodPauseStartDate = if (active) today else null
        )
        _userSettings.value = updated

        val log = _todayPrayerLog.value.copy(isPeriodPausedDay = active)
        _todayPrayerLog.value = log

        viewModelScope.launch {
            settingsDao.saveUserSettings(updated)
            prayerDao.insertOrUpdatePrayerLog(log)
        }
    }

    fun setLanguage(code: String) {
        val updated = _userSettings.value.copy(languageCode = code)
        _userSettings.value = updated
        viewModelScope.launch { settingsDao.saveUserSettings(updated) }
    }

    fun setCalculationMethod(method: String) {
        val updated = _userSettings.value.copy(calculationMethod = method)
        _userSettings.value = updated
        viewModelScope.launch { settingsDao.saveUserSettings(updated) }
        recalculatePrayers(updated)
    }

    fun updateLocation(city: String, lat: Double, lng: Double) {
        val updated = _userSettings.value.copy(cityName = city, latitude = lat, longitude = lng)
        _userSettings.value = updated
        viewModelScope.launch { settingsDao.saveUserSettings(updated) }
        recalculatePrayers(updated)

        qiblaEngine?.calculateQiblaBearing(lat, lng)
        qiblaEngine?.let {
            _makkahDistanceKm.value = it.calculateDistanceToMakkah(lat, lng)
        }
    }

    fun setActiveTab(index: Int) {
        _activeTab.value = index
    }

    fun triggerStrictLock() {
        _strictLockActive.value = true
    }

    fun dismissStrictLock(verified: Boolean) {
        _strictLockActive.value = false
        if (verified) {
            _nextPrayer.value?.let { prayer ->
                togglePrayerCompleted(prayer.type)
            }
        }
    }

    fun incrementTasbih(id: Long) {
        viewModelScope.launch {
            val item = _tasbihList.value.find { it.id == id } ?: return@launch
            var newCount = item.currentCount + 1
            var laps = item.totalLaps
            if (newCount >= item.targetCount) {
                newCount = 0
                laps += 1
            }
            tasbihDao.updateCount(id, newCount, laps)
        }
    }

    fun resetTasbih(id: Long) {
        viewModelScope.launch {
            tasbihDao.resetCount(id)
        }
    }

    private fun initQiblaEngine() {
        qiblaEngine = QiblaCompassEngine(getApplication()) { azimuth, bearing, isAligned ->
            _qiblaAzimuth.value = azimuth
            _qiblaBearing.value = bearing
            _isQiblaAligned.value = isAligned
        }
        val settings = _userSettings.value
        qiblaEngine?.let {
            _makkahDistanceKm.value = it.calculateDistanceToMakkah(settings.latitude, settings.longitude)
        }

package com.example.verification

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

object PrayerMatVerifier {

    data class VerificationResult(
        val isVerified: Boolean,
        val confidencePercent: Int,
        val message: String
    )

    fun analyzePrayerMatImage(bitmap: Bitmap): VerificationResult {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return VerificationResult(false, 0, "Invalid image dimensions")
        }

        val step = (width / 50).coerceAtLeast(1)
        var sampledPixels = 0
        var totalRed = 0L
        var totalGreen = 0L
        var totalBlue = 0L
        var colorVarianceCount = 0

        var prevColor = 0

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                totalRed += r
                totalGreen += g
                totalBlue += b
                sampledPixels++

                if (sampledPixels > 1) {
                    val prevR = Color.red(prevColor)
                    val prevG = Color.green(prevColor)
                    val prevB = Color.blue(prevColor)
                    val diff = abs(r - prevR) + abs(g - prevG) + abs(b - prevB)
                    if (diff > 35) {
                        colorVarianceCount++
                    }
                }
                prevColor = pixel
            }
        }

        if (sampledPixels == 0) return VerificationResult(false, 0, "No pixel data sampled")

        val avgR = (totalRed / sampledPixels).toInt()
        val avgG = (totalGreen / sampledPixels).toInt()
        val avgB = (totalBlue / sampledPixels).toInt()

        val varianceRatio = colorVarianceCount.toDouble() / sampledPixels.toDouble()
        val brightness = (avgR * 299 + avgG * 587 + avgB * 114) / 1000

        val isRichCarpetColor = (avgG > avgR && avgG > avgB) || (avgR > 100 && avgG < 100) || (avgB > 100 && avgR < 100) || (avgR > 120 && avgG > 100 && avgB < 90)
        val hasFabricTexture = varianceRatio > 0.10 && brightness > 15

        return if (hasFabricTexture || isRichCarpetColor) {
            val score = ((varianceRatio * 100) + (if (isRichCarpetColor) 40 else 20)).toInt().coerceIn(78, 98)
            VerificationResult(
                isVerified = true,
                confidencePercent = score,
                message = "Prayer Mat Verified! ($score% Pattern Confidence)"
            )
        } else {
            VerificationResult(
                isVerified = false,
                confidencePercent = 42,
                message = "Align camera clearly over Prayer Mat carpet/fabric."
            )
        }
    }
}package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.LanguageManager
import com.example.ui.theme.MusallaBgDark
import com.example.ui.theme.MusallaBorderGreen
import com.example.ui.theme.MusallaButtonDark
import com.example.ui.theme.MusallaCardBorder
import com.example.ui.theme.MusallaCardDark
import com.example.ui.theme.MusallaPrimaryDark
import com.example.ui.theme.MusallaPrimaryGreen
import com.example.ui.theme.MusallaRoseAccent
import com.example.ui.theme.MusallaTextGreenAccent
import com.example.ui.theme.MusallaTextLight
import com.example.ui.theme.MusallaTextMuted
import com.example.ui.theme.MusallaTextSage
import com.example.viewmodel.MusallaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: MusallaViewModel,
    onNavigateToSettings: () -> Unit,
    onOpenTool: (String) -> Unit
) {
    val settings by viewModel.userSettings.collectAsState()
    val nextPrayer by viewModel.nextPrayer.collectAsState()
    val langCode = settings.languageCode

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val currentTimeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MusallaBgDark)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = LanguageManager.getString("app_name", langCode),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    color = MusallaTextSage,
                    letterSpacing = (-0.5).sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(pulseAlpha)
                            .background(Color(0xFF22C55E), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${settings.cityName} • $currentTimeString",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MusallaTextMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MusallaPrimaryGreen)
                    .border(1.dp, MusallaBorderGreen, CircleShape)
                    .clickable { onNavigateToSettings() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⚙️", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Hero: Next Prayer Card
        val prayerName = nextPrayer?.type?.displayNameKey ?: "Asr"
        val prayerTime = nextPrayer?.timeFormatted ?: "15:42"
        val isCompleted = nextPrayer?.isCompleted ?: false

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(MusallaPrimaryGreen, MusallaPrimaryDark)
                    )
                )
                .border(1.dp, MusallaBorderGreen, RoundedCornerShape(32.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = LanguageManager.getString("upcoming_prayer", langCode).uppercase(Locale.ROOT),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MusallaTextGreenAccent,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = LanguageManager.getString(prayerName.lowercase(Locale.ROOT), langCode),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = MusallaTextLight
                    )
                    Text(
                        text = prayerTime,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MusallaTextGreenAccent
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = LanguageManager.getString("status", langCode).uppercase(Locale.ROOT),
                            fontSize = 10.sp,
                            color = MusallaTextMuted,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (isCompleted) LanguageManager.getString("prayed", langCode)
                            else LanguageManager.getString("locked_mat_required", langCode),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isCompleted) MusallaTextGreenAccent else MusallaTextLight
                        )
                    }

                    Button(
                        onClick = { viewModel.triggerStrictLock() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MusallaTextSage,
                            contentColor = MusallaBgDark
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text(
                            text = LanguageManager.getString("verify_now", langCode),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                colors = CardDefaults.cardColors(containerColor = MusallaCardDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, MusallaCardBorder),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = LanguageManager.getString("prayer_streak", langCode).uppercase(Locale.ROOT),
                        fontSize = 10.sp,
                        color = MusallaTextMuted,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${settings.currentStreakDays}",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Light,
                            color = MusallaTextSage
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = LanguageManager.getString("days", langCode),
                            fontSize = 12.sp,
                            color = MusallaTextMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                colors = CardDefaults.cardColors(containerColor = MusallaCardDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, MusallaCardBorder),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = LanguageManager.getString("period_pause", langCode).uppercase(Locale.ROOT),
                        fontSize = 10.sp,
                        color = MusallaTextMuted,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Switch(
                            checked = settings.isPeriodPauseActive,
                            onCheckedChange = { viewModel.togglePeriodPause(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MusallaBgDark,
                                checkedTrackColor = MusallaRoseAccent,
                                uncheckedThumbColor = MusallaTextMuted,
                                uncheckedTrackColor = MusallaButtonDark
                            )
                        )
                        Text(
                            text = if (settings.isPeriodPauseActive) LanguageManager.getString("active", langCode)
                            else LanguageManager.getString("inactive", langCode),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (settings.isPeriodPauseActive) MusallaRoseAccent else MusallaTextMuted
                        )
                    }

                    Text(
                        text = LanguageManager.getString("streak_preserved", langCode),
                        fontSize = 9.sp,
                        color = MusallaTextMuted,
                        maxLines = 2,
                        lineHeight = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Tools Section
        Text(
            text = LanguageManager.getString("quick_tools", langCode).uppercase(Locale.ROOT),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MusallaTextMuted,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ToolButton(
                icon = "🧭",
                label = LanguageManager.getString("qibla", langCode),
                modifier = Modifier.weight(1f)
            ) { onOpenTool("qibla") }

            ToolButton(
                icon = "📿",
                label = LanguageManager.getString("tasbih", langCode),
                modifier = Modifier.weight(1f)
            ) { onOpenTool("tasbih") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ToolButton(
                icon = "📖",
                label = LanguageManager.getString("duas", langCode),
                modifier = Modifier.weight(1f)
            ) { onOpenTool("duas") }

            ToolButton(
                icon = "🌙",
                label = LanguageManager.getString("adhkar", langCode),
                modifier = Modifier.weight(1f)
            ) { onOpenTool("adhkar") }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ToolButton(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MusallaButtonDark)
            .border(1.dp, MusallaCardBorder, RoundedCornerShape(24.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label.uppercase(Locale.ROOT),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MusallaTextSage,
                letterSpacing = 0.8.sp
            )
        }
    }
}




name: Build Android APK

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Build Debug APK with Gradle
        run: gradle assembleDebug --no-daemon

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: musalla-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk



    
