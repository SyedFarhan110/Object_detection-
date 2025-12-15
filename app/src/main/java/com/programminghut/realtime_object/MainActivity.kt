package com.programminghut.Object_Detection
import android.view.View
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
import android.view.SurfaceView
import android.view.TextureView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import android.view.Gravity
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.DataType
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.*
import java.net.URL
import org.json.JSONArray
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import com.programminghut.Object_Detection.helpers.PoseEstimationHelper
import com.programminghut.Object_Detection.helpers.SegmentationHelper
import com.programminghut.Object_Detection.helpers.LicensePlateDetectionHelper
import com.programminghut.Object_Detection.helpers.BlinkDrowseDetectionHelper
import com.programminghut.Object_Detection.helpers.DentDetectionHelper
import com.programminghut.Object_Detection.helpers.FaceDetectionHelper
import com.programminghut.Object_Detection.helpers.FireSmokeDetectionHelper
import com.programminghut.Object_Detection.helpers.GroceryDetectionHelper
import com.programminghut.Object_Detection.helpers.HelmetDetectionHelper

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
    private var gpuDelegate: GpuDelegate? = null
    private var loadingDialog: AlertDialog? = null
    lateinit var settingsButton: ImageButton
    lateinit var fpsTextView: TextView
    private var lastInferenceTime: Long = 0
    private lateinit var cameraButton: Button
    private lateinit var uploadButton: Button
    private lateinit var staticImageView: ImageView
    private var isInCameraMode = true
    private var uploadedImageUri: Uri? = null
    
    data class ModelInfo(
        val name: String,
        val modelUrl: String,
        val labelsUrl: String,
        val type: String,
        val fileName: String,
        val labelsFileName: String,
        var isDownloaded: Boolean = false
    )
    
    
    private val availableModels = mutableListOf<ModelInfo>()
    private var currentModelIndex: Int? = null
    private val API_URL = "https://raw.githubusercontent.com/SyedFarhan110/Object-detection-/refs/heads/main/transformed_models.json"
    
    lateinit var bottomDashboard: LinearLayout
    lateinit var dashboardEmoji1: TextView
    lateinit var dashboardCount1: TextView
    lateinit var dashboardEmoji2: TextView
    lateinit var dashboardCount2: TextView

    private val labelToEmoji: Map<String, String> = mapOf(
        "person" to "🧍", "bicycle" to "🚲", "car" to "🚗", "motorcycle" to "🏍️",
        "airplane" to "✈️", "bus" to "🚌", "train" to "🚆", "truck" to "🚚",
        "boat" to "🚤", "traffic light" to "🚦", "fire hydrant" to "🚒",
        "stop sign" to "🛑", "parking meter" to "🅿️", "bench" to "🪑",
        "bird" to "🐦", "cat" to "🐱", "dog" to "🐶", "horse" to "🐴",
        "sheep" to "🐑", "cow" to "🐄", "elephant" to "🐘", "bear" to "🐻",
        "zebra" to "🦓", "giraffe" to "🦒", "backpack" to "🎒", "umbrella" to "☂️",
        "handbag" to "👜", "tie" to "👔", "suitcase" to "🧳", "frisbee" to "🥏",
        "skis" to "🎿", "snowboard" to "🏂", "sports ball" to "⚽", "kite" to "🪁",
        "baseball bat" to "⚾", "baseball glove" to "🥎", "skateboard" to "🛹",
        "surfboard" to "🏄", "tennis racket" to "🎾", "bottle" to "🍼",
        "wine glass" to "🍷", "cup" to "☕", "fork" to "🍴", "knife" to "🔪",
        "spoon" to "🥄", "bowl" to "🥣", "banana" to "🍌", "apple" to "🍎",
        "sandwich" to "🥪", "orange" to "🍊", "broccoli" to "🥦", "carrot" to "🥕",
        "hot dog" to "🌭", "pizza" to "🍕", "donut" to "🍩", "cake" to "🎂",
        "chair" to "🪑", "couch" to "🛋️", "potted plant" to "🪴", "bed" to "🛏️",
        "dining table" to "🍽️", "toilet" to "🚽", "tv" to "📺", "laptop" to "💻",
        "mouse" to "🖱️", "remote" to "🕹️", "keyboard" to "⌨️", "cell phone" to "📱",
        "microwave" to "📡", "oven" to "🔥", "toaster" to "🍞", "sink" to "🚰",
        "refrigerator" to "🧊", "book" to "📚", "clock" to "🕒", "vase" to "🏺",
        "scissors" to "✂️", "teddy bear" to "🧸", "hair drier" to "💨", "toothbrush" to "🪥"
    )

    private val counts: MutableMap<String, Int> = mutableMapOf()

    private var frameCounter = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps = 0.0
    
    private var poseEstimationHelper: PoseEstimationHelper? = null
    private var cachedPoseVisualization: Bitmap? = null
    private var licensePlateDetectionHelper: LicensePlateDetectionHelper? = null
    private var blinkDrowseHelper: BlinkDrowseDetectionHelper? = null
    private var dentHelper: DentDetectionHelper? = null
    private var faceHelper: FaceDetectionHelper? = null
    private var fireSmokeHelper: FireSmokeDetectionHelper? = null
    private var groceryHelper: GroceryDetectionHelper? = null
    private var helmetHelper: HelmetDetectionHelper? = null
    private var isUsingYolox: Boolean
        get() = currentModelIndex?.let { availableModels.getOrNull(it)?.type == "yolox" } ?: false
        set(value) { }

    private var isUsingPose: Boolean
        get() = currentModelIndex?.let { availableModels.getOrNull(it)?.type == "pose" } ?: false
        set(value) { }
    
    private var isProcessing = false
    private var lastProcessTime = 0L
    private val MIN_PROCESS_INTERVAL = 33L  // ~30 FPS target
    
    private var INPUT_SIZE = 300
    private var INPUT_WIDTH = 300
    private var INPUT_HEIGHT = 300
    private var OUTPUT_SHAPE: IntArray? = null
    private val CONFIDENCE_THRESHOLD = 0.5f
    private val NMS_THRESHOLD = 0.45f
    
    private val modelInterpreters = mutableMapOf<String, Interpreter>()
    private var currentInterpreter: Interpreter? = null
    private var segmentationHelper: SegmentationHelper? = null
    
    // Camera state management
    private var isCameraOpen = false
    private var captureSession: CameraCaptureSession? = null
    
    // Performance optimizations: Reuse TensorImage
    private var reusableTensorImage: TensorImage? = null
    
    // Performance optimizations: Cache bitmap dimensions
    private var cachedBitmapWidth = 0
    private var cachedBitmapHeight = 0
    
    // Performance optimizations: Cached scaled mask for segmentation
    private var cachedScaledMask: Bitmap? = null
    
    data class Detection(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val confidence: Float, val classId: Int, val label: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permission
        get_permission()

        // ----- Paint Setup -----
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.isAntiAlias = true

        // ----- Background Thread -----
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        // ----- Overlay View -----
        overlayView = findViewById(R.id.overlayView)
        overlayView.setZOrderOnTop(true)
        overlayView.holder.setFormat(PixelFormat.TRANSPARENT)

        // ----- FPS TextView -----
        fpsTextView = findViewById(R.id.fpsTextView)

        // ----- Dashboard Components -----
        bottomDashboard = findViewById(R.id.bottomDashboard)
        dashboardEmoji1 = findViewById(R.id.dashboardEmoji1)
        dashboardCount1 = findViewById(R.id.dashboardCount1)
        dashboardEmoji2 = findViewById(R.id.dashboardEmoji2)
        dashboardCount2 = findViewById(R.id.dashboardCount2)

        bottomDashboard.setOnClickListener { showExpandedDashboard() }

        // ----- Settings Button -----
        settingsButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener { showModelSelectionDialog() }

        // ----- TextureView / Camera Preview -----
        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                Log.d("Camera", "SurfaceTexture available")

                // Only open camera if we have permission & camera not opened
                val hasPermission = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (!isCameraOpen && hasPermission) {
                    open_camera()
                }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                Log.d("Camera", "Surface changed: $w x $h")
            }

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                Log.d("Camera", "Surface destroyed")
                close_camera()
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {

                // Stop if not in camera mode
                if (!isInCameraMode) return

                // Interpreter or model not ready
                if (currentModelIndex == null || currentInterpreter == null) return

                val now = System.currentTimeMillis()

                // Frame rate limiting
                if (now - lastProcessTime < MIN_PROCESS_INTERVAL) return

                // Prevent overlapping frame processing
                if (isProcessing) return

                lastProcessTime = now
                isProcessing = true

                try {
                    val bitmap = textureView.bitmap
                    if (bitmap != null) {
                        processFrameSynchronous(bitmap)
                    }
                } catch (e: Exception) {
                    Log.e("FrameCapture", "Error: ${e.message}")
                } finally {
                    isProcessing = false
                }
            }
        }

        // ----- Camera Manager -----
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // ----- Camera & Upload buttons -----
        cameraButton = findViewById(R.id.cameraButton)
        uploadButton = findViewById(R.id.uploadButton)

        staticImageView = findViewById(R.id.staticImageView)

        // ----- Fetch model list from server -----
        showLoadingDialog("Fetching available models...")

        // ----- Add click listeners for camera and upload buttons -----
        cameraButton.setOnClickListener {
            switchToCameraMode()
        }
        uploadButton.setOnClickListener {
            showUploadOptions()
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                fetchModelList()
                runOnUiThread { dismissLoadingDialog("Models list loaded!") }
            } catch (e: Exception) {
                Log.e("ModelAPI", "Error fetching model list: ${e.message}")
                runOnUiThread {
                    dismissLoadingDialog("Failed to load models")
                    showErrorDialog("Could not load model list. Please check your connection.")
                }
            }
        }
    }

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
        handleUploadedMedia(it)
    }
}

    private suspend fun fetchModelList() {
        try {
            val jsonString = withContext(Dispatchers.IO) {
                URL(API_URL).readText()
            }
            val jsonArray = JSONArray(jsonString)
            availableModels.clear()
            for (i in 0 until jsonArray.length()) {
                val modelObj = jsonArray.getJSONObject(i)
                val fileName = modelObj.getString("model_url").substringAfterLast("/")
                val labelsFileName = modelObj.getString("labels_url").substringAfterLast("/")
                
                // Check if model is already downloaded
                val modelFile = File(filesDir, fileName)
                val labelsFile = File(filesDir, labelsFileName)
                val isDownloaded = modelFile.exists() && labelsFile.exists()
                
                val modelInfo = ModelInfo(
                    name = modelObj.getString("name"),
                    modelUrl = modelObj.getString("model_url"),
                    labelsUrl = modelObj.getString("labels_url"),
                    type = modelObj.getString("type"),
                    fileName = fileName,
                    labelsFileName = labelsFileName,
                    isDownloaded = isDownloaded
                )
                availableModels.add(modelInfo)
            }
            Log.d("ModelAPI", "Found ${availableModels.size} models from API")
        } catch (e: Exception) {
         e.printStackTrace()
        }
    }

    private suspend fun downloadModelIfNeeded(modelInfo: ModelInfo): Boolean {
        return try {
            if (!modelInfo.isDownloaded) {
                downloadModel(modelInfo)
                downloadLabels(modelInfo)
                modelInfo.isDownloaded = true
            }
            true
        } catch (e: Exception) {
            Log.e("ModelDownload", "Failed to download ${modelInfo.name}: ${e.message}")
            false
        }
    }

    private fun loadLabelsForModel(modelInfo: ModelInfo) {
        val labelsFile = File(filesDir, modelInfo.labelsFileName)
        labels = labelsFile.readLines().filter { it.isNotBlank() }
        Log.d("ModelDebug", "Loaded ${labels.size} labels for ${modelInfo.name}")
    }

    private suspend fun downloadModel(modelInfo: ModelInfo) {
        val modelFile = File(filesDir, modelInfo.fileName)
        
        withContext(Dispatchers.IO) {
            try {
                Log.d("ModelDownload", "Downloading ${modelInfo.name}")
                val connection = URL(modelInfo.modelUrl).openConnection()
                connection.connect()
                
                val input = BufferedInputStream(connection.getInputStream())
                val output = FileOutputStream(modelFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                    
                    // Update progress every 100KB
                    if (totalBytes % (100 * 1024) == 0L) {
                        val mb = totalBytes / (1024.0 * 1024.0)
                        updateLoadingDialog("Downloading ${modelInfo.name}... ${String.format("%.1f", mb)} MB")
                    }
                }
                
                output.flush()
                output.close()
                input.close()
                Log.d("ModelDownload", "${modelInfo.name} downloaded successfully")
            } catch (e: Exception) {
                Log.e("ModelDownload", "Error downloading ${modelInfo.name}: ${e.message}")
                throw e
            }
        }
    }

    private suspend fun downloadLabels(modelInfo: ModelInfo) {
        val labelsFile = File(filesDir, modelInfo.labelsFileName)
        
        withContext(Dispatchers.IO) {
            try {
                Log.d("ModelDownload", "Downloading labels")
                val labelsText = URL(modelInfo.labelsUrl).readText()
                labelsFile.writeText(labelsText)
                Log.d("ModelDownload", "Labels downloaded successfully")
            } catch (e: Exception) {
                Log.e("ModelDownload", "Error downloading labels: ${e.message}")
                throw e
            }
        }
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val modelFile = File(filesDir, fileName)
        val inputStream = FileInputStream(modelFile)
        val fileChannel = inputStream.channel
        val declaredLength = fileChannel.size()
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, declaredLength)
    }

    private fun loadAndInitializeModel(modelInfo: ModelInfo): Boolean {
        try {
            // Check if already loaded
            if (modelInterpreters.containsKey(modelInfo.fileName)) {
                currentInterpreter = modelInterpreters[modelInfo.fileName]
                loadLabelsForModel(modelInfo)
                // IMPORTANT: Always detect and set shapes when switching models
                detectAndSetModelShapes(currentInterpreter!!, modelInfo.name)
                return true
            }
            
            val compatList = CompatibilityList()
            val interpreterOptions = Interpreter.Options()
            
            if(compatList.isDelegateSupportedOnThisDevice) {
                Log.d("ModelDebug", "✅ GPU acceleration enabled")
                if (gpuDelegate == null) {
                    gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                }
                interpreterOptions.addDelegate(gpuDelegate)
            } else {
                Log.d("ModelDebug", "⚠️ Using CPU")
            }
            
            val interpreter = Interpreter(loadModelFile(modelInfo.fileName), interpreterOptions)
            modelInterpreters[modelInfo.fileName] = interpreter
            currentInterpreter = interpreter
            
            Log.d("ModelDebug", "✅ Loaded ${modelInfo.name}")
            
            // Dynamically detect and set model shapes
            detectAndSetModelShapes(interpreter, modelInfo.name)
            
            // Load labels
            loadLabelsForModel(modelInfo)
            
            // Initialize helpers for pose, segmentation, or license plate detection models
            if (modelInfo.type.lowercase().contains("pose") || 
                modelInfo.type.lowercase().contains("segmentation") ||
                modelInfo.type.lowercase().contains("license") ||
                modelInfo.type.lowercase().contains("lpd")) {
                initializeSegmentationHelper(modelInfo)
            }
            
            return true
        } catch (e: Exception) {
            Log.e("ModelDebug", "Error loading model: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    private fun initializeSegmentationHelper(modelInfo: ModelInfo) {
        try {
            val normalizedType = modelInfo.type.lowercase()
            
            // License Plate Detection
            if (normalizedType.contains("license") || normalizedType.contains("lpd")) {
                Log.d("LicensePlate", "Initializing LicensePlateDetectionHelper for ${modelInfo.name}")
                val modelFile = File(filesDir, modelInfo.fileName)
                val labelsFile = File(filesDir, modelInfo.labelsFileName)
                if (!modelFile.exists()) {
                    Log.e("LicensePlate", "Model file not found: ${modelFile.path}")
                    return
                }
                if (!labelsFile.exists()) {
                    Log.e("LicensePlate", "Labels file not found: ${labelsFile.path}")
                    return
                }
                licensePlateDetectionHelper = LicensePlateDetectionHelper(this)
                val success = licensePlateDetectionHelper!!.initialize(modelFile, labelsFile)
                if (success) {
                    Log.d("LicensePlate", "✅ LicensePlateDetectionHelper initialized successfully")
                } else {
                    Log.e("LicensePlate", "❌ LicensePlateDetectionHelper initialization failed")
                    licensePlateDetectionHelper = null
                }
                return
            }
            
            // Pose Estimation
            if (normalizedType.contains("pose")) {
                Log.d("PoseEstimation", "Initializing PoseEstimationHelper for ${modelInfo.name}")
                val modelFile = File(filesDir, modelInfo.fileName)
                val labelsFile = File(filesDir, modelInfo.labelsFileName)
                if (!modelFile.exists()) {
                    Log.e("PoseEstimation", "Model file not found: ${modelFile.path}")
                    return
                }
                if (!labelsFile.exists()) {
                    Log.e("PoseEstimation", "Labels file not found: ${labelsFile.path}")
                    return
                }
                poseEstimationHelper = PoseEstimationHelper(this)
                val success = poseEstimationHelper!!.initialize(modelFile, labelsFile)
                if (success) {
                    Log.d("PoseEstimation", "✅ PoseEstimationHelper initialized successfully")
                } else {
                    Log.e("PoseEstimation", "❌ PoseEstimationHelper initialization failed")
                    poseEstimationHelper = null
                }
                return
            }
            
            // Segmentation
            if (normalizedType.contains("segmentation")) {
                Log.d("Segmentation", "Initializing SegmentationHelper for ${modelInfo.name}")
                val modelFile = File(filesDir, modelInfo.fileName)
                val labelsFile = File(filesDir, modelInfo.labelsFileName)
                if (!modelFile.exists()) {
                    Log.e("Segmentation", "Model file not found: ${modelFile.path}")
                    return
                }
                if (!labelsFile.exists()) {
                    Log.e("Segmentation", "Labels file not found: ${labelsFile.path}")
                    return
                }
                segmentationHelper = SegmentationHelper(this)
                val success = segmentationHelper!!.initialize(modelFile, labelsFile)
                if (success) {
                    Log.d("Segmentation", "✅ SegmentationHelper initialized successfully")
                } else {
                    Log.e("Segmentation", "❌ SegmentationHelper initialization failed")
                    segmentationHelper = null
                }
            }
        } catch (e: Exception) {
            Log.e("InitializationError", "Error initializing helper", e)
            segmentationHelper = null
            poseEstimationHelper = null
            licensePlateDetectionHelper = null
        }
    }

    private fun detectAndSetModelShapes(interpreter: Interpreter, modelName: String) {
        try {
            // Get input tensor shape
            val inputShape = interpreter.getInputTensor(0).shape()
            val inputDataType = interpreter.getInputTensor(0).dataType()
            
            Log.d("ModelShape", "📊 $modelName Input Shape: ${inputShape.contentToString()}")
            Log.d("ModelShape", "📊 $modelName Input DataType: $inputDataType")
            
            // Dynamically set input dimensions
            // Typical formats: [batch, height, width, channels] or [batch, channels, height, width]
            if (inputShape.size == 4) {
                // Determine format by checking channel position
                if (inputShape[3] == 3 || inputShape[3] == 1) {
                    // NHWC format: [1, height, width, channels]
                    INPUT_HEIGHT = inputShape[1]
                    INPUT_WIDTH = inputShape[2]
                    Log.d("ModelShape", "Detected NHWC format")
                } else if (inputShape[1] == 3 || inputShape[1] == 1) {
                    // NCHW format: [1, channels, height, width]
                    INPUT_HEIGHT = inputShape[2]
                    INPUT_WIDTH = inputShape[3]
                    Log.d("ModelShape", "Detected NCHW format")
                } else {
                    // Fallback: assume NHWC
                    Log.w("ModelShape", "⚠️ Unclear format, assuming NHWC")
                    INPUT_HEIGHT = inputShape[1]
                    INPUT_WIDTH = inputShape[2]
                }
                INPUT_SIZE = INPUT_HEIGHT
                
                Log.d("ModelShape", "✅ Dynamically detected input size: ${INPUT_WIDTH}x${INPUT_HEIGHT}")
            }
            
            // Get output tensor shape
            val outputShape = interpreter.getOutputTensor(0).shape()
            val outputDataType = interpreter.getOutputTensor(0).dataType()
            OUTPUT_SHAPE = outputShape
            
            Log.d("ModelShape", "📊 $modelName Output Shape: ${outputShape.contentToString()}")
            Log.d("ModelShape", "📊 $modelName Output DataType: $outputDataType")
            
            // Log all output tensors if multiple
            val numOutputs = interpreter.outputTensorCount
            Log.d("ModelShape", "📊 $modelName has $numOutputs output tensor(s)")
            for (i in 0 until numOutputs) {
                val shape = interpreter.getOutputTensor(i).shape()
                val dtype = interpreter.getOutputTensor(i).dataType()
                Log.d("ModelShape", "📊 Output[$i] Shape: ${shape.contentToString()}, Type: $dtype")
            }
            
            // Update image processor with dynamically detected dimensions
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(INPUT_WIDTH, INPUT_HEIGHT, ResizeOp.ResizeMethod.BILINEAR))
                .add(org.tensorflow.lite.support.image.ops.Rot90Op(0))
                .build()
            
            Log.d("ModelShape", "✅ Image processor updated for ${INPUT_WIDTH}x${INPUT_HEIGHT}")
            
        } catch (e: Exception) {
            Log.e("ModelShape", "❌ Error detecting model shapes: ${e.message}")
            e.printStackTrace()
            
            // Fallback to default values
            INPUT_SIZE = 300
            INPUT_WIDTH = 300
            INPUT_HEIGHT = 300
            OUTPUT_SHAPE = null
            
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(INPUT_WIDTH, INPUT_HEIGHT, ResizeOp.ResizeMethod.BILINEAR))
                .add(org.tensorflow.lite.support.image.ops.Rot90Op(0))
                .build()
        }
    }

    private suspend fun processStaticImage(bitmap: Bitmap) {
    try {
        // Cache bitmap dimensions for static image mode
        cachedBitmapWidth = bitmap.width
        cachedBitmapHeight = bitmap.height
        
        // Reuse existing tensor image processing
        if (reusableTensorImage == null || reusableTensorImage!!.dataType != currentInterpreter!!.getInputTensor(0).dataType()) {
            reusableTensorImage = TensorImage(currentInterpreter!!.getInputTensor(0).dataType())
        }
        
        reusableTensorImage!!.load(bitmap)
        val processedImage = imageProcessor.process(reusableTensorImage!!)
        
        // Run inference using existing method
        val detections = runInference(processedImage, bitmap)
        
        // Draw detections on overlay
        withContext(Dispatchers.Main) {
            drawDetections(detections)
            fpsTextView.text = "Inference: ${lastInferenceTime} ms"
        }
        
    } catch (e: Exception) {
        Log.e("StaticInference", "Error processing static image: ${e.message}")
        e.printStackTrace()
    }
}

