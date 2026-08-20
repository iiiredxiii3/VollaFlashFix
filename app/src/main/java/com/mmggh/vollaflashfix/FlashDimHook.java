package com.mmggh.vollaflashfix;

import android.hardware.camera2.CameraCharacteristics;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class FlashDimHook implements IXposedHookLoadPackage {
    private static final int FAKE_MAX = 31;
    private static final String SYSFS_CH1 = "/sys/class/leds/mt6360_flash_ch1/brightness";
    private static final String SYSFS_CH2 = "/sys/class/leds/mt6360_flash_ch2/brightness";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!"com.cyb3rko.flashdim".equals(lpparam.packageName)) return;

        XposedBridge.log("VollaFlashFix: Loaded into FlashDim");

        // 1. Spoof Camera Characteristics
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraCharacteristics", lpparam.classLoader,
            "get", CameraCharacteristics.Key.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args[0] == null) return;
                    String keyStr = param.args[0].toString();
                    
                    if (keyStr.contains("STRENGTH_MAXIMUM_LEVEL") || keyStr.contains("strengthMaximumLevel")) {
                        param.setResult(FAKE_MAX);
                    } else if (keyStr.contains("STRENGTH_DEFAULT_LEVEL") || keyStr.contains("strengthDefaultLevel")) {
                        param.setResult(1);
                    }
                }
            });

        // 2. Spoof getTorchStrengthLevel
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraManager", lpparam.classLoader,
            "getTorchStrengthLevel", String.class,
            new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return 1;
                }
            });

        // 3. Redirect turnOnTorchWithStrengthLevel
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraManager", lpparam.classLoader,
            "turnOnTorchWithStrengthLevel", String.class, int.class,
            new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    int level = (int) param.args[1];
                    if (level > 31) level = 31;
                    if (level < 1) level = 1;
                    writeSysfs(level);
                    return null;
                }
            });

        // 4. Handle Torch Off
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraManager", lpparam.classLoader,
            "setTorchMode", String.class, boolean.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    boolean enabled = (boolean) param.args[1];
                    if (!enabled) {
                        writeSysfs(0);
                    }
                }
            });
    }

    private void writeSysfs(int level) {
        try {
            String cmd = "echo " + level + " > " + SYSFS_CH1 + " && echo " + level + " > " + SYSFS_CH2;
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.waitFor();
            XposedBridge.log("VollaFlashFix: Set brightness to " + level);
        } catch (Exception e) {
            XposedBridge.log("VollaFlashFix: Root write failed: " + e.getMessage());
        }
    }
}
