package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: HabitRpgViewModel = viewModel()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF060E20)
                ) {
                    RpgAppContent(viewModel)
                }
            }
        }
    }
}

// --- CORE DATA STRUCTURES ---
data class Quest(
    val id: Long,
    val type: String, // "EASY", "MEDIUM", "HARD", "DONE"
    val title: String,
    val description: String,
    val xp: Int,
    val gp: Int,
    var completed: Boolean
)

data class ShopItem(
    val id: Int,
    val name: String,
    val desc: String,
    val cost: Int,
    var quantity: Int,
    val type: String, // "CONSUMABLE", "GEAR", "SPECIAL"
    val color: Color
)

data class GameProjectile(
    val id: Double,
    val x: Float,
    var y: Float,
    val speed: Float
)

data class Arena(
    val levelName: String,
    val enemyName: String,
    val backgroundName: String,
    val maxHP: Int,
    val currentHP: Int,
    val bgTheme: Color,
    val accentColor: Color,
    val symbolSeed: String,
    val imageRes: Int
)

// --- VIEWMODEL ---
class HabitRpgViewModel : ViewModel() {
    // Player Progression Status
    var trainerName by mutableStateOf("TRAINER 124")
    var trainerLevel by mutableStateOf(124) // Level initialized to 124
    var gpBalance by mutableStateOf(1250)
    var xpBalance by mutableStateOf(3450)
    var playerHP by mutableStateOf(100) // Out of 100

    // Multipliers & Day Tracker
    var streakCount by mutableStateOf(4)
    var xpMultiplier by mutableStateOf(1.2f)
    var totalFocusSeconds by mutableStateOf(350)

    // Progression index
    var maxUnlockedIndex by mutableStateOf(0)
    var currentArenaIndex by mutableStateOf(0)

    // Alert Messages & Popups Flow
    var alertMessage by mutableStateOf<String?>(null)
    var activeOracleMessage by mutableStateOf<String?>(null)
    var showOraclePopup by mutableStateOf(false)
    
    var activePostureResultMsg by mutableStateOf<String?>(null)
    var posturePopupGood by mutableStateOf(true)
    var showPosturePopup by mutableStateOf(false)
    var isStrikeFlashing by mutableStateOf(false)

    // Lists state models
    val arenas = listOf(
        Arena("LEVEL 1", "LAZER-EYED GODZILLA", "CITY OF FOG AND DESTRUCTION", 50000, 42000, Color(0xFF0F1B3E), Color(0xFF6BFB9A), "godzilla", com.example.R.drawable.img_godzilla_1779982615354),
        Arena("LEVEL 2", "ROCK-HARD GORILLA", "MOUNTAINS OF LAVA", 80000, 74200, Color(0xFF381212), Color(0xFFFF9800), "gorilla", com.example.R.drawable.img_gorilla_1779982637201),
        Arena("LEVEL 3", "FLAME-MOUTH LIZARD DRAGON", "FOREST TEMPLE OF THE LIZARDS", 120000, 110000, Color(0xFF11381E), Color(0xFF00E676), "dragon", com.example.R.drawable.img_dragon_1779982660293),
        Arena("BOSS LEVEL", "SHARP-CLAWED ARMOURED BEAR", "CELEBRATION - CHAMPION ARENA", 200000, 185000, Color(0xFF26123E), Color(0xFFE040FB), "bear", com.example.R.drawable.img_bear_1779982679855)
    )
    val levelBossHealthTiers = listOf(100, 200, 350, 500)

    var quests = mutableStateListOf(
        Quest(1L, "EASY", "Hydration Ritual", "Consume 2.5L of water to optimize core parameters.", 50, 100, false),
        Quest(2L, "MEDIUM", "Cardio Burnout", "Achieve 20 minutes sustained heart rate above 140 BPM.", 150, 300, false),
        Quest(3L, "HARD", "Iron Core Protocol", "Complete 5 sets of heavy compound lifts.", 400, 850, false),
        Quest(4L, "DONE", "Sleep Cycle Sync", "Achieve 8 hours of restorative daily sleep.", 200, 150, true)
    )

    var shopItems = mutableStateListOf(
        ShopItem(1, "HP Medic Pack", "Instantly restores 35 HP. Grants temporary ATK boosts.", 150, 1, "CONSUMABLE", Color(0xFF00E676)),
        ShopItem(2, "M4A4 Assault Rifle", "Tactical weapon yielding energy discharge advantages.", 450, 0, "GEAR", Color(0xFFFFD9C1)),
        ShopItem(3, "XP Crystal Container", "Crystalline container with potent training energy boost.", 300, 0, "SPECIAL", Color(0xFFDDB7FF)),
        ShopItem(4, "Real Life Break Coupon", "Take a cozy 15-minute screen-free rest.", 200, 0, "CONSUMABLE", Color(0xFFFFB74D))
    )

    // Category Distrib Log Tracker
    var categoryLogs = mutableStateMapOf(
        "Health" to 14,
        "Mind" to 9,
        "School" to 18,
        "Skills" to 11
    )

    // Pomodoro Rig State
    var pomoActive by mutableStateOf(false)
    var pomoMode by mutableStateOf("focus") // 'focus' or 'rest'
    var pomoSecondsLeft by mutableStateOf(25 * 60)
    private var pomoJob: Job? = null

    // Fitness workout monitor
    var fitnessActive by mutableStateOf(false)
    var fitnessSeconds by mutableStateOf(0)
    var simulatedPulse by mutableStateOf<Int?>(null)
    var simulatedWatts by mutableStateOf<Int?>(null)
    private var fitnessJob: Job? = null

    // 2D Arcade Battle Modals
    var minigameActive by mutableStateOf(false)
    var gameState by mutableStateOf("intro") // "intro", "playing", "won", "lost"
    var playerX by mutableStateOf(50f)
    var playerY by mutableStateOf(75f)
    val projectiles = mutableStateListOf<GameProjectile>()
    val playerProjectiles = mutableStateListOf<GameProjectile>()
    var gameLives by mutableStateOf(3)
    var bossHP by mutableStateOf(100)
    var bossMaxHP by mutableStateOf(100)
    private var gameJob: Job? = null

    // Alert / Toast utility
    fun triggerAlert(msg: String) {
        alertMessage = msg
    }

    fun dismissAlert() {
        alertMessage = null
    }

    // Pomodoro Ticker Controller
    fun togglePomodoro(scope: CoroutineScope) {
        if (pomoActive) {
            pomoActive = false
            pomoJob?.cancel()
        } else {
            pomoActive = true
            pomoJob = scope.launch {
                while (pomoActive && pomoSecondsLeft > 0) {
                    delay(1000L)
                    pomoSecondsLeft -= 1
                    if (pomoMode == "focus") {
                        totalFocusSeconds += 1
                    }
                    if (pomoSecondsLeft <= 0) {
                        pomoActive = false
                        val nextMode = if (pomoMode == "focus") "rest" else "focus"
                        pomoMode = nextMode
                        pomoSecondsLeft = if (nextMode == "focus") 25 * 60 else 5 * 60
                        triggerAlert("Time's up! Transitioned to ${nextMode.uppercase()} mode.")
                        break
                    }
                }
            }
        }
    }

