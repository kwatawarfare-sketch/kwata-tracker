package com.kwata.tracker;

import android.Manifest;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Hardware step counter (Sensor.TYPE_STEP_COUNTER). The chip counts steps in low power even while
 * the app is closed; it reports the total since the phone last booted. The web app turns that into
 * "steps today" by keeping a daily baseline.
 */
@CapacitorPlugin(
    name = "Steps",
    permissions = { @Permission(strings = { Manifest.permission.ACTIVITY_RECOGNITION }, alias = "activity") }
)
public class StepsPlugin extends Plugin implements SensorEventListener {

    private SensorManager sm;
    private Sensor sensor;
    private boolean listening = false;
    private float total = -1f;

    @PluginMethod
    public void start(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 29 && getPermissionState("activity") != PermissionState.GRANTED) {
            requestPermissionForAlias("activity", call, "permDone");
            return;
        }
        begin(call);
    }

    @PermissionCallback
    private void permDone(PluginCall call) {
        if (getPermissionState("activity") == PermissionState.GRANTED) begin(call);
        else call.reject("Physical activity permission denied");
    }

    private void begin(PluginCall call) {
        sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        sensor = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (sensor == null) {
            call.reject("No step sensor on this phone");
            return;
        }
        if (!listening) {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
            listening = true;
        }
        JSObject r = new JSObject();
        r.put("ok", true);
        call.resolve(r);
    }

    @PluginMethod
    public void get(PluginCall call) {
        JSObject r = new JSObject();
        r.put("total", total);
        call.resolve(r);
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (sm != null && listening) sm.unregisterListener(this);
        listening = false;
        call.resolve();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        total = e.values[0];
        JSObject d = new JSObject();
        d.put("total", total);
        notifyListeners("step", d);
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {}

    @Override
    protected void handleOnDestroy() {
        if (sm != null && listening) sm.unregisterListener(this);
        listening = false;
    }
}
