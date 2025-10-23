package com.ahm.capacitor.camera.preview.camerax;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * CameraXActivity replaces the legacy CameraActivity with CameraX implementation
 * providing modern camera functionality with better lifecycle management.
 */
public class CameraXActivity extends Fragment {
    private static final String TAG = "CameraXActivity";

    public interface CameraXActivityListener {
        void onPictureTaken(String originalPicture);
        void onPictureTakenError(String message);
        void onSnapshotTaken(String originalPicture);
        void onSnapshotTakenError(String message);
        void onFocusSet(int pointX, int pointY);
        void onFocusSetError(String message);
        void onBackButton();
        void onCameraStarted();
        void onStartRecordVideo();
        void onStartRecordVideoError(String message);
        void onStopRecordVideo(String file);
        void onStopRecordVideoError(String error);
    }

    private CameraXActivityListener eventListener;
    private CameraXPreview cameraXPreview;
    private CameraXManager cameraXManager;
    
    // Configuration
    private boolean tapToTakePicture = false;
    private boolean dragEnabled = false;
    private boolean tapToFocus = true;
    private boolean disableExifHeaderStripping = false;
    private boolean storeToFile = false;
    private boolean toBack = false;
    private boolean enableOpacity = false;
    private boolean enableZoom = false;
    
    // Layout dimensions
    private int width;
    private int height;
    private int x;
    private int y;
    
    // Camera state
    private String defaultCamera = "back";
    private androidx.camera.core.CameraSelector currentCameraSelector;
    
    // Recording state
    private boolean isRecording = false;
    private String recordFilePath;
    
    // UI components
    private View view;
    private FrameLayout mainLayout;
    private FrameLayout frameContainerLayout;
    
    private String appResourcesPackage;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        appResourcesPackage = getActivity().getPackageName();
        
        // Inflate the layout for this fragment
        view = inflater.inflate(getResources().getIdentifier("camera_activity", "layout", appResourcesPackage), container, false);
        
        // Initialize CameraX components
        initializeCameraX();
        
        // Setup camera preview
        createCameraPreview();
        