    fun changePomoMode(mode: String) {
        pomoActive = false
        pomoJob?.cancel()
        pomoMode = mode
        pomoSecondsLeft = if (mode == "focus") 25 * 60 else 5 * 60
        triggerAlert("Switched to ${mode.uppercase()} timer template.")
    }

    // Fitness Workout Controller
    fun toggleFitnessWork(scope: CoroutineScope) {
        if (fitnessActive) {
            fitnessActive = false
            fitnessJob?.cancel()
        } else {
            fitnessActive = true
            fitnessJob = scope.launch {
                while (fitnessActive) {
                    delay(1000L)
                    fitnessSeconds += 1
                    simulatedPulse = Random.nextInt(120, 141)
                    simulatedWatts = Random.nextInt(165, 216)
                }
            }
            triggerAlert("Workout room scanning initialized! Heart rate metrics active.")
        }
    }

    fun finishWorkout() {
        if (!fitnessActive && fitnessSeconds == 0) {
            triggerAlert("Activate workout chamber first before finishing.")
            return
        }
        fitnessActive = false
        fitnessJob?.cancel()
        fitnessSeconds = 0
        simulatedPulse = null
        simulatedWatts = null
        
        // Reward Player
        val addedXp = 842
        val nextXp = xpBalance + addedXp
        if (nextXp >= 5000) {
            trainerLevel += 1
            xpBalance = nextXp - 5000
            triggerAlert("LEVEL UP! Reached level $trainerLevel. Workout complete (+842 XP).")
        } else {
            xpBalance = nextXp
            triggerAlert("Workout session ended successfully! Gained +842 XP.")
        }
    }

    // Quest complete & Antagonist strikes
    fun completeQuest(questId: Long) {
        val questIndex = quests.indexOfFirst { it.id == questId }
        if (questIndex != -1 && !quests[questIndex].completed) {
            val q = quests[questIndex]
            q.completed = true
            
            // Adjust balances
            val boostedGp = Math.round(q.gp * xpMultiplier)
            gpBalance += boostedGp
            
            val nextXp = xpBalance + q.xp
            if (nextXp >= 5000) {
                trainerLevel += 1
                xpBalance = nextXp - 5000
                triggerAlert("LEVEL UP! Reached lvl $trainerLevel. Quest done (+${q.xp} XP / +$boostedGp GP).")
            } else {
                xpBalance = nextXp
                triggerAlert("Quest synchronized! Gained +$boostedGp GP and +${q.xp} XP.")
            }

            // Up indices
            streakCount += 1
            xpMultiplier = (1.0f + (streakCount * 0.1f)).coerceAtMost(1.5f)

            // Increment stats log
            val category = when (q.type) {
                "EASY" -> "Mind"
                "MEDIUM" -> "School"
                else -> "Skills"
            }
            categoryLogs[category] = (categoryLogs[category] ?: 0) + 1
            quests[questIndex] = q
        }
    }

    fun triggerMissedQuestPenalty(scope: CoroutineScope, q: Quest) {
        // Antagonist strike flash
        scope.launch {
            isStrikeFlashing = true
            delay(500L)
            isStrikeFlashing = false
        }

        // Damage Player
        val penalty = 15
        playerHP = (playerHP - penalty).coerceAtLeast(0)
        streakCount = 0
        xpMultiplier = 1.0f

        triggerAlert("CRITICAL HIT! Passed deadline on '${q.title}'. Screen flash! Lost -$penalty HP. Multiplier reset.")
        quests.removeIf { it.id == q.id }
    }

    // Custom Quest Forge creator
    fun addNewQuest(title: String, desc: String, xp: Int, gp: Int) {
        val newQ = Quest(
            id = System.currentTimeMillis(),
            type = "HARD",
            title = title,
            description = desc.ifBlank { "Forced custom training habit matrix." },
            xp = xp,
            gp = gp,
            completed = false
        )
        quests.add(0, newQ)
        triggerAlert("Custom quest successfully forged into active checklist!")
    }

    fun addRandomQuest() {
        val randomTitles = listOf("Aerobics Dynamic Run", "Core Core Stretch", "Cardio Sprint Set", "Synchronized Breath")
        val randomXps = listOf(100, 120, 150)
        val randomGps = listOf(150, 200, 250)
        
        val idxTitle = Random.nextInt(0, randomTitles.size)
        val idxXp = Random.nextInt(0, randomXps.size)
        val idxGp = Random.nextInt(0, randomGps.size)

        addNewQuest(
            title = randomTitles[idxTitle],
            desc = "Engage inside synchronized physical restoration protocols.",
            xp = randomXps[idxXp],
            gp = randomGps[idxGp]
        )
    }

