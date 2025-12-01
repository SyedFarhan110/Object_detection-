# Quick Reference Guide - Modular Architecture

## 📁 Package Overview

### 🎨 UI Package (`ui/`)
**Purpose:** User interface components
- `MainActivity.kt` - Main activity with camera and controls

### 📊 Models Package (`models/`)
**Purpose:** Data structures
- `ModelInfo.kt` - Model metadata (name, URLs, download status)

### 🤖 Helpers Package (`helpers/`)
**Purpose:** Detection algorithms
- `BaseDetectionHelper.kt` - Common interface for all detectors
- `LicensePlateDetectionHelper.kt` - License plate detection
- `PoseEstimationHelper.kt` - Pose estimation with keypoints
- `SegmentationHelper.kt` - Instance segmentation

### ⚙️ Managers Package (`managers/`)
**Purpose:** Business logic orchestration
- `ModelManager.kt` - Model download, loading, and caching

### 🛠️ Utils Package (`utils/`)
**Purpose:** Reusable utilities
- `DashboardManager.kt` - Detection statistics dashboard
- `DialogManager.kt` - Dialog operations (loading, error, etc.)
- `FPSCalculator.kt` - Frame rate calculation

---

## 🔄 Common Usage Patterns

### Initialize ModelManager
```kotlin
val modelManager = ModelManager(context)
modelManager.onLoadingProgressUpdate = { msg -> 
    // Update UI with progress
}
modelManager.onModelLoaded = { modelInfo -> 
    // Model loaded successfully
}
```

### Fetch and Load Models
```kotlin
// Fetch available models
val models = modelManager.fetchModelList()

// Download if needed
modelManager.downloadModelIfNeeded(models[0])

// Load model
modelManager.loadAndInitializeModel(models[0])
```

### Use Detection Helpers
```kotlin
// License Plate Detection
val lpHelper = LicensePlateDetectionHelper(context)
lpHelper.initialize(modelFile, labelsFile, useGpu = true)
val results = lpHelper.runInference(bitmap)

// Pose Estimation
val poseHelper = PoseEstimationHelper(context)
poseHelper.initialize(modelFile, labelsFile)
val (poseResults, visualization) = poseHelper.runInference(bitmap)

// Segmentation
val segHelper = SegmentationHelper(context)
segHelper.initialize(modelFile, labelsFile)
val segResults = segHelper.runInference(bitmap)
```

### Manage Dialogs
```kotlin
val dialogManager = DialogManager(context)

// Loading dialog
dialogManager.showLoadingDialog("Loading model...")
dialogManager.updateLoadingDialog("Downloading... 5MB")
dialogManager.dismissLoadingDialog()

// Error dialog
dialogManager.showErrorDialog("Failed to load model")

// Confirmation
dialogManager.showConfirmationDialog(
    "Delete Model?",
    "Are you sure?",
    onConfirm = { /* delete */ }
)
```

### Calculate FPS
```kotlin
val fpsCalculator = FPSCalculator()

// In your frame processing loop
fpsCalculator.updateFPSDisplay(fpsTextView)

// Or get value directly
val currentFPS = fpsCalculator.getCurrentFPS()
```

### Manage Dashboard
```kotlin
val dashboardManager = DashboardManager(context)

// Update counts
val counts = mapOf("car" to 5, "person" to 3)
dashboardManager.updateCounts(counts)

// Show dashboard
dashboardManager.showDashboard()

// Get emoji for label
val emoji = dashboardManager.getEmojiForLabel("car") // 🚗
```

---

## 📦 Import Statements

### For Activities
```kotlin
import com.programminghut.Object_Detection.ui.MainActivity
```

### For Models
```kotlin
import com.programminghut.Object_Detection.models.ModelInfo
```

### For Helpers
```kotlin
import com.programminghut.Object_Detection.helpers.BaseDetectionHelper
import com.programminghut.Object_Detection.helpers.LicensePlateDetectionHelper
import com.programminghut.Object_Detection.helpers.PoseEstimationHelper
import com.programminghut.Object_Detection.helpers.SegmentationHelper
```

