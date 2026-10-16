package com.arnix.codepad.studio.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.arnix.codepad.studio.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedRecordingInfo(
    val filePath: String,
    val fileUri: Uri,
    val fileName: String,
    val durationSeconds: Int,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class ScreenRecorderState(
    val isRecording: Boolean = false,
    val isPreparing: Boolean = false,
    val durationSeconds: Int = 0,
    val currentFilePath: String? = null,
    val lastSavedRecording: SavedRecordingInfo? = null,
    val isAudioEnabled: Boolean = false,
    val targetFps: Int = 60,
    val videoQualityLabel: String = "1080p FHD",
    val error: String? = null
)

object ScreenRecorderEngine {
    private const val TAG = "ScreenRecorderEngine"
    private val _state = MutableStateFlow(ScreenRecorderState())
    val state: StateFlow<ScreenRecorderState> = _state.asStateFlow()

    // Configurable Preferences
    var isAutoRecordOnShortsStart: Boolean = false
    var recordAudio: Boolean = false
    var prefer60Fps: Boolean = true
    var prefer1080p: Boolean = true

    fun setAutoRecordOnShorts(enabled: Boolean) {
        isAutoRecordOnShortsStart = enabled
    }

    fun setRecordAudioPref(enabled: Boolean) {
        recordAudio = enabled
        _state.update { it.copy(isAudioEnabled = enabled) }
    }

    fun setPrefer60FpsPref(enabled: Boolean) {
        prefer60Fps = enabled
        _state.update { it.copy(targetFps = if (enabled) 60 else 30) }
    }

    fun setPrefer1080pPref(enabled: Boolean) {
        prefer1080p = enabled
        _state.update { it.copy(videoQualityLabel = if (enabled) "1080p FHD" else "720p HD") }
    }

    fun clearLastSavedRecording() {
        _state.update { it.copy(lastSavedRecording = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    internal fun updateState(update: (ScreenRecorderState) -> ScreenRecorderState) {
        _state.update(update)
    }

    fun startServiceRecording(
        context: Context,
        resultCode: Int,
        data: Intent,
        width: Int,
        height: Int,
        densityDpi: Int
    ) {
        val intent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_START
            putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordingService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenRecordingService.EXTRA_WIDTH, width)
            putExtra(ScreenRecordingService.EXTRA_HEIGHT, height)
            putExtra(ScreenRecordingService.EXTRA_DENSITY, densityDpi)
            putExtra(ScreenRecordingService.EXTRA_RECORD_AUDIO, recordAudio)
            putExtra(ScreenRecordingService.EXTRA_FPS, if (prefer60Fps) 60 else 30)
            putExtra(ScreenRecordingService.EXTRA_BITRATE, if (prefer1080p) 10_000_000 else 6_000_000)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopServiceRecording(context: Context) {
        val intent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun playRecordedVideo(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Play CodePad Recording"))
        } catch (e: Exception) {
            Log.e(TAG, "Error playing video", e)
        }
    }

    fun shareRecordedVideo(context: Context, uri: Uri, projectName: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$projectName - CodePad Studio Recording")
                putExtra(Intent.EXTRA_TEXT, "Check out my coding creation recorded with CodePad Studio! #CodePad #WebDev #Shorts")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Recording"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing video", e)
        }
    }
}

class ScreenRecordingService : Service() {

    companion object {
        const val ACTION_START = "com.arnix.codepad.studio.ACTION_START_RECORDING"
        const val ACTION_STOP = "com.arnix.codepad.studio.ACTION_STOP_RECORDING"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_DENSITY = "extra_density"
        const val EXTRA_RECORD_AUDIO = "extra_record_audio"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_BITRATE = "extra_bitrate"

        private const val CHANNEL_ID = "codepad_screen_recording_channel"
        private const val NOTIFICATION_ID = 4099
        private const val TAG = "ScreenRecordingService"
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var recordedSeconds = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val width = intent.getIntExtra(EXTRA_WIDTH, 1080)
                val height = intent.getIntExtra(EXTRA_HEIGHT, 1920)
                val density = intent.getIntExtra(EXTRA_DENSITY, 420)
                val recordAudio = intent.getBooleanExtra(EXTRA_RECORD_AUDIO, false)
                val fps = intent.getIntExtra(EXTRA_FPS, 60)
                val bitrate = intent.getIntExtra(EXTRA_BITRATE, 10_000_000)

                if (resultCode != 0 && resultData != null) {
                    startRecording(resultCode, resultData, width, height, density, recordAudio, fps, bitrate)
                } else {
                    ScreenRecorderEngine.updateState { it.copy(error = "Recording permission was denied or cancelled.") }
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "In-App Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status during CodePad Studio video recording."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(seconds: Int): Notification {
        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIntent = Intent(this, MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mins = seconds / 60
        val secs = seconds % 60
        val timeStr = String.format(Locale.US, "%02d:%02d", mins, secs)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording CodePad Studio ($timeStr)")
            .setContentText("Capturing high-definition video of your coding session.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Recording", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startRecording(
        resultCode: Int,
        resultData: Intent,
        width: Int,
        height: Int,
        density: Int,
        recordAudio: Boolean,
        fps: Int,
        bitrate: Int
    ) {
        try {
            ScreenRecorderEngine.updateState { it.copy(isPreparing = true, error = null) }

            // Ensure dimensions are even numbers (H.264 requirement)
            val videoWidth = if (width % 2 == 0) width else width - 1
            val videoHeight = if (height % 2 == 0) height else height - 1

            // Prepare output file
            val moviesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            val subDir = File(moviesDir, "CodePadRecordings").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(subDir, "CodePad_Rec_$timeStamp.mp4")
            currentOutputFile = file

            // Configure MediaRecorder
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }
            mediaRecorder = recorder

            var audioConfigured = false
            if (recordAudio) {
                try {
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    audioConfigured = true
                } catch (e: Exception) {
                    Log.w(TAG, "Microphone audio not available, continuing without audio", e)
                }
            }

            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (audioConfigured) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(44100)
            }
            recorder.setVideoSize(videoWidth, videoHeight)
            recorder.setVideoFrameRate(fps)
            recorder.setVideoEncodingBitRate(bitrate)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()

            // Start Foreground Service first (Strict Android 14+ requirement before getMediaProjection)
            val notification = buildNotification(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Obtain MediaProjection
            val projection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                ScreenRecorderEngine.updateState { it.copy(isPreparing = false, error = "Failed to obtain MediaProjection.") }
                stopSelf()
                return
            }
            mediaProjection = projection

            // Create VirtualDisplay piping to recorder Surface
            virtualDisplay = projection.createVirtualDisplay(
                "CodePadScreenRecorder",
                videoWidth,
                videoHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            recorder.start()
            recordedSeconds = 0

            ScreenRecorderEngine.updateState {
                it.copy(
                    isRecording = true,
                    isPreparing = false,
                    durationSeconds = 0,
                    currentFilePath = file.absolutePath,
                    isAudioEnabled = audioConfigured,
                    targetFps = fps
                )
            }

            // Start duration timer and notification updater
            timerJob?.cancel()
            timerJob = serviceScope.launch {
                while (isActive) {
                    delay(1000L)
                    recordedSeconds++
                    ScreenRecorderEngine.updateState { it.copy(durationSeconds = recordedSeconds) }
                    val updatedNotification = buildNotification(recordedSeconds)
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, updatedNotification)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen recording", e)
            ScreenRecorderEngine.updateState {
                it.copy(
                    isRecording = false,
                    isPreparing = false,
                    error = "Failed to record: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
            cleanup()
            stopSelf()
        }
    }

    private fun stopRecording() {
        timerJob?.cancel()
        val duration = recordedSeconds
        val file = currentOutputFile

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        }

        cleanup()

        if (file != null && file.exists() && file.length() > 0) {
            // Index file into Android MediaStore so it appears in device Gallery
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4")
            ) { _, uri ->
                val contentUri = uri ?: try {
                    FileProvider.getUriForFile(
                        applicationContext,
                        "${applicationContext.packageName}.fileprovider",
                        file
                    )
                } catch (_: Exception) {
                    Uri.fromFile(file)
                }

                val savedInfo = SavedRecordingInfo(
                    filePath = file.absolutePath,
                    fileUri = contentUri,
                    fileName = file.name,
                    durationSeconds = duration,
                    fileSizeBytes = file.length(),
                    width = 1080,
                    height = 1920
                )
                ScreenRecorderEngine.updateState {
                    it.copy(
                        isRecording = false,
                        isPreparing = false,
                        durationSeconds = 0,
                        currentFilePath = null,
                        lastSavedRecording = savedInfo
                    )
                }
            }
        } else {
            ScreenRecorderEngine.updateState {
                it.copy(
                    isRecording = false,
                    isPreparing = false,
                    durationSeconds = 0,
                    currentFilePath = null
                )
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (_: Exception) {}

        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (_: Exception) {}

        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}
