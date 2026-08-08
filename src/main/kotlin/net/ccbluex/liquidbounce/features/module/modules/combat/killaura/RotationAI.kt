/*
 * Air Client
 * Independent AI Rotation Module - Decoupled from KillAura
 * Uses real player behavior data from PlayerBehaviorRecorder
 * Provides human-like rotation wobble for KillAura, Scaffold, and other modules
 * 新增：多目标交替轮换逻辑，多目标时击打一次切换一个目标，单目标锁定
 */
package net.ccbluex.liquidbounce.features.module.modules.client

import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.BehaviorSampler
import net.ccbluex.liquidbounce.features.module.modules.combat.BehaviorStatistics
import net.ccbluex.liquidbounce.features.module.modules.combat.PlayerBehaviorRecorder
import net.ccbluex.liquidbounce.utils.client.ClientUtils
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import org.lwjgl.input.Keyboard
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 独立 AI 转头模块
 * 从 KillAura 解耦，供 KillAura、Scaffold 等模块共同使用
 * 
 * 特性：
 * - 使用游戏内真人数据（PlayerBehaviorRecorder）
 * - 数据保存到独立 RotationAI 文件夹
 * - 启动客户端自动加载
 * - 使用真人原始速度（不弱化）
 * - 不影响瞄准精度（只添加微小抖动）
 * 新增多目标轮换：
 * - 目标≥2时，每完成一次攻击切换下一个目标循环击打
 * - 仅单个目标时持续锁定不切换
 */
object RotationAI : Module("RotationAI", Category.CLIENT, Keyboard.KEY_NONE) {

    // ========== 存储路径（独立文件夹） ==========
    private val gameMc = Minecraft.getMinecraft()
    private val ROTATION_AI_DIR: File by lazy {
        val dir = File(gameMc.mcDataDir, "RotationAI")
        if (!dir.exists()) dir.mkdirs()
        dir
    }
    private val SAMPLES_FILE = File(ROTATION_AI_DIR, "rotation_samples.dat.gz")
    private val STATS_FILE = File(ROTATION_AI_DIR, "rotation_stats.dat")
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
    
    // ========== 核心设置 ==========
    private val recordSamples by boolean("RecordSamples", true)
    private val applyBehavior by boolean("ApplyBehavior", true)
    private val autoSave by boolean("AutoSave", true) { recordSamples }
    private val autoSaveInterval by int("AutoSaveInterval", 5, 1..30) { autoSave } // 分钟

    // ========== 多目标轮换新增参数 ==========
    private val multiTargetSwitch by boolean("MultiTargetSwitch", true) { applyBehavior }
    private val switchTickDelay by int("SwitchTickDelay", 1, 1..20) { multiTargetSwitch } // 攻击后延迟多少tick切换

    // ========== 抖动设置（真人速度，不弱化） ==========
    private val wobbleScale by float("WobbleScale", 1.0f, 0.1f..5.0f) { applyBehavior } // 直接使用真人数据 × scale
    private val minSamples by int("MinSamples", 50, 10..500) { applyBehavior }
    
    // ========== 统计信息 ==========
    private val showStats by boolean("ShowStats", false)
    
    // ========== 内部状态 ==========
    private var sampler: BehaviorSampler? = null
    private var currentStats: BehaviorStatistics? = null
    private val statsUpdateTimer = MSTimer()
    private val autoSaveTimer = MSTimer()
    
    // Wobble state（平滑过渡）
    private var lastYawOffset = 0f
    private var lastPitchOffset = 0f
    private var lastWobbleUpdate = 0L

    // ========== 多目标轮换状态变量 ==========
    private var targetList: MutableList<Entity> = mutableListOf()
    private var currentTargetIndex = 0
    private var attackTickCounter = 0
    private var switchCoolDown = 0

    // 标记是否已初始化（启动时自动加载）
    private var initialized = false
    
    override fun onEnable() {
        super.onEnable()
        initializeOnStartup()
        if (recordSamples) {
            PlayerBehaviorRecorder.startRecording()
        }
    }
    
    override fun onDisable() {
        super.onDisable()
        PlayerBehaviorRecorder.stopRecording()
        
        // 保存数据到独立文件夹
        if (recordSamples) {
            saveRotationData()
        }
        
        sampler = null
        currentStats = null
        targetList.clear()
        currentTargetIndex = 0
        attackTickCounter = 0
        switchCoolDown = 0
    }
    
    /**
     * 启动时初始化 - 自动加载本地数据
     */
    private fun initializeOnStartup() {
        if (initialized) return
        initialized = true
        
        // 自动加载本地保存的数据
        loadRotationData()
        
        ClientUtils.LOGGER.info("[RotationAI] Initialized with ${PlayerBehaviorRecorder.getSampleCount()} samples")
    }
    