    // Room Shop Purchases
    fun buyShopItem(itemId: Int) {
        val index = shopItems.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val item = shopItems[index]
            if (gpBalance >= item.cost) {
                gpBalance -= item.cost
                item.quantity += 1
                shopItems[index] = item
                
                if (itemId == 1) { // HP Med Pack
                    playerHP = (playerHP + 35).coerceAtMost(100)
                    triggerAlert("HP Medic pack purchased and consumed! Restored 35 HP.")
                } else {
                    triggerAlert("Acquired ${item.name}! Quantity increased to ${item.quantity}.")
                }
            } else {
                triggerAlert("Insufficient GP copper energy! Costs ${item.cost} GP.")
            }
        }
    }

    fun restoreShieldHP() {
        if (gpBalance >= 150) {
            if (playerHP >= 100) {
                triggerAlert("Stamina Shield is already fully optimized.")
                return
            }
            gpBalance -= 150
            playerHP = (playerHP + 35).coerceAtMost(100)
            triggerAlert("Stamina Shield HP restored +35! Spent 150 GP.")
        } else {
            triggerAlert("Requires 150 GP copper tokens to trigger recovery pulse!")
        }
    }

    // AI POSTURE SCAN SIMULATOR
    fun triggerPostureCameraScan(scope: CoroutineScope, hasFile: Boolean) {
        if (!hasFile) {
            triggerAlert("Skeletal Error: Load posture image setup first in checking flow!")
            return
        }
        scope.launch {
            triggerAlert("Posture analysis scan active. Reading skeletal orientation vectors...")
            delay(1500L)
            
            // Randomly evaluate posture: 70% straight, 30% bad
            val isGood = Random.nextFloat() > 0.3f
            showPosturePopup = true
            posturePopupGood = isGood
            
            if (isGood) {
                val basicGP = 150
                val boostedGP = Math.round(basicGP * xpMultiplier)
                gpBalance += boostedGP
                
                val nextXp = xpBalance + 200
                if (nextXp >= 5000) {
                    trainerLevel += 1
                    xpBalance = nextXp - 5000
                } else {
                    xpBalance = nextXp
                }
                
                streakCount += 1
                xpMultiplier = (1.0f + (streakCount * 0.1f)).coerceAtMost(1.5f)
                categoryLogs["Health"] = (categoryLogs["Health"] ?: 0) + 1
                
                activePostureResultMsg = "AI Skeletal Check: Perfect alignment index! Added +$boostedGP GP and +200 XP to your inventory assets!"
            } else {
                val penalty = 15
                playerHP = (playerHP - penalty).coerceAtLeast(0)
                streakCount = 0
                xpMultiplier = 1.0f
                isStrikeFlashing = true
                delay(400L)
                isStrikeFlashing = false
                
                activePostureResultMsg = "AI Check Error: Lumbar spine angle curvature exceeded safe threshold! Active Threat boss ${arenas[currentArenaIndex].enemyName} penetrated defenses for -$penalty HP! Multipliers reset."
            }
        }
    }

    // AI SENTIMENT LINGUISTIC MODEL
    fun triggerSentimentAudit(scope: CoroutineScope, text: String) {
        if (text.isBlank()) {
            triggerAlert("Linguistic warning: Empty diary message cannot run in sentiment audit.")
            return
        }
        scope.launch {
            triggerAlert("Sentimental NLP loading natural language layers...")
            delay(1200L)
            
            val cleaned = text.lowercase()
            activeOracleMessage = when {
                cleaned.contains("sad") || cleaned.contains("cry") || cleaned.contains("down") || cleaned.contains("hurt") -> {
                    "Oracle Guidance: System detects a slight dip in emotional resilience today, Trainer. Remember, even the ultimate sentries experience heavy shields. Logged reflection success! Rest up and know you're level-building anyway."
                }
                cleaned.contains("stress") || cleaned.contains("anxious") || cleaned.contains("tired") || cleaned.contains("exhausted") -> {
                    "Oracle Guidance: Sentry alert! Critical stress coefficients. Rest and screen-free buffers are essential gameplay rest loops. Use your 'Real Life Coupon' and replenish stamina vectors. Let's conquer tomorrow."
                }
                cleaned.contains("angry") || cleaned.contains("mad") || cleaned.contains("frustrated") -> {
                    "Oracle Guidance: Heat metrics spiking! Let's translate raw emotional surge into calculated defensive structures. Cool down, focus on simple compound breaths, and retain tactical command. You are bigger than this threat."
                }
                cleaned.contains("happy") || cleaned.contains("good") || cleaned.contains("excited") -> {
                    "Oracle Guidance: Spectacular bio-signals! Added gold tier positive aura to mental assets database. Take this optimized energy to active quests and clear goals. Keep scaling upward!"
                }
                else -> {
                    "Oracle Guidance: Focused mental journaling complete! Deep reflections processed. Cleansed Mind category attributes. Proceed with confidence on habit milestones."
                }
            }
            
            // Log reward
            val boostedGp = Math.round(100 * xpMultiplier)
            gpBalance += boostedGp
            xpBalance = (xpBalance + 150).let {
                if (it >= 5000) {
                    trainerLevel += 1
                    it - 5000
                } else it
            }
            streakCount += 1
            xpMultiplier = (1.0f + (streakCount * 0.1f)).coerceAtMost(1.5f)
            categoryLogs["Mind"] = (categoryLogs["Mind"] ?: 0) + 1
            
            showOraclePopup = true
        }
    }

    // 2D ARCADE OPERATIONS
    fun launchArcadeGame(scope: CoroutineScope) {
        minigameActive = true
        gameState = "intro"
        projectiles.clear()
        playerProjectiles.clear()
        gameLives = 3
    }

    fun startArcadeRunning(scope: CoroutineScope) {
        gameState = "playing"
        val maxHp = levelBossHealthTiers[currentArenaIndex]
        bossMaxHP = maxHp
        bossHP = maxHp
        gameLives = 3
        playerX = 50f
        playerY = 75f
        projectiles.clear()
        playerProjectiles.clear()

        val spawnProb = 0.08f + (currentArenaIndex * 0.04f)
        val speedMin = 3f + (currentArenaIndex * 1f)
        val speedMax = 5f + (currentArenaIndex * 1.5f)

        gameJob?.cancel()
        gameJob = scope.launch {
            while (gameState == "playing") {
                delay(60L) // roughly 16 ticks/sec

                // Spawn meteor attack
                if (Random.nextFloat() < spawnProb) {
                    val dropX = Random.nextInt(10, 91).toFloat()
                    val speed = speedMin + Random.nextFloat() * (speedMax - speedMin)
                    projectiles.add(GameProjectile(Math.random(), dropX, 0f, speed))
                }

                // Move Player Lasers Upward
                val laserIterator = playerProjectiles.iterator()
                while (laserIterator.hasNext()) {
                    val p = laserIterator.next()
                    p.y -= 7f
                    
                    // Collision with boss box (sit around Y=12f, width 40%)
                    if (p.y <= 16f && Math.abs(p.x - 50f) < 22f) {
                        bossHP = (bossHP - 10).coerceAtLeast(0)
                        laserIterator.remove()
                        if (bossHP <= 0) {
                            gameState = "won"
                            break
                        }
                    } else if (p.y < 0f) {
                        laserIterator.remove()
                    }
                }

                // Move Enemy Projectiles Downward
                if (gameState == "playing") {
                    val bulletIterator = projectiles.iterator()
                    while (bulletIterator.hasNext()) {
                        val bullet = bulletIterator.next()
                        bullet.y += bullet.speed
                        
                        // Collision check with Player
                        val distX = Math.abs(bullet.x - playerX)
                        val distY = Math.abs(bullet.y - playerY)
                        if (distX < 12f && distY < 8f) {
                            gameLives = (gameLives - 1).coerceAtLeast(0)
                            bulletIterator.remove()
                            if (gameLives <= 0) {
                                gameState = "lost"
                                break
                            }
                        } else if (bullet.y > 100f) {
                            bulletIterator.remove()
                        }
                    }
                }
            }
        }
    }

    fun fireTacticalLaser() {
        if (gameState != "playing") return
        playerProjectiles.add(GameProjectile(Math.random(), playerX, playerY - 4f, 8f))
    }

    fun claimArcadeVictoryRewards() {
        gpBalance += 500
        playerHP = (playerHP + 25).coerceAtMost(100)
        
        val nextXp = xpBalance + 1000
        if (nextXp >= 5000) {
            trainerLevel += 1
            xpBalance = nextXp - 5000
        } else {
            xpBalance = nextXp
        }
        
        maxUnlockedIndex = (maxUnlockedIndex + 1).coerceAtMost(3)
        currentArenaIndex = maxUnlockedIndex
        minigameActive = false
        triggerAlert("Boss vanquished! Gained +1000 XP, +500 GP, +25 Shield HP. Upgraded Stage index!")
    }

    fun closeArcade() {
        gameJob?.cancel()
        minigameActive = false
    }

    override fun onCleared() {
        pomoJob?.cancel()
        fitnessJob?.cancel()
        gameJob?.cancel()
        super.onCleared()
    }
}

