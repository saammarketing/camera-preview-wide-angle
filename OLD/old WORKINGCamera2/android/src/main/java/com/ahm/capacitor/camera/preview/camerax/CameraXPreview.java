package com.ahm.capacitor.camera.preview.camerax;

import android.content.Context;
import android.graphics.Matrix;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.RelativeLayout;

import androidx.camera.core.CameraSelector;
import androidx.camera.view.PreviewView;

import java.io.File;

/**
 * CameraXPreview replaces the legacy Preview class with CameraX PreviewView
 * and provides modern camera preview functionality with gesture support.
 */
public class CameraXPreview extends RelativeLayout {
    private static final String TAG = "CameraXPreview";
    
    private PreviewView previewView;
    private CameraXManager cameraManager;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    
    // Camera state
    private boolean isPreviewActive = false;
    private float currentZoom = 1.0f;
    private float opacity = 1.0f;
    
    // Gesture handling
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private boolean isDragging = false;
    
    // Configuration
    private boolean enableOpacity = false;
    private boolean enableZoom = false;
    private boolean tapToFocus = true;
    private boolean tapToTakePicture = false;
    private boolean dragEnabled = false;
    
    public interface PreviewCallback {
        void onTapToFocus(float x, float y);
        void onTapToTakePicture(float x, float y);
        void onZoomChanged(float zoomLevel);
        void onDragChanged(float deltaX, float deltaY);
    }
    
    private PreviewCallback callback;
    
    public CameraXPreview(Context context) {
        super(context);
        init(context);
    }
    
    public CameraXPreview(Context context, boolean enableOpacity) {
        super(context);
        this.enableOpacity = enableOpacity;
        init(context);
    }
    
    private void init(Context context) {
        // Create PreviewView
        previewView = new PreviewView(context);
        previewView.setImplementationMode(PreviewView.ImplementationMode.PERFORMANCE);
        
        // Set layout parameters
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        );
        addView(previewView, params);
        
        // Initialize camera manager
        cameraManager = new CameraXManager(context);
        cameraManager.setCallback(new CameraXManager.CameraXCallback() {
            @Override
            public void onCameraStarted() {
                isPreviewActive = true;
                Log.d(TAG, "Camera preview started");
            }
            
            @Override
            public void onCameraError(String error) {
                isPreviewActive = false;
                Log.e(TAG, "Camera preview error: " + error);
            }
            
            @Override
            public void onImageCaptured(File imageFile) {
                Log.d(TAG, "Image captured: " + imageFile.getAbsolutePath());
            }
            
            @Override
            public void onImageCaptureError(String error) {
                Log.e(TAG, "Image capture error: " + error);
            }
            
            @Override
            public void onVideoRecordingStarted() {
                Log.d(TAG, "Video recording started");
            }
            
            @Override
            public void onVideoRecordingStopped(File videoFile) {
                Log.d(TAG, "Video recording stopped: " + videoFile.getAbsolutePath());
            }
            
            @Override
            public void onVideoRecordingError(String error) {
                Log.e(TAG, "Video recording error: " + error);
            }
        });
        
        // Setup gesture detection
        setupGestureDetection();
        
