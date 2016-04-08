/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package org.totschnig.myexpenses.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ContribInfoDialogActivity;
import org.totschnig.myexpenses.activity.MyPreferenceActivity;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.task.GenericTask;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.util.Utils;

public class AutoBackupService extends WakefulIntentService {

	private static final String TAG = AutoBackupService.class.getSimpleName();
    public static final String ACTION_AUTO_BACKUP = "org.totschnig.myexpenses.ACTION_AUTO_BACKUP";
    public static final String ACTION_SCHEDULE_AUTO_BACKUP = "org.totschnig.myexpenses.ACTION_SCHEDULE_AUTO_BACKUP";

    public AutoBackupService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("DEBUG", "Created AutoBackupService service ...");
    }

    @Override
	protected void doWakefulWork(Intent intent) {
        String action = intent.getAction();
        if (ACTION_AUTO_BACKUP.equals(action)) {
            Log.i("DEBUG","now doing backup");
            Result result = GenericTask.doBackup();
            String notifTitle = Utils.concatResStrings(this, " ", R.string.app_name, R.string.contrib_feature_auto_backup_label);
            if (result.success) {
                int remaining = ContribFeature.AUTO_BACKUP.recordUsage();
                if (remaining < 1) {
                    CharSequence content = TextUtils.concat(
                        getText(R.string.warning_auto_backup_limit_reached), " ",
                        ContribFeature.AUTO_BACKUP.buildRemoveLimitation(this,true));
                    Intent contribIntent = new Intent(this, ContribInfoDialogActivity.class);
                    contribIntent.putExtra(ContribInfoDialogActivity.KEY_FEATURE, ContribFeature.AUTO_BACKUP);
                    NotificationCompat.Builder builder =
                        new NotificationCompat.Builder(this)
                            .setSmallIcon(R.drawable.ic_home_dark)
                            .setContentTitle(notifTitle)
                            .setContentText(content)
                            .setContentIntent(PendingIntent.getActivity(this, 0, contribIntent, PendingIntent.FLAG_CANCEL_CURRENT))
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(content));
                    Notification notification = builder.build();
                    notification.flags = Notification.FLAG_AUTO_CANCEL;
                    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(0,notification);
                }
            } else {
                String content = result.print(this);
                Intent preferenceIntent = new Intent(this, MyPreferenceActivity.class);
                preferenceIntent.putExtra(MyPreferenceActivity.KEY_OPEN_PREF_KEY, MyApplication.PrefKey.APP_DIR.getKey());
                NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_home_dark)
                        .setContentTitle(notifTitle)
                        .setContentText(content)
                        .setContentIntent(PendingIntent.getActivity(this, 0, preferenceIntent, PendingIntent.FLAG_CANCEL_CURRENT))
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(content));
                Notification notification = builder.build();
                notification.flags = Notification.FLAG_AUTO_CANCEL;
                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(0,notification);
            }
        }  else if (ACTION_SCHEDULE_AUTO_BACKUP.equals(action)) {
            DailyAutoBackupScheduler.updateAutoBackupAlarms(this);
        }
    }

}