// --- MAIN COMPOSE DIRECTIVE ---
@Composable
fun RpgAppContent(viewModel: HabitRpgViewModel = viewModel()) {
    var activeTab by remember { mutableStateOf("home") }
    val scope = rememberCoroutineScope()

    // Overlay Strike red visual indicators
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Screen HUD Header
            HeaderHUD(viewModel)

            // Dynamic Views Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    "home" -> HomeTabScreen(viewModel, scope)
                    "quests" -> QuestsTabScreen(viewModel, scope)
                    "fitness" -> FitnessTabScreen(viewModel, scope)
                    "analytics" -> AnalyticsTabScreen(viewModel)
                    "shop" -> ShopTabScreen(viewModel)
                }
            }

            // Bottom Core Navigation
            BottomNavBar(activeTab = activeTab, onTabSelect = { activeTab = it })
        }

        // Active notification banner overlay
        viewModel.alertMessage?.let { msg ->
            Dialog(onDismissRequest = { viewModel.dismissAlert() }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                    border = BorderStroke(1.5.dp, Color(0xFF6BFB9A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "HUD NOTIFICATION",
                            color = Color(0xFF6BFB9A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = { viewModel.dismissAlert() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6BFB9A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Acknowledge", color = Color(0xFF003919), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // RED HIT FLASH LAYER
        if (viewModel.isStrikeFlashing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = 0.45f))
            )
        }

        // Oracle Sentiment Insight Dialog
        if (viewModel.showOraclePopup) {
            Dialog(onDismissRequest = { viewModel.showOraclePopup = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF23143A)),
                    border = BorderStroke(2.dp, Color(0xFFDDB7FF)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDDB7FF).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFDDB7FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = "Oracle", tint = Color(0xFFDDB7FF), modifier = Modifier.size(28.dp))
                        }
                        Text(
                            text = "NLP AUDIT REPORT",
                            color = Color(0xFFDDB7FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        Text(
                            text = "Oracle Sentimental Reframing",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = viewModel.activeOracleMessage ?: "",
                            color = Color(0xFFE2D9F3),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        Button(
                            onClick = { viewModel.showOraclePopup = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDDB7FF)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("Accept Counseling", color = Color(0xFF26123E), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Camera CV Posture outcome Dialog
        if (viewModel.showPosturePopup) {
            Dialog(onDismissRequest = { viewModel.showPosturePopup = false }) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (viewModel.posturePopupGood) Color(0xFF0F2615) else Color(0xFF2D1212)
                    ),
                    border = BorderStroke(2.dp, if (viewModel.posturePopupGood) Color(0xFF00E676) else Color.Red),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (viewModel.posturePopupGood) Color(0xFF00E676).copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (viewModel.posturePopupGood) Icons.Filled.Check else Icons.Filled.Warning,
                                contentDescription = "Result",
                                tint = if (viewModel.posturePopupGood) Color(0xFF00E676) else Color.Red,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "SKELETAL RESULT MATRIX",
                            color = if (viewModel.posturePopupGood) Color(0xFF00E676) else Color.Red,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        Text(
                            text = if (viewModel.posturePopupGood) "EXCELLENT POSTURE!" else "CURVATURE DETECTED!",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = viewModel.activePostureResultMsg ?: "",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        Button(
                            onClick = { viewModel.showPosturePopup = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.posturePopupGood) Color(0xFF00E676) else Color.Red
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(
                                "Back to Training Room", 
                                color = if (viewModel.posturePopupGood) Color(0xFF003311) else Color.White, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2D MINIGAME MODAL OVERLAY
        if (viewModel.minigameActive) {
            Dialog(
                onDismissRequest = { viewModel.closeArcade() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF050B18)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(12.dp)
                    ) {
                        // Title Frame bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = viewModel.arenas[viewModel.currentArenaIndex].enemyName,
                                    color = Color(0xFFFFB4AB),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Shoot boss. Dodge falling projectiles!",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp
                                )
                            }
                            IconButton(onClick = { viewModel.closeArcade() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Exit", tint = Color.White)
                            }
                        }

                        // Play Chamber Canvas
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF020712))
                                .border(1.dp, Color(0xFF3D4A3E), RoundedCornerShape(16.dp))
                        ) {
                            // Render game states
                            when (viewModel.gameState) {
                                "intro" -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "ACTIVE BOSS FIGHT",
                                            color = Color(0xFFFFC107),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "Trigger Core Chamber!",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                        Text(
                                            text = "Move player using bottom navigation arrows, and fire tactical power with the Fire Trigger. Strike down threat health vectors to clear the stage target!",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(bottom = 24.dp)
                                        )
                                        Button(
                                            onClick = { viewModel.startArcadeRunning(scope) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6BFB9A)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("arcade_start_btn").height(48.dp)
                                        ) {
                                            Text("ENGAGE BOSS BATTLE", color = Color(0xFF003919), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                "playing" -> {
                                    // Custom Game engine canvas with rich generated image illustrations
                                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                        val width = maxWidth
                                        val height = maxHeight

                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            // Draw lasers
                                            viewModel.playerProjectiles.forEach { lp ->
                                                drawCircle(
                                                    color = Color(0xFF00E676),
                                                    radius = 4.dp.toPx(),
                                                    center = Offset((lp.x / 100f) * size.width, (lp.y / 100f) * size.height)
                                                )
                                            }

                                            // Draw boss bullets
                                            viewModel.projectiles.forEach { bp ->
                                                drawCircle(
                                                    color = Color(0xFFFB923C),
                                                    radius = 5.dp.toPx(),
                                                    center = Offset((bp.x / 100f) * size.width, (bp.y / 100f) * size.height)
                                                )
                                            }
                                        }

                                        // Image for current level Boss
                                        val currentBoss = viewModel.arenas[viewModel.currentArenaIndex]
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(id = currentBoss.imageRes),
                                            contentDescription = currentBoss.enemyName,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .offset(x = (width / 2) - 32.dp, y = height * 0.04f)
                                                .clip(CircleShape)
                                                .border(2.dp, currentBoss.accentColor, CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )

                                        // Image for active Player Cyber Ranger
                                        val activeRangerImageId = when {
                                            viewModel.trainerLevel <= 123 -> com.example.R.drawable.img_ranger_starter_1779983746821
                                            viewModel.trainerLevel == 124 -> com.example.R.drawable.img_ranger_upgraded_1779983768353
                                            else -> com.example.R.drawable.img_ranger_master_1779983790584
                                        }
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(id = activeRangerImageId),
                                            contentDescription = "Active Cyber Ranger",
                                            modifier = Modifier
                                                .size(28.dp)
                                                .offset(
                                                    x = (width * (viewModel.playerX / 100f)) - 14.dp,
                                                    y = (height * (viewModel.playerY / 100f)) - 14.dp
                                                )
                                                .clip(CircleShape)
                                                .border(1.5.dp, Color(0xFF6BFB9A), CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )

                                        // Boss health matrix top HUD
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0x99000000)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                                Text(
                                                    "BOSS INTEGRITY: ${viewModel.bossHP}/${viewModel.bossMaxHP}",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    progress = { viewModel.bossHP.toFloat() / viewModel.bossMaxHP },
                                                    color = Color.Red,
                                                    trackColor = Color.DarkGray,
                                                    modifier = Modifier.width(140.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                                                )
                                            }
                                        }
                                    }
                                }
                                "won" -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "VICTORY ACHIEVED!",
                                            color = Color(0xFF00E676),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "The targets are completely neutralized. Superb performance utilizing customized weapons. Claims rewards below!",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 12.dp)
                                        )
                                        Button(
                                            onClick = { viewModel.claimArcadeVictoryRewards() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6BFB9A)),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("CLAIM +1000 XP / +500 GP", color = Color(0xFF003919), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                "lost" -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "CHAMBER DEFENSE DEFEATED",
                                            color = Color.Red,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "Active threat matrix broke down shielding layers. Settle down, train physical parameters further and try again!",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 12.dp)
                                        )
                                        Button(
                                            onClick = { viewModel.closeArcade() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("RETREAT BATTLE", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Mobile control elements
                        if (viewModel.gameState == "playing") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left navigation cross buttons
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = { viewModel.playerX = (viewModel.playerX - 10f).coerceAtLeast(5f) },
                                        modifier = Modifier.size(54.dp).background(Color(0xFF131F33), RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "Left", tint = Color.White)
                                    }
                                    IconButton(
                                        onClick = { viewModel.playerX = (viewModel.playerX + 10f).coerceAtMost(95f) },
                                        modifier = Modifier.size(54.dp).background(Color(0xFF131F33), RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(Icons.Filled.ArrowForward, contentDescription = "Right", tint = Color.White)
                                    }
                                }

                                // Right weapon fire circular trigger button (Requirement checklist: 48dp target)
                                Button(
                                    onClick = { viewModel.fireTacticalLaser() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    shape = CircleShape,
                                    modifier = Modifier.size(72.dp).testTag("fire_m4a4_weapon_trigger")
                                ) {
                                    Text("FIRE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        // Footer statistics panel
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFF1B2435)).padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ATTACK: LASER ENERGY", color = Color(0xFF6BFB9A), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("SHIELDS: ${viewModel.gameLives} / 3", color = Color(0xFFFB923C), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

// --- APP BAR HEADER HUD ---
@Composable
fun HeaderHUD(viewModel: HabitRpgViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1326)),
        shape = RoundedCornerShape(0.dp, 0.dp, 16.dp, 16.dp),
        border = BorderStroke(1.dp, Color(0xFF222A3D)),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Character Badge Viewport
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF131F33))
                            .border(
                                1.dp, 
                                if (viewModel.trainerLevel >= 125) Color(0xFF6BFB9A) else Color.Gray, 
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val activeProfileImageRes = when {
                            viewModel.trainerLevel <= 124 -> com.example.R.drawable.img_ranger_starter_1779983746821
                            else -> com.example.R.drawable.img_ranger_master_1779983790584
                        }
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = activeProfileImageRes),
                            contentDescription = "Active Profile Icon",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Column {
                        Text(viewModel.trainerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("LEVEL ${viewModel.trainerLevel}", color = Color(0xFF6BFB9A), fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                // Balance tokens
                Column(
                    modifier = Modifier.width(110.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF4A2D1F), RoundedCornerShape(12.dp))
                            .border(0.5.dp, Color(0xFFFFB47E), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GP", color = Color(0xFFFFB47E), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("${viewModel.gpBalance}", color = Color(0xFFFFB47E), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2D1F4A), RoundedCornerShape(12.dp))
                            .border(0.5.dp, Color(0xFFDDB7FF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("XP", color = Color(0xFFDDB7FF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("${viewModel.xpBalance}", color = Color(0xFFDDB7FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic progression bar rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TRAINER EXPERIENCE", color = Color.White.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Text("${viewModel.xpBalance}/5000", color = Color(0xFFDDB7FF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { viewModel.xpBalance.toFloat() / 5000f },
                        color = Color(0xFF9575CD),
                        trackColor = Color(0xFF131F33),
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("PLAYER STAMINA / HP", color = Color.White.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Text("${viewModel.playerHP}/100", color = if (viewModel.playerHP <= 30) Color.Red else Color(0xFF6BFB9A), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { viewModel.playerHP.toFloat() / 100f },
                        color = if (viewModel.playerHP <= 30) Color.Red else Color(0xFF00E676),
                        trackColor = Color(0xFF131F33),
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

// --- BOTTOM TAB NAVIGATION ---
@Composable
fun BottomNavBar(activeTab: String, onTabSelect: (String) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF131F33),
        tonalElevation = 4.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .height(58.dp)
    ) {
        val navTabs = listOf(
            Triple("home", Icons.Filled.Home, "Home"),
            Triple("quests", Icons.Filled.Check, "Quests"),
            Triple("fitness", Icons.Filled.Face, "Fitness"),
            Triple("analytics", Icons.Filled.Star, "Stats"),
            Triple("shop", Icons.Filled.ShoppingCart, "Shop")
        )

        navTabs.forEach { tab ->
            val isSelected = activeTab == tab.first
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelect(tab.first) },
                icon = {
                    Icon(
                        imageVector = tab.second,
                        contentDescription = tab.third,
                        tint = if (isSelected) Color(0xFF6BFB9A) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.third,
                        color = if (isSelected) Color(0xFF6BFB9A) else Color.White.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color(0xFF0F1B3E)
                )
            )
        }
    }
}

// --- HOME TAB SCREENS ---
@Composable
fun HomeTabScreen(viewModel: HabitRpgViewModel, scope: CoroutineScope) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pomodoro Work widget
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3449))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "HYBRID WORK SYSTEM",
                            color = Color(0xFF6BFB9A),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0F1B3E), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("POMODORO GEAR", color = Color(0xFFDDB7FF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Stitch Pomodoro Rig", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Reward complete focus intervals with rest coupons!", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Buttons selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.changePomoMode("focus") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.pomoMode == "focus") Color(0xFF0F1B3E) else Color(0xFF222A3D)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Focus (25m)", color = if (viewModel.pomoMode == "focus") Color(0xFF6BFB9A) else Color.LightGray, fontSize = 10.sp)
                        }
                        Button(
                            onClick = { viewModel.changePomoMode("rest") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.pomoMode == "rest") Color(0xFF0F1B3E) else Color(0xFF222A3D)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Rest (5m)", color = if (viewModel.pomoMode == "rest") Color(0xFF6BFB9A) else Color.LightGray, fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Timer displays
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF060E20), RoundedCornerShape(10.dp))
                            .border(0.5.dp, Color(0xFF222A3D), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val label = if (viewModel.pomoMode == "focus") "Focus Countdown" else "Rest Countdown"
                            Text(label.uppercase(), color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            val minutes = viewModel.pomoSecondsLeft / 60
                            val seconds = viewModel.pomoSecondsLeft % 60
                            val timeText = String.format("%02d:%02d", minutes, seconds)
                            Text(timeText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = { viewModel.togglePomodoro(scope) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.pomoActive) Color.Red else Color(0xFF00E676)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(if (viewModel.pomoActive) "Pause" else "Start", color = if (viewModel.pomoActive) Color.White else Color(0xFF003311), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Stitch Avatar Evolutionary progress widget
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3449))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "EVOLUTIONARY MATRIX",
                        color = Color(0xFF6BFB9A),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    val (title, detail) = when {
                        viewModel.trainerLevel <= 123 -> "Novice Hero Starter" to "Standard starter garments and basic training compass parameters."
                        viewModel.trainerLevel == 124 -> "Upgraded Cyber-Tactical Ranger" to "Equipped with titanium chest coordinates plate and visual HUD optics."
                        else -> "Master Neon Elite Sentinel" to "Quantum energy battle suit loaded with neon coordinates sensor shields."
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF060E20))
                                .border(1.dp, Color(0xFF6BFB9A), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val profileRangerImageId = when {
                                viewModel.trainerLevel <= 123 -> com.example.R.drawable.img_ranger_starter_1779983746821
                                viewModel.trainerLevel == 124 -> com.example.R.drawable.img_ranger_upgraded_1779983768353
                                else -> com.example.R.drawable.img_ranger_master_1779983790584
                            }
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = profileRangerImageId),
                                contentDescription = "Active Cyber Ranger",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(detail, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, lineHeight = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Sim level filters for test
                    Row(
                        modifier = Modifier.fillMaxWidth().border(0.5.dp, Color(0xFF222A3D), RoundedCornerShape(6.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sim Level:", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            arrayOf(123, 124, 125).forEach { lvl ->
                                Button(
                                    onClick = { viewModel.trainerLevel = lvl },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (viewModel.trainerLevel == lvl) Color(0xFF6BFB9A) else Color(0xFF222A3D)
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(width = 44.dp, height = 24.dp)
                                ) {
                                    Text("LV $lvl", color = if (viewModel.trainerLevel == lvl) Color(0xFF003311) else Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Opponent / Boss Stage container
        item {
            val activeBoss = viewModel.arenas[viewModel.currentArenaIndex]
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3449))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .background(Color.Red.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("ACTIVE THREAT LEVEL", color = Color(0xFFFFB4AB), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(activeBoss.enemyName, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        
                        val isLocked = viewModel.currentArenaIndex > viewModel.maxUnlockedIndex
                        Box(
                            modifier = Modifier
                                .background(if (isLocked) Color(0xFF2E1212) else Color(0xFF0F2615), RoundedCornerShape(12.dp))
                                .border(0.5.dp, if (isLocked) Color.Red else Color(0xFF00E676), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(if (isLocked) "LOCKED" else "UNLOCKED", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("ENV: ${activeBoss.backgroundName}", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Real Boss Stage Illustration and threat indicator
                    // Animated Scanning Line Overlay
                    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
                    val scanY by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(4000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scanLine"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.5.dp, activeBoss.accentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = activeBoss.imageRes),
                            contentDescription = activeBoss.enemyName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        // Dark overlay gradient for polished look and readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                    )
                                )
                        )
                        
                        // Active Scan line sweep canvas overlay
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val yOffset = size.height * scanY
                            drawLine(
                                color = activeBoss.accentColor,
                                start = Offset(0f, yOffset),
                                end = Offset(size.width, yOffset),
                                strokeWidth = 3.dp.toPx(),
                                alpha = 0.9f
                            )
                            // Draw ambient glow under the scan line
                            drawRect(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        activeBoss.accentColor.copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                ),
                                topLeft = Offset(0f, yOffset),
                                size = Size(size.width, 15.dp.toPx())
                            )
                        }

                        Text(
                            text = "THREAT SCANNER DETECTED",
                            color = activeBoss.accentColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                        )
                        
                        if (viewModel.currentArenaIndex > viewModel.maxUnlockedIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color.Red, modifier = Modifier.size(24.dp))
                                    Text("STATION UNREACHED", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.launchArcadeGame(scope) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6BFB9A)),
                        shape = RoundedCornerShape(8.dp),
                        enabled = viewModel.currentArenaIndex <= viewModel.maxUnlockedIndex,
                        modifier = Modifier.testTag("arcade_engage_btn").fillMaxWidth().height(40.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Arcade", tint = Color(0xFF003919), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ENGAGE BOSS BATTLE", color = Color(0xFF003919), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.restoreShieldHP() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2435)),
                            border = BorderStroke(1.dp, Color(0xFFFFB47E)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Heal", tint = Color(0xFFFFB47E), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Recover Shield (150 GP)", color = Color.White, fontSize = 9.sp)
                        }
                        Button(
                            onClick = {
                                val nextIndex = (viewModel.currentArenaIndex + 1) % viewModel.arenas.size
                                viewModel.currentArenaIndex = nextIndex
                                viewModel.triggerAlert("Cyckled active threat viewport coordinates to Level ${nextIndex + 1}!")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2435)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Cycle Area", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // DEV Levels system bypass
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("DEV SYSTEM BYPASS", color = Color(0xFF6BFB9A), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Levels bypass matrix", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            viewModel.trainerLevel += 1
                            viewModel.triggerAlert("By-pass engaged! Set Trainer Level to ${viewModel.trainerLevel}!")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6BFB9A)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("LEVEL UP PIN", color = Color(0xFF003919), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// --- QUESTS CHECKLIST SCREEN TAB ---
@Composable
fun QuestsTabScreen(viewModel: HabitRpgViewModel, scope: CoroutineScope) {
    var mindfulnessText by remember { mutableStateOf("") }
    var currentAttachedFilename by remember { mutableStateOf<String?>(null) }
    var currentAttachedUri by remember { mutableStateOf<Uri?>(null) }
    var isForgingQuest by remember { mutableStateOf(false) }

    // Quest creator form fields
    var questFormTitle by remember { mutableStateOf("") }
    var questFormDesc by remember { mutableStateOf("") }
    var questFormXp by remember { mutableStateOf("150") }
    var questFormGp by remember { mutableStateOf("250") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            currentAttachedUri = it
            currentAttachedFilename = "skeletal_frame_optics.png"
            viewModel.triggerAlert("Success: Loaded physical image parameters to alignment slot!")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Double verification systems
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3449))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "AI DOUBLE VERIFICATION MATRIX",
                        color = Color(0xFF6BFB9A),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text("Artificial validation algorithms", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Validate physical posture and cognitive journal statements reactively!", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)

                    Spacer(modifier = Modifier.height(12.dp))

                    // CV Flow Posture Cam
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2435)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("AI Flow 1: Sitting Posture Alignment", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("Compute spinal curvature metrics from camera file.", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { filePicker.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F1B3E)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text(if (currentAttachedFilename == null) "Load File" else "Change Setup", color = Color.White, fontSize = 10.sp)
                                }
                                Button(
                                    onClick = {
                                        viewModel.triggerPostureCameraScan(scope, currentAttachedFilename != null)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (currentAttachedFilename != null) Color(0xFF00E676) else Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text("Analyze", color = Color.White, fontSize = 10.sp)
                                }
                            }
                            
                            currentAttachedFilename?.let {
                                Text("Attached Setup File: $it", color = Color(0xFF6BFB9A), fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                            }

                            currentAttachedUri?.let { uri ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1B3E).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF6BFB9A).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        coil.compose.AsyncImage(
                                            model = uri,
                                            contentDescription = "Attached posture skeletal preview",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp))
                                                .testTag("posture_attached_image"),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // NLP Flow Mood Diary
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2435)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("AI Flow 2: Sentiment NLP Audit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("Write focus state log to verify attributes logs.", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = mindfulnessText,
                                onValueChange = { mindfulnessText = it },
                                placeholder = { Text("Log active focus, anxiety index or workout records...", fontSize = 10.sp, color = Color.Gray) },
                                singleLine = false,
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF6BFB9A),
                                    unfocusedBorderColor = Color(0xFF222A3D)
                                ),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = { 
                                    viewModel.triggerSentimentAudit(scope, mindfulnessText)
                                    mindfulnessText = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6BFB9A)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            ) {
                                Text("Process Sentiment Log", color = Color(0xFF003919), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Active Checklist
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("HABIT MATRIX CHECKLIST", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Box(
                    modifier = Modifier
                        .background(Color(0xFF0F1B3E), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Multiplier Boost: ${viewModel.xpMultiplier}x", color = Color(0xFF6BFB9A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Iterate active quests list
        items(viewModel.quests) { quest ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                border = BorderStroke(
                    1.dp, 
                    if (quest.completed) Color(0xFF6BFB9A).copy(alpha = 0.5f) else Color(0xFF222A3D)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    quest.title, 
                                    color = Color.White, 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (quest.completed) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(quest.description, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("+${quest.xp} XP", color = Color(0xFFDDB7FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("+${quest.gp} GP", color = Color(0xFFFFB47E), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    if (!quest.completed) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.completeQuest(quest.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Text("Complete Log", color = Color(0xFF003310), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                            Button(
                                onClick = { viewModel.triggerMissedQuestPenalty(scope, quest) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1212)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Text("Missed Deadlines", color = Color(0xFFFF8A80), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        // Action controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.addRandomQuest() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2435)),
                    border = BorderStroke(1.dp, Color(0xFF222A3D)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("Load Random Task", color = Color.White, fontSize = 10.sp)
                }

                Button(
                    onClick = { isForgingQuest = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F1B3E)),
                    border = BorderStroke(1.dp, Color(0xFF6BFB9A).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(44.dp).testTag("custom_quest_forge_trigger")
                ) {
                    Text("Forge Custom Task", color = Color(0xFF6BFB9A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Modal popup forge quest dialog
    if (isForgingQuest) {
        Dialog(onDismissRequest = { isForgingQuest = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                border = BorderStroke(1.5.dp, Color(0xFF6BFB9A)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "CUSTOM QUEST FORGE",
                        color = Color(0xFF6BFB9A),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("Configure your habit parameters", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    OutlinedTextField(
                        value = questFormTitle,
                        onValueChange = { questFormTitle = it },
                        label = { Text("Task Title", color = Color.Gray, fontSize = 10.sp) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6BFB9A)),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = questFormDesc,
                        onValueChange = { questFormDesc = it },
                        label = { Text("Description Condition", color = Color.Gray, fontSize = 10.sp) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6BFB9A)),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = questFormXp,
                            onValueChange = { questFormXp = it },
                            label = { Text("XP Energy", color = Color.Gray, fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6BFB9A)),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = questFormGp,
                            onValueChange = { questFormGp = it },
                            label = { Text("GP Coins", color = Color.Gray, fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6BFB9A)),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { isForgingQuest = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222A3D)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("Cancel", color = Color.LightGray)
                        }
                        Button(
                            onClick = {
                                if (questFormTitle.isNotBlank()) {
                                    val nxp = questFormXp.toIntOrNull() ?: 150
                                    val ngp = questFormGp.toIntOrNull() ?: 250
                                    viewModel.addNewQuest(questFormTitle, questFormDesc, nxp, ngp)
                                    // Reset fields
                                    questFormTitle = ""
                                    questFormDesc = ""
                                    isForgingQuest = false
                                } else {
                                    viewModel.triggerAlert("Quests require a valid, non-blank title setup!")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6BFB9A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(40.dp).testTag("custom_quest_submit_btn")
                        ) {
                            Text("Forge Task", color = Color(0xFF003919), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// --- FITNESS LAB TRAINING ROOM ---
@Composable
fun FitnessTabScreen(viewModel: HabitRpgViewModel, scope: CoroutineScope) {
    val activeBoss = viewModel.arenas[viewModel.currentArenaIndex]
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3449))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "PHYSIOLOGICAL SYNC CHAMBER",
                        color = Color(0xFFDDB7FF),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    Text(
                        if (viewModel.fitnessActive) "🧬 BIOMETRIC HARVEST ACTIVE" else "💤 STANDBY PROTOCOLS",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Simulated training screen frame (Point 5 active boss visualization)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, activeBoss.accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .background(activeBoss.bgTheme),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = activeBoss.imageRes),
                            contentDescription = activeBoss.enemyName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        // Dark overlay gradient for polished look and readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                    )
                                )
                        )
                        
                        // Futuristic Target HUD lines corner decorations
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val pad = 12.dp.toPx()
                            val len = 8.dp.toPx()
                            val color = activeBoss.accentColor.copy(alpha = 0.7f)
                            // Top-left corner tick
                            drawLine(color, Offset(pad, pad), Offset(pad + len, pad), strokeWidth = 1.5.dp.toPx())
                            drawLine(color, Offset(pad, pad), Offset(pad, pad + len), strokeWidth = 1.5.dp.toPx())
                            // Top-right corner tick
                            drawLine(color, Offset(size.width - pad, pad), Offset(size.width - pad - len, pad), strokeWidth = 1.5.dp.toPx())
                            drawLine(color, Offset(size.width - pad, pad), Offset(size.width - pad, pad + len), strokeWidth = 1.5.dp.toPx())
                            // Bottom-left corner tick
                            drawLine(color, Offset(pad, size.height - pad), Offset(pad + len, size.height - pad), strokeWidth = 1.5.dp.toPx())
                            drawLine(color, Offset(pad, size.height - pad), Offset(pad, size.height - pad - len), strokeWidth = 1.5.dp.toPx())
                            // Bottom-right corner tick
                            drawLine(color, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - len, size.height - pad), strokeWidth = 1.5.dp.toPx())
                            drawLine(color, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - len), strokeWidth = 1.5.dp.toPx())
                        }

                        // Dynamic graphic info
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                            Text(
                                "TARGET SYNCHRONIZED VECTOR",
                                color = activeBoss.accentColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                activeBoss.enemyName,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                activeBoss.backgroundName,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("CHAMBER SYNCED", color = Color(0xFF6BFB9A), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sync stats
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF060E20), RoundedCornerShape(10.dp))
                                .border(0.5.dp, Color(0xFF222A3D), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text("PULSE MONITOR", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            val pulseText = viewModel.simulatedPulse?.let { "$it BPM" } ?: "--"
                            Text(pulseText, color = Color(0xFF6BFB9A), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF060E20), RoundedCornerShape(10.dp))
                                .border(0.5.dp, Color(0xFF222A3D), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text("WATT OUTPUT", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            val wattText = viewModel.simulatedWatts?.let { "${it}W" } ?: "--"
                            Text(wattText, color = Color(0xFFDDB7FF), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF060E20), RoundedCornerShape(10.dp))
                                .border(0.5.dp, Color(0xFF222A3D), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text("ELAPSED TIME", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            val mins = viewModel.fitnessSeconds / 60
                            val secs = viewModel.fitnessSeconds % 60
                            val elapsed = String.format("%02d:%02d", mins, secs)
                            Text(elapsed, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF060E20), RoundedCornerShape(10.dp))
                                .border(0.5.dp, Color(0xFF222A3D), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text("STRENGTH RATING", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text("+12 PTS", color = Color(0xFFFFD9C1), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.toggleFitnessWork(scope) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.fitnessActive) Color(0xFFFFB74D) else Color(0xFF6BFB9A)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.2f).height(44.dp)
                        ) {
                            Icon(
                                imageVector = if (viewModel.fitnessActive) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                                contentDescription = "ActiveTimer",
                                tint = Color(0xFF003919)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (viewModel.fitnessActive) "Pause" else "Start workout", 
                                color = Color(0xFF003919), 
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = { viewModel.finishWorkout() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222A3D)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.8f).height(44.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Stop", tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Settle Session", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- HISTORICAL ANALYTICS AND ACHIEVEMENT PORTRAITS ---
@Composable
fun AnalyticsTabScreen(viewModel: HabitRpgViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Logging Bars
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3449))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "ACTIVITY DISTRIBUTION SUMMARY",
                        color = Color(0xFF6BFB9A),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text("Interactive Analytics Hub", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Historical category progress mapping.", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)

                    Spacer(modifier = Modifier.height(14.dp))

                    viewModel.categoryLogs.forEach { (cat, count) ->
                        Column(modifier = Modifier.padding(bottom = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cat.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("$count Logs", color = Color(0xFF6BFB9A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            val percent = (count.toFloat() / 25f).coerceAtMost(1f)
                            LinearProgressIndicator(
                                progress = { percent },
                                color = Color(0xFF6BFB9A),
                                trackColor = Color(0xFF060E20),
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF060E20), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text("ACTIVE DAILY STREAK", color = Color.Gray, fontSize = 8.sp)
                            Text("${viewModel.streakCount} Days", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF060E20), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text("STREAK MULTIPLIER BOOST", color = Color.Gray, fontSize = 8.sp)
                            Text("${viewModel.xpMultiplier}x Coins", color = Color(0xFF6BFB9A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Proof of Mastery Badge locks lists (Requirement Point 5)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3449))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = "", tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Proof of Mastery Badges", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text("Permanent locks list. Unlike weapons, these can never be traded!", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)

                    Spacer(modifier = Modifier.height(10.dp))

                    val badges = listOf(
                        Triple("7-Day Streak Master", "Log consecutive habits checklist entries for 7 days.", viewModel.streakCount >= 7),
                        Triple("Active Trainer (10m Check)", "Focus with Pomodoro timers for at least 10 minutes total.", viewModel.totalFocusSeconds >= 600),
                        Triple("Godzilla Tamer Stage", "Dethrone Lazer-Eyed Godzilla boss in 2D active play.", viewModel.maxUnlockedIndex >= 1),
                        Triple("Silverback Gorilla Conqueror", "Conquer Level 2 Rock-Hard Gorilla threat.", viewModel.maxUnlockedIndex >= 2),
                        Triple("Dragon Flame Slayer", "Extinguish flame dragon attributes.", viewModel.maxUnlockedIndex >= 3),
                        Triple("Grizzly Colossus Champion", "Dethrone the Grizzly master boss armor.", viewModel.maxUnlockedIndex >= 4)
                    )

                    badges.forEach { (title, desc, isUnlocked) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .background(
                                    if (isUnlocked) Color(0xFF0F1B3E).copy(alpha = 0.7f) else Color(0xFF060E20).copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    0.5.dp, 
                                    if (isUnlocked) Color(0xFF6BFB9A).copy(alpha = 0.4f) else Color.Transparent, 
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    title, 
                                    color = if (isUnlocked) Color.White else Color.Gray, 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 11.sp
                                )
                                Text(desc, color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp, lineHeight = 10.sp)
                                
                                // Specific active focus checklist progress bounds
                                if (title.startsWith("Active Trainer") && !isUnlocked) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val percent = (viewModel.totalFocusSeconds.toFloat() / 600f).coerceAtMost(1f)
                                    Column {
                                        LinearProgressIndicator(
                                            progress = { percent },
                                            color = Color(0xFFDDB7FF),
                                            trackColor = Color.DarkGray,
                                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.dp))
                                        )
                                        Text("Progress: ${viewModel.totalFocusSeconds}/600s (${Math.round(percent*100)}%)", color = Color.LightGray, fontSize = 7.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isUnlocked) Color(0xFF0F2615) else Color(0xFF2E1212),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    if (isUnlocked) "UNLOCKED" else "LOCKED", 
                                    color = if (isUnlocked) Color(0xFF00E676) else Color.Red, 
                                    fontSize = 8.sp, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SHOP LOOT MARKET ---
@Composable
fun ShopTabScreen(viewModel: HabitRpgViewModel) {
    var shopFilter by remember { mutableStateOf("CONSUMABLE") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3449))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "REWARD LOOT SHOP",
                        color = Color(0xFF6BFB9A),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text("Armor & Supplies Market", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Unlock specialty resources utilizing active Trainer copper tokens!", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons selectors category
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("CONSUMABLE", "GEAR", "SPECIAL").forEach { cat ->
                            Button(
                                onClick = { shopFilter = cat },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (shopFilter == cat) Color(0xFF6BFB9A) else Color(0xFF222A3D)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text(
                                    cat, 
                                    color = if (shopFilter == cat) Color(0xFF003919) else Color.White, 
                                    fontSize = 8.sp, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Inventory items checklist
                    val filtered = viewModel.shopItems.filter { it.type == shopFilter }
                    filtered.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .background(Color(0xFF060E20), RoundedCornerShape(10.dp))
                                .border(0.5.dp, Color(0xFF222A3D), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF131F33)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Custom visual item indicator using Canvas drawing
                                    Canvas(modifier = Modifier.size(24.dp)) {
                                        drawCircle(color = item.color, radius = 8.dp.toPx())
                                    }
                                }
                                Column {
                                    Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(item.desc, color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp, lineHeight = 10.sp)
                                    Text("Owned: ${item.quantity}", color = Color(0xFF6BFB9A), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                            
                            Button(
                                onClick = { viewModel.buyShopItem(item.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F1B3E)),
                                border = BorderStroke(1.dp, Color(0xFF6BFB9A).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(38.dp).width(80.dp)
                            ) {
                                Text("${item.cost} GP", color = Color(0xFF6BFB9A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
