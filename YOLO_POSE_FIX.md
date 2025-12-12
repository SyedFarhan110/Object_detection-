# YOLO Pose Duplicate Bounding Boxes - FIX

## Problem
When using YOLO 11 Pose model for live streaming, **two different bounding boxes** were appearing:
1. **Blue box** (incorrect position) - drawn from detection coordinates returned by the inference
2. **Green box** (correct position) - drawn by the PoseEstimationHelper in the annotated bitmap

This caused duplicate and confusing visualization on screen.

## Root Cause
The issue occurred in the `drawDetectionsWithComposite()` function in `MainActivity.kt`:

```
runInference() 
  ↓ (for YOLO Pose)
  ├─ PoseEstimationHelper.runInference()
  │  └─ Returns: (poseResults, annotatedBitmap with GREEN boxes already drawn)
  │
  └─ Converts poseResults to Detection objects
     ↓
  Returns: InferenceResult(detections, annotatedBitmap)

drawDetectionsWithComposite()
  ├─ Draws annotatedBitmap (GREEN boxes) ✓
  └─ **Also draws ALL detections from the list (BLUE boxes)** ✗ ← THIS WAS THE PROBLEM
```

**The fix**: Since `annotatedBitmap` already contains perfectly drawn and positioned bounding boxes (green color with skeleton visualization), we should **NOT redraw** the detection boxes from the `detections` list.

## Solution Implemented

### Change 1: Clarified Code Comments in `runInference()`
Modified the "yolo 11 pose" case to add clear documentation:

```kotlin
"yolo 11 pose" -> {
    Log.d("Inference", "Using PoseEstimationHelper for ${currentModel.name}")
    if (poseHelper != null) {
        val bitmap = image.bitmap
        val (poseResults, poseBitmap) = poseHelper!!.runInference(bitmap)
        // Store annotated bitmap for overlay display
        annotatedBitmap = poseBitmap
        // Convert pose results to standard Detection format for counting only
        // NOTE: When annotatedBitmap is present, these detections will NOT be drawn
        // to avoid duplicate bounding boxes. The bounding boxes in annotatedBitmap
        // are already properly positioned and styled (green color with skeleton).
        val detections = poseResults.map { result ->
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
        InferenceResult(detections, poseBitmap)
    }
    // ... fallback
}
```

### Change 2: Modified `drawDetectionsWithComposite()` Function
**Completely refactored** to skip drawing detection boxes when an annotated bitmap is present:

**BEFORE:**
```kotlin
private fun drawDetectionsWithComposite(detections: List<Detection>, annotatedBitmap: Bitmap, cameraFrame: Bitmap) {
    // ... draw canvas setup ...
    canvas.drawBitmap(annotatedBitmap, annotRect, canvasRect, null)
    
    // ✗ PROBLEM: This loop was drawing ALL detection boxes again!
    detections.forEach { detection ->
        canvas.drawRect(left, top, right, bottom, paint)  // Blue box
        canvas.drawText(labelText, textX, textY, paint)   // Label
    }
}
```

**AFTER:**
```kotlin
private fun drawDetectionsWithComposite(detections: List<Detection>, annotatedBitmap: Bitmap, cameraFrame: Bitmap) {
    // ... draw canvas setup ...
    canvas.drawBitmap(annotatedBitmap, annotRect, canvasRect, null)
    
    counts.clear()
    
    // ✓ FIX: Only count detections for dashboard, don't redraw them
    // The annotatedBitmap already contains all visualizations (boxes + skeleton)
    detections.forEach { detection ->
        counts[detection.label] = (counts[detection.label] ?: 0) + 1
    }
}
```

## Benefits of This Fix

1. **Eliminates Duplicate Boxes**: Only the green boxes from the annotated bitmap are visible
2. **Preserves Counting**: Detection counts still work for the dashboard/statistics
3. **Correct Positioning**: Uses the PoseEstimationHelper's proper coordinate transformation
4. **Clean Visualization**: Shows skeleton with proper pose visualization, not conflicting boxes
5. **Consistent with Segmentation**: Segmentation models also use annotated bitmaps the same way

## Visual Result

**Before Fix:**
```
Green box (correct) + Blue box (wrong position) = Confusing double visualization
```

**After Fix:**
```
Green box only with skeleton visualization = Clean, correct detection
```

## Testing
To verify the fix works:
1. Load a YOLO 11 Pose model
2. Point camera at people
3. Observe: Only **ONE green bounding box** per person with skeleton overlay
4. No duplicate blue boxes should appear
5. Dashboard count should show correct number of detected persons

## Files Modified
- `app/src/main/java/com/programminghut/realtime_object/ui/MainActivity.kt`
  - Function: `runInference()` - clarified comments
  - Function: `drawDetectionsWithComposite()` - removed duplicate box drawing

