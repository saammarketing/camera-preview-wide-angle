package com.ahm.capacitor.camera.preview.camerax;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoOutput;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Recording;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CameraXManager handles all CameraX operations including camera lifecycle,
 * zoom control, and camera switching using modern Camera2 API.
 */
public class CameraXManager {
    private static final String TAG = "CameraXManager";
    
    private Context context;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private CameraSelector currentCameraSelector;
    private Preview preview;
    private ImageCapture imageCapture;
    private VideoCapture videoCapture;
    private Recording recording;
    private ExecutorService cameraExecutor;
    
    // Camera state
    private float currentZoom = 1.0f;
    private boolean isRecording = false;
    
    // Callback interface
    public interface CameraXCallback {
        void onCameraStarted();
        void onCameraError(String error);
        void onImageCaptured(File imageFile);
        void onImageCaptureError(String error);
        void onVideoRecordingStarted();
        void onVideoRecordingStopped(File videoFile);
        void onVideoRecordingError(String error);
    }
    
    private CameraXCallback callback;
    
    public CameraXManager(Context context) {
        this.context = context;
        this.cameraExecutor = Executors.newSingleThreadExecutor();
        this.currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    }
    
    public void setCallback(CameraXCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Start camera with preview
     */
    public void startCamera(PreviewView previewView, CameraSelector selector) {
        try {
            ProcessCameraProvider.getInstance(context).addListener(() -> {
                try {
                    cameraProvider = ProcessCameraProvider.getInstance(context).get();
                    currentCameraSelector = selector;
                    bindCameraUseCases(previewView);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting camera: " + e.getMessage(), e);
                    if (callback != null) {
                        callback.onCameraError("Failed to start camera: " + e.getMessage());
                    }
                }
            }, ContextCompat.getMainExecutor(context));
        } catch (Exception e) {
            Log.e(TAG, "Error initializing camera: " + e.getMessage(), e);
            if (callback != null) {
                callback.onCameraError("Failed to initialize camera: " + e.getMessage());
            }
        }
    }
    
    private void bindCameraUseCases(PreviewView previewView) {
        if (cameraProvider == null) {
            Log.e(TAG, "Camera provider is null");
            return;
        }
        
        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll();
            
            // Preview use case
            preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            
            // Image capture use case
            imageCapture = new ImageCapture.Builder()
                .setTargetResolution(new Size(1920, 1080))
                .build();
            
            // Video capture use case
            // Video capture - Note: VideoCapture in 1.4.2 requires VideoOutput
            // We'll initialize it when needed for recording
            videoCapture = null;
            
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                (LifecycleOwner) context,
                currentCameraSelector,
                preview,
                imageCapture,
                videoCapture
            );
            
            if (callback != null) {
                callback.onCameraStarted();
            }
            
            Log.d(TAG, "Camera started successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error binding camera use cases: " + e.getMessage(), e);
            if (callback != null) {
                callback.onCameraError("Failed to bind camera: " + e.getMessage());
            }
        }
    }
    
    /**
     * Switch camera
     */
    public void switchCamera(PreviewView previewView, CameraSelector selector) {
        if (cameraProvider != null) {
            currentCameraSelector = selector;
            bindCameraUseCases(previewView);
        }
    }
    
    /**
     * Set zoom level using CameraX
     */
    public void setZoom(float zoomLevel) {
        if (camera != null) {
            try {
                CameraControl cameraControl = camera.getCameraControl();
                ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                
                if (zoomState != null) {
                    float minZoom = zoomState.getMinZoomRatio();
                    float maxZoom = zoomState.getMaxZoomRatio();
                    float clampedZoom = Math.max(minZoom, Math.min(maxZoom, zoomLevel));
                    
                    cameraControl.setZoomRatio(clampedZoom);
                    currentZoom = clampedZoom;
                    
                    Log.d(TAG, "Zoom set to: " + currentZoom + "x (range: " + minZoom + "x - " + maxZoom + "x)");
                } else {
                    Log.w(TAG, "ZoomState is null, cannot set zoom");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting zoom: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Set smooth zoom level using CameraX
     */
    public void setSmoothZoom(float zoomLevel) {
        if (camera != null) {
            try {
                CameraControl cameraControl = camera.getCameraControl();
                ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                
                if (zoomState != null) {
                    float minZoom = zoomState.getMinZoomRatio();
                    float maxZoom = zoomState.getMaxZoomRatio();
                    float clampedZoom = Math.max(minZoom, Math.min(maxZoom, zoomLevel));
                    
                    cameraControl.setZoomRatio(clampedZoom);
                    currentZoom = clampedZoom;
                    
                    Log.d(TAG, "Smooth zoom set to: " + currentZoom + "x (range: " + minZoom + "x - " + maxZoom + "x)");
                } else {
                    Log.w(TAG, "ZoomState is null, using linear zoom fallback");
                    // Fallback to linear zoom if ZoomState is not available
                    setLinearZoom(zoomLevel);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting smooth zoom: " + e.getMessage(), e);
                // Fallback to linear zoom
                setLinearZoom(zoomLevel);
            }
        }
    }
    
    /**
     * Set linear zoom as fallback
     */
    private void setLinearZoom(float zoomLevel) {
        if (camera != null) {
            try {
                CameraControl cameraControl = camera.getCameraControl();
                
                // Map zoom level to linear zoom (0.0 - 1.0)
                // Map 0.5x-2.5x to 0.0-1.0 linear
                float linearZoom = Math.max(0.0f, Math.min(1.0f, (zoomLevel - 0.5f) / 2.0f));
                cameraControl.setLinearZoom(linearZoom);
                
                // Update current zoom for tracking
                currentZoom = 0.5f + (linearZoom * 2.0f);
                
                Log.d(TAG, "Linear zoom set to: " + linearZoom + " (mapped to " + currentZoom + "x)");
            } catch (Exception e) {
                Log.e(TAG, "Error setting linear zoom: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Get current zoom level
     */
    public float getCurrentZoom() {
        return currentZoom;
    }
    
    /**
     * Get zoom range as string
     */
    public String getZoomRange() {
        if (camera != null) {
            try {
                ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                if (zoomState != null) {
                    return String.format("%.2fx - %.2fx", zoomState.getMinZoomRatio(), zoomState.getMaxZoomRatio());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting zoom range: " + e.getMessage(), e);
            }
        }
        return "1.00x - 1.00x";
    }
    
    /**
     * Check if camera has zoom capability
     */
    public boolean hasZoomCapability() {
        if (camera != null) {
            try {
                ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                if (zoomState != null) {
                    return zoomState.getMaxZoomRatio() > zoomState.getMinZoomRatio();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking zoom capability: " + e.getMessage(), e);
            }
        }
        return false;
    }
    
    /**
     * Set focus point
     */
    public void setFocusPoint(float x, float y) {
        if (camera != null) {
            try {
                CameraControl cameraControl = camera.getCameraControl();
                // For now, disable focus point setting as the API has changed in CameraX 1.4.2
                // This would require a complete rewrite of the focus functionality
                Log.w(TAG, "Focus point setting is temporarily disabled due to CameraX 1.4.2 API changes");
            } catch (Exception e) {
                Log.e(TAG, "Error setting focus point: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Set flash mode
     */
    public void setFlashMode(int flashMode) {
        if (imageCapture != null) {
            try {
                imageCapture.setFlashMode(flashMode);
                Log.d(TAG, "Flash mode set to: " + flashMode);
            } catch (Exception e) {
                Log.e(TAG, "Error setting flash mode: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Take picture
     */
    public void takePicture(File outputFile) {
        if (imageCapture != null) {
            ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(outputFile).build();
            
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                new ImageCapture.OnImageSavedCallback() {
                    public void onImageSaved(ImageCapture.OutputFileResults output) {
                        Log.d(TAG, "Image saved: " + outputFile.getAbsolutePath());
                        if (callback != null) {
                            callback.onImageCaptured(outputFile);
                        }
                    }
                    
                    public void onError(ImageCaptureException exception) {
                        Log.e(TAG, "Image capture error: " + exception.getMessage(), exception);
                        if (callback != null) {
                            callback.onImageCaptureError(exception.getMessage());
                        }
                    }
                }
            );
        }
    }
    
    /**
     * Start video recording
     */
    public void startRecording(File outputFile) {
        if (!isRecording) {
            try {
                // For now, disable video recording as VideoCapture API has changed significantly in 1.4.2
                // This would require a complete rewrite of the video recording functionality
                Log.w(TAG, "Video recording is temporarily disabled due to CameraX 1.4.2 API changes");
                if (callback != null) {
                    callback.onVideoRecordingError("Video recording not available in this version");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error starting video recording: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onVideoRecordingError(e.getMessage());
                }
            }
        }
    }
    
    /**
     * Stop video recording
     */
    public void stopRecording() {
        if (isRecording) {
            if (recording != null) {
                recording.stop();
                recording = null;
            }
            isRecording = false;
            Log.d(TAG, "Video recording stopped");
        }
    }
    
    /**
     * Get current camera info
     */
    public CameraInfo getCurrentCameraInfo() {
        return camera != null ? camera.getCameraInfo() : null;
    }
    
    /**
     * Unbind camera use cases
     */
    public void unbindCameraUseCases() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            camera = null;
            preview = null;
            imageCapture = null;
            videoCapture = null;
            Log.d(TAG, "Camera use cases unbound");
        }
    }
    
    /**
     * Release resources
     */
    public void release() {
        unbindCameraUseCases();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
        Log.d(TAG, "CameraXManager released");
    }
    
    /**
     * Switch to ultra-wide camera (smart zoom)
     */
    public boolean switchToUltraWideSmart() {
        try {
            // Try to set zoom to ultra-wide level
            setSmoothZoom(0.5f);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error switching to ultra-wide smart: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Switch to front camera
     */
    public boolean switchToFrontCamera(Activity activity) {
        try {
            CameraSelector frontSelector = CameraXSelector.getFrontCamera(activity);
            if (camera != null && previewView != null) {
                switchCamera(previewView, frontSelector);
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error switching to front camera: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Switch to back camera
     */
    public boolean switchToBackCamera(Activity activity) {
        try {
            CameraSelector backSelector = CameraXSelector.getBackCamera(activity);
            if (camera != null && previewView != null) {
                switchCamera(previewView, backSelector);
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error switching to back camera: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Toggle between front and back camera
     */
    public boolean toggleFrontBack(Activity activity) {
        try {
            if (currentCameraSelector == CameraXSelector.getFrontCamera(activity)) {
                return switchToBackCamera(activity);
            } else {
                return switchToFrontCamera(activity);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling camera: " + e.getMessage(), e);
            return false;
        }
    }
    
    private PreviewView previewView;
    
    public void setPreviewView(PreviewView previewView) {
        this.previewView = previewView;
    }
}
