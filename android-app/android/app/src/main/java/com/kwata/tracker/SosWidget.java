package com.kwata.tracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

/** Home-screen SOS button. One tap opens Kwata Tracker straight into the SOS countdown. */
public class SosWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sos);
            views.setOnClickPendingIntent(R.id.widget_sos_btn, sosIntent(context));
            manager.updateAppWidget(id, views);
        }
    }

    static PendingIntent sosIntent(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.setAction(MainActivity.ACTION_SOS);
        i.setData(Uri.parse("kwata://sos"));
        i.putExtra(MainActivity.EXTRA_SOS, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, 7101, i, flags);
    }
}
