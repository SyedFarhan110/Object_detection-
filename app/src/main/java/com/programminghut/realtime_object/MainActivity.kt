package com.programminghut.real_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.cardview.widget.CardView
import android.view.View
import com.programminghut.real_object.ml.YoloxNano
import com.programminghut.real_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import kotlin.math.exp

class MainActivity : AppCompatActivity() {

    lateinit var labels:List<String>
    var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap:Bitmap
    lateinit var overlayView: SurfaceView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    private var yoloxModel: YoloxNano? = null
    private var ssdModel: SsdMobilenetV11Metadata1? = null
    private var gpuDelegate: GpuDelegate? = null
    private var loadingDialog: AlertDialog? = null
    lateinit var switchModelButton: Button
    
    // UI Elements for object counting
    lateinit var objectCountText: TextView
    lateinit var totalCountText: TextView
    lateinit var modelNameText: TextView
    lateinit var fpsText: TextView
    lateinit var statsCard: CardView
    lateinit var toggleStatsButton: Button
    private var isStatsVisible = true
    
    // Object counting dictionary - maintains persistent keys
    private val objectCountMap = mutableMapOf<String, Int>()
    // Set to track all objects ever detected (for persistent display)
    private val detectedObjectsHistory = mutableSetOf<String>()
    
    // FPS calculation
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    private var currentFps = 0.0
    private var lastInferenceTime = 0L
    
    // Model state
    private var isUsingYolox = true
    
    // Performance optimization variables
    private var isProcessing = false
    private var lastProcessTime = 0L
    private val MIN_PROCESS_INTERVAL = 50L  // Reduced to 50ms (~20 FPS for inference) for better sync
    private var pendingProcessing = false
    
    // Model parameters
    private var INPUT_SIZE = 416  // YOLOx Nano: 416, SSD MobileNet: 300
    private val CONFIDENCE_THRESHOLD = 0.5f
    private val NMS_THRESHOLD = 0.45f  // Non-Maximum Suppression threshold
    
    // Detection data class
    data class Detection(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val confidence: Float,
        val classId: Int,
        val label: String
    )
    
    // Parse YOLOx output with NMS
    private fun parseYoloxOutput(output: FloatArray): List<Detection> {
        val allDetections = mutableListOf<Detection>()
        
        // YOLOx Nano output: [1, 3549, 85]
        // 3549 = (52*52 + 26*26 + 13*13) predictions
        // 85 = [x_center, y_center, width, height, objectness, 80 class scores]
        val numPredictions = 3549
        val numValues = 85
        
        var highObjectnessCount = 0
        var maxObjectness = 0f
        var maxConfidence = 0f
        
        for (i in 0 until numPredictions) {
            val offset = i * numValues
            
            val xCenter = output[offset]
            val yCenter = output[offset + 1]
            val width = output[offset + 2]
            val height = output[offset + 3]
            val objectness = output[offset + 4]
            
            if (objectness > maxObjectness) maxObjectness = objectness
            
            // Try lower threshold for debugging
            if (objectness > 0.1f) highObjectnessCount++
            
            // Skip if objectness is too low
            if (objectness < CONFIDENCE_THRESHOLD) continue
            
            // Find best class
            var maxClassScore = 0f
            var maxClassId = 0
            for (c in 0 until 80) {
                val classScore = output[offset + 5 + c]
                if (classScore > maxClassScore) {
                    maxClassScore = classScore
                    maxClassId = c
                }
            }
            
            // Final confidence = objectness * class score
            val confidence = objectness * maxClassScore
            if (confidence > maxConfidence) maxConfidence = confidence
            if (confidence < CONFIDENCE_THRESHOLD) continue
            
            // Convert to x1, y1, x2, y2
            val x1 = xCenter - width / 2f
            val y1 = yCenter - height / 2f
            val x2 = xCenter + width / 2f
            val y2 = yCenter + height / 2f
            
            val label = if (maxClassId < labels.size) labels[maxClassId] else "Unknown"
            
            allDetections.add(Detection(x1, y1, x2, y2, confidence, maxClassId, label))
        }
        
        Log.d("YoloxDebug", "⭐ Max objectness: $maxObjectness, Max confidence: $maxConfidence")
        Log.d("YoloxDebug", "⭐ Predictions with objectness > 0.1: $highObjectnessCount")
        Log.d("YoloxDebug", "Detections before NMS (threshold=$CONFIDENCE_THRESHOLD): ${allDetections.size}")
        
        // Apply Non-Maximum Suppression
        val finalDetections = applyNMS(allDetections, NMS_THRESHOLD)
        
        Log.d("YoloxDebug", "Detections after NMS: ${finalDetections.size}")
        
        if (finalDetections.isNotEmpty()) {
            finalDetections.take(3).forEach {
                Log.d("YoloxDebug", "Detection: ${it.label} ${String.format("%.2f", it.confidence)} at (${it.x1}, ${it.y1}, ${it.x2}, ${it.y2})")
            }
        }
        
        return finalDetections
    }
    