private fun downloadAndRunInferenceOnStatic(
    modelIndex: Int, 
    bitmap: Bitmap, 
    isVideo: Boolean, 
    videoUri: Uri?
) {
    val modelInfo = availableModels[modelIndex]
    
    showLoadingDialog("Downloading ${modelInfo.name}...")
    
    GlobalScope.launch(Dispatchers.Main) {
        val downloadSuccess = withContext(Dispatchers.IO) {
            downloadModelIfNeeded(modelInfo)
        }
        
        if (downloadSuccess) {
            updateLoadingDialog("Loading ${modelInfo.name}...")
            
            val loadSuccess = withContext(Dispatchers.IO) {
                loadAndInitializeModel(modelInfo)
            }
            
            if (loadSuccess) {
                currentModelIndex = modelIndex
                dismissLoadingDialog("Running inference...")
                
                // Run inference on the bitmap
                withContext(Dispatchers.IO) {
                    processStaticImage(bitmap)
                }
                
            } else {
                dismissLoadingDialog("Failed to load model")
                showErrorDialog("Failed to load ${modelInfo.name}")
            }
        } else {
            dismissLoadingDialog("Download failed")
            showErrorDialog("Failed to download ${modelInfo.name}")
        }
    }
}
private fun runInferenceOnStaticMedia(
    modelIndex: Int, 
    bitmap: Bitmap, 
    isVideo: Boolean, 
    videoUri: Uri?
) {
    val modelInfo = availableModels[modelIndex]
    
    showLoadingDialog("Loading ${modelInfo.name}...")
    
    GlobalScope.launch(Dispatchers.Main) {
        val success = withContext(Dispatchers.IO) {
            loadAndInitializeModel(modelInfo)
        }
        
        if (success) {
            currentModelIndex = modelIndex
            dismissLoadingDialog("Running inference...")
            
            // Run inference on the bitmap
            withContext(Dispatchers.IO) {
                processStaticImage(bitmap)
            }
            
            if (isVideo && videoUri != null) {
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Video Processing")
                        .setMessage("Inference complete on first frame. Full video processing not yet implemented.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            
        } else {
            dismissLoadingDialog("Failed to load model")
            showErrorDialog("Failed to load ${modelInfo.name}")
        }
    }
}
private fun showModelSelectionForStaticMedia(
    bitmap: Bitmap, 
    isVideo: Boolean, 
    videoUri: Uri? = null
) {
    if (availableModels.isEmpty()) {
        showErrorDialog("No models available. Please check your internet connection.")
        return
    }
    
    val modelItems = availableModels.map { model ->
        val typeTag = model.type.uppercase()
        if (model.isDownloaded) {
            "${model.name} ✓ [$typeTag]"
        } else {
            "${model.name} (Download) [$typeTag]"
        }
    }.toTypedArray()
    
    AlertDialog.Builder(this)
        .setTitle("Select Model for ${if (isVideo) "Video" else "Image"}")
        .setItems(modelItems) { dialog, which ->
            dialog.dismiss()
            val selectedModel = availableModels[which]
            
            if (selectedModel.isDownloaded) {
                runInferenceOnStaticMedia(which, bitmap, isVideo, videoUri)
            } else {
                // Download model first, then run inference
                downloadAndRunInferenceOnStatic(which, bitmap, isVideo, videoUri)
            }
        }
        .setNegativeButton("Cancel") { _, _ ->
            switchToCameraMode()
        }
        .show()
    }

    private fun handleUploadedVideo(uri: Uri) {
    try {
        // Switch to static mode
        switchToStaticMode()
        
        // Extract first frame from video
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(this, uri)
        val bitmap = retriever.getFrameAtTime(0)
        retriever.release()
        
        if (bitmap != null) {
            staticImageView.setImageBitmap(bitmap)
            
            // Show model selection dialog
            showModelSelectionForStaticMedia(bitmap, isVideo = true, videoUri = uri)
        } else {
            showErrorDialog("Failed to extract frame from video")
        }
        
    } catch (e: Exception) {
        Log.e("VideoUpload", "Error loading video: ${e.message}")
        showErrorDialog("Failed to load video: ${e.message}")
    }
}

private fun handleUploadedImage(uri: Uri) {
    try {
        // Switch to static mode
        switchToStaticMode()
        
        // Load and display image
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        staticImageView.setImageBitmap(bitmap)
        
        // Show model selection dialog
        showModelSelectionForStaticMedia(bitmap, isVideo = false)
        
    } catch (e: Exception) {
        Log.e("ImageUpload", "Error loading image: ${e.message}")
        showErrorDialog("Failed to load image: ${e.message}")
    }
}

private fun switchToStaticMode() {
    isInCameraMode = false
    staticImageView.visibility = View.VISIBLE
    textureView.visibility = View.GONE
    overlayView.visibility = View.VISIBLE
    
    // Stop camera if open
    if (isCameraOpen) {
        close_camera()
        isCameraOpen = false
    }

    Log.d("ModeSwitch", "Switched to Static Mode")
}

private fun handleUploadedMedia(uri: Uri) {
    uploadedImageUri = uri
    
    try {
        val mimeType = contentResolver.getType(uri)
        
        when {
            mimeType?.startsWith("image/") == true -> {
                handleUploadedImage(uri)
            }
            mimeType?.startsWith("video/") == true -> {
                handleUploadedVideo(uri)
            }
            else -> {
                showErrorDialog("Unsupported file type")
            }
        }
    } catch (e: Exception) {
        Log.e("Upload", "Error handling media: ${e.message}")
        showErrorDialog("Failed to load media: ${e.message}")
    }
}
private fun showUploadOptions() {
    if (currentModelIndex == null) {
        AlertDialog.Builder(this)
            .setTitle("No Model Selected")
            .setMessage("Please select a detection model first from the settings menu.")
            .setPositiveButton("OK", null)
            .show()
        return
    }
    
    val options = arrayOf("Select Image")
    AlertDialog.Builder(this)
        .setTitle("Upload Media")
        .setItems(options) { _, which ->
            when (which) {
                0 -> pickMediaLauncher.launch("image/*")
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}
private fun switchToCameraMode() {
    isInCameraMode = true
    staticImageView.visibility = android.view.View.GONE
    textureView.visibility = android.view.View.VISIBLE
    overlayView.visibility = android.view.View.VISIBLE
    
    // Reopen camera if not already open
    if (!isCameraOpen && textureView.isAvailable && 
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        open_camera()
    }
    
    Log.d("ModeSwitch", "Switched to Camera Mode")
}


 private fun processFrameSynchronous(currentBitmap: Bitmap) {
    try {
        if (currentModelIndex == null || currentInterpreter == null) {
            isProcessing = false
            currentBitmap.recycle()
            return
        }
        
        // Cache bitmap dimensions
        cachedBitmapWidth = currentBitmap.width
        cachedBitmapHeight = currentBitmap.height
        
        // Check what data type the model expects
        val inputDataType = currentInterpreter!!.getInputTensor(0).dataType()
        
        // Reuse TensorImage instead of creating new one
        if (reusableTensorImage == null || reusableTensorImage!!.dataType != inputDataType) {
            reusableTensorImage = TensorImage(inputDataType)
        }
        
        reusableTensorImage!!.load(currentBitmap)
        val processedImage = imageProcessor.process(reusableTensorImage!!)

        val detections = runInference(processedImage, currentBitmap)
        drawDetections(detections)
        
        isProcessing = false
        currentBitmap.recycle()
        
        updateFps()
        
        // Batch UI updates to reduce overhead
        if (frameCounter % 5 == 0) {  // Update dashboard every 5 frames
            updateBottomDashboard()
            updateDashboardIfVisible()
        }
    } catch (e: Exception) {
        Log.e("ProcessingError", "Error: ${e.message}")
        e.printStackTrace()
        isProcessing = false
        currentBitmap.recycle()
    }
}


    private fun runInference(image: TensorImage, rawBitmap: Bitmap): List<Detection> {
    val modelIndex = currentModelIndex ?: return emptyList()
    val currentModel = availableModels[modelIndex]
    val interpreter = currentInterpreter ?: return emptyList()
    
    // Normalize the model type for better matching
    val normalizedType = currentModel.type.lowercase().trim()
    val modelName = currentModel.name.lowercase()
    
    // Determine the correct inference method
    val inferenceType = when {
        // Check type field first
        normalizedType.contains("license") || normalizedType.contains("lpd") -> "license_plate"
        normalizedType.contains("pose") -> "pose"
        normalizedType.contains("segmentation") -> "segmentation"
        normalizedType.contains("blink") || normalizedType.contains("drowse") -> "blink_drowse"
        normalizedType.contains("dent") || normalizedType.contains("vehicle_damage") -> "dent"
        normalizedType.contains("face") -> "face"
        normalizedType.contains("fire") || normalizedType.contains("smoke") -> "fire_smoke"
        normalizedType.contains("grocery") -> "grocery"
        normalizedType.contains("helmet") || normalizedType.contains("safety") -> "helmet"
        normalizedType in listOf("yolox", "yolo", "yolov5", "yolov8", "yolov11") -> "yolo"
        normalizedType in listOf("ssd", "ssd_mobilenet") -> "ssd"
        
        // Fallback: Check model name if type is unclear
        modelName.contains("license") || modelName.contains("plate") || modelName.contains("lpd") -> "license_plate"
        modelName.contains("pose") || modelName.contains("keypoint") -> "pose"
        modelName.contains("segment") || modelName.contains("mask") -> "segmentation"
        modelName.contains("blink") || modelName.contains("drowse") -> "blink_drowse"
        modelName.contains("dent") -> "dent"
        modelName.contains("face") -> "face"
        modelName.contains("fire") || modelName.contains("smoke") -> "fire_smoke"
        modelName.contains("grocery") -> "grocery"
        modelName.contains("helmet") -> "helmet"
        modelName.contains("yolo") -> "yolo"
        modelName.contains("ssd") || modelName.contains("mobilenet") -> "ssd"
        
        // Last resort: Try to detect from output shape
        else -> detectInferenceTypeFromOutput(interpreter)
    }
    
    return try {
        when (inferenceType) {
            "license_plate" -> {
                Log.d("Inference", "Using License Plate Detection inference for ${currentModel.name}")
                runLicensePlateInference(rawBitmap)
            }
            "pose" -> {
                Log.d("Inference", "Using Pose Estimation inference for ${currentModel.name}")
                runPoseInference(rawBitmap)
            }
            "segmentation" -> {
                Log.d("Inference", "Using Segmentation inference for ${currentModel.name}")
                runSegmentationInference(rawBitmap)
            }
            "blink_drowse" -> {
                Log.d("Inference", "Using Blink/Drowsiness Detection for ${currentModel.name}")
                runBlinkDrowseInference(rawBitmap)
            }
            "dent" -> {
                Log.d("Inference", "Using Dent Detection for ${currentModel.name}")
                runDentInference(rawBitmap)
            }
            "face" -> {
                Log.d("Inference", "Using Face Detection for ${currentModel.name}")
                runFaceInference(rawBitmap)
            }
            "fire_smoke" -> {
                Log.d("Inference", "Using Fire/Smoke Detection for ${currentModel.name}")
                runFireSmokeInference(rawBitmap)
            }
            "grocery" -> {
                Log.d("Inference", "Using Grocery Detection for ${currentModel.name}")
                runGroceryInference(rawBitmap)
            }
            "helmet" -> {
                Log.d("Inference", "Using Helmet Detection for ${currentModel.name}")
                runHelmetInference(rawBitmap)
            }
            "yolo" -> {
                Log.d("Inference", "Using YOLO inference for ${currentModel.name}")
                runYoloxInference(interpreter, image)
            }
            "ssd" -> {
                Log.d("Inference", "Using SSD inference for ${currentModel.name}")
                runSsdInference(interpreter, image)
            }
            else -> {
                Log.w("Inference", "Could not determine inference type for '${currentModel.name}', defaulting to YOLO")
                runYoloxInference(interpreter, image)
            }
        }
    } catch (e: Exception) {
        Log.e("Inference", "Inference failed for ${currentModel.name}: ${e.message}")
        e.printStackTrace()
        emptyList()
    }
}

private fun detectInferenceTypeFromOutput(interpreter: Interpreter): String {
    return try {
        val outputCount = interpreter.outputTensorCount
        val outputShape = interpreter.getOutputTensor(0).shape()
        
        Log.d("InferenceDetection", "Output count: $outputCount")
        Log.d("InferenceDetection", "Output 0 shape: ${outputShape.contentToString()}")
        
        when {
            // SSD has 4 outputs
            outputCount == 4 -> {
                Log.d("InferenceDetection", "Detected SSD model (4 outputs)")
                "ssd"
            }
            // Segmentation has 2 outputs (detections + masks)
            outputCount == 2 -> {
                val output1Shape = interpreter.getOutputTensor(1).shape()
                if (output1Shape.size == 4 && output1Shape[3] > 10) {
                    Log.d("InferenceDetection", "Detected Segmentation model (2 outputs with mask channels)")
                    "segmentation"
                } else {
                    Log.d("InferenceDetection", "Detected YOLO model (2 outputs)")
                    "yolo"
                }
            }
            // YOLO typically has 1 output with shape [1, features, anchors]
            outputCount == 1 && outputShape.size == 3 -> {
                val features = outputShape[1]
                val anchors = outputShape[2]
                // License plate detection: [1, 5, 8400]
                if (features == 5 && anchors == 8400) {
                    Log.d("InferenceDetection", "Detected License Plate Detection model (5 features, 8400 anchors)")
                    "license_plate"
                }
                // Pose models have 56 features (4 bbox + 1 conf + 51 keypoints)
                else if (features == 56) {
                    Log.d("InferenceDetection", "Detected Pose model (56 features)")
                    "pose"
                } else {
                    Log.d("InferenceDetection", "Detected YOLO model (standard features)")
                    "yolo"
                }
            }
            else -> {
                Log.w("InferenceDetection", "Unknown output format, defaulting to YOLO")
                "yolo"
            }
        }
    } catch (e: Exception) {
        Log.e("InferenceDetection", "Error detecting inference type: ${e.message}")
        "yolo" // Safe default
    }
}

    private fun runLicensePlateInference(bitmap: Bitmap): List<Detection> {
        val helper = licensePlateDetectionHelper
        if (helper == null) {
            Log.e("LicensePlate", "LicensePlateDetectionHelper not initialized")
            return emptyList()
        }
        return try {
            val startTime = System.nanoTime()
            val lpResults = helper.runInference(bitmap)
            val endTime = System.nanoTime()
            lastInferenceTime = (endTime - startTime) / 1_000_000 // ms
            Log.d("LicensePlate", "License plate detection complete: ${lpResults.size} plate(s) detected")
            lpResults.map { result ->
                Detection(
                    x1 = result.x1,
                    y1 = result.y1,
                    x2 = result.x2,
                    y2 = result.y2,
                    confidence = result.confidence,
                    classId = result.classId,
                    label = result.label
                )
            }
        } catch (e: Exception) {
            Log.e("LicensePlate", "License plate detection inference failed", e)
            emptyList()
        }
    }

    private fun runPoseInference(bitmap: Bitmap): List<Detection> {
        val helper = poseEstimationHelper
        if (helper == null) {
            Log.e("PoseEstimation", "PoseEstimationHelper not initialized")
            return emptyList()
        }
        return try {
            val startTime = System.nanoTime()
            val (poseResults, visualizationBitmap) = helper.runInference(bitmap)
            val endTime = System.nanoTime()
            lastInferenceTime = (endTime - startTime) / 1_000_000 // ms
            Log.d("PoseEstimation", "Pose estimation complete: ${poseResults.size} persons detected")
            cachedPoseVisualization?.recycle()
            cachedPoseVisualization = visualizationBitmap
            poseResults.map { result ->
                Detection(
                    x1 = result.x1,
                    y1 = result.y1,
                    x2 = result.x2,
                    y2 = result.y2,
                    confidence = result.confidence,
                    classId = 0,
                    label = "person"
                )
            }
        } catch (e: Exception) {
            Log.e("PoseEstimation", "Pose estimation inference failed", e)
            emptyList()
        }
    }

    private fun runYoloxInference(interpreter: Interpreter, image: TensorImage): List<Detection> {
        val inputBuffer = image.tensorBuffer.buffer
        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputSize = outputShape.fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
        
        interpreter.run(inputBuffer, outputBuffer)
        
        outputBuffer.rewind()
        val outputArray = FloatArray(outputSize)
        outputBuffer.asFloatBuffer().get(outputArray)
        
        return parseYoloxOutput(outputArray)
    }

    private fun runSsdInference(interpreter: Interpreter, image: TensorImage): List<Detection> {
    val inputBuffer = image.tensorBuffer.buffer
    
    // Log input details for debugging
    Log.d("SsdDebug", "Input buffer size: ${inputBuffer.capacity()} bytes")
    Log.d("SsdDebug", "Expected input: ${interpreter.getInputTensor(0).shape().contentToString()}")
    Log.d("SsdDebug", "Input data type: ${interpreter.getInputTensor(0).dataType()}")
    
    // Check if this model actually has 4 outputs (SSD format)
    val numOutputs = interpreter.outputTensorCount
    Log.d("SsdDebug", "Model has $numOutputs output tensor(s)")
    
    if (numOutputs < 4) {
        Log.e("SsdDebug", "⚠️ Model doesn't have 4 outputs (has $numOutputs). This might not be an SSD model. Falling back to YOLO inference.")
        // Fall back to single-output inference (might be YOLO format)
        return runYoloxInference(interpreter, image)
    }
    
    // SSD MobileNet typically outputs in this order:
    // Output 0: locations [1, 10, 4] - bounding boxes
    // Output 1: classes [1, 10] - class indices
    // Output 2: scores [1, 10] - confidence scores
    // Output 3: numberOfDetections [1] - number of valid detections
    
    val locationsShape = interpreter.getOutputTensor(0).shape()
    val classesShape = interpreter.getOutputTensor(1).shape()
    val scoresShape = interpreter.getOutputTensor(2).shape()
    val numDetShape = interpreter.getOutputTensor(3).shape()
    
    Log.d("SsdDebug", "Locations shape: ${locationsShape.contentToString()}")
    Log.d("SsdDebug", "Classes shape: ${classesShape.contentToString()}")
    Log.d("SsdDebug", "Scores shape: ${scoresShape.contentToString()}")
    Log.d("SsdDebug", "NumDet shape: ${numDetShape.contentToString()}")
    
    // Calculate actual sizes based on FLOAT32 data type (4 bytes per float)
    val locationsSize = locationsShape.fold(1) { acc, i -> acc * i }
    val classesSize = classesShape.fold(1) { acc, i -> acc * i }
    val scoresSize = scoresShape.fold(1) { acc, i -> acc * i }
    val numDetSize = numDetShape.fold(1) { acc, i -> acc * i }
    
    // Allocate properly sized ByteBuffers
    val locations = ByteBuffer.allocateDirect(locationsSize * 4).order(ByteOrder.nativeOrder())
    val classes = ByteBuffer.allocateDirect(classesSize * 4).order(ByteOrder.nativeOrder())
    val scores = ByteBuffer.allocateDirect(scoresSize * 4).order(ByteOrder.nativeOrder())
    val numDet = ByteBuffer.allocateDirect(numDetSize * 4).order(ByteOrder.nativeOrder())
    
    // Create output map - IMPORTANT: indices must match tensor output order
    val outputs = mapOf(
        0 to locations,
        1 to classes,
        2 to scores,
        3 to numDet
    )
    
    // Run inference
    try {
        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        Log.d("SsdDebug", "Inference completed successfully")
    } catch (e: Exception) {
        Log.e("SsdDebug", "Inference error: ${e.message}")
        e.printStackTrace()
        return emptyList()
    }
    
    return parseSsdOutput(locations, classes, scores, numDet)
}

private fun parseSsdOutput(
    locationsBuffer: ByteBuffer, 
    classesBuffer: ByteBuffer, 
    scoresBuffer: ByteBuffer, 
    numDetBuffer: ByteBuffer
): List<Detection> {
    val detections = mutableListOf<Detection>()
    
    // Rewind all buffers to start
    locationsBuffer.rewind()
    classesBuffer.rewind()
    scoresBuffer.rewind()
    numDetBuffer.rewind()
    
    // Convert ByteBuffers to FloatArrays
    val locationsArray = FloatArray(locationsBuffer.remaining() / 4)
    val classesArray = FloatArray(classesBuffer.remaining() / 4)
    val scoresArray = FloatArray(scoresBuffer.remaining() / 4)
    
    locationsBuffer.asFloatBuffer().get(locationsArray)
    classesBuffer.asFloatBuffer().get(classesArray)
    scoresBuffer.asFloatBuffer().get(scoresArray)
    
    val numberOfDetections = numDetBuffer.float.toInt()
    
    Log.d("SsdDebug", "Number of detections: $numberOfDetections")
    Log.d("SsdDebug", "Scores array size: ${scoresArray.size}")
    if (scoresArray.isNotEmpty()) {
        Log.d("SsdDebug", "First 5 scores: ${scoresArray.take(5).joinToString { "%.3f".format(it) }}")
    }
    
    // Process detections
    for (i in 0 until minOf(numberOfDetections, scoresArray.size)) {
        val score = scoresArray[i]
        
        if (score >= CONFIDENCE_THRESHOLD) {
            // SSD outputs locations as [ymin, xmin, ymax, xmax] normalized to [0, 1]
            val boxIndex = i * 4
            val ymin = locationsArray[boxIndex] * INPUT_SIZE
            val xmin = locationsArray[boxIndex + 1] * INPUT_SIZE
            val ymax = locationsArray[boxIndex + 2] * INPUT_SIZE
            val xmax = locationsArray[boxIndex + 3] * INPUT_SIZE
            
            val classId = classesArray[i].toInt()
            val label = if (classId < labels.size) labels[classId] else "Unknown"
            
            Log.d("SsdDebug", "✓ Detection: $label ($classId) conf=%.2f%% at [%.0f,%.0f,%.0f,%.0f]".format(
                score * 100, xmin, ymin, xmax, ymax))
            
            detections.add(Detection(xmin, ymin, xmax, ymax, score, classId, label))
        }
    }
    
    Log.d("SsdDebug", "Total valid detections: ${detections.size}")
    return detections
}

    private var cachedSegmentationMask: Bitmap? = null
    
    private fun runSegmentationInference(bitmap: Bitmap): List<Detection> {
        val helper = segmentationHelper
        if (helper == null) {
            Log.e("Segmentation", "SegmentationHelper not initialized")
            return emptyList()
        }
        
        return try {
            val startTime = System.nanoTime()
            // Run segmentation inference on provided bitmap
            val (segResults, maskBitmap) = helper.runInference(bitmap)
            val endTime = System.nanoTime()
            lastInferenceTime = (endTime - startTime) / 1_000_000 // ms
            
            // Log results
            Log.d("Segmentation", "Segmentation complete: ${segResults.size} objects detected")
            
            // Cache mask for drawing (will be drawn in drawDetections)
            cachedSegmentationMask?.recycle()
            cachedSegmentationMask = maskBitmap
            
            // Convert SegmentationHelper.SegmentationResult to Detection
            // Mark these as segmentation detections by using a flag
            segResults.map { result ->
                Detection(
                    x1 = result.x1,
                    y1 = result.y1,
                    x2 = result.x2,
                    y2 = result.y2,
                    confidence = result.confidence,
                    classId = result.classId,
                    label = result.label
                )
            }
        } catch (e: Exception) {
            Log.e("Segmentation", "Segmentation inference failed", e)
            emptyList()
        }
    }
    
    private fun runBlinkDrowseInference(bitmap: Bitmap): List<Detection> {
        val helper = blinkDrowseHelper ?: return emptyList()
        return try {
            helper.runInference(bitmap).map { result ->
                Detection(result.x1, result.y1, result.x2, result.y2, result.confidence, result.classId, result.label)
            }
        } catch (e: Exception) {
            Log.e("BlinkDrowse", "Inference failed", e)
            emptyList()
        }
    }

    private fun runDentInference(bitmap: Bitmap): List<Detection> {
        val helper = dentHelper ?: return emptyList()
        return try {
            helper.runInference(bitmap).map { result ->
                Detection(result.x1, result.y1, result.x2, result.y2, result.confidence, result.classId, result.label)
            }
        } catch (e: Exception) {
            Log.e("Dent", "Inference failed", e)
            emptyList()
        }
    }

    private fun runFaceInference(bitmap: Bitmap): List<Detection> {
        val helper = faceHelper ?: return emptyList()
        return try {
            helper.runInference(bitmap).map { result ->
                Detection(result.x1, result.y1, result.x2, result.y2, result.confidence, result.classId, result.label)
            }
        } catch (e: Exception) {
            Log.e("Face", "Inference failed", e)
            emptyList()
        }
    }

    private fun runFireSmokeInference(bitmap: Bitmap): List<Detection> {
        val helper = fireSmokeHelper ?: return emptyList()
        return try {
            helper.runInference(bitmap).map { result ->
                Detection(result.x1, result.y1, result.x2, result.y2, result.confidence, result.classId, result.label)
            }
        } catch (e: Exception) {
            Log.e("FireSmoke", "Inference failed", e)
            emptyList()
        }
    }

    private fun runGroceryInference(bitmap: Bitmap): List<Detection> {
        val helper = groceryHelper ?: return emptyList()
        return try {
            helper.runInference(bitmap).map { result ->
                Detection(result.x1, result.y1, result.x2, result.y2, result.confidence, result.classId, result.label)
            }
        } catch (e: Exception) {
            Log.e("Grocery", "Inference failed", e)
            emptyList()
        }
    }

    private fun runHelmetInference(bitmap: Bitmap): List<Detection> {
        val helper = helmetHelper ?: return emptyList()
        return try {
            helper.runInference(bitmap).map { result ->
                Detection(result.x1, result.y1, result.x2, result.y2, result.confidence, result.classId, result.label)
            }
        } catch (e: Exception) {
            Log.e("Helmet", "Inference failed", e)
            emptyList()
        }
    }
    
    private fun drawSegmentationMask(maskBitmap: Bitmap) {
        try {
            val canvas = overlayView.holder.lockCanvas()
            if (canvas != null) {
                // Clear canvas
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                
                // Draw mask with transparency
                val maskPaint = Paint().apply {
                    alpha = 128 // 50% transparency
                }
                
                // Scale mask to overlay size
                val scaledMask = Bitmap.createScaledBitmap(
                    maskBitmap,
                    overlayView.width,
                    overlayView.height,
                    true
                )
                
                canvas.drawBitmap(scaledMask, 0f, 0f, maskPaint)
                scaledMask.recycle()
                
                overlayView.holder.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e("Segmentation", "Error drawing mask overlay", e)
        }
    }

    private enum class YoloOutputLayout { FEATURES_FIRST, ANCHORS_FIRST }

    private data class YoloOutputDims(
        val anchors: Int,
        val features: Int,
        val layout: YoloOutputLayout
    )

    private fun resolveYoloOutputDims(shape: IntArray?): YoloOutputDims? {
        if (shape == null || shape.isEmpty()) return null
        val dims = mutableListOf<Int>()
        for (i in 1 until shape.size) {
            val value = shape[i]
            if (value > 1) dims.add(value)
        }
        if (dims.size < 2) return null
        val dimA = dims[0]
        val dimB = dims[1]
        val isFeaturesFirst = dimA <= dimB
        val features = if (isFeaturesFirst) dimA else dimB
        val anchors = if (isFeaturesFirst) dimB else dimA
        val layout = if (isFeaturesFirst) YoloOutputLayout.FEATURES_FIRST else YoloOutputLayout.ANCHORS_FIRST
        if (features < 5 || anchors <= 0) return null
        return YoloOutputDims(anchors, features, layout)
    }

    private fun parseYoloxOutput(output: FloatArray): List<Detection> {
        val dims = resolveYoloOutputDims(OUTPUT_SHAPE)
        val detections = mutableListOf<Detection>()

        if (dims != null) {
            val (anchors, features, layout) = dims
            val anchorValues = FloatArray(features)
            val numClasses = maxOf(1, features - 5)

            for (anchor in 0 until anchors) {
                when (layout) {
                    YoloOutputLayout.FEATURES_FIRST -> {
                        for (featureIndex in 0 until features) {
                            val offset = featureIndex * anchors + anchor
                            anchorValues[featureIndex] = if (offset < output.size) output[offset] else 0f
                        }
                    }
                    YoloOutputLayout.ANCHORS_FIRST -> {
                        val base = anchor * features
                        if (base + features > output.size) continue
                        for (featureIndex in 0 until features) {
                            anchorValues[featureIndex] = output[base + featureIndex]
                        }
                    }
                }

                val objectness = anchorValues[4]
                if (objectness < CONFIDENCE_THRESHOLD) continue

                var bestClassScore = 0f
                var bestClassId = 0
                for (classIdx in 0 until numClasses) {
                    val idx = 5 + classIdx
                    val score = if (idx < anchorValues.size) anchorValues[idx] else 0f
                    if (score > bestClassScore) {
                        bestClassScore = score
                        bestClassId = classIdx
                    }
                }

                val confidence = objectness * bestClassScore
                if (confidence < CONFIDENCE_THRESHOLD) continue

                val xCenter = anchorValues[0]
                val yCenter = anchorValues[1]
                val width = anchorValues[2]
                val height = anchorValues[3]

                val x1 = xCenter - width / 2f
                val y1 = yCenter - height / 2f
                val x2 = xCenter + width / 2f
                val y2 = yCenter + height / 2f

                val label = if (bestClassId < labels.size) labels[bestClassId] else "Class $bestClassId"
                detections.add(Detection(x1, y1, x2, y2, confidence, bestClassId, label))
            }

            return applyNMS(detections, NMS_THRESHOLD)
        }

        // Fallback to legacy parsing for unexpected shapes
        val numValues = labels.size + 5
        if (numValues <= 0) return emptyList()
        val numPredictions = output.size / numValues

        for (i in 0 until numPredictions) {
            val offset = i * numValues
            if (offset + 4 >= output.size) break

            val xCenter = output[offset]
            val yCenter = output[offset + 1]
            val width = output[offset + 2]
            val height = output[offset + 3]
            val objectness = output[offset + 4]

            if (objectness < CONFIDENCE_THRESHOLD) continue

            var bestClassScore = 0f
            var bestClassId = 0
            val classSlots = numValues - 5
            for (c in 0 until classSlots) {
                val idx = offset + 5 + c
                if (idx >= output.size) break
                val classScore = output[idx]
                if (classScore > bestClassScore) {
                    bestClassScore = classScore
                    bestClassId = c
                }
            }

            val confidence = objectness * bestClassScore
            if (confidence < CONFIDENCE_THRESHOLD) continue

            val x1 = xCenter - width / 2f
            val y1 = yCenter - height / 2f
            val x2 = xCenter + width / 2f
            val y2 = yCenter + height / 2f

            val label = if (bestClassId < labels.size) labels[bestClassId] else "Class $bestClassId"
            detections.add(Detection(x1, y1, x2, y2, confidence, bestClassId, label))
        }

        return applyNMS(detections, NMS_THRESHOLD)
    }

    private fun applyNMS(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        
        val sortedDetections = detections.sortedByDescending { it.confidence }
        val keep = mutableListOf<Detection>()
        val suppressed = BooleanArray(sortedDetections.size) { false }
        
        for (i in sortedDetections.indices) {
            if (suppressed[i]) continue
            keep.add(sortedDetections[i])
            
            for (j in (i + 1) until sortedDetections.size) {
                if (suppressed[j]) continue
                if (sortedDetections[i].classId == sortedDetections[j].classId) {
                    val iou = calculateIoU(sortedDetections[i], sortedDetections[j])
                    if (iou > iouThreshold) suppressed[j] = true
                }
            }
        }
        
        return keep
    }

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

// Replace the existing drawDetections function with this enhanced version:
private fun drawDetections(detections: List<Detection>) {
    val canvas = overlayView.holder.lockCanvas()
    if (canvas != null) {
        try {
            val h = canvas.height.toFloat()
            val w = canvas.width.toFloat()
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val modelIndex = currentModelIndex
            val isPose = modelIndex?.let {
                availableModels.getOrNull(it)?.type?.lowercase()?.contains("pose")
            } ?: false

            if (isPose) {
                cachedPoseVisualization?.let { poseBitmap ->
                    val scaledPose = Bitmap.createScaledBitmap(
                        poseBitmap,
                        overlayView.width,
                        overlayView.height,
                        true
                    )
                    canvas.drawBitmap(scaledPose, 0f, 0f, null)
                    scaledPose.recycle()
                }
                paint.textSize = h / 50f
                paint.strokeWidth = h / 250f
                counts.clear()
                detections.forEach { detection ->
                    counts[detection.label] = (counts[detection.label] ?: 0) + 1
                }
                updateFps()
                overlayView.holder.unlockCanvasAndPost(canvas)
                return
            }

            // Draw segmentation mask first (if available)
            cachedSegmentationMask?.let { maskBitmap ->
                if (cachedScaledMask == null ||
                    cachedScaledMask!!.width != overlayView.width ||
                    cachedScaledMask!!.height != overlayView.height) {
                    cachedScaledMask?.recycle()
                    cachedScaledMask = Bitmap.createScaledBitmap(
                        maskBitmap,
                        overlayView.width,
                        overlayView.height,
                        false
                    )
                } else {
                    val canvas2 = Canvas(cachedScaledMask!!)
                    canvas2.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    val srcRect = Rect(0, 0, maskBitmap.width, maskBitmap.height)
                    val dstRect = Rect(0, 0, overlayView.width, overlayView.height)
                    val fastPaint = Paint().apply {
                        isFilterBitmap = false
                    }
                    canvas2.drawBitmap(maskBitmap, srcRect, dstRect, fastPaint)
                }
                val maskPaint = Paint().apply {
                    alpha = 128
                    isFilterBitmap = false
                }
                canvas.drawBitmap(cachedScaledMask!!, 0f, 0f, maskPaint)
            }

            paint.textSize = h / 50f
            paint.strokeWidth = h / 250f
            counts.clear()
            
            val modelType = modelIndex?.let {
                availableModels.getOrNull(it)?.type?.lowercase()
            } ?: ""
            
            val isSegmentation = modelType.contains("segmentation")
            val isLicensePlate = modelType.contains("license") || modelType.contains("lpd")
            
            // For static images, calculate the actual displayed image bounds
            var offsetX = 0f
            var offsetY = 0f
            var displayWidth = w
            var displayHeight = h
            
            if (!isInCameraMode && cachedBitmapWidth > 0 && cachedBitmapHeight > 0) {
                // ImageView scales the image to fit (CENTER_INSIDE behavior)
                val bitmapAspect = cachedBitmapWidth.toFloat() / cachedBitmapHeight.toFloat()
                val canvasAspect = w / h
                
                if (bitmapAspect > canvasAspect) {
                    // Image is wider - fit to width, add vertical padding
                    displayWidth = w
                    displayHeight = w / bitmapAspect
                    offsetY = (h - displayHeight) / 2f
                } else {
                    // Image is taller - fit to height, add horizontal padding
                    displayHeight = h
                    displayWidth = h * bitmapAspect
                    offsetX = (w - displayWidth) / 2f
                }
            }
            
            detections.forEach { detection ->
                val color = colors[detection.classId % colors.size]
                paint.color = color
                paint.style = Paint.Style.STROKE
                val left: Float
                val top: Float
                val right: Float
                val bottom: Float
                
                // Models that return coordinates in original bitmap space (not INPUT_SIZE space)
                if (isSegmentation || isLicensePlate) {
                    val bitmapWidth = if (cachedBitmapWidth > 0) cachedBitmapWidth.toFloat() else INPUT_SIZE.toFloat()
                    val bitmapHeight = if (cachedBitmapHeight > 0) cachedBitmapHeight.toFloat() else INPUT_SIZE.toFloat()
                    val scaleX = displayWidth / bitmapWidth
                    val scaleY = displayHeight / bitmapHeight
                    left = detection.x1 * scaleX + offsetX
                    top = detection.y1 * scaleY + offsetY
                    right = detection.x2 * scaleX + offsetX
                    bottom = detection.y2 * scaleY + offsetY
                } else {
                    // Standard YOLO models return coordinates in INPUT_SIZE space
                    val scaleX = displayWidth / INPUT_SIZE
                    val scaleY = displayHeight / INPUT_SIZE
                    left = detection.x1 * scaleX + offsetX
                    top = detection.y1 * scaleY + offsetY
                    right = detection.x2 * scaleX + offsetX
                    bottom = detection.y2 * scaleY + offsetY
                }
                canvas.drawRect(left, top, right, bottom, paint)
                counts[detection.label] = (counts[detection.label] ?: 0) + 1
                paint.style = Paint.Style.FILL
                val labelText = "${detection.label} ${String.format("%.0f%%", detection.confidence * 100)}"
                val textX = left
                val textY = top - 5f
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
                paint.color = Color.WHITE
                paint.alpha = 255
                canvas.drawText(labelText, textX, textY, paint)
            }
        } finally {
            overlayView.holder.unlockCanvasAndPost(canvas)
        }
    }
}


    override fun onDestroy() {
        super.onDestroy()
        close_camera()
        modelInterpreters.values.forEach { it.close() }
        modelInterpreters.clear()
        gpuDelegate?.close()
        cachedSegmentationMask?.recycle()
        cachedSegmentationMask = null
        cachedScaledMask?.recycle()
        cachedScaledMask = null
        reusableTensorImage = null
        poseEstimationHelper?.close()
        poseEstimationHelper = null
        cachedPoseVisualization?.recycle()
        cachedPoseVisualization = null
    }
    
    override fun onPause() {
        super.onPause()
        Log.d("Lifecycle", "onPause - closing camera")
        close_camera()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d("Lifecycle", "onResume - checking camera state")
        // Reopen camera if we have permission and surface is ready
        if (textureView.isAvailable && !isCameraOpen && 
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.d("Lifecycle", "Reopening camera")
            open_camera()
        }
    }

    @SuppressLint("MissingPermission")
    fun open_camera(){
        if (isCameraOpen) {
            Log.d("Camera", "Camera already open, skipping")
            return
        }
        
        try {
            Log.d("Camera", "Opening camera...")
            cameraManager.openCamera(cameraManager.cameraIdList[0], object:CameraDevice.StateCallback(){
                override fun onOpened(p0: CameraDevice) {
                    Log.d("Camera", "Camera opened successfully")
                    cameraDevice = p0
                    isCameraOpen = true
                    
                    try {
                        var surfaceTexture = textureView.surfaceTexture
                        if (surfaceTexture == null) {
                            Log.e("Camera", "Surface texture is null!")
                            isCameraOpen = false
                            p0.close()
                            return
                        }
                        
                        surfaceTexture.setDefaultBufferSize(1280, 720)
                        var surface = Surface(surfaceTexture)
                        var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        captureRequest.addTarget(surface)

                        cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                            override fun onConfigured(p0: CameraCaptureSession) {
                                captureSession = p0
                                try {
                                    p0.setRepeatingRequest(captureRequest.build(), null, null)
                                    Log.d("Camera", "Preview started successfully")
                                } catch (e: Exception) {
                                    Log.e("Camera", "Failed to start preview: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
                            override fun onConfigureFailed(p0: CameraCaptureSession) {
                                Log.e("Camera", "Session configuration failed")
                                isCameraOpen = false
                            }
                        }, handler)
                    } catch (e: Exception) {
                        Log.e("Camera", "Error creating capture session: ${e.message}")
                        e.printStackTrace()
                        isCameraOpen = false
                        p0.close()
                    }
                }
                override fun onDisconnected(p0: CameraDevice) {
                    Log.w("Camera", "Camera disconnected")
                    isCameraOpen = false
                    captureSession = null
                    p0.close()
                }
                override fun onError(p0: CameraDevice, p1: Int) {
                    Log.e("Camera", "Camera error: $p1")
                    isCameraOpen = false
                    captureSession = null
                    p0.close()
                }
            }, handler)
        } catch (e: Exception) {
            Log.e("Camera", "Exception opening camera: ${e.message}")
            e.printStackTrace()
            isCameraOpen = false
        }
    }
    
    fun close_camera() {
        if (!isCameraOpen) {
            Log.d("Camera", "Camera already closed")
            return
        }
        
        try {
            Log.d("Camera", "Closing camera...")
            captureSession?.close()
            captureSession = null
            
            if (::cameraDevice.isInitialized) {
                cameraDevice.close()
            }
            
            isCameraOpen = false
            Log.d("Camera", "Camera closed successfully")
        } catch (e: Exception) {
            Log.e("Camera", "Error closing camera: ${e.message}")
            e.printStackTrace()
            isCameraOpen = false
        }
    }

    fun get_permission(){
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            get_permission()
        } else if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("Permission", "Camera permission granted")
            // Permission granted, open camera if surface is ready
            if (textureView.isAvailable && !isCameraOpen) {
                open_camera()
            }
        }
    }
    
    private fun showLoadingDialog(message: String) {
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Loading")
            builder.setMessage(message)
            builder.setCancelable(false)
            loadingDialog = builder.create()
            loadingDialog?.show()
        }
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
            }, 800)
        }
    }
    
    private fun showErrorDialog(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun updateFps() {
        frameCounter++
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsTimestamp
        if (elapsed >= 1000) {
            currentFps = (frameCounter * 1000.0) / elapsed
            frameCounter = 0
            lastFpsTimestamp = now
            
            // Update FPS text only when FPS is recalculated (once per second)
            runOnUiThread {
                fpsTextView.text = String.format("FPS: %.1f | Inference: %d ms", currentFps, lastInferenceTime)
            }
        }
    }
    
    private fun updateFpsText() {
        // Removed - FPS text now updates in updateFps() to reduce overhead
    }

    private fun updateBottomDashboard() {
    val top2 = counts.entries.sortedByDescending { it.value }.take(2)
    
    runOnUiThread {
        if (top2.isNotEmpty()) {
            val (label1, cnt1) = top2[0]
            val emoji1 = labelToEmoji[label1] ?: "❓"
            dashboardEmoji1.text = emoji1
            dashboardCount1.text = cnt1.toString()
        } else {
            dashboardEmoji1.text = "🔍"
            dashboardCount1.text = "0"
        }
        
        if (top2.size >= 2) {
            val (label2, cnt2) = top2[1]
            val emoji2 = labelToEmoji[label2] ?: "❓"
            dashboardEmoji2.text = emoji2
            dashboardCount2.text = cnt2.toString()
        } else {
            dashboardEmoji2.text = "🔍"
            dashboardCount2.text = "0"
        }
    }
}

    private var dashboardDialog: AlertDialog? = null
    private var dashboardContainer: LinearLayout? = null
    
    private fun showExpandedDashboard() {
        if (dashboardDialog != null) {
            dashboardDialog?.show()
            return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32,32,32,32)
        }
        dashboardContainer = container
        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("📊 Current Frames Detected Objects Dashboard")
            .setView(container)
            .setPositiveButton("Close") { d, _ -> d.dismiss() }
        dashboardDialog = builder.create()
        dashboardDialog?.show()
        updateDashboardIfVisible()
    }
    
    private fun updateDashboardIfVisible() {
        val dialog = dashboardDialog ?: return
        if (!dialog.isShowing) return
        val container = dashboardContainer ?: return
        runOnUiThread {
            container.removeAllViews()
            if (counts.isEmpty()) {
                val tv = TextView(this@MainActivity).apply {
                    text = "🔍 No objects detected"
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(16, 32, 16, 32)
                    setTextColor(Color.GRAY)
                }
                container.addView(tv)
            } else {
                val header = TextView(this@MainActivity).apply {
                    text = "Live Detection (${counts.values.sum()} total)"
                    textSize = 14f
                    setTextColor(Color.GRAY)
                    setPadding(0, 0, 0, 16)
                }
                container.addView(header)
                
                counts.entries.sortedByDescending { it.value }.forEach { (label, cnt) ->
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(8, 12, 8, 12)
                    }

                    val emoji = labelToEmoji[label] ?: "❓"
                    
                    val emojiView = TextView(this@MainActivity).apply {
                        text = emoji
                        textSize = 32f
                        setPadding(0, 0, 16, 0)
                    }
                    
                    val countBadge = TextView(this@MainActivity).apply {
                        text = "× $cnt"
                        textSize = 20f
                        setTextColor(Color.WHITE)
                        setPadding(16, 8, 16, 8)
                        setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, 0)
                        }
                    }

                    row.addView(emojiView)
                    row.addView(countBadge)
                    container.addView(row)
                }
            }
        }
    }
    
    private fun showModelSelectionDialog() {
        if (availableModels.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Models Available")
                .setMessage("Failed to fetch models list. Please check your internet connection and restart the app.")
                .setPositiveButton("Retry") { _, _ ->
                    // Retry fetching model list
                    showLoadingDialog("Fetching available models...")
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            fetchModelList()
                            runOnUiThread {
                                dismissLoadingDialog("Models list loaded!")
                                showModelSelectionDialog()
                            }
                        } catch (e: Exception) {
                            Log.e("ModelAPI", "Error: ${e.message}")
                            runOnUiThread {
                                dismissLoadingDialog("Error: ${e.message}")
                                showErrorDialog("Failed to fetch model list. Check your connection.")
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        
        // Create custom list items showing model name and download status
        val modelItems = availableModels.map { model ->
            val typeTag = model.type.uppercase()
            if (model.isDownloaded) {
                "${model.name} ✓ [$typeTag]"
            } else {
                "${model.name} (Download) [$typeTag]"
            }
        }.toTypedArray()
        
        val currentSelection = currentModelIndex ?: -1
        
        AlertDialog.Builder(this)
            .setTitle("Select Detection Model")
            .setSingleChoiceItems(modelItems, currentSelection) { dialog, which ->
                dialog.dismiss()
                val selectedModel = availableModels[which]
                
                if (selectedModel.isDownloaded) {
                    // Model already downloaded, just load it
                    loadAndSwitchToModel(which)
                } else {
                    // Need to download the model first
                    downloadAndLoadModel(which)
                }
            }
            .setNeutralButton("Refresh List") { _, _ ->
                // Refresh model list from API
                showLoadingDialog("Refreshing model list...")
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        fetchModelList()
                        runOnUiThread {
                            dismissLoadingDialog("Models list updated!")
                            showModelSelectionDialog()
                        }
                    } catch (e: Exception) {
                        Log.e("ModelAPI", "Error: ${e.message}")
                        runOnUiThread {
                            dismissLoadingDialog("Error: ${e.message}")
                            showErrorDialog("Failed to refresh model list.")
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun loadAndSwitchToModel(modelIndex: Int) {
        val modelInfo = availableModels[modelIndex]
        
        showLoadingDialog("Loading ${modelInfo.name}...")
        
        GlobalScope.launch(Dispatchers.Main) {
            val success = withContext(Dispatchers.IO) {
                loadAndInitializeModel(modelInfo)
            }
            
            if (success) {
                currentModelIndex = modelIndex
                dismissLoadingDialog("${modelInfo.name} loaded!")
                
                // Open camera if not already open
                if (textureView.isAvailable && !this@MainActivity::cameraDevice.isInitialized) {
                    open_camera()

                }
                
                Log.d("ModelDebug", "✅ Switched to: ${modelInfo.name}")
                Log.d("ModelDebug", "✅ Input size: ${INPUT_WIDTH}x${INPUT_HEIGHT}")
                Log.d("ModelDebug", "✅ Output shape: ${OUTPUT_SHAPE?.contentToString()}")
            } else {
                dismissLoadingDialog("Failed to load ${modelInfo.name}")
                showErrorDialog("Failed to load ${modelInfo.name}. Please try again.")
            }
        }
    }
    
    private fun downloadAndLoadModel(modelIndex: Int) {
        val modelInfo = availableModels[modelIndex]
        
        showLoadingDialog("Downloading ${modelInfo.name}...")
        
        GlobalScope.launch(Dispatchers.Main) {
            val downloadSuccess = withContext(Dispatchers.IO) {
                downloadModelIfNeeded(modelInfo)
            }
            
            if (downloadSuccess) {
                updateLoadingDialog("Loading ${modelInfo.name}...")
                
                val loadSuccess = withContext(Dispatchers.IO) {
                    loadAndInitializeModel(modelInfo)
                }
                
                if (loadSuccess) {
                    currentModelIndex = modelIndex
                    dismissLoadingDialog("${modelInfo.name} ready!")
                    
                    // Open camera if not already open
                    if (textureView.isAvailable && !this@MainActivity::cameraDevice.isInitialized) {
                        open_camera()
                    }
                    
                    Log.d("ModelDebug", "✅ Downloaded and loaded: ${modelInfo.name}")
                    Log.d("ModelDebug", "✅ Input size: ${INPUT_WIDTH}x${INPUT_HEIGHT}")
                    Log.d("ModelDebug", "✅ Output shape: ${OUTPUT_SHAPE?.contentToString()}")
                } else {
                    dismissLoadingDialog("Failed to load ${modelInfo.name}")
                    showErrorDialog("Downloaded but failed to load ${modelInfo.name}. Please try again.")
                }
            } else {
                dismissLoadingDialog("Download failed")
                showErrorDialog("Failed to download ${modelInfo.name}. Please check your internet connection.")
            }
        }
    }
}