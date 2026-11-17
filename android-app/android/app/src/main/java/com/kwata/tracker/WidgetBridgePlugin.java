package com.kwata.tracker;

import android.content.Context;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Lets the web app push fresh numbers to the home-screen widget. */
@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {
    @PluginMethod
    public void update(PluginCall call) {
        Context c = getContext();
        c.getSharedPreferences(CircleWidget.PREFS, Context.MODE_PRIVATE).edit()
            .putInt("steps", call.getInt("steps", 0))
            .putInt("circle", call.getInt("circle", 0))
            .putString("status", call.getString("status", "Live"))
            .apply();
        CircleWidget.refreshAll(c);
        call.resolve();
    }
}
