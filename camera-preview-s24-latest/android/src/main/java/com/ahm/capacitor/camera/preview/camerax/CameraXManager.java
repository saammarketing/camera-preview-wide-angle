package com.ahm.capacitor.camera.preview.camerax;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CameraXManager handles all CameraX operations including camera lifecycle,
 * use case binding, and camera switching.
 */
public class CameraXManager {
    private static final String TAG = "CameraXManager";

    private final Context context;
    private final ExecutorService cameraExecutor;

    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private Preview preview;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;

    private PreviewView boundPreviewView;

    private CameraSelector currentCameraSelector;
    private boolean isCameraBound = false;

    // Camera state
    private int flashMode = ImageCapture.FLASH_MODE_OFF;
    private float zoomLevel = 1.0f;
    private boolean isRecording = false;
    private long lastCameraSwitchTime = 0;
    private static final long CAMERA_SWITCH_DEBOUNCE_MS = 500; // 500ms debounce

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
     * Start the camera with the specified camera selector
     */
    public void startCamera(PreviewView previewView, CameraSelector selector) {
        if (isCameraBound) {
            Log.d(TAG, "Camera already bound, switching camera");
            switchCamera(selector);
            return;
        }

        this.currentCameraSelector = selector;

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
            ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = cameraProviderFuture.get();
                bindCameraUseCases(provider, previewView, selector);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                if (callback != null) {
                    callback.onCameraError("Failed to start camera: " + e.getMessage());
                }
            }
        }, ContextCompat.getMainExecutor(context));
    }

    /**
     * Bind camera use cases to the camera provider
     */
    private void bindCameraUseCases(ProcessCameraProvider provider,
                                   PreviewView previewView,
                                   CameraSelector selector) {
        try {
            // Unbind any existing use cases
            if (this.cameraProvider != null) {
                this.cameraProvider.unbindAll();
            }

            this.cameraProvider = provider;
            if (previewView != null) {
                this.boundPreviewView = previewView;
            }

            // Create preview use case
            preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build();

            // Create image capture use case
            imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG)
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setFlashMode(flashMode)
                .build();

            // Create video capture use case
            Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build();
            videoCapture = VideoCapture.withOutput(recorder);

            // Bind use cases to camera
            camera = provider.bindToLifecycle(
                (LifecycleOwner) context,
                selector,
                preview,
                imageCapture,
                videoCapture
            );

            // Connect preview to PreviewView
            if (this.boundPreviewView != null) {
                preview.setSurfaceProvider(this.boundPreviewView.getSurfaceProvider());
            }

            isCameraBound = true;

            Log.d(TAG, "Camera use cases bound successfully");

            if (callback != null) {
                callback.onCameraStarted();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error binding camera use cases", e);
            if (callback != null) {
                callback.onCameraError("Failed to bind camera use cases: " + e.getMessage());
            }
        }
    }

    /**
     * Switch to a different camera
     */
    public void switchCamera(CameraSelector selector) {
        if (!isCameraBound || cameraProvider == null) {
            Log.w(TAG, "Camera not bound, cannot switch");
            return;
        }

        try {
            // Unbind current use cases
            cameraProvider.unbindAll();
            isCameraBound = false;

            // Rebind with new selector (keep boundPreviewView)
            bindCameraUseCases(cameraProvider, this.boundPreviewView, selector);

        } catch (Exception e) {
            Log.e(TAG, "Error switching camera", e);
            if (callback != null) {
                callback.onCameraError("Failed to switch camera: " + e.getMessage());
            }
        }
    }

    /**
     * Take a picture
     */
    public void takePicture(File outputFile) {
        if (!isCameraBound || imageCapture == null) {
            Log.w(TAG, "Camera not bound or image capture not available");
            if (callback != null) {
                callback.onImageCaptureError("Camera not ready");
            }
            return;
        }

        ImageCapture.OutputFileOptions outputOptions =
            new ImageCapture.OutputFileOptions.Builder(outputFile).build();

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                    Log.d(TAG, "Image saved successfully: " + outputFile.getAbsolutePath());
                    if (callback != null) {
                        callback.onImageCaptured(outputFile);
                    }
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    Log.e(TAG, "Image capture failed", exception);
                    if (callback != null) {
                        callback.onImageCaptureError("Image capture failed: " + exception.getMessage());
                    }
                }
            }
        );
    }

    /**
     * Start video recording
     */
    public void startRecording(File outputFile) {
        if (!isCameraBound || videoCapture == null || isRecording) {
            Log.w(TAG, "Cannot start recording: camera not bound, video capture not available, or already recording");
            return;
        }

        try {
            FileOutputOptions outputOptions = new FileOutputOptions.Builder(outputFile).build();
            PendingRecording pending = videoCapture.getOutput()
                .prepareRecording(context, outputOptions)
                .withAudioEnabled();

            recording = pending.start(ContextCompat.getMainExecutor(context), event -> {
                if (event instanceof VideoRecordEvent.Start) {
                    isRecording = true;
                    if (callback != null) callback.onVideoRecordingStarted();
                    Log.d(TAG, "Video recording started");
                } else if (event instanceof VideoRecordEvent.Finalize) {
                    isRecording = false;
                    VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event;
                    if (fin.hasError()) {
                        Log.e(TAG, "Video recording failed: " + fin.getError());
                        if (callback != null) callback.onVideoRecordingError("Video recording failed: " + fin.getError());
                    } else {
                        Log.d(TAG, "Video saved: " + outputFile.getAbsolutePath());
                        if (callback != null) callback.onVideoRecordingStopped(outputFile);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error starting video recording", e);
            if (callback != null) {
                callback.onVideoRecordingError("Failed to start recording: " + e.getMessage());
            }
        }
    }

    /**
     * Stop video recording
     */
    public void stopRecording() {
        if (recording != null && isRecording) {
            recording.stop();
            isRecording = false;
            Log.d(TAG, "Video recording stopped");
        }
    }

    /**
     * Set flash mode - uses torch instead of flash for continuous lighting
     */
    public void setFlashMode(int flashMode) {
        this.flashMode = flashMode;
        
        // Use torch instead of flash for continuous lighting
        if (flashMode == ImageCapture.FLASH_MODE_ON) {
            // Enable torch for continuous lighting instead of flash
            enableTorch(true);
            Log.d(TAG, "Flash mode ON - using torch for continuous lighting");
        } else if (flashMode == ImageCapture.FLASH_MODE_OFF) {
            // Disable torch
            enableTorch(false);
            Log.d(TAG, "Flash mode OFF - torch disabled");
        } else {
            // For auto mode, still use traditional flash
            if (imageCapture != null && isCameraBound) {
                imageCapture.setFlashMode(flashMode);
                Log.d(TAG, "Flash mode AUTO - using traditional flash");
            }
        }
    }

    /** Enable/disable torch (continuous light) */
    public void enableTorch(boolean enabled) {
        if (camera != null) {
            camera.getCameraControl().enableTorch(enabled);
        }
    }

    /**
     * Set zoom level with smart lens switching
     */
    public void setZoom(float zoomLevel) {
        // Clamp zoom level to supported range
        float clampedZoom = Math.max(0.5f, Math.min(zoomLevel, 10.0f));
        this.zoomLevel = clampedZoom;
        
        Log.d(TAG, "setZoom called with: " + zoomLevel + " (clamped to: " + clampedZoom + ")");
        
        // Determine target camera selector based on zoom level
        CameraSelector targetSelector = determineCameraSelector(clampedZoom);
        
        // If we need to switch cameras, rebind with new selector
        if (targetSelector != currentCameraSelector) {
            Log.d(TAG, "Switching camera lens for zoom level: " + clampedZoom);
            switchCameraLens(targetSelector, clampedZoom);
        } else {
            // Same camera, just adjust zoom
            if (camera != null) {
                camera.getCameraControl().setZoomRatio(clampedZoom);
                Log.d(TAG, "Set zoom ratio to: " + clampedZoom + " on current camera");
            }
        }
    }
    
    /**
     * Set smooth zoom level with smart ultra-wide switching
     * Uses CameraX's built-in smooth zoom for fluid pinch gestures
     */
    public void setSmoothZoom(float zoomLevel) {
        if (camera == null) {
            Log.w(TAG, "Camera is null, cannot set smooth zoom");
            return;
        }
        
        // Get actual zoom range from camera
        androidx.camera.core.ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        if (zoomState == null) {
            Log.w(TAG, "ZoomState is null, using fallback zoom range");
            // Fallback to basic zoom
            float clampedZoom = Math.max(1.0f, Math.min(zoomLevel, 10.0f));
            this.zoomLevel = clampedZoom;
            camera.getCameraControl().setZoomRatio(clampedZoom);
            return;
        }
        
        float minZoom = zoomState.getMinZoomRatio();
        float maxZoom = zoomState.getMaxZoomRatio();
        
        Log.d(TAG, "Camera zoom range: " + minZoom + "x - " + maxZoom + "x");
        
        // Check if we're trying to zoom below the camera's minimum
        if (zoomLevel < minZoom) {
            Log.d(TAG, "Requested zoom " + zoomLevel + "x is below camera minimum " + minZoom + "x");
            // Just clamp to minimum - no automatic ultra-wide switching
            zoomLevel = minZoom;
        }
        
        // No automatic camera switching - just smooth zoom within current camera range
        
        // Clamp to actual camera range
        float clampedZoom = Math.max(minZoom, Math.min(zoomLevel, maxZoom));
        this.zoomLevel = clampedZoom;
        
        Log.d(TAG, "setSmoothZoom: " + zoomLevel + " -> " + clampedZoom + " (range: " + minZoom + "-" + maxZoom + ")");
        
        try {
            // Use CameraX's smooth zoom
            camera.getCameraControl().setZoomRatio(clampedZoom);
            Log.d(TAG, "Smooth zoom ratio set to: " + clampedZoom + "x on current camera");
        } catch (Exception e) {
            Log.e(TAG, "Error setting smooth zoom: " + e.getMessage(), e);
        }
    }
    
    /**
     * Determine which camera selector to use based on zoom level
     */
    private CameraSelector determineCameraSelector(float zoomLevel) {
        if (zoomLevel < 1.0f) {
            // Ultra-wide for zoom < 1.0x
            CameraSelector ultraWide = CameraXSelector.getUltraWideCamera(context);
            if (ultraWide != null) {
                Log.d(TAG, "Selecting ultra-wide camera for zoom: " + zoomLevel);
                return ultraWide;
            } else {
                Log.d(TAG, "Ultra-wide not available, using default back camera");
                return CameraSelector.DEFAULT_BACK_CAMERA;
            }
        } else if (zoomLevel > 2.0f) {
            // Telephoto for zoom > 2.0x
            CameraSelector telephoto = CameraXSelector.getTelephotoCamera(context);
            if (telephoto != null) {
                Log.d(TAG, "Selecting telephoto camera for zoom: " + zoomLevel);
                return telephoto;
            } else {
                Log.d(TAG, "Telephoto not available, using default back camera");
                return CameraSelector.DEFAULT_BACK_CAMERA;
            }
        } else {
            // Main camera for 1.0x - 2.0x
            Log.d(TAG, "Selecting main camera for zoom: " + zoomLevel);
            return CameraSelector.DEFAULT_BACK_CAMERA;
        }
    }
    
    /**
     * Switch to a different camera lens and set zoom
     */
    private void switchCameraLens(CameraSelector newSelector, float zoomLevel) {
        if (cameraProvider == null) {
            Log.e(TAG, "CameraProvider is null, cannot switch lens");
            return;
        }
        
        try {
            // Unbind current use cases
            unbindCameraUseCases();
            
            // Update current selector
            currentCameraSelector = newSelector;
            
            // Rebind with new selector
            bindCameraUseCases(cameraProvider, boundPreviewView, currentCameraSelector);
            
            // Set zoom after camera is ready
            if (camera != null) {
                camera.getCameraControl().setZoomRatio(zoomLevel);
                Log.d(TAG, "Successfully switched lens and set zoom to: " + zoomLevel);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error switching camera lens: " + e.getMessage(), e);
            // Fallback: try to recover with default back camera
            try {
                currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                bindCameraUseCases(cameraProvider, boundPreviewView, currentCameraSelector);
                if (camera != null) {
                    camera.getCameraControl().setZoomRatio(zoomLevel);
                }
            } catch (Exception recoveryError) {
                Log.e(TAG, "Failed to recover from lens switch error: " + recoveryError.getMessage());
            }
        }
    }

    /**
     * Set focus point (expects normalized [0..1] coordinates)
     */
    public void setFocusPoint(float x, float y) {
        if (camera != null) {
            SurfaceOrientedMeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(1.0f, 1.0f);
            MeteringPoint point = factory.createPoint(x, y);
            FocusMeteringAction action = new FocusMeteringAction.Builder(point,
                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE).build();
            camera.getCameraControl().startFocusAndMetering(action);
        }
    }
    
    /**
     * Get current zoom level
     */
    public float getCurrentZoom() {
        return zoomLevel;
    }
    
    /**
     * Get current camera mode description
     */
    public String getCurrentCameraMode() {
        if (zoomLevel < 1.0f) {
            return "Ultra-wide (" + zoomLevel + "x)";
        } else if (zoomLevel > 2.0f) {
            return "Telephoto (" + zoomLevel + "x)";
        } else {
            return "Main camera (" + zoomLevel + "x)";
        }
    }
    
    /**
     * Get current zoom range from camera characteristics
     * Based on Android Camera2 API CONTROL_ZOOM_RATIO_RANGE
     */
    public String getZoomRange() {
        if (camera != null) {
            try {
                androidx.camera.core.ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                if (zoomState != null) {
                    float minZoom = zoomState.getMinZoomRatio();
                    float maxZoom = zoomState.getMaxZoomRatio();
                    return String.format("Zoom range: %.2fx - %.2fx", minZoom, maxZoom);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting zoom range: " + e.getMessage());
            }
        }
        return "Zoom range: unknown";
    }

    /**
     * Check if camera is bound
     */
    public boolean isCameraBound() {
        return isCameraBound;
    }

    /**
     * Check if recording is in progress
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Get current camera selector
     */
    public CameraSelector getCurrentCameraSelector() {
        return currentCameraSelector;
    }

    public androidx.camera.core.CameraInfo getCurrentCameraInfo() {
        return camera != null ? camera.getCameraInfo() : null;
    }

    /**
     * Unbind all camera use cases
     */
    public void unbindCameraUseCases() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            isCameraBound = false;
            isRecording = false;
            recording = null;
            Log.d(TAG, "Camera use cases unbound");
        }
    }

    /**
     * Release resources
     */
    public void release() {
        unbindCameraUseCases();
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
        Log.d(TAG, "CameraXManager released");
    }

    private boolean runOnMainAndWait(Callable<Boolean> action) {
        final AtomicBoolean result = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        ContextCompat.getMainExecutor(context).execute(() -> {
            try {
                result.set(action.call());
            } catch (Exception e) {
                Log.e(TAG, "Error executing on main thread", e);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result.get();
    }

    public boolean switchCamera() {
        Log.d(TAG, "switchCamera called");
        try {
            // Get next available camera
            androidx.camera.core.CameraSelector nextSelector = CameraXSelector.getNextCamera(
                context, currentCameraSelector
            );
            if (nextSelector != null) {
                Log.d(TAG, "Switching to next camera selector");
                return switchToCamera(nextSelector);
            } else {
                Log.w(TAG, "No next camera available");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in switchCamera: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean switchToCamera(androidx.camera.core.CameraSelector newSelector) {
        Log.d(TAG, "switchToCamera called with selector: " + newSelector);
        if (newSelector == null) {
            Log.e(TAG, "Camera selector is null");
            return false;
        }

        return runOnMainAndWait(() -> {
            try {
                // Unbind current use cases
                unbindCameraUseCases();
                
                // Update current selector
                currentCameraSelector = newSelector;
                Log.d(TAG, "Updated current camera selector");
                
                // Rebind with new selector
                bindCameraUseCases(cameraProvider, boundPreviewView, currentCameraSelector);
                
                // Log bound camera id and zoom range
                if (camera != null) {
                    String boundId = androidx.camera.camera2.interop.Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId();
                    ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
                    Log.d(TAG, "Bound cameraId=" + boundId + (zs != null ? (", zoomRange=[" + zs.getMinZoomRatio() + ", " + zs.getMaxZoomRatio() + "]") : ""));
                }
                
                // Check if binding was successful
                if (isCameraBound) {
                    Log.d(TAG, "Successfully switched to new camera");
                    return true;
                } else {
                    Log.e(TAG, "Failed to bind camera use cases for new camera");
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error switching to camera: " + e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * Fast switch to ultra-wide camera for responsive pinch gestures
     */
    public boolean switchToUltraWideSmart() {
        try {
            Log.d(TAG, "switchToUltraWideSmart called - using fast direct switching");
            
            // Get ultra-wide camera selector
            androidx.camera.core.CameraSelector ultraWideSelector = CameraXSelector.getUltraWideCamera(context);
            if (ultraWideSelector == null) {
                Log.w(TAG, "Ultra-wide camera not available");
                return false;
            }
            
            // Direct camera switching for faster response
            boolean success = switchToCamera(ultraWideSelector);
            if (success) {
                // Set zoom to minimum on ultra-wide camera
                if (camera != null) {
                    androidx.camera.core.ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                    if (zoomState != null) {
                        float minZoom = zoomState.getMinZoomRatio();
                        camera.getCameraControl().setZoomRatio(minZoom);
                        this.zoomLevel = minZoom;
                        Log.d(TAG, "Set ultra-wide zoom to minimum: " + minZoom + "x");
                    }
                }
            }
            
            Log.d(TAG, "Ultra-wide switching completed: " + success);
            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in switchToUltraWideSmart: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Prefer switching to main wide by setting zoom to 1.0 on logical multi-camera; fallback to rebinding selector.
     */
    public boolean switchToMainWideSmart() {
        try {
            // Determine if current bound camera is ultra-wide
            boolean isUltra = runOnMainAndWait(() -> {
                if (camera != null) {
                    return CameraXSelector.isUltraWide(context,
                        camera.getCameraInfo());
                }
                return false;
            });

            if (isUltra) {
                Log.d(TAG, "Currently bound to ultra-wide physical camera; rebinding to wide/main");
                androidx.camera.core.CameraSelector wide = CameraXSelector.getWideCamera(context);
                if (wide == null) wide = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
                return switchToCamera(wide);
            }

            boolean zoomed = runOnMainAndWait(() -> {
                if (camera != null) {
                    ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
                    if (zs != null) {
                        float min = zs.getMinZoomRatio();
                        float max = zs.getMaxZoomRatio();
                        Log.d(TAG, "switchToMainWideSmart zoomRange=[" + min + ", " + max + "]");
                        try {
                            // Try to set exact 1.0x if within range
                            float target = 1.0f;
                            target = Math.max(min, Math.min(target, max));
                            Log.d(TAG, "Setting zoomRatio to " + target + " for main wide");
                            camera.getCameraControl().setZoomRatio(target);
                            return true;
                        } catch (Exception ex) {
                            Log.w(TAG, "Setting zoomRatio failed (" + ex.getMessage() + "), trying linear zoom ~main");
                            try {
                                // Heuristic: center of linear zoom often maps near main wide
                                camera.getCameraControl().setLinearZoom(0.35f);
                                return true;
                            } catch (Exception ex2) {
                                Log.w(TAG, "Linear zoom fallback failed: " + ex2.getMessage());
                            }
                        }
                    } else {
                        Log.w(TAG, "ZoomState is null; cannot set main wide");
                    }
                }
                return false;
            });
            if (zoomed) return true;

            androidx.camera.core.CameraSelector wide = CameraXSelector.getWideCamera(context);
            if (wide == null) {
                wide = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
            }
            return switchToCamera(wide);
        } catch (Exception e) {
            Log.e(TAG, "Error in switchToMainWideSmart: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean switchToFrontCamera() {
        try {
            androidx.camera.core.CameraSelector selector = androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA;
            return switchToCamera(selector);
        } catch (Exception e) {
            Log.e(TAG, "Error switching to front camera", e);
            return false;
        }
    }

    public boolean switchToBackCamera(Context ctx) {
        try {
            // Prefer main wide back camera
            androidx.camera.core.CameraSelector selector = CameraXSelector.getWideCamera(ctx);
            if (selector == null) selector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
            return switchToCamera(selector);
        } catch (Exception e) {
            Log.e(TAG, "Error switching to back camera", e);
            return false;
        }
    }

    public boolean toggleFrontBack(Context ctx) {
        try {
            androidx.camera.core.CameraInfo info = getCurrentCameraInfo();
            if (info != null && CameraXSelector.isFront(ctx, info)) {
                return switchToBackCamera(ctx);
            } else {
                return switchToFrontCamera();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling front/back", e);
            return false;
        }
    }
}