    // Non-Maximum Suppression
    private fun applyNMS(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        
        // Sort by confidence (descending)
        val sortedDetections = detections.sortedByDescending { it.confidence }
        val keep = mutableListOf<Detection>()
        val suppressed = BooleanArray(sortedDetections.size) { false }
        
        for (i in sortedDetections.indices) {
            if (suppressed[i]) continue
            
            keep.add(sortedDetections[i])
            
            // Suppress overlapping boxes of same class
            for (j in (i + 1) until sortedDetections.size) {
                if (suppressed[j]) continue
                
                // Only compare same class detections
                if (sortedDetections[i].classId == sortedDetections[j].classId) {
                    val iou = calculateIoU(sortedDetections[i], sortedDetections[j])
                    if (iou > iouThreshold) {
                        suppressed[j] = true
                    }
                }
            }
        }
        
        return keep
    }
    
    // Calculate Intersection over Union
    private fun calculateIoU(det1: Detection, det2: Detection): Float {
        val x1 = maxOf(det1.x1, det2.x1)
        val y1 = maxOf(det1.y1, det2.y1)
        val x2 = minOf(det1.x2, det2.x2)
        val y2 = minOf(det1.y2, det2.y2)
        
        val intersectionArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        
        val area1 = (det1.x2 - det1.x1) * (det1.y2 - det1.y1)
        val area2 = (det2.x2 - det2.x1) * (det2.y2 - det2.y1)
        
        val unionArea = area1 + area2 - intersectionArea
        
        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permission()

        // Show loading dialog
        showLoadingDialog()

        // Load both models
        loadModels()
        
        // Load labels for current model
        loadLabelsForCurrentModel()
        
        // Configure paint for efficient drawing
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.isAntiAlias = false  // Disable antialiasing for performance
        
        // SINGLE THREAD MODE - Everything runs on main/UI thread
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        
        Log.d("ThreadMode", "⚠️ RUNNING IN SINGLE THREAD MODE - Camera may stutter!")

        overlayView = findViewById(R.id.overlayView)
        overlayView.setZOrderOnTop(true)
        overlayView.holder.setFormat(PixelFormat.TRANSPARENT)

        // Initialize UI elements
        objectCountText = findViewById(R.id.objectCountText)
        totalCountText = findViewById(R.id.totalCountText)
        modelNameText = findViewById(R.id.modelNameText)
        fpsText = findViewById(R.id.fpsText)
        statsCard = findViewById(R.id.statsCard)
        toggleStatsButton = findViewById(R.id.toggleStatsButton)

        switchModelButton = findViewById(R.id.switchModelButton)
        switchModelButton.setOnClickListener {
            switchModel()
        }

        toggleStatsButton.setOnClickListener {
            toggleStatsVisibility()
        }

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera()
            }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                val currentTime = System.currentTimeMillis()
                
