package com.ahm.capacitor.camera.preview.camerax;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        void onStopRecordVideo();
        void onStopRecordVideoError(String message);
    }

    private CameraXActivityListener eventListener;
    private CameraXManager cameraXManager;
    private CameraXPreview cameraXPreview;
    private CameraXSelector cameraXSelector;
    private androidx.camera.core.CameraSelector currentCameraSelector;
    private boolean isRecording = false;
    private String tempVideoPath;

    // Camera facing constants
    public static final int CAMERA_FACING_FRONT = 1;
    public static final int CAMERA_FACING_BACK = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "CameraXActivity created");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "CameraXActivity onCreateView");
        
        // Create main container
        RelativeLayout mainLayout = new RelativeLayout(getActivity());
        mainLayout.setLayoutParams(new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ));

        // Initialize CameraX components
        cameraXManager = new CameraXManager(getActivity());
        cameraXPreview = new CameraXPreview(getActivity());
        cameraXSelector = new CameraXSelector();

        // Set up camera manager callback
        cameraXManager.setCallback(new CameraXManager.CameraXCallback() {
            @Override
            public void onCameraStarted() {
                Log.d(TAG, "Camera started callback");
                if (eventListener != null) {
                    eventListener.onCameraStarted();
                }
            }

            @Override
            public void onCameraError(String error) {
                Log.e(TAG, "Camera error: " + error);
                if (eventListener != null) {
                    eventListener.onPictureTakenError(error);
                }
            }

            @Override
            public void onImageCaptured(File imageFile) {
                Log.d(TAG, "Image captured: " + imageFile.getAbsolutePath());
                if (eventListener != null) {
                    eventListener.onPictureTaken(imageFile.getAbsolutePath());
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
                    eventListener.onStopRecordVideo();
                }
            }

            @Override
            public void onVideoRecordingError(String error) {
                Log.e(TAG, "Video recording error: " + error);
                isRecording = false;
                if (eventListener != null) {
                    eventListener.onStopRecordVideoError(error);
                }
            }
        });

        // Inject camera manager into preview
        cameraXPreview.setCameraManager(cameraXManager);

        // Add preview to main layout
        mainLayout.addView(cameraXPreview);

        return mainLayout;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "CameraXActivity onResume");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "CameraXActivity onPause");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CameraXActivity onDestroy");
        if (cameraXManager != null) {
            cameraXManager.release();
        }
    }

    /**
     * Set camera facing (front/back)
     */
    public void setCameraFacing(int facing) {
        Log.d(TAG, "Setting camera facing: " + facing);
        if (facing == CAMERA_FACING_FRONT) {
            currentCameraSelector = CameraXSelector.getFrontCamera(getActivity());
        } else {
            currentCameraSelector = CameraXSelector.getBackCamera(getActivity());
        }
    }

    /**
     * Set configuration options
     */
    public void setConfiguration(boolean enableOpacity, boolean enableZoom, boolean tapToFocus, 
                                boolean tapToTakePicture, boolean dragEnabled, boolean enableTorch, 
                                boolean enableAutoFocus, boolean disableExifHeaderStripping, String position) {
        Log.d(TAG, "Setting configuration - opacity: " + enableOpacity + ", zoom: " + enableZoom + 
              ", tapToFocus: " + tapToFocus + ", tapToTakePicture: " + tapToTakePicture + 
              ", dragEnabled: " + dragEnabled);
        
        if (cameraXPreview != null) {
            cameraXPreview.setConfiguration(enableOpacity, enableZoom, tapToFocus, tapToTakePicture, dragEnabled);
        }
    }

    /**
     * Start camera
     */
    public void startCamera() {
        Log.d(TAG, "Starting camera");
        if (cameraXManager != null && cameraXPreview != null) {
            androidx.camera.core.CameraSelector selector = currentCameraSelector != null ? 
                currentCameraSelector : CameraXSelector.getBackCamera(getActivity());
            cameraXManager.startCamera(cameraXPreview.getPreviewView(), selector);
        }
    }

    /**
     * Stop camera
     */
    public void stopCamera() {
        Log.d(TAG, "Stopping camera");
        if (cameraXManager != null) {
            cameraXManager.unbindCameraUseCases();
        }
    }

    /**
     * Switch camera
     */
    public void switchCamera(androidx.camera.core.CameraSelector selector) {
        Log.d(TAG, "Switching camera");
        if (cameraXManager != null && cameraXPreview != null) {
            currentCameraSelector = selector;
            cameraXManager.switchCamera(cameraXPreview.getPreviewView(), selector);
        }
    }

    /**
     * Switch to ultra-wide camera
     */
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
                    cameraXManager.switchCamera(cameraXPreview.getPreviewView(), ultraWideSelector);
                    Log.d(TAG, "Successfully switched to ultra-wide camera");
                    currentCameraSelector = ultraWideSelector;
                    return true;
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
                androidx.camera.core.CameraSelector mainWideSelector = CameraXSelector.getBackCamera(getActivity());
                cameraXManager.switchCamera(cameraXPreview.getPreviewView(), mainWideSelector);
                currentCameraSelector = mainWideSelector;
                Log.d(TAG, "Successfully switched to main wide camera");
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error switching to main wide camera: " + e.getMessage(), e);
                return false;
            }
        }
        Log.e(TAG, "CameraXManager is null");
        return false;
    }

    public boolean switchToFrontCamera() {
        Log.d(TAG, "switchToFrontCamera called");
        if (cameraXManager != null) {
            return cameraXManager.switchToFrontCamera(getActivity());
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

    public void switchToWideAngle() {
        Log.d(TAG, "switchToWideAngle called - using CameraX implementation");
        if (cameraXPreview != null) {
            cameraXPreview.switchToWideAngle();
        } else {
            Log.e(TAG, "CameraXPreview is null");
        }
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

    public void takePicture(int quality) {
        takePicture(0, 0, quality);
    }

    public void takeSnapshot(int quality) {
        if (cameraXPreview != null) {
            try {
                File outputFile = new File(getTempFilePath());
                cameraXPreview.takePicture(outputFile);
            } catch (Exception e) {
                Log.e(TAG, "Error taking snapshot", e);
                if (eventListener != null) {
                    eventListener.onSnapshotTakenError("Failed to take snapshot: " + e.getMessage());
                }
            }
        }
    }

    public void startRecording() {
        if (cameraXPreview != null) {
            try {
                tempVideoPath = getTempFilePath();
                File outputFile = new File(tempVideoPath);
                cameraXPreview.startRecording(outputFile);
            } catch (Exception e) {
                Log.e(TAG, "Error starting recording", e);
                if (eventListener != null) {
                    eventListener.onStopRecordVideoError("Failed to start recording: " + e.getMessage());
                }
            }
        }
    }

    public void stopRecording() {
        if (cameraXPreview != null) {
            cameraXPreview.stopRecording();
        }
    }

    public void setFlashMode(String flashMode) {
        if (cameraXManager != null) {
            int mode = 0; // Default to off
            switch (flashMode.toLowerCase()) {
                case "on":
                case "torch":
                    mode = 1;
                    break;
                case "auto":
                    mode = 2;
                    break;
                case "red-eye":
                    mode = 3;
                    break;
            }
            cameraXManager.setFlashMode(mode);
        }
    }

    public void setZoom(float zoom) {
        if (cameraXManager != null) {
            cameraXManager.setZoom(zoom);
        }
    }

    public void setOpacity(float opacity) {
        if (cameraXPreview != null) {
            cameraXPreview.setOpacity(opacity);
        }
    }

    public void setFocusPoint(float x, float y) {
        if (cameraXManager != null) {
            cameraXManager.setFocusPoint(x, y);
        }
    }

    public void switchToMainCamera() {
        Log.d(TAG, "switchToMainCamera called");
        if (cameraXManager != null) {
            androidx.camera.core.CameraSelector mainSelector = CameraXSelector.getBackCamera(getActivity());
            switchCamera(mainSelector);
        }
    }

    public List<String> getSupportedCameras() {
        List<String> cameras = new ArrayList<>();
        cameras.add("back");
        cameras.add("front");
        return cameras;
    }

    public void setCameraXActivityListener(CameraXActivityListener listener) {
        this.eventListener = listener;
    }

    private String getTempFilePath() {
        String fileName = "temp_" + UUID.randomUUID().toString() + ".jpg";
        return getActivity().getExternalFilesDir(null).getAbsolutePath() + "/" + fileName;
    }

    // Missing methods that CameraPreview expects
    public void setEventListener(CameraXActivityListener listener) {
        this.eventListener = listener;
    }

    public void setRect(int x, int y, int width, int height) {
        // Implementation for setting camera rect
        Log.d(TAG, "setRect called: " + x + ", " + y + ", " + width + ", " + height);
    }

    public boolean isToBack() {
        return currentCameraSelector != null && currentCameraSelector == CameraXSelector.getBackCamera(getActivity());
    }

    public FrameLayout getFrameContainerLayout() {
        return (FrameLayout) getView();
    }

    public void startRecord(String filePath, String position, Integer width, Integer height, int quality, Boolean withFlash, Integer maxDuration) {
        // Implementation for starting video recording
        Log.d(TAG, "startRecord called: " + filePath);
        if (eventListener != null) {
            eventListener.onStartRecordVideo();
        }
    }

    public void stopRecord() {
        // Implementation for stopping video recording
        Log.d(TAG, "stopRecord called");
        if (eventListener != null) {
            eventListener.onStopRecordVideo();
        }
    }
}