        Log.d(TAG, "CameraXPreview initialized");
    }

    /**
     * Inject a shared CameraXManager so callbacks propagate to the fragment listener.
     */
    public void setCameraManager(CameraXManager manager) {
        if (manager != null) {
            this.cameraManager = manager;
            Log.d(TAG, "Camera manager injected from activity");
        }
    }
    
    private void setupGestureDetection() {
        // Single tap gesture detector
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                handleSingleTap(e.getX(), e.getY());
                return true;
            }
        });
        
        // Enhanced scale gesture detector with better emulator support
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private float initialZoom = 1.0f;
            private long lastGestureTime = 0;
            private static final long GESTURE_DEBOUNCE_MS = 16; // ~60fps
            
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                // Debounce rapid gestures
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastGestureTime < GESTURE_DEBOUNCE_MS) {
                    return false;
                }
                lastGestureTime = currentTime;
                
                initialZoom = currentZoom;
                Log.d(TAG, "Enhanced pinch zoom started, current zoom: " + currentZoom);
                return enableZoom;
            }
            
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (!enableZoom || cameraManager == null) {
                    return false;
                }
                
                float scaleFactor = detector.getScaleFactor();
                
                // Apply sensitivity adjustment for better control
                float adjustedScaleFactor = applySensitivityAdjustment(scaleFactor);
                float newZoom = currentZoom * adjustedScaleFactor;
                
                Log.d(TAG, "Enhanced pinch zoom: scaleFactor=" + scaleFactor + 
                      ", adjusted=" + adjustedScaleFactor + 
                      ", currentZoom=" + currentZoom + ", newZoom=" + newZoom);
                
                // Use enhanced smooth zoom with better emulator support
                setSmoothZoom(newZoom);
                return true;
            }
            
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                Log.d(TAG, "Enhanced pinch zoom ended, final zoom: " + currentZoom);
            }
            
            /**
             * Apply sensitivity adjustment for better zoom control
             */
            private float applySensitivityAdjustment(float scaleFactor) {
                // More responsive sensitivity for better zoom control
                float sensitivity = 1.0f; // Full sensitivity for real devices
                
                // Only reduce sensitivity for emulators
                if (isEmulatorEnvironment()) {
                    sensitivity = 0.7f; // Less aggressive reduction
                }
                
                float adjusted = 1.0f + ((scaleFactor - 1.0f) * sensitivity);
                return Math.max(0.1f, Math.min(5.0f, adjusted)); // Allow wider zoom range
            }
            
            /**
             * Detect emulator environment
             */
            private boolean isEmulatorEnvironment() {
                try {
                    String product = android.os.Build.PRODUCT.toLowerCase();
                    String model = android.os.Build.MODEL.toLowerCase();
                    String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();
                    
                    return product.contains("sdk") || 
                           product.contains("emulator") || 
                           model.contains("emulator") ||
                           manufacturer.contains("genymotion") ||
                           android.os.Build.FINGERPRINT.startsWith("generic");
                } catch (Exception e) {
                    return false;
                }
            }
        });
        
        // Touch listener for all gestures
        setOnTouchListener((v, event) -> {
            boolean handled = false;
            
            // Handle scale gestures first
            handled = scaleGestureDetector.onTouchEvent(event);
            
            // Handle single tap gestures
            if (!handled) {
                handled = gestureDetector.onTouchEvent(event);
            }
            
            // Handle drag gestures
            if (!handled && dragEnabled) {
                handled = handleDragGesture(event);
            }
            
            return handled;
        });
    }
    
    private boolean handleDragGesture(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isDragging = false;
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - lastTouchX) > 10 || Math.abs(event.getY() - lastTouchY) > 10) {
                    isDragging = true;
                    float deltaX = event.getX() - lastTouchX;
                    float deltaY = event.getY() - lastTouchY;
                    
                    if (callback != null) {
                        callback.onDragChanged(deltaX, deltaY);
                    }
                    
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                }
                break;
                
            case MotionEvent.ACTION_UP:
                isDragging = false;
                break;
        }
        return isDragging;
    }
    
    private void handleSingleTap(float x, float y) {
        if (tapToFocus) {
            // Convert screen coordinates to camera coordinates
            float normalizedX = x / getWidth();
            float normalizedY = y / getHeight();
            
            // Set focus point
            cameraManager.setFocusPoint(normalizedX, normalizedY);
            
            if (callback != null) {
                callback.onTapToFocus(x, y);
            }
            
            Log.d(TAG, "Tap to focus at: " + x + ", " + y);
        }
        
        if (tapToTakePicture) {
            if (callback != null) {
                callback.onTapToTakePicture(x, y);
            }
            
            Log.d(TAG, "Tap to take picture at: " + x + ", " + y);
        }
    }
    
    /**
     * Start camera preview
     */
    public void startPreview() {
        startPreview(CameraSelector.DEFAULT_BACK_CAMERA);
    }
    
    /**
     * Start camera preview with specific camera selector
     */
    public void startPreview(CameraSelector selector) {
        if (cameraManager != null) {
            cameraManager.startCamera(previewView, selector);
        }
    }
    
    /**
     * Stop camera preview
     */
    public void stopPreview() {
        if (cameraManager != null) {
            cameraManager.unbindCameraUseCases();
            isPreviewActive = false;
        }
    }
    
    /**
     * Switch camera
     */
    public void switchCamera(CameraSelector selector) {
        if (cameraManager != null) {
            cameraManager.switchCamera(selector);
        }
    }
    
    /**
     * Take a picture
     */
    public void takePicture(File outputFile) {
        if (cameraManager != null) {
            cameraManager.takePicture(outputFile);
        }
    }
    
    /**
     * Start video recording
     */
    public void startRecording(File outputFile) {
        if (cameraManager != null) {
            cameraManager.startRecording(outputFile);
        }
    }
    
    /**
     * Stop video recording
     */
    public void stopRecording() {
        if (cameraManager != null) {
            cameraManager.stopRecording();
        }
    }
    
    /**
     * Set opacity
     */
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        if (enableOpacity) {
            previewView.setAlpha(this.opacity);
        }
    }
    
    /**
     * Set zoom level with smart lens switching
     */
    public void setZoom(float zoomLevel) {
        float previousZoom = this.currentZoom;
        this.currentZoom = Math.max(0.5f, Math.min(10.0f, zoomLevel)); // Allow ultra-wide zoom < 1.0
        
        Log.d(TAG, "setZoom: " + previousZoom + " -> " + this.currentZoom + 
              " (requested: " + zoomLevel + ")");
        
        if (cameraManager != null) {
            cameraManager.setZoom(this.currentZoom);
        }
        
        if (callback != null) {
            callback.onZoomChanged(this.currentZoom);
        }
        
        // Log lens switching info
        if (this.currentZoom < 1.0f) {
            Log.d(TAG, "Ultra-wide mode activated (zoom: " + this.currentZoom + ")");
        } else if (this.currentZoom > 2.0f) {
            Log.d(TAG, "Telephoto mode activated (zoom: " + this.currentZoom + ")");
        } else {
            Log.d(TAG, "Main camera mode (zoom: " + this.currentZoom + ")");
        }
    }
    
    /**
     * Set smooth zoom level with smart ultra-wide switching
     * Uses CameraX's built-in smooth zoom for fluid pinch gestures
     */
    public void setSmoothZoom(float zoomLevel) {
        float previousZoom = this.currentZoom;
        
        Log.d(TAG, "setSmoothZoom: " + previousZoom + " -> " + zoomLevel + " (requested: " + zoomLevel + ")");
        
        if (cameraManager != null) {
            // Use smooth zoom with smart ultra-wide switching
            cameraManager.setSmoothZoom(zoomLevel);
            
            // Update current zoom from camera manager
            this.currentZoom = cameraManager.getCurrentZoom();
        } else {
            // Fallback if camera manager is null
            this.currentZoom = Math.max(1.0f, Math.min(zoomLevel, 10.0f));
        }
        
        if (callback != null) {
            callback.onZoomChanged(this.currentZoom);
        }
        
        Log.d(TAG, "Smooth zoom applied: " + this.currentZoom + "x");
    }
    
    /**
     * Set flash mode
     */
    public void setFlashMode(int flashMode) {
        if (cameraManager != null) {
            cameraManager.setFlashMode(flashMode);
        }
    }
    
    /**
     * Set focus point
     */
    public void setFocusPoint(float x, float y) {
        if (cameraManager != null) {
            cameraManager.setFocusPoint(x, y);
        }
    }
    
    /**
     * Set configuration options
     */
    public void setConfiguration(boolean enableOpacity, boolean enableZoom, boolean tapToFocus, boolean tapToTakePicture, boolean dragEnabled) {
        this.enableOpacity = enableOpacity;
        this.enableZoom = enableZoom;
        this.tapToFocus = tapToFocus;
        this.tapToTakePicture = tapToTakePicture;
        this.dragEnabled = dragEnabled;
        
        // Apply opacity if enabled
        if (enableOpacity) {
            previewView.setAlpha(opacity);
        }
    }
    
    /**
     * Set callback
     */
    public void setCallback(PreviewCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Get camera manager
     */
    public CameraXManager getCameraManager() {
        return cameraManager;
    }
    
    /**
     * Check if preview is active
     */
    public boolean isPreviewActive() {
        return isPreviewActive;
    }
    
    /**
     * Get current zoom level
     */
    public float getCurrentZoom() {
        return currentZoom;
    }
    
    /**
     * Get current opacity
     */
    public float getOpacity() {
        return opacity;
    }
    
    /**
     * Release resources
     */
    public void release() {
        if (cameraManager != null) {
            cameraManager.release();
        }
        Log.d(TAG, "CameraXPreview released");
    }
}