### For Managers
```kotlin
import com.programminghut.Object_Detection.managers.ModelManager
```

### For Utils
```kotlin
import com.programminghut.Object_Detection.utils.DashboardManager
import com.programminghut.Object_Detection.utils.DialogManager
import com.programminghut.Object_Detection.utils.FPSCalculator
```

---

## 🎯 Design Principles Applied

### 1. Single Responsibility Principle
Each class has one clear purpose:
- `ModelManager` → Manage models only
- `DialogManager` → Handle dialogs only
- `FPSCalculator` → Calculate FPS only

### 2. Separation of Concerns
- UI logic in `ui/`
- Business logic in `managers/`
- Detection algorithms in `helpers/`
- Utilities in `utils/`
- Data in `models/`

### 3. Dependency Inversion
- `BaseDetectionHelper` interface allows polymorphism
- UI depends on abstractions, not concrete implementations

### 4. Open/Closed Principle
- Easy to add new detection helpers by implementing `BaseDetectionHelper`
- Easy to add new utilities without modifying existing code

---

## 🚀 Adding New Features

### Add a New Detection Model
1. Implement `BaseDetectionHelper` interface
2. Create new file in `helpers/` package
3. Implement required methods:
   - `initialize()`
   - `detect()`
   - `draw()`
   - `close()`

Example:
```kotlin
package com.programminghut.Object_Detection.helpers

class FaceDetectionHelper(context: Context) : BaseDetectionHelper {
    override fun initialize(modelFile: File, labelsFile: File?, useGpu: Boolean) {
        // Initialize TFLite model
    }
    
    override fun detect(bitmap: Bitmap): Any {
        // Run inference
    }
    
    override fun draw(canvas: Canvas, results: Any, w: Int, h: Int) {
        // Draw results
    }
    
    override fun close() {
        // Cleanup
    }
    
    override fun isInitialized(): Boolean = /* check */
    override fun getInputSize(): Pair<Int, Int> = Pair(640, 640)
}
```

### Add a New Utility
1. Create new file in `utils/` package
2. Keep it stateless or manage internal state carefully
3. Document public API clearly

### Add a New Manager
1. Create new file in `managers/` package
2. Inject dependencies via constructor
3. Use callbacks for async operations

---

## 📝 Best Practices

### DO ✅
- Keep classes focused on single responsibility
- Use dependency injection (pass dependencies via constructor)
- Document public APIs
- Handle errors gracefully
- Clean up resources in `close()` methods

### DON'T ❌
- Don't mix UI and business logic
- Don't create circular dependencies
- Don't use hard-coded values (use constants)
- Don't forget to update AndroidManifest.xml for new activities
- Don't leak contexts or resources

---

## 🐛 Troubleshooting

### Import Errors
**Problem:** Cannot resolve imports after restructuring
**Solution:** Update imports to use new package structure:
```kotlin
// Old
import com.programminghut.Object_Detection.MainActivity

// New
import com.programminghut.Object_Detection.ui.MainActivity
```

### Activity Not Found
**Problem:** App crashes with "Activity not found"
**Solution:** Update AndroidManifest.xml:
```xml
<activity android:name=".ui.MainActivity" ...>
```

### Model Loading Fails
**Problem:** Model fails to load after restructuring
**Solution:** Check that `ModelManager` is properly initialized and model files exist in correct location

---

## 📚 Documentation Files

- **ARCHITECTURE.md** - Detailed architecture documentation
- **STRUCTURE.md** - Visual diagrams and structure info
- **QUICK_REFERENCE.md** - This file (quick guide)

---

## 🎓 Learning Resources

### Understanding the Architecture
1. Read `ARCHITECTURE.md` for detailed overview
2. Review `STRUCTURE.md` for visual diagrams
3. Explore code starting from `MainActivity.kt`
4. Follow data flow: UI → Manager → Helper → Results

### Code Navigation Tips
- Start in `ui/MainActivity.kt` to understand user flow
- Check `managers/ModelManager.kt` for model operations
- Review `helpers/` to understand detection algorithms
- Explore `utils/` for reusable components

---

**Last Updated:** December 2025
**Version:** 1.0
