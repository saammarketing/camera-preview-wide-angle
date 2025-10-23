package com.ahm.capacitor.camera.preview.camerax;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * CameraXSelector utility class for camera selection and capability detection
 */
public class CameraXSelector {
    private static final String TAG = "CameraXSelector";
    
    /**
     * Get the default back camera selector
     */
    public static androidx.camera.core.CameraSelector getDefaultBackCamera() {
        return androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
    }
    
    /**
     * Get the default front camera selector
     */
    public static androidx.camera.core.CameraSelector getDefaultFrontCamera() {
        return androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA;
    }
    
    /**
     * Get a selector for ultra-wide camera (if available)
     */
    public static androidx.camera.core.CameraSelector getUltraWideCamera(Context context) {
        Log.d(TAG, "getUltraWideCamera called");
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) {
                Log.e(TAG, "CameraManager is null");
                return null;
            }

            String[] cameraIds = cameraManager.getCameraIdList();
            Log.d(TAG, "Available camera IDs: " + Arrays.toString(cameraIds));

            String targetCameraId = null;
            float minFocal = Float.MAX_VALUE;

            for (String cameraId : cameraIds) {
                try {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                        if (focalLengths != null && focalLengths.length > 0) {
                            float localMin = Float.MAX_VALUE;
                            for (float f : focalLengths) localMin = Math.min(localMin, f);
                            Log.d(TAG, "Back cam " + cameraId + " minFocal: " + localMin + " (all=" + Arrays.toString(focalLengths) + ")");
                            if (localMin < minFocal) {
                                minFocal = localMin;
                                targetCameraId = cameraId;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error reading characteristics for " + cameraId + ": " + e.getMessage());
                }
            }

            if (targetCameraId == null) {
                Log.w(TAG, "No ultra-wide candidate found");
                return null;
            }

            final String chosenId = targetCameraId;
            Log.d(TAG, "Ultra-wide chosen cameraId: " + chosenId + " (focal=" + minFocal + ")");
            return new androidx.camera.core.CameraSelector.Builder()
                .addCameraFilter(cameraInfos -> {
                    List<androidx.camera.core.CameraInfo> filtered = new ArrayList<>();
                    for (androidx.camera.core.CameraInfo info : cameraInfos) {
                        String infoCameraId = androidx.camera.camera2.interop.Camera2CameraInfo.from(info).getCameraId();
                        if (infoCameraId.equals(chosenId)) {
                            filtered.add(info);
                            break;
                        }
                    }
                    return filtered;
                })
                .build();
        } catch (Exception e) {
            Log.e(TAG, "Error in getUltraWideCamera: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get a selector for wide camera (main back camera)
     */
    public static androidx.camera.core.CameraSelector getWideCamera(Context context) {
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) return androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;

            class CamEntry { String id; float focal; CamEntry(String id, float focal){ this.id=id; this.focal=focal; } }
            List<CamEntry> backCams = new ArrayList<>();

            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (focalLengths != null && focalLengths.length > 0) {
                        float localMin = Float.MAX_VALUE;
                        for (float f : focalLengths) localMin = Math.min(localMin, f);
                        backCams.add(new CamEntry(cameraId, localMin));
                    }
                }
            }

            if (backCams.isEmpty()) return androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;

            // Sort ascending by focal length (ultra-wide -> wide/main -> tele)
            Collections.sort(backCams, Comparator.comparingDouble(e -> e.focal));

            String chosenId;
            if (backCams.size() == 1) {
                chosenId = backCams.get(0).id; // only option
            } else if (backCams.size() == 2) {
                chosenId = backCams.get(1).id; // pick the larger focal (main)
            } else {
                chosenId = backCams.get(1).id; // pick middle as main (common 3-lens setup)
            }

            final String selected = chosenId;
            Log.d(TAG, "Wide (main) chosen cameraId: " + selected);
            return new androidx.camera.core.CameraSelector.Builder()
                .addCameraFilter(cameraInfos -> {
                    List<androidx.camera.core.CameraInfo> filtered = new ArrayList<>();
                    for (androidx.camera.core.CameraInfo info : cameraInfos) {
                        String infoCameraId = androidx.camera.camera2.interop.Camera2CameraInfo.from(info).getCameraId();
                        if (infoCameraId.equals(selected)) {
                            filtered.add(info);
                            break;
                        }
                    }
                    return filtered;
                })
                .build();
        } catch (Exception e) {
            Log.e(TAG, "Error in getWideCamera: " + e.getMessage(), e);
            return androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
        }
    }
    
    /**
     * Get a selector for telephoto camera (if available)
     */
    public static androidx.camera.core.CameraSelector getTelephotoCamera(Context context) {
        return buildFilteredBackSelector(context, LensType.TELEPHOTO);
    }
    
    /**
     * Check if the device has an ultra-wide camera
     */
    public static boolean hasUltraWideCamera(Context context) {
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager != null) {
                for (String cameraId : cameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                    Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    
                    if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                        if (focalLengths != null && focalLengths.length > 0) {
                            float localMin = Float.MAX_VALUE;
                            for (float f : focalLengths) localMin = Math.min(localMin, f);
                            // Ultra-wide typically has focal length < 2.2
                            if (localMin <= 2.2f) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking for ultra-wide camera", e);
        }
        return false;
    }
    
    /**
     * Check if the device has a telephoto camera
     */
    public static boolean hasTelephotoCamera(Context context) {
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager != null) {
                for (String cameraId : cameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                    Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    
                    if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                        if (focalLengths != null && focalLengths.length > 0) {
                            float localMin = Float.MAX_VALUE;
                            for (float f : focalLengths) localMin = Math.min(localMin, f);
                            // Telephoto typically has focal length > 3.0
                            if (localMin > 3.0f) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking for telephoto camera", e);
        }
        return false;
    }
    
    /**
     * Check if the device has a front camera
     */
    public static boolean hasFrontCamera(Context context) {
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager != null) {
                for (String cameraId : cameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                    Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    
                    if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking for front camera", e);
        }
        return false;
    }
    
    /**
     * Get a list of available camera types
     */
    public static List<String> getAvailableCameraTypes(Context context) {
        List<String> availableCameras = new ArrayList<>();
        
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager != null) {
                for (String cameraId : cameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                    Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    
                    if (lensFacing != null) {
                        if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                            availableCameras.add("front");
                        } else if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                            if (focalLengths != null && focalLengths.length > 0) {
                                float localMin = Float.MAX_VALUE;
                                for (float f : focalLengths) localMin = Math.min(localMin, f);
                                if (localMin <= 1.8f) {
                                    availableCameras.add("ultra-wide");
                                } else if (localMin <= 2.2f) {
                                    availableCameras.add("wide");
                                } else if (localMin > 3.0f) {
                                    availableCameras.add("telephoto");
                                } else {
                                    availableCameras.add("back");
                                }
                            } else {
                                availableCameras.add("back");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting available camera types", e);
        }
        
        return availableCameras;
    }
    
    /**
     * Get camera selector by type string
     */
    public static androidx.camera.core.CameraSelector getCameraSelectorByType(Context context, String cameraType) {
        if (cameraType == null) {
            return androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
        }
        switch (cameraType.toLowerCase()) {
            case "front":
                return androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA;
            case "ultra-wide":
                return getUltraWideCamera(context);
            case "telephoto":
                return getTelephotoCamera(context);
            case "wide":
                return getWideCamera(context);
            case "back":
            default:
                // Use CameraX default back to avoid misclassification
                return androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
        }
    }

    private enum LensType { ULTRA_WIDE, WIDE, TELEPHOTO }

    private static androidx.camera.core.CameraSelector buildFilteredBackSelector(Context context, LensType type) {
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        return new androidx.camera.core.CameraSelector.Builder()
            .addCameraFilter(cameraInfos -> {
                List<androidx.camera.core.CameraInfo> result = new ArrayList<>();
                float bestScore = (type == LensType.ULTRA_WIDE) ? Float.MAX_VALUE : -Float.MAX_VALUE;
                androidx.camera.core.CameraInfo bestInfo = null;
                for (androidx.camera.core.CameraInfo info : cameraInfos) {
                    String cameraId = androidx.camera.camera2.interop.Camera2CameraInfo.from(info).getCameraId();
                    try {
                        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                        if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
                        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                        if (focalLengths == null || focalLengths.length == 0) continue;
                        float focal = focalLengths[0];
                        switch (type) {
                            case ULTRA_WIDE:
                                // pick smallest focal length
                                if (focal < bestScore) { bestScore = focal; bestInfo = info; }
                                break;
                            case WIDE:
                                // choose middle/main by preferring around median; fallback to larger if only two
                                // this method is kept but getWideCamera() is preferred
                                float score = -Math.abs(focal - 3.5f);
                                if (score > bestScore) { bestScore = score; bestInfo = info; }
                                break;
                            case TELEPHOTO:
                                // pick largest focal length
                                if (focal > bestScore) { bestScore = focal; bestInfo = info; }
                                break;
                        }
                    } catch (Exception ignore) {}
                }
                if (bestInfo != null) result.add(bestInfo);
                return result.isEmpty() ? cameraInfos : result;
            })
            .build();
    }
    
    /**
     * Get camera selector for the next available camera
     */
    public static androidx.camera.core.CameraSelector getNextCamera(Context context, androidx.camera.core.CameraSelector currentSelector) {
        List<String> availableTypes = getAvailableCameraTypes(context);
        
        if (availableTypes.size() <= 1) {
            return currentSelector;
        }
        
        // Simple rotation logic - can be enhanced
        if (currentSelector == androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA) {
            if (availableTypes.contains("front")) {
                return androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA;
            }
        } else if (currentSelector == androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA) {
            return androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
        }
        
        return currentSelector;
    }
    
    /**
     * Check if a camera selector is valid for the current device
     */
    public static boolean isCameraSelectorValid(Context context, androidx.camera.core.CameraSelector selector) {
        try {
            // This is a simplified check - in practice, you'd want to verify
            // that the selector can actually bind to a camera
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error validating camera selector", e);
            return false;
        }
    }

    public static boolean isUltraWide(Context context, androidx.camera.core.CameraInfo cameraInfo) {
        try {
            String id = androidx.camera.camera2.interop.Camera2CameraInfo.from(cameraInfo).getCameraId();
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics c = cm.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) return false;
            float[] focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (focal == null || focal.length == 0) return false;
            // Heuristic: if current focal equals the minimum available among back lenses, treat as ultra-wide
            float minBack = Float.MAX_VALUE;
            for (String cid : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(cid);
                Integer f = cc.get(CameraCharacteristics.LENS_FACING);
                if (f != null && f == CameraCharacteristics.LENS_FACING_BACK) {
                    float[] fl = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (fl != null && fl.length > 0) {
                        float localMin = Float.MAX_VALUE;
                        for (float v : fl) localMin = Math.min(localMin, v);
                        minBack = Math.min(minBack, localMin);
                    }
                }
            }
            float currentMin = Float.MAX_VALUE;
            for (float v : focal) currentMin = Math.min(currentMin, v);
            return currentMin <= minBack + 0.05f;
        } catch (Exception e) {
            Log.e(TAG, "Error in isUltraWide", e);
            return false;
        }
    }

    public static boolean isFront(Context context, androidx.camera.core.CameraInfo cameraInfo) {
        try {
            String id = androidx.camera.camera2.interop.Camera2CameraInfo.from(cameraInfo).getCameraId();
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics c = cm.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            return facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
        } catch (Exception e) {
            Log.e(TAG, "Error in isFront", e);
            return false;
        }
    }

    public static boolean isBack(Context context, androidx.camera.core.CameraInfo cameraInfo) {
        try {
            String id = androidx.camera.camera2.interop.Camera2CameraInfo.from(cameraInfo).getCameraId();
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics c = cm.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            return facing != null && facing == CameraCharacteristics.LENS_FACING_BACK;
        } catch (Exception e) {
            Log.e(TAG, "Error in isBack", e);
            return false;
        }
    }
}
