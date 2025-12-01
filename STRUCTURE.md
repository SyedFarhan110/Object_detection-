# Project Structure Visualization

## Directory Tree

```
app/src/main/java/com/programminghut/realtime_object/
│
├── 📱 ui/                              # USER INTERFACE LAYER
│   └── MainActivity.kt                 # Main screen with camera & controls
│
├── 📊 models/                          # DATA MODELS
│   └── ModelInfo.kt                    # Model metadata structure
│
├── 🤖 helpers/                         # DETECTION BACKEND
│   ├── BaseDetectionHelper.kt          # Common interface
│   ├── LicensePlateDetectionHelper.kt  # License plate detection
│   ├── PoseEstimationHelper.kt         # Pose estimation
│   └── SegmentationHelper.kt           # Instance segmentation
│
├── ⚙️ managers/                        # BUSINESS LOGIC
│   └── ModelManager.kt                 # Model operations & caching
│
└── 🛠️ utils/                           # UTILITIES
    ├── DashboardManager.kt             # Statistics dashboard
    ├── DialogManager.kt                # Dialog operations
    └── FPSCalculator.kt                # FPS calculation

```

## Component Interaction Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                         USER                                 │
│                           ↓                                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    UI LAYER (ui/)                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              MainActivity.kt                        │   │
│  │  • Camera preview & controls                        │   │
│  │  • User input handling                              │   │
│  │  • Display results                                  │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
         │           │           │              │
         ↓           ↓           ↓              ↓
    ┌────────┐  ┌────────┐  ┌──────────┐  ┌────────┐
    │ Dialog │  │Dashboard│  │   FPS    │  │ Model  │
    │Manager │  │ Manager │  │Calculator│  │Manager │
    └────────┘  └────────┘  └──────────┘  └────────┘
    (utils/)    (utils/)     (utils/)      (managers/)
                                               │
                                               ↓
┌──────────────────────────────────────────────────────────────┐
│               BACKEND LAYER (helpers/)                        │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         BaseDetectionHelper (Interface)              │   │
│  └──────────────────────────────────────────────────────┘   │
│         │              │                  │                  │
│         ↓              ↓                  ↓                  │
│  ┌─────────────┐ ┌────────────┐ ┌──────────────────┐       │
│  │  License    │ │   Pose     │ │  Segmentation    │       │
│  │   Plate     │ │ Estimation │ │     Helper       │       │
│  │  Detection  │ │   Helper   │ │                  │       │
│  └─────────────┘ └────────────┘ └──────────────────┘       │
└──────────────────────────────────────────────────────────────┘
                         │
                         ↓
┌──────────────────────────────────────────────────────────────┐
│                    DATA LAYER (models/)                       │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              ModelInfo.kt                            │   │
│  │  • Model metadata                                    │   │
│  │  • Download status                                   │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

## Data Flow

```
Camera Frame
     │
     ↓
MainActivity (UI)
     │
     ├──→ FPSCalculator.updateFPS()
     │
     ├──→ ModelManager.getCurrentInterpreter()
     │         ↓
     │    [TensorFlow Lite Model]
     │
     ↓
Detection Helper
     │
     ├──→ Preprocess Image
     ├──→ Run Inference
     ├──→ Post-process (NMS, Filtering)
     └──→ Return Results
          ↓
     MainActivity (UI)
          │
          ├──→ Draw on Canvas
          ├──→ DashboardManager.updateCounts()
          └──→ Display FPS
```

## Responsibility Matrix

| Component | Read Data | Process Data | Display UI | Network | File I/O |
|-----------|-----------|--------------|------------|---------|----------|
| **MainActivity** | ✓ | ✗ | ✓ | ✗ | ✗ |
| **ModelManager** | ✓ | ✗ | ✗ | ✓ | ✓ |
| **Detection Helpers** | ✓ | ✓ | ✗ | ✗ | ✗ |
| **DashboardManager** | ✓ | ✗ | ✓ | ✗ | ✗ |
| **DialogManager** | ✗ | ✗ | ✓ | ✗ | ✗ |
| **FPSCalculator** | ✗ | ✓ | ✗ | ✗ | ✗ |
| **ModelInfo** | ✓ | ✗ | ✗ | ✗ | ✗ |

## Package Dependencies

```
ui/
 │
 ├─→ models/        (imports ModelInfo)
 ├─→ helpers/       (imports detection helpers)
 ├─→ managers/      (imports ModelManager)
 └─→ utils/         (imports all utilities)

managers/
 │
 └─→ models/        (imports ModelInfo)

helpers/
 │
 └─→ (no dependencies on other app packages)

utils/
 │
 └─→ (no dependencies on other app packages)

models/
 │
 └─→ (no dependencies - pure data)
```

## Layer Communication Rules

1. **UI Layer** → Can call any layer
2. **Managers Layer** → Can call Models, but NOT UI
3. **Helpers Layer** → Independent, no app package dependencies
4. **Utils Layer** → Independent, no app package dependencies
5. **Models Layer** → Pure data, no dependencies

## File Size Comparison (Before vs After)

### Before Modularization
```
MainActivity.kt: ~2114 lines (monolithic)
Total: 1 large file
```

### After Modularization
```
ui/MainActivity.kt:           ~1800 lines (still needs refactoring)
managers/ModelManager.kt:      ~250 lines
helpers/BaseDetectionHelper.kt: ~50 lines
helpers/LicensePlate*.kt:      ~470 lines
helpers/PoseEstimation*.kt:    ~430 lines
helpers/Segmentation*.kt:      ~550 lines
utils/DashboardManager.kt:     ~150 lines
utils/DialogManager.kt:         ~100 lines
utils/FPSCalculator.kt:         ~50 lines
models/ModelInfo.kt:            ~15 lines

Total: 10 well-organized files
Average: ~385 lines per file (more manageable)
```

## Key Improvements

✅ **Separation of Concerns** - Each package has a clear purpose
✅ **Single Responsibility** - Each class does one thing well
✅ **Dependency Management** - Clear dependency hierarchy
✅ **Testability** - Components can be tested independently
✅ **Maintainability** - Easy to find and modify code
✅ **Scalability** - Easy to add new features
✅ **Reusability** - Utils and helpers can be reused

## Next Steps for Further Improvement

1. **Extract Camera Logic** from MainActivity to CameraManager
2. **Reduce MainActivity** size by extracting more UI logic
3. **Add ViewModel** for better state management
4. **Implement Repository Pattern** for data operations
5. **Add Dependency Injection** (Hilt/Dagger)
6. **Create Unit Tests** for each component
