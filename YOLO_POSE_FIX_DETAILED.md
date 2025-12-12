# YOLO Pose Duplicate Boxes - Code Flow Diagram

## THE PROBLEM: DUPLICATE BOUNDING BOXES

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Live Stream Frame Processing                      │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
                    ┌───────────────────────────┐
                    │  runInference(bitmap)     │
                    │  - Calls PoseHelper       │
                    │  - Gets poses + annotated │
                    └───────────────────────────┘
                                    │
                 ┌──────────────────┴──────────────────┐
                 ▼                                      ▼
    ┌──────────────────────────┐        ┌──────────────────────────┐
    │   poseResults List       │        │   annotatedBitmap        │
    │ [PoseResult, ...]        │        │ (Already has GREEN boxes │
    │                          │        │  + skeleton drawn)       │
    │ Converted to Detection   │        │                          │
    │ objects for counting     │        │ ✓ Correct positioning    │
    └──────────────────────────┘        │ ✓ Green color            │
                 │                       │ ✓ Skeleton overlay       │
                 │                       └──────────────────────────┘
                 │
    ┌────────────┴────────────┐
    ▼                          ▼
InferenceResult()
├─ detections: [List]
└─ annotatedBitmap: Bitmap


┌─────────────────────────────────────────────────────────────────────┐
│           drawDetectionsWithComposite(detections, annotBitmap)       │
└─────────────────────────────────────────────────────────────────────┘

BEFORE FIX ❌:
    canvas.drawBitmap(annotatedBitmap)     ← Draw GREEN boxes ✓
    detections.forEach { detection ->
        canvas.drawRect(...)               ← Draw BLUE boxes  ✗ (DUPLICATE!)
        canvas.drawText(...)               ← Draw labels again ✗
    }
    
    RESULT: 2 sets of boxes visible!
    ┌────────┐
    │ BLUE   │ ← Wrong: From raw detection coordinates
    │ GREEN  │ ← Correct: From PoseEstimationHelper
    └────────┘
    Confusing and overlapping!


AFTER FIX ✓:
    canvas.drawBitmap(annotatedBitmap)     ← Draw GREEN boxes ✓
    detections.forEach { detection ->
        counts[detection.label]++          ← Count for dashboard ✓
        // NO DRAWING HERE!
    }
    
    RESULT: Only 1 set of boxes visible!
    ┌────────┐
    │ GREEN  │ ← Correct: From PoseEstimationHelper
    └────────┘
    Clean and correct!
```

## Data Flow Comparison

### BEFORE (With Bug):
```
Camera Frame
     ↓
Pose Detection Model
     ├─ Returns: poseResults + annotatedBitmap(GREEN boxes)
     ├─ Convert to Detection list
     └─ InferenceResult(detections=[...], annotBitmap=GREEN)
          ↓
    drawDetectionsWithComposite()
          ├─ Draw annotatedBitmap           → Shows GREEN boxes ✓
          └─ Loop through detections array  → Shows BLUE boxes  ✗
               ↓
    CANVAS RESULT: 2 boxes per detection ❌
```

### AFTER (Fixed):
```
Camera Frame
     ↓
Pose Detection Model
     ├─ Returns: poseResults + annotatedBitmap(GREEN boxes)
     ├─ Convert to Detection list (for counting only)
     └─ InferenceResult(detections=[...], annotBitmap=GREEN)
          ↓
    drawDetectionsWithComposite()
          ├─ Draw annotatedBitmap           → Shows GREEN boxes ✓
          └─ Count detections only         → Updates dashboard  ✓
               ↓
    CANVAS RESULT: 1 box per detection ✓
```

## Why This Fix Works

| Aspect | BEFORE | AFTER |
|--------|--------|-------|
| **Visual Display** | 2 boxes per person (blue + green) ❌ | 1 box per person (green only) ✓ |
| **Color Accuracy** | Blue (wrong) boxes visible | Green boxes from helper (correct) |
| **Positioning** | Blue boxes off-position | Green boxes perfectly positioned |
| **Skeleton Overlay** | Covered by duplicate blue box | Fully visible and clean |
| **Dashboard Counting** | Would count twice if not careful | Counts once, correctly ✓ |
| **Performance** | Extra drawing calls | Reduced drawing calls ✓ |

## Technical Explanation

### Why PoseEstimationHelper Draws Better Boxes:

1. **Proper Coordinate Transformation**
   - Helper has access to original bitmap dimensions
   - Correctly scales from model coordinates → bitmap coordinates

2. **Specialized Drawing**
   - Draws green boxes (not blue)
   - Adds skeleton connections
   - Adds keypoint circles
   - All in one properly-transformed coordinate space

### Why Detection List Coordinates Were Wrong:

1. **Coordinate Space Mismatch**
   - Detections returned as absolute pixel coordinates from helper
   - Canvas uses different coordinate system (screen space)
   - Scaling calculations in drawDetectionsWithComposite() were approximate
   - Lead to offset/misaligned blue boxes

2. **Redundant Drawing**
   - Same information (bounding boxes) already in annotatedBitmap
   - Drawing twice served no purpose
   - Only added confusion and visual clutter

## Summary

**The core issue**: Attempting to draw detection boxes that were already perfectly rendered in the annotated bitmap.

**The solution**: Trust the annotated bitmap for all visual rendering (boxes + skeleton), and only use the detection list for statistics/counting.

