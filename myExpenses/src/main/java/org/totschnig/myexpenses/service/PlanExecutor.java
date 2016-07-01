package org.totschnig.myexpenses.service;

import java.util.Date;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ExpenseEdit;
import org.totschnig.myexpenses.activity.MyExpenses;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.Template;
import org.totschnig.myexpenses.model.Transaction;

import static org.totschnig.myexpenses.provider.DatabaseConstants.*;

import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.provider.CalendarProviderProxy;
import org.totschnig.myexpenses.util.AcraHelper;
import org.totschnig.myexpenses.util.Utils;

import com.android.calendar.CalendarContractCompat;
import com.android.calendar.CalendarContractCompat.Events;

import android.Manifest;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

public class PlanExecutor extends IntentService {
  public static final String ACTION_CANCEL = "Cancel";
  public static final String ACTION_APPLY = "Apply";
  public static final String KEY_TITLE = "title";
  //production: 21600000 6* 60 * 60 * 1000 6 hours; for testing: 60000 1 minute
  public static final long INTERVAL = BuildConfig.DEBUG ? 60000 : 21600000;

  public PlanExecutor() {
    super("PlanExexcutor");
  }

  @Override
  public void onHandleIntent(Intent intent) {
    String plannerCalendarId;
    long now = System.currentTimeMillis();
    if (ContextCompat.checkSelfPermission(this,
        Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
      return;
    }
    try {
      plannerCalendarId = MyApplication.getInstance().checkPlanner();
    } catch (Exception e) {
      //has been reported to fail (report 9bc4e977220f559fcd8a204195bcf47f)
      AcraHelper.report(e);
      return;
    }
    if (plannerCalendarId.equals("-1")) {
      Log.i(MyApplication.TAG, "PlanExecutor: no planner set, nothing to do");
      return;
    }
    long lastExecutionTimeStamp = PrefKey.PLANNER_LAST_EXECUTION_TIMESTAMP.getLong(0L);
    if (lastExecutionTimeStamp == 0) {
      Log.i(MyApplication.TAG, "PlanExecutor started first time, nothing to do");
    } else {
      Log.i(MyApplication.TAG, String.format(
          "executing plans from %d to %d",
          lastExecutionTimeStamp,
          now));

      Uri.Builder eventsUriBuilder = CalendarProviderProxy.INSTANCES_URI.buildUpon();
      ContentUris.appendId(eventsUriBuilder, lastExecutionTimeStamp);
      ContentUris.appendId(eventsUriBuilder, now);
      Uri eventsUri = eventsUriBuilder.build();
      Cursor cursor;
      try {
        cursor = getContentResolver().query(eventsUri, null,
            Events.CALENDAR_ID + " = " + plannerCalendarId,
            null,
            null);
      } catch (Exception e) {
        //} catch (SecurityException | IllegalArgumentException e) {
        AcraHelper.report(e);
        //android.permission.READ_CALENDAR or android.permission.WRITE_CALENDAR missing (SecurityException)
        //buggy calendar provider implementation on Sony (IllegalArgumentException)
        //sqlite database not yet available observed on samsung GT-N7100 (SQLiteException)
        return;
      }
      if (cursor != null) {
        if (cursor.moveToFirst()) {
          while (!cursor.isAfterLast()) {
            long planId = cursor.getLong(cursor.getColumnIndex(CalendarContractCompat.Instances.EVENT_ID));
            Long instanceId = cursor.getLong(cursor.getColumnIndex(CalendarContractCompat.Instances._ID));
            long date = cursor.getLong(cursor.getColumnIndex(CalendarContractCompat.Instances.BEGIN));
            //2) check if they are part of a plan linked to a template
            //3) execute the template
            Log.i(MyApplication.TAG, String.format("found instance %d of plan %d", instanceId, planId));
            //TODO if we have multiple Event instances for one plan, we should maybe cache the template objects
            //TODO we should set the date of the Event instance on the created transactions
            Template template = Template.getInstanceForPlanIfInstanceIsOpen(planId, instanceId);
            if (template != null) {
              Log.i(MyApplication.TAG, String.format("belongs to template %d", template.getId()));
              Notification notification;
              int notificationId = instanceId.hashCode();
              PendingIntent resultIntent;
              Account account = Account.getInstanceFromDb(template.accountId);
              NotificationManager mNotificationManager =
                  (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
              String content = template.label;
              if (!content.equals("")) {
                content += " : ";
              }
              content += Utils.formatCurrency(template.getAmount());
              String title = account.label + " : " + template.getTitle();
              NotificationCompat.Builder builder =
                  new NotificationCompat.Builder(this)
                      .setSmallIcon(R.drawable.ic_stat_planner)
                      .setContentTitle(title)
                      .setContentText(content);
              if (template.isPlanExecutionAutomatic()) {
                Transaction t = Transaction.getInstanceFromTemplate(template);
                t.originPlanInstanceId = instanceId;
                t.setDate(new Date(date));
                if (t.save() != null) {
                  Intent displayIntent = new Intent(this, MyExpenses.class)
                      .putExtra(KEY_ROWID, template.accountId)
                      .putExtra(KEY_TRANSACTIONID, t.getId());
                  resultIntent = PendingIntent.getActivity(this, notificationId, displayIntent,
                      PendingIntent.FLAG_UPDATE_CURRENT);
                  builder.setContentIntent(resultIntent);
                } else {
                  builder.setContentText(getString(R.string.save_transaction_error));
                }
                builder.setAutoCancel(true);
                notification = builder.build();
              } else {
                Intent cancelIntent = new Intent(this, PlanNotificationClickHandler.class)
                    .setAction(ACTION_CANCEL)
                    .putExtra(MyApplication.KEY_NOTIFICATION_ID, notificationId)
                    .putExtra(KEY_TEMPLATEID, template.getId())
                    .putExtra(KEY_INSTANCEID, instanceId)
                    //we also put the title in the intent, because we need it while we update the notification
                    .putExtra(KEY_TITLE, title);
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(android.R.string.cancel),
                    PendingIntent.getService(this, notificationId, cancelIntent, 0));
                Intent editIntent = new Intent(this, ExpenseEdit.class)
                    .putExtra(MyApplication.KEY_NOTIFICATION_ID, notificationId)
                    .putExtra(KEY_TEMPLATEID, template.getId())
                    .putExtra(KEY_INSTANCEID, instanceId)
                    .putExtra(KEY_DATE, date);
                resultIntent = PendingIntent.getActivity(this, notificationId, editIntent, 0);
                builder.addAction(
                    android.R.drawable.ic_menu_edit,
                    getString(R.string.menu_edit),
                    resultIntent);
                Intent applyIntent = new Intent(this, PlanNotificationClickHandler.class);
                applyIntent.setAction(ACTION_APPLY)
                    .putExtra(MyApplication.KEY_NOTIFICATION_ID, notificationId)
                    .putExtra("title", title)
                    .putExtra(KEY_TEMPLATEID, template.getId())
                    .putExtra(KEY_INSTANCEID, instanceId)
                    .putExtra(KEY_DATE, date);
                builder.addAction(
                    android.R.drawable.ic_menu_save,
                    getString(R.string.menu_apply_template),
                    PendingIntent.getService(this, notificationId, applyIntent, 0));
                builder.setContentIntent(resultIntent);
                notification = builder.build();
                notification.flags |= Notification.FLAG_NO_CLEAR;
              }
              mNotificationManager.notify(notificationId, notification);
            } else {
              Log.i(MyApplication.TAG, "Template.getInstanceForPlanIfInstanceIsOpen returned null, instance might already have been dealt with");
            }
            cursor.moveToNext();
          }
        }
        cursor.close();
      }
    }
    PrefKey.PLANNER_LAST_EXECUTION_TIMESTAMP.putLong(now);
    setAlarm(this, now + INTERVAL);
  }

  public static void setAlarm(Context ctx, long when) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED) {
      PendingIntent pendingIntent = getPendingIntent(ctx);
      AlarmManager manager = (AlarmManager) ctx.getSystemService(ALARM_SERVICE);
      manager.set(AlarmManager.RTC, when, pendingIntent);
    }
  }

  private static PendingIntent getPendingIntent(Context ctx) {
    return PendingIntent.getService(ctx, 0, new Intent(ctx, PlanExecutor.class), 0);
  }

  public static void cancelPlans(Context ctx) {
    PendingIntent pendingIntent = getPendingIntent(ctx);
    AlarmManager manager = (AlarmManager) ctx.getSystemService(ALARM_SERVICE);
    manager.cancel(pendingIntent);
  }
}