    /**
     * 加载 RotationAI 独立文件夹的数据
     */
    private fun loadRotationData() {
        // 加载样本
        if (SAMPLES_FILE.exists()) {
            try {
                GZIPInputStream(FileInputStream(SAMPLES_FILE)).use { gzipIn ->
                    ObjectInputStream(gzipIn).use { objIn ->
                        val samples = objIn.readObject() as List<net.ccbluex.liquidbounce.features.module.modules.combat.PlayerBehaviorSample>
                        for (sample in samples) {
                            PlayerBehaviorRecorder.addSample(sample)
                        }
                        ClientUtils.LOGGER.info("[RotationAI] Loaded ${samples.size} samples from ${SAMPLES_FILE.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                ClientUtils.LOGGER.error("[RotationAI] Failed to load samples: ${e.message}")
            }
        }
        
        // 更新统计
        updateStats()
    }
    
    /**
     * 保存数据到 RotationAI 独立文件夹
     */
    private fun saveRotationData() {
        val samples = PlayerBehaviorRecorder.getSamples()
        if (samples.isEmpty()) return
        
        try {
            // 保存样本（压缩格式）
            GZIPOutputStream(FileOutputStream(SAMPLES_FILE)).use { gzipOut ->
                ObjectOutputStream(gzipOut).use { objOut ->
                    objOut.writeObject(samples)
                    objOut.writeInt(samples.size)
                }
            }
            
            // 保存统计
            val stats = PlayerBehaviorRecorder.getStatistics()
            ObjectOutputStream(FileOutputStream(STATS_FILE)).use { objOut ->
                objOut.writeObject(stats)
            }
            
            ClientUtils.LOGGER.info("[RotationAI] Saved ${samples.size} samples to ${ROTATION_AI_DIR.absolutePath}")
            
            // 创建备份
            createBackup(samples, stats)
        } catch (e: Exception) {
            ClientUtils.LOGGER.error("[RotationAI] Failed to save data: ${e.message}")
        }
    }
    
    /**
     * 创建备份
     */
    private fun createBackup(samples: List<net.ccbluex.liquidbounce.features.module.modules.combat.PlayerBehaviorSample>, stats: BehaviorStatistics) {
        try {
            val backupDir = File(ROTATION_AI_DIR, "backups")
            backupDir.mkdirs()
            
            val timestamp = dateFormat.format(Date())
            val backupFile = File(backupDir, "backup_$timestamp.dat.gz")
            
            GZIPOutputStream(FileOutputStream(backupFile)).use { gzipOut ->
                ObjectOutputStream(gzipOut).use { objOut ->
                    objOut.writeObject(samples)
                    objOut.writeObject(stats)
                    objOut.writeInt(samples.size)
                }
            }
            
            // 清理旧备份（保留最近10个）
            cleanOldBackups(backupDir)
        } catch (e: Exception) {
            ClientUtils.LOGGER.error("[RotationAI] Failed to create backup: ${e.message}")
        }
    }
    
    /**
     * 清理旧备份
     */
    private fun cleanOldBackups(backupDir: File) {
        val backups = backupDir.listFiles { _, name -> name.startsWith("backup_") && name.endsWith(".dat.gz") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        
        if (backups.size > 10) {
            backups.drop(10).forEach { it.delete() }
        }
    }
    
    /**
     * 更新统计数据
     */
    private fun updateStats() {
        currentStats = PlayerBehaviorRecorder.getStatistics()
        if (currentStats?.attackSamples ?: 0 >= minSamples) {
            sampler = BehaviorSampler(currentStats!!)
        }
    }
    
    val onGameTick = handler<GameTickEvent> {
        // 冷却递减
        if (switchCoolDown > 0) switchCoolDown--

        // 定期更新统计
        if (statsUpdateTimer.hasTimePassed(5000)) {
            updateStats()
            statsUpdateTimer.reset()
        }
        
        // 自动保存
        if (autoSave && autoSaveTimer.hasTimePassed(autoSaveInterval * 60000L)) {
            val sampleCount = PlayerBehaviorRecorder.getSampleCount()
            if (sampleCount > 50) {
                saveRotationData()
            }
            autoSaveTimer.reset()
        }
    }

    // ===================== 对外新增接口给KillAura调用 =====================
    /**
     * 每tick传入当前所有有效目标列表
     */
    fun setTargetEntities(targets: List<Entity>) {
        targetList = targets.filter { it.isAlive }.toMutableList()
        // 目标清空重置索引
        if (targetList.isEmpty()) {
            currentTargetIndex = 0
            attackTickCounter = 0
        }
        // 当前索引超出列表长度则重置
        if (currentTargetIndex >= targetList.size) currentTargetIndex = 0
    }

    /**
     * KillAura成功攻击一次后调用，触发目标切换判断
     */
    fun onAttackHit() {
        if (!multiTargetSwitch || targetList.size < 2 || switchCoolDown > 0) return
        attackTickCounter++
        switchCoolDown = switchTickDelay
        // 切换下一个目标循环
        currentTargetIndex = (currentTargetIndex + 1) % targetList.size
    }

    /**
     * 获取当前需要瞄准的目标
     */
    fun getCurrentTarget(): Entity? {
        if (targetList.isEmpty()) return null
        return targetList[currentTargetIndex]
    }
    // ======================================================================
    
    /**
     * 应用 AI 转头处理
     * 使用真人原始速度，不弱化
     * 
     * @param baseRotation 基础瞄准旋转
     * @return 添加真人抖动后的旋转（不改变瞄准方向）
     */
    fun applyRotation(baseRotation: Rotation): Rotation {
        if (!state || !applyBehavior) return baseRotation
        
        val yawOffset = sampleYawWobble()
        val pitchOffset = samplePitchWobble()
        
        // 平滑过渡（避免突变）
        val now = System.currentTimeMillis()
        val blendFactor = if (now - lastWobbleUpdate < 50) 0.3f else 1.0f
        lastWobbleUpdate = now
        
        val smoothedYawOffset = lerp(lastYawOffset, yawOffset, blendFactor)
        val smoothedPitchOffset = lerp(lastPitchOffset, pitchOffset, blendFactor)
        
        lastYawOffset = smoothedYawOffset
        lastPitchOffset = smoothedPitchOffset
        
        // 应用偏移，不改变瞄准方向
        val finalYaw = baseRotation.yaw + smoothedYawOffset
        val finalPitch = (baseRotation.pitch + smoothedPitchOffset).coerceIn(-90f, 90f)
        
        return Rotation(finalYaw, finalPitch)
    }
    
    /**
     * 采样 yaw 抖动（使用真人原始速度）
     * 直接使用真人数据 × wobbleScale，不弱化
     */
    private fun sampleYawWobble(): Float {
        val stats = currentStats
        
        // 如果有足够真人数据，直接使用真人原始速度
        if (stats != null && stats.attackSamples >= minSamples && sampler != null) {
            val humanYawChange = sampler!!.sampleYawChange()
            // 处理 yaw 跳变
            val normalizedYaw = if (humanYawChange > 180) humanYawChange - 360
                                else if (humanYawChange < -180) humanYawChange + 360
                                else humanYawChange
            
            // 直接使用真人速度 × wobbleScale，不弱化
            return normalizedYaw * wobbleScale
        }
        
        // 无数据时返回0（不干扰）
        return 0f
    }
    
    /**
     * 采样 pitch 抖动（使用真人原始速度）
     * 直接使用真人数据 × wobbleScale，不弱化
     */
    private fun samplePitchWobble(): Float {
        val stats = currentStats
        
        // 如果有足够真人数据，直接使用真人原始速度
        if (stats != null && stats.attackSamples >= minSamples && sampler != null) {
            val humanPitchChange = sampler!!.samplePitchChange()
            
            // 直接使用真人速度 × wobbleScale，不弱化
            return humanPitchChange * wobbleScale
        }
        
        // 无数据时返回0（不干扰）
        return 0f
    }
    
    /**
     * 简单线性插值
     */
    private fun lerp(current: Float, target: Float, speed: Float): Float {
        return current + (target - current) * speed
    }
    
    /**
     * 检查是否有足够的真人数据
     */
    fun hasEnoughData(): Boolean {
        return (currentStats?.attackSamples ?: 0) >= minSamples
    }
    
    /**
     * 获取统计信息
     */
    fun getStatisticsInfo(): String {
        val stats = currentStats
        if (stats == null || stats.attackSamples == 0) {
            return "No data (${PlayerBehaviorRecorder.getSampleCount()} samples)"
        }
        
        return buildString {
            append("Samples: ${stats.attackSamples}/${minSamples}")
            if (showStats) {
                append("\nYaw: mean=${String.format("%.2f", stats.yawChangeMean)}, std=${String.format("%.2f", stats.yawChangeStdDev)}")
                append("\nPitch: mean=${String.format("%.2f", stats.pitchChangeMean)}, std=${String.format("%.2f", stats.pitchChangeStdDev)}")
                append("\nCPS: ${String.format("%.1f", stats.cpsMean)} (${stats.cpsMin}-${stats.cpsMax})")
                append("\nStorage: ${ROTATION_AI_DIR.absolutePath}")
            }
            // 多目标信息
            if (multiTargetSwitch) {
                append("\nTargets: ${targetList.size} | CurIndex:$currentTargetIndex")
            }
        }
    }
    
    /**
     * 模块是否应该处理转头
     */
    fun shouldHandleRotation(): Boolean {
        return state && applyBehavior
    }
    
    /**
     * 获取采样器
     */
    fun getSampler(): BehaviorSampler? = sampler
    
    /**
     * 获取统计数据
     */
    fun getStats(): BehaviorStatistics? = currentStats
    
    /**
     * 获取存储路径
     */
    fun getStoragePath(): String = ROTATION_AI_DIR.absolutePath
    
    override val tag: String
        get() {
            val baseTag = if (hasEnoughData()) "Ready (${currentStats?.attackSamples ?: 0})" else "Collecting..."
            return if (multiTargetSwitch && targetList.size >=2) "$baseTag | MultiTarget" else baseTag
        }
}
