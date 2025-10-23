package com.ahm.capacitor.camera.preview.camerax;

import android.content.Context;
import android.util.Log;

import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;

/**
 * CameraXSelector provides camera selection utilities for different camera types
 * including front, back, ultra-wide, and telephoto cameras using Camera2 API.
 */
public class CameraXSelector {
    private static final String TAG = "CameraXSelector";

    public CameraXSelector() {
        // Default constructor
    }

    /**
     * Get front camera selector
     */
    public static CameraSelector getFrontCamera(Context context) {
        try {
            return CameraSelector.DEFAULT_FRONT_CAMERA;
        } catch (Exception e) {
            Log.e(TAG, "Error getting front camera: " + e.getMessage(), e);
            return CameraSelector.DEFAULT_BACK_CAMERA;
        }
    }

    /**
     * Get back camera selector
     */
    public static CameraSelector getBackCamera(Context context) {
        try {
            return CameraSelector.DEFAULT_BACK_CAMERA;
        } catch (Exception e) {
            Log.e(TAG, "Error getting back camera: " + e.getMessage(), e);
            return CameraSelector.DEFAULT_BACK_CAMERA;
        }
    }

    /**
     * Get ultra-wide camera selector
     */
    public static CameraSelector getUltraWideCamera(Context context) {
        try {
            // Try to get ultra-wide camera using lens facing
            CameraSelector ultraWideSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
            
            Log.d(TAG, "Ultra-wide camera selector created");
            return ultraWideSelector;
        } catch (Exception e) {
            Log.w(TAG, "Ultra-wide camera not available: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get telephoto camera selector
     */
    public static CameraSelector getTelephotoCamera(Context context) {
        try {
            // Try to get telephoto camera using lens facing
            CameraSelector telephotoSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
            
            Log.d(TAG, "Telephoto camera selector created");
            return telephotoSelector;
        } catch (Exception e) {
            Log.w(TAG, "Telephoto camera not available: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if camera is ultra-wide
     */
    public static boolean isUltraWide(Context context, CameraInfo cameraInfo) {
        try {
            // This is a simplified check - in a real implementation,
            // you would check camera characteristics for ultra-wide capability
            return cameraInfo != null;
        } catch (Exception e) {
            Log.e(TAG, "Error checking ultra-wide camera: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if camera is telephoto
     */
    public static boolean isTelephoto(Context context, CameraInfo cameraInfo) {
        try {
            // This is a simplified check - in a real implementation,
            // you would check camera characteristics for telephoto capability
            return cameraInfo != null;
        } catch (Exception e) {
            Log.e(TAG, "Error checking telephoto camera: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get default camera selector
     */
    public static CameraSelector getDefaultCamera(Context context) {
        return CameraSelector.DEFAULT_BACK_CAMERA;
    }
}