        return view;
    }

    private void initializeCameraX() {
        // Set camera selector based on requested defaultCamera; fallback to back camera
        if (defaultCamera != null && getActivity() != null) {
            try {
                // Map "back" to DEFAULT_BACK_CAMERA to avoid accidentally choosing ultra-wide
                if ("back".equalsIgnoreCase(defaultCamera)) {
                    currentCameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
                } else if ("wide".equalsIgnoreCase(defaultCamera)) {
                    currentCameraSelector = CameraXSelector.getWideCamera(getActivity());
                } else {
                    currentCameraSelector = CameraXSelector.getCameraSelectorByType(getActivity(), defaultCamera);
                }
            } catch (Exception e) {
                currentCameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
            }
        } else {
            currentCameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
        }
        
        // Initialize camera manager
        cameraXManager = new CameraXManager(getActivity());
        cameraXManager.setCallback(new CameraXManager.CameraXCallback() {
            @Override
            public void onCameraStarted() {
                Log.d(TAG, "Camera started successfully");
                if (eventListener != null) {
                    eventListener.onCameraStarted();
                }
            }
            
            @Override
            public void onCameraError(String error) {
                Log.e(TAG, "Camera error: " + error);
            }
            
            @Override
            public void onImageCaptured(File imageFile) {
                Log.d(TAG, "Image captured: " + imageFile.getAbsolutePath());
                if (eventListener != null) {
                    if (storeToFile) {
                        eventListener.onPictureTaken(imageFile.getAbsolutePath());
                    } else {
                        // Convert to base64
                        try {
                            String base64Image = convertImageToBase64(imageFile);
                            eventListener.onPictureTaken(base64Image);
                        } catch (IOException e) {
                            eventListener.onPictureTakenError("Failed to convert image: " + e.getMessage());
                        }
                    }
                }
            }
            
            @Override
            public void onImageCaptureError(String error) {
                Log.e(TAG, "Image capture error: " + error);
                if (eventListener != null) {
                    eventListener.onPictureTakenError(error);
                }
            }
            
            @Override
            public void onVideoRecordingStarted() {
                Log.d(TAG, "Video recording started");
                isRecording = true;
                if (eventListener != null) {
                    eventListener.onStartRecordVideo();
                }
            }
            
            @Override
            public void onVideoRecordingStopped(File videoFile) {
                Log.d(TAG, "Video recording stopped: " + videoFile.getAbsolutePath());
                isRecording = false;
                if (eventListener != null) {
                    eventListener.onStopRecordVideo(videoFile.getAbsolutePath());
                }
            }
            
            @Override
            public void onVideoRecordingError(String error) {
                Log.e(TAG, "Video recording error: " + error);
                isRecording = false;
                if (eventListener != null) {
                    eventListener.onStartRecordVideoError(error);
                }
            }
        });
    }

    private void createCameraPreview() {
        // Set box position and size
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);
        
        frameContainerLayout = (FrameLayout) view.findViewById(
            getResources().getIdentifier("frame_container", "id", appResourcesPackage)
        );
        frameContainerLayout.setLayoutParams(layoutParams);

        // Create CameraX preview
        cameraXPreview = new CameraXPreview(getActivity(), enableOpacity);
        // Share the same CameraXManager instance so callbacks propagate correctly
        cameraXPreview.setCameraManager(cameraXManager);
        cameraXPreview.setConfiguration(enableOpacity, enableZoom, tapToFocus, tapToTakePicture, dragEnabled);
        cameraXPreview.setCallback(new CameraXPreview.PreviewCallback() {
            @Override
            public void onTapToFocus(float x, float y) {
                if (eventListener != null) {
                    eventListener.onFocusSet((int) x, (int) y);
                }
            }
            
            @Override
            public void onTapToTakePicture(float x, float y) {
                if (tapToTakePicture) {
                    takePicture(0, 0, 85);
                }
            }
            
            @Override
            public void onZoomChanged(float zoomLevel) {
                Log.d(TAG, "Zoom changed to: " + zoomLevel);
            }
            
            @Override
            public void onDragChanged(float deltaX, float deltaY) {
                // Handle drag changes if needed
            }
        });

        // Add preview to main layout
        mainLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("video_view", "id", appResourcesPackage));
        mainLayout.setLayoutParams(
            new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        );
        mainLayout.addView(cameraXPreview);
        mainLayout.setEnabled(false);

        // Setup touch and back button handling
        if (enableZoom || tapToFocus || tapToTakePicture || dragEnabled) {
            setupTouchAndBackButton();
        }
    }

    private void setupTouchAndBackButton() {
        // Setup back button listener
        frameContainerLayout.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                if (eventListener != null) {
                    eventListener.onBackButton();
                }
                return true;
            }
            return false;
        });
        
        frameContainerLayout.setFocusableInTouchMode(true);
        frameContainerLayout.requestFocus();
    }

    public void setEventListener(CameraXActivityListener listener) {
        eventListener = listener;
    }

    public void setRect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        
        if (frameContainerLayout != null) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) frameContainerLayout.getLayoutParams();
            layoutParams.setMargins(x, y, 0, 0);
            frameContainerLayout.setLayoutParams(layoutParams);
        }
    }

    public void setConfiguration(boolean tapToTakePicture, boolean dragEnabled, boolean tapToFocus, 
                               boolean disableExifHeaderStripping, boolean storeToFile, boolean toBack, 
                               boolean enableOpacity, boolean enableZoom, String defaultCamera) {
        this.tapToTakePicture = tapToTakePicture;
        this.dragEnabled = dragEnabled;
        this.tapToFocus = tapToFocus;
        this.disableExifHeaderStripping = disableExifHeaderStripping;
        this.storeToFile = storeToFile;
        this.toBack = toBack;
        this.enableOpacity = enableOpacity;
        this.enableZoom = enableZoom;
        this.defaultCamera = defaultCamera;
        
        // Defer camera selector resolution until fragment is attached (initializeCameraX)
        
        // Update preview configuration
        if (cameraXPreview != null) {
            cameraXPreview.setConfiguration(enableOpacity, enableZoom, tapToFocus, tapToTakePicture, dragEnabled);
        }
    }

    // Expose read-only accessors used by plugin view bridge
    public boolean isToBack() {
        return toBack;
    }

    public FrameLayout getFrameContainerLayout() {
        return frameContainerLayout;
    }

    public void startCamera() {
        if (cameraXPreview != null) {
            cameraXPreview.startPreview(currentCameraSelector);
        }
    }

    public void stopCamera() {
        if (cameraXPreview != null) {
            cameraXPreview.stopPreview();
        }
    }

    public boolean switchCamera() {
        Log.d(TAG, "switchCamera called");
        if (cameraXManager != null) {
            try {
                boolean success = cameraXManager.switchCamera();
                if (success) {
                    Log.d(TAG, "Camera switched successfully");
                    return true;
                } else {
                    Log.e(TAG, "Failed to switch camera");
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error switching camera: " + e.getMessage(), e);
                return false;
            }
        }
        Log.e(TAG, "CameraXManager is null");
        return false;
    }

    public boolean switchToUltraWideCamera() {
        Log.d(TAG, "switchToUltraWideCamera called");
        if (cameraXManager != null) {
            try {
                // If already on ultra-wide, toggle back to main wide
                if (cameraXPreview != null && cameraXPreview.getCameraManager() != null) {
                    androidx.camera.core.CameraInfo info = cameraXPreview.getCameraManager().getCurrentCameraInfo();
                    if (info != null && CameraXSelector.isUltraWide(getActivity(), info)) {
                        Log.d(TAG, "Already ultra-wide, toggling back to main wide");
                        return switchToMainWideCamera();
                    }
                }
                // Try smart zoom path first
                if (cameraXManager.switchToUltraWideSmart()) {
                    Log.d(TAG, "Ultra-wide achieved via logical zoom");
                    return true;
                }
                // Fallback to rebind with ultra-wide selector
                androidx.camera.core.CameraSelector ultraWideSelector = CameraXSelector.getUltraWideCamera(getActivity().getApplicationContext());
                if (ultraWideSelector != null) {
                    Log.d(TAG, "Ultra-wide camera selector found, switching...");
                    boolean success = cameraXManager.switchToCamera(ultraWideSelector);
                    if (success) {
                        Log.d(TAG, "Successfully switched to ultra-wide camera");
                        currentCameraSelector = ultraWideSelector;
                        return true;
                    }
                } else {
                    Log.w(TAG, "No ultra-wide camera available");
                }
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Error switching to ultra-wide camera: " + e.getMessage(), e);
                return false;
            }
        }
        Log.e(TAG, "CameraXManager is null");
        return false;
    }

    public boolean switchToMainWideCamera() {
        Log.d(TAG, "switchToMainWideCamera called");
        if (cameraXManager != null) {
            try {
                if (cameraXManager.switchToMainWideSmart()) {
                    Log.d(TAG, "Main wide achieved via logical zoom");
                    return true;
                }
                androidx.camera.core.CameraSelector wideSelector = CameraXSelector.getWideCamera(getActivity());
                if (wideSelector == null) {
                    wideSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
                }
                boolean success = cameraXManager.switchToCamera(wideSelector);
                if (success) {
                    currentCameraSelector = wideSelector;
                    return true;
                }
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Error switching to main wide camera: " + e.getMessage(), e);
                return false;
            }
        }
        return false;
    }

    public boolean switchToFrontCamera() {
        Log.d(TAG, "switchToFrontCamera called");
        if (cameraXManager != null) {
            return cameraXManager.switchToFrontCamera();
        }
        return false;
    }

    public boolean switchToBackCamera() {
        Log.d(TAG, "switchToBackCamera called");
        if (cameraXManager != null) {
            return cameraXManager.switchToBackCamera(getActivity());
        }
        return false;
    }

    public boolean toggleFrontBack() {
        Log.d(TAG, "toggleFrontBack called");
        if (cameraXManager != null) {
            return cameraXManager.toggleFrontBack(getActivity());
        }
        return false;
    }

    public void takePicture(int width, int height, int quality) {
        if (cameraXPreview != null) {
            try {
                File outputFile = new File(getTempFilePath());
                cameraXPreview.takePicture(outputFile);
            } catch (Exception e) {
                Log.e(TAG, "Error taking picture", e);
                if (eventListener != null) {
                    eventListener.onPictureTakenError("Failed to take picture: " + e.getMessage());
                }
            }
        }
    }

    public void takeSnapshot(int quality) {
        // For now, use takePicture as snapshot
        // In a full implementation, this would capture from preview
        takePicture(0, 0, quality);
    }

    public void startRecord(String filePath, String camera, int width, int height, int quality, boolean withFlash, int maxDuration) {
        if (cameraXPreview != null && !isRecording) {
            try {
                recordFilePath = filePath;
                File outputFile = new File(filePath);
                cameraXPreview.startRecording(outputFile);
            } catch (Exception e) {
                Log.e(TAG, "Error starting video recording", e);
                if (eventListener != null) {
                    eventListener.onStartRecordVideoError("Failed to start recording: " + e.getMessage());
                }
            }
        }
    }

    public void stopRecord() {
        if (cameraXPreview != null && isRecording) {
            cameraXPreview.stopRecording();
        }
    }

    public void setOpacity(float opacity) {
        if (cameraXPreview != null) {
            cameraXPreview.setOpacity(opacity);
        }
    }

    public void setFocusArea(int pointX, int pointY, Object callback) {
        if (cameraXPreview != null) {
            // Convert to normalized coordinates
            float normalizedX = (float) pointX / width;
            float normalizedY = (float) pointY / height;
            cameraXPreview.setFocusPoint(normalizedX, normalizedY);
            
            if (eventListener != null) {
                eventListener.onFocusSet(pointX, pointY);
            }
        }
    }

    public void setFlashMode(String flashMode) {
        if (cameraXPreview != null) {
            int flashModeInt = androidx.camera.core.ImageCapture.FLASH_MODE_OFF;
            switch (flashMode.toLowerCase()) {
                case "on":
                    // Use torch instead of flash for continuous lighting
                    flashModeInt = androidx.camera.core.ImageCapture.FLASH_MODE_ON;
                    break;
                case "auto":
                    flashModeInt = androidx.camera.core.ImageCapture.FLASH_MODE_AUTO;
                    break;
                case "torch":
                    // Explicit torch mode
                    flashModeInt = androidx.camera.core.ImageCapture.FLASH_MODE_ON;
                    break;
            }
            cameraXPreview.setFlashMode(flashModeInt);
            
            // Additional torch handling for explicit torch mode
            if ("torch".equalsIgnoreCase(flashMode) && cameraXPreview != null && cameraXPreview.getCameraManager() != null) {
                cameraXPreview.getCameraManager().enableTorch(true);
            }
        }
    }

    public void setZoom(float zoomLevel) {
        if (cameraXPreview != null) {
            cameraXPreview.setZoom(zoomLevel);
        }
    }

    public boolean hasFrontCamera() {
        return CameraXSelector.hasFrontCamera(getActivity());
    }

    public java.util.List<String> getSupportedCameras() {
        return CameraXSelector.getAvailableCameraTypes(getActivity());
    }

    private String getTempDirectoryPath() {
        File cache = getActivity().getCacheDir();
        cache.mkdirs();
        return cache.getAbsolutePath();
    }

    private String getTempFilePath() {
        return getTempDirectoryPath() + "/cpcp_capture_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".jpg";
    }

    private String convertImageToBase64(File imageFile) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        
        // Apply EXIF rotation if needed
        if (!disableExifHeaderStripping) {
            try {
                ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                bitmap = rotateBitmap(bitmap, orientation);
            } catch (Exception e) {
                Log.w(TAG, "Error reading EXIF data", e);
            }
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
        byte[] imageBytes = outputStream.toByteArray();
        outputStream.close();
        
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
    }

    private Bitmap rotateBitmap(Bitmap source, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
        }
        
        if (!matrix.isIdentity()) {
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        }
        return source;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (cameraXPreview != null) {
            // Always prefer default back (main) when resuming unless a specific lens was chosen
            if (currentCameraSelector == null) {
                currentCameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
            }
            cameraXPreview.startPreview(currentCameraSelector);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (cameraXPreview != null) {
            cameraXPreview.stopPreview();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Handle configuration changes if needed
    }

    public void release() {
        if (cameraXPreview != null) {
            cameraXPreview.release();
        }
        if (cameraXManager != null) {
            cameraXManager.release();
        }
    }
}
