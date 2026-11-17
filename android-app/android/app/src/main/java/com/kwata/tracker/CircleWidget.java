package com.kwata.tracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;
import java.text.NumberFormat;

/** 4x2 home-screen widget: steps today, circle size, live status, and SOS / Buzz / Map buttons. */
public class CircleWidget extends AppWidgetProvider {

    static final String PREFS = "kwata_widget";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    static RemoteViews build(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_circle);
        v.setTextViewText(R.id.w_steps, NumberFormat.getIntegerInstance().format(p.getInt("steps", 0)));
        v.setTextViewText(R.id.w_circle, String.valueOf(p.getInt("circle", 0)));
        v.setTextViewText(R.id.w_status, p.getString("status", "Live"));
        v.setOnClickPendingIntent(R.id.w_sos, SosWidget.sosIntent(c));
        v.setOnClickPendingIntent(R.id.w_buzz, link(c, "kwata://buzz", 7102));
        v.setOnClickPendingIntent(R.id.w_map, link(c, "kwata://map", 7103));
        v.setOnClickPendingIntent(R.id.w_root, link(c, "kwata://map", 7104));
        return v;
    }

    static PendingIntent link(Context c, String uri, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse(uri));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(c, req, i, flags);
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, CircleWidget.class));
        for (int id : ids) m.updateAppWidget(id, build(c));
    }
}