                // Time-based throttling - only trigger processing if enough time has passed
                if (currentTime - lastProcessTime < MIN_PROCESS_INTERVAL) {
                    return
                }
                
                // If already processing, skip this frame
                if (isProcessing) {
                    return
                }
                
                lastProcessTime = currentTime
                isProcessing = true
                
                // SINGLE THREAD MODE - Process directly on UI thread (will cause stuttering)
                try {
                    // Capture the latest frame at the exact moment of processing
                    val frameBitmap = textureView.bitmap ?: run {
                        isProcessing = false
                        return
                    }
                    processFrameSynchronous(frameBitmap)
                } catch (e: Exception) {
                    Log.e("FrameCapture", "Error capturing frame: ${e.message}")
                    isProcessing = false
                }
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

    }

    private fun processFrameSynchronous(currentBitmap: Bitmap) {
        try {
            val startTime = System.currentTimeMillis()
            val bitmapTime = 0L  // Bitmap already captured, no time spent here
            
            Log.d("SingleThread", "⚠️ Processing on thread: ${Thread.currentThread().name}")
            
            // Preprocess image
            var image = TensorImage(DataType.FLOAT32)
            image.load(currentBitmap)
            image = imageProcessor.process(image)
            val preprocessTime = System.currentTimeMillis() - startTime - bitmapTime

            // Run inference (THIS WILL BLOCK THE UI THREAD!)
            val inferenceStart = System.currentTimeMillis()
            val detections = if (isUsingYolox) {
                // YOLOx inference
                val inputBuffer = image.tensorBuffer
                val outputs = yoloxModel!!.process(inputBuffer)
                val outputArray = outputs.outputFeature0AsTensorBuffer.floatArray
                parseYoloxOutput(outputArray)
            } else {
                // SSD MobileNet inference
                val outputs = ssdModel!!.process(image)
                parseSsdOutput(outputs)
            }
            val inferenceTime = System.currentTimeMillis() - inferenceStart
            lastInferenceTime = inferenceTime
            
            // Update object count dictionary
            updateObjectCounts(detections)
            
            // Calculate FPS
            frameCount++
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastFpsTime >= 1000) {
                currentFps = frameCount * 1000.0 / (currentTime - lastFpsTime)
                frameCount = 0
                lastFpsTime = currentTime
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            
            Log.d("PerformanceDetailed", "Bitmap: ${bitmapTime}ms, Preprocess: ${preprocessTime}ms, Inference: ${inferenceTime}ms, Total: ${totalTime}ms")
            Log.d("ModelDebug", "Using model: ${if (isUsingYolox) "YOLOx Nano" else "SSD MobileNet"}")
            
            // Draw on overlay surface
            val canvas = overlayView.holder.lockCanvas()
            if (canvas != null) {
                try {
                    val h = canvas.height.toFloat()
                    val w = canvas.width.toFloat()
                    
                    // Clear previous drawings
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                    paint.textSize = h / 50f
                    paint.strokeWidth = h / 250f
                    
                    var detectionCount = 0
                    
                    detections.forEach { detection ->
                        detectionCount++
                        val color = colors[detection.classId % colors.size]
                        paint.color = color
                        paint.style = Paint.Style.STROKE
                        
                        // Scale coordinates to canvas size
                        val scaleX = w / INPUT_SIZE
                        val scaleY = h / INPUT_SIZE
                        
                        val left = detection.x1 * scaleX
                        val top = detection.y1 * scaleY
                        val right = detection.x2 * scaleX
                        val bottom = detection.y2 * scaleY
                        
                        // Draw bounding box
                        canvas.drawRect(left, top, right, bottom, paint)
                        
                        // Draw label with background
                        paint.style = Paint.Style.FILL
                        val labelText = "${detection.label} ${String.format("%.0f%%", detection.confidence * 100)}"
                        
                        val textX = left
                        val textY = top - 5f
                        
                        // Draw text background
                        paint.alpha = 200
                        val textBounds = Rect()
                        paint.getTextBounds(labelText, 0, labelText.length, textBounds)
                        canvas.drawRect(
                            textX - 4f,
                            textY - textBounds.height() - 4f,
                            textX + textBounds.width() + 4f,
                            textY + 4f,
                            paint
                        )
                        
                        // Draw text
                        paint.color = Color.WHITE
                        paint.alpha = 255
                        canvas.drawText(labelText, textX, textY, paint)
                    }
                    
                    val drawTime = System.currentTimeMillis() - inferenceStart - inferenceTime
                    Log.d("Performance", "Total: ${totalTime}ms | Inference: ${inferenceTime}ms | Drawing: ${drawTime}ms | Detections: $detectionCount")
                } finally {
                    overlayView.holder.unlockCanvasAndPost(canvas)
                }
            }
            
            // Update UI on main thread
            runOnUiThread {
                updateStatsUI()
            }
            
            isProcessing = false
            
            // Recycle the processed bitmap
            currentBitmap.recycle()
            
            Log.d("SingleThread", "✅ Frame processed in ${System.currentTimeMillis() - startTime}ms on UI thread")
            
        } catch (e: Exception) {
            Log.e("ProcessingError", "Error processing frame: ${e.message}")
            e.printStackTrace()
            isProcessing = false
            currentBitmap.recycle()
        }
    }
    
    // Update object counts using dictionary - maintains persistent keys
    private fun updateObjectCounts(detections: List<Detection>) {
        // First, reset all previously detected objects to 0
        detectedObjectsHistory.forEach { label ->
            objectCountMap[label] = 0
        }
        
        // Count each detected object in current frame
        detections.forEach { detection ->
            val label = detection.label
            objectCountMap[label] = objectCountMap.getOrDefault(label, 0) + 1
            // Add to history for persistent display
            detectedObjectsHistory.add(label)
        }
    }
    
    // Update statistics UI
    private fun updateStatsUI() {
        // Update total count (only non-zero counts)
        val totalCount = objectCountMap.values.sum()
        totalCountText.text = "Total: $totalCount"
        
        // Update object count list - sorted by count (descending)
        if (objectCountMap.isEmpty()) {
            objectCountText.text = "Waiting for detections..."
        } else {
            // Sort by count in descending order (highest count first)
            val sortedObjects = objectCountMap.entries.sortedByDescending { it.value }
            val countText = StringBuilder()
            sortedObjects.forEachIndexed { index, entry ->
                val emoji = getEmojiForObject(entry.key)
                countText.append("$emoji ${entry.key}: ${entry.value}")
                if (index < sortedObjects.size - 1) {
                    countText.append("\n")
                }
            }
            objectCountText.text = countText.toString()
        }
        
        // Update FPS and inference time
        fpsText.text = String.format("FPS: %.1f | Inference: %dms", currentFps, lastInferenceTime)
        
        // Update model name
        modelNameText.text = "Model: ${if (isUsingYolox) "YOLOx Nano" else "SSD MobileNet"}"
    }
    
    // Get emoji for common objects
    private fun getEmojiForObject(objectName: String): String {
        return when (objectName.lowercase()) {
            "person" -> "👤"
            "car" -> "🚗"
            "bus" -> "🚌"
            "truck" -> "🚚"
            "bicycle" -> "🚲"
            "motorcycle" -> "🏍️"
            "airplane" -> "✈️"
            "train" -> "🚂"
            "boat" -> "⛵"
            "cat" -> "🐱"
            "dog" -> "🐕"
            "bird" -> "🐦"
            "horse" -> "🐴"
            "sheep" -> "🐑"
            "cow" -> "🐄"
            "elephant" -> "🐘"
            "bear" -> "🐻"
            "zebra" -> "🦓"
            "giraffe" -> "🦒"
            "backpack" -> "🎒"
            "umbrella" -> "☂️"
            "handbag" -> "👜"
            "tie" -> "👔"
            "suitcase" -> "🧳"
            "frisbee" -> "🥏"
            "skis" -> "🎿"
            "snowboard" -> "🏂"
            "sports ball", "ball" -> "⚽"
            "kite" -> "🪁"
            "baseball bat" -> "⚾"
            "skateboard" -> "🛹"
            "surfboard" -> "🏄"
            "tennis racket" -> "🎾"
            "bottle" -> "🍾"
            "wine glass" -> "🍷"
            "cup" -> "☕"
            "fork" -> "🍴"
            "knife" -> "🔪"
            "spoon" -> "🥄"
            "bowl" -> "🥣"
            "banana" -> "🍌"
            "apple" -> "🍎"
            "sandwich" -> "🥪"
            "orange" -> "🍊"
            "broccoli" -> "🥦"
            "carrot" -> "🥕"
            "hot dog" -> "🌭"
            "pizza" -> "🍕"
            "donut" -> "🍩"
            "cake" -> "🎂"
            "chair" -> "🪑"
            "couch", "sofa" -> "🛋️"
            "bed" -> "🛏️"
            "dining table", "table" -> "🍽️"
            "toilet" -> "🚽"
            "tv", "television" -> "📺"
            "laptop" -> "💻"
            "mouse" -> "🖱️"
            "remote" -> "📱"
            "keyboard" -> "⌨️"
            "cell phone", "phone" -> "📱"
            "microwave" -> "📟"
            "oven" -> "🔥"
            "toaster" -> "🍞"
            "sink" -> "🚰"
            "refrigerator" -> "🧊"
            "book" -> "📚"
            "clock" -> "🕐"
            "vase" -> "🏺"
            "scissors" -> "✂️"
            "teddy bear" -> "🧸"
            "hair drier" -> "💨"
            "toothbrush" -> "🪥"
            "traffic light" -> "🚦"
            "fire hydrant" -> "🧯"
            "stop sign" -> "🛑"
            "parking meter" -> "🅿️"
            "bench" -> "🪑"
            else -> "📦"
        }
    }
    
    // Toggle stats card visibility
    private fun toggleStatsVisibility() {
        isStatsVisible = !isStatsVisible
        statsCard.visibility = if (isStatsVisible) View.VISIBLE else View.GONE
        toggleStatsButton.text = if (isStatsVisible) "📊" else "📈"
    }

    override fun onDestroy() {
        super.onDestroy()
        yoloxModel?.close()
        ssdModel?.close()
        gpuDelegate?.close()
    }
    
    private fun loadModels() {
        try {
            val compatList = CompatibilityList()
            val options = if(compatList.isDelegateSupportedOnThisDevice) {
                Log.d("ModelDebug", "✅ GPU Delegate is supported, enabling GPU acceleration")
                updateLoadingDialog("Loading models with GPU acceleration...")
                gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                org.tensorflow.lite.support.model.Model.Options.Builder().setDevice(org.tensorflow.lite.support.model.Model.Device.GPU).build()
            } else {
                Log.d("ModelDebug", "⚠️ GPU Delegate not supported, using CPU")
                updateLoadingDialog("Loading models with CPU...")
                org.tensorflow.lite.support.model.Model.Options.Builder().build()
            }
            
            // Load both models
            yoloxModel = YoloxNano.newInstance(this, options)
            ssdModel = SsdMobilenetV11Metadata1.newInstance(this, options)
            
            Log.d("ModelDebug", "✅ Both models loaded successfully")
            dismissLoadingDialog("Models loaded successfully!")
        } catch (e: Exception) {
            Log.e("ModelDebug", "❌ Error loading models: ${e.message}")
            e.printStackTrace()
            dismissLoadingDialog("Error loading models: ${e.message}")
        }
    }
    
    private fun loadLabelsForCurrentModel() {
        val labelFile = if (isUsingYolox) "yoloLabels.txt" else "labels.txt"
        labels = FileUtil.loadLabels(this, labelFile)
        INPUT_SIZE = if (isUsingYolox) 416 else 300
        
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(org.tensorflow.lite.support.image.ops.Rot90Op(0))
            .build()
        
        Log.d("ModelDebug", "Loaded ${labels.size} labels from $labelFile, Input size: $INPUT_SIZE")
    }
    
    private fun switchModel() {
        // Pause processing
        isProcessing = true
        
        // Switch model
        isUsingYolox = !isUsingYolox
        
        // Update button text
        switchModelButton.text = if (isUsingYolox) "Switch to SSD MobileNet" else "Switch to YOLOx Nano"
        
        // Clear object history when switching models (optional - gives fresh start)
        // Comment out these lines if you want to keep history across model switches
        objectCountMap.clear()
        detectedObjectsHistory.clear()
        
        // Reload labels and image processor
        loadLabelsForCurrentModel()
        
        Log.d("ModelDebug", "Switched to: ${if (isUsingYolox) "YOLOx Nano" else "SSD MobileNet"}")
        
        // Resume processing
        isProcessing = false
    }
    
    private fun parseSsdOutput(outputs: SsdMobilenetV11Metadata1.Outputs): List<Detection> {
        val detections = mutableListOf<Detection>()
        
        val locations = outputs.locationsAsTensorBuffer.floatArray
        val classes = outputs.classesAsTensorBuffer.floatArray
        val scores = outputs.scoresAsTensorBuffer.floatArray
        val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray[0].toInt()
        
        for (i in 0 until minOf(numberOfDetections, scores.size)) {
            if (scores[i] >= CONFIDENCE_THRESHOLD) {
                val x = i * 4
                // SSD outputs: [ymin, xmin, ymax, xmax] normalized to [0, 1]
                val ymin = locations[x] * INPUT_SIZE
                val xmin = locations[x + 1] * INPUT_SIZE
                val ymax = locations[x + 2] * INPUT_SIZE
                val xmax = locations[x + 3] * INPUT_SIZE
                
                val classId = classes[i].toInt()
                val label = if (classId < labels.size) labels[classId] else "Unknown"
                
                detections.add(Detection(xmin, ymin, xmax, ymax, scores[i], classId, label))
            }
        }
        
        Log.d("SsdDebug", "SSD Detections: ${detections.size}")
        return detections
    }

    @SuppressLint("MissingPermission")
    fun open_camera(){
        cameraManager.openCamera(cameraManager.cameraIdList[0], object:CameraDevice.StateCallback(){
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                var surfaceTexture = textureView.surfaceTexture
                
                // Set lower resolution for better performance (640x480 instead of 1920x1080)
                surfaceTexture?.setDefaultBufferSize(640, 480)
                Log.d("CameraConfig", "Camera resolution set to: 640x480")
                
                var surface = Surface(surfaceTexture)

                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null)
                    }
                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                    }
                }, handler)
            }

            override fun onDisconnected(p0: CameraDevice) {

            }

            override fun onError(p0: CameraDevice, p1: Int) {

            }
        }, handler)
    }

    fun get_permission(){
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
            get_permission()
        }
    }
    
    // Loading dialog functions
    private fun showLoadingDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Loading Model")
        builder.setMessage("Initializing YOLOx Nano model...")
        builder.setCancelable(false)
        loadingDialog = builder.create()
        loadingDialog?.show()
    }
    
    private fun updateLoadingDialog(message: String) {
        runOnUiThread {
            loadingDialog?.setMessage(message)
        }
    }
    
    private fun dismissLoadingDialog(message: String) {
        runOnUiThread {
            loadingDialog?.setMessage(message)
            Handler(mainLooper).postDelayed({
                loadingDialog?.dismiss()
                loadingDialog = null
            }, 800) // Show success message briefly before dismissing
        }
    }
}