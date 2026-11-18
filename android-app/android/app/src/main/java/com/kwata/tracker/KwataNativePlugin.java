package com.kwata.tracker;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.util.List;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;

/**
 * Small native helpers for Kwata:
 *  - sendSms: texts the SOS straight from the phone (works with no mobile data).
 *  - status / requestBackground / ignoreBattery: keep background tracking alive.
 */
@CapacitorPlugin(
    name = "KwataNative",
    permissions = {
        @Permission(alias = "sms", strings = { Manifest.permission.SEND_SMS }),
        @Permission(alias = "bg", strings = { Manifest.permission.ACCESS_BACKGROUND_LOCATION }),
        @Permission(alias = "phone", strings = { Manifest.permission.READ_PHONE_STATE })
    }
)
public class KwataNativePlugin extends Plugin {

    @PluginMethod
    public void sendSms(PluginCall call) {
        if (getPermissionState("sms") != PermissionState.GRANTED) {
            requestPermissionForAlias("sms", call, "smsPermCallback");
            return;
        }
        doSend(call);
    }

    @PermissionCallback
    private void smsPermCallback(PluginCall call) {
        if (getPermissionState("sms") == PermissionState.GRANTED) doSend(call);
        else call.reject("SMS permission denied", "DENIED");
    }

    private void doSend(PluginCall call) {
        JSArray nums = call.getArray("numbers");
        String text = call.getString("text", "");
        if (nums == null || nums.length() == 0 || text.isEmpty()) { call.reject("Nothing to send"); return; }
        try {
            SmsManager sm = Build.VERSION.SDK_INT >= 31
                ? getContext().getSystemService(SmsManager.class)
                : SmsManager.getDefault();
            ArrayList<String> parts = sm.divideMessage(text);
            int sent = 0;
            for (int i = 0; i < nums.length(); i++) {
                String n = nums.getString(i);
                if (n == null || n.trim().isEmpty()) continue;
                try { sm.sendMultipartTextMessage(n.trim(), null, parts, null, null); sent++; } catch (Exception ignored) {}
            }
            if (sent == 0) { call.reject("Could not send any SMS"); return; }
            JSObject r = new JSObject(); r.put("sent", sent); call.resolve(r);
        } catch (Exception e) {
            call.reject("SMS failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void status(PluginCall call) {
        Context c = getContext();
        boolean fine = ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean bg = Build.VERSION.SDK_INT < 29
            ? fine
            : ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean bat = true;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            bat = pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
        }
        boolean sms = ContextCompat.checkSelfPermission(c, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        JSObject r = new JSObject();
        r.put("fine", fine); r.put("background", bg); r.put("battery", bat); r.put("sms", sms);
        call.resolve(r);
    }

    /** Android 11+ only grants "all the time" from the app's settings page, so send the user there. */
    @PluginMethod
    public void requestBackground(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 30) { openAppSettings(); call.resolve(); return; }
        if (Build.VERSION.SDK_INT == 29 && getPermissionState("bg") != PermissionState.GRANTED) {
            requestPermissionForAlias("bg", call, "bgPermCallback"); return;
        }
        call.resolve();
    }

    @PermissionCallback
    private void bgPermCallback(PluginCall call) { call.resolve(); }

    @PluginMethod
    public void ignoreBattery(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getContext().getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            }
            call.resolve();
        } catch (Exception e) {
            openAppSettings(); call.resolve();
        }
    }

    @PluginMethod
    public void openSettings(PluginCall call) { openAppSettings(); call.resolve(); }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getContext().getPackageName(), null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
    }

    /**
     * SIM fingerprint for SIM-change alerts. Without READ_PHONE_STATE we only see the SIM's network;
     * with it we also get subscription ids, which change whenever a different SIM card goes in.
     */
    @PluginMethod
    public void simInfo(PluginCall call) {
        JSObject r = new JSObject();
        try {
            TelephonyManager tm = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) { r.put("state", "NONE"); call.resolve(r); return; }
            int st = tm.getSimState();
            String state = st == TelephonyManager.SIM_STATE_READY ? "READY" : st == TelephonyManager.SIM_STATE_ABSENT ? "ABSENT"
                : st == TelephonyManager.SIM_STATE_NOT_READY ? "NOT_READY" : st == TelephonyManager.SIM_STATE_UNKNOWN ? "UNKNOWN" : "LOCKED";
            r.put("state", state);
            r.put("operator", tm.getSimOperator());
            r.put("operatorName", tm.getSimOperatorName());
            r.put("country", tm.getSimCountryIso());
            if (Build.VERSION.SDK_INT >= 22 && getPermissionState("phone") == PermissionState.GRANTED) {
                SubscriptionManager sm = (SubscriptionManager) getContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                List<SubscriptionInfo> l = sm != null ? sm.getActiveSubscriptionInfoList() : null;
                JSArray subs = new JSArray();
                if (l != null) for (SubscriptionInfo si : l) subs.put(si.getSubscriptionId() + ":" + si.getSimSlotIndex());
                r.put("subs", subs);
            }
        } catch (Exception e) { r.put("error", e.getMessage()); }
        call.resolve(r);
    }

    @PluginMethod
    public void requestPhone(PluginCall call) {
        if (getPermissionState("phone") == PermissionState.GRANTED) { call.resolve(); return; }
        requestPermissionForAlias("phone", call, "phonePermCallback");
    }

    @PermissionCallback
    private void phonePermCallback(PluginCall call) {
        if (getPermissionState("phone") == PermissionState.GRANTED) call.resolve();
        else call.reject("denied");
    }
}
