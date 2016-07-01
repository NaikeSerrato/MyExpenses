/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses.dialog;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.dialog.MessageDialogFragment.MessageDialogListener;
import org.totschnig.myexpenses.ui.ScrollableProgressDialog;
import org.totschnig.myexpenses.util.AcraHelper;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.content.DialogInterface;
import android.os.Bundle;

public class ProgressDialogFragment extends CommitSafeDialogFragment {
  private static final String KEY_PROGRESS_STYLE = "progressStyle";
  private static final String KEY_MESSAGE = "message";
  private static final String KEY_WITH_BUTTON = "withButton";
  private static final String KEY_TITLE = "title";
  private static final String KEY_TASK_COMPLETED = "taskCompleted";
  private static final String KEY_PROGRESS = "progress";
  private static final String KEY_MAX = "max";
  private ProgressDialog mDialog;
  boolean mTaskCompleted = false;
  int progress = 0, max = 0;
  String title, message;
 

  /**
   * @param message if different from 0 a resource string identifier displayed as the dialogs's message
   * @return the dialog fragment
   */
  public static ProgressDialogFragment newInstance(int message) {
    return newInstance(0,message,0, false);
  }
  /**
   * @param message if different from 0 a resource string identifier displayed as the dialogs's message
   * @param progressStyle {@link ProgressDialog#STYLE_SPINNER} or {@link ProgressDialog#STYLE_HORIZONTAL}
   * @param withButton if true dialog is rendered with an OK button that is initially disabled
   * @return the dialog fragment
   */
  public static ProgressDialogFragment newInstance(int title, int message,int progressStyle, boolean withButton) {
    String titleString = title != 0 ? MyApplication.getInstance().getString(title) : null;
    String messageString = message != 0 ? MyApplication.getInstance().getString(message) : null;
    return newInstance(titleString,messageString,progressStyle,withButton);
  }
  /**
   * @param message the dialogs's message
   * @param progressStyle {@link ProgressDialog#STYLE_SPINNER} or {@link ProgressDialog#STYLE_HORIZONTAL}
   * @param withButton if true dialog is rendered with an OK button that is initially disabled
   * @return the dialog fragment
   */
  public static ProgressDialogFragment newInstance(String title, String message,int progressStyle, boolean withButton) {
    ProgressDialogFragment f = new ProgressDialogFragment ();
    Bundle bundle = new Bundle();
    bundle.putString(KEY_MESSAGE, message);
    bundle.putString(KEY_TITLE, title);
    bundle.putInt(KEY_PROGRESS_STYLE, progressStyle);
    bundle.putBoolean(KEY_WITH_BUTTON, withButton);
    f.setArguments(bundle);
    f.setCancelable(false);
    return f;
  }
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (savedInstanceState != null) {
      mTaskCompleted = savedInstanceState.getBoolean(KEY_TASK_COMPLETED,false);
      message = savedInstanceState.getString(KEY_MESSAGE);
      title = savedInstanceState.getString(KEY_TITLE);
      progress = savedInstanceState.getInt(KEY_PROGRESS);
      max = savedInstanceState.getInt(KEY_MAX);
    }
    setRetainInstance(true);
  }
  @Override
  public void onResume() {
    super.onResume();
    mDialog.setProgress(progress);
    mDialog.setMax(max);
    mDialog.setTitle(title);
    if (!TextUtils.isEmpty(message)) {
      mDialog.setMessage(message);
    }
    if (mTaskCompleted) {
      mDialog.setIndeterminateDrawable(null);
    }
    else {
      Button b =  mDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
      if (b != null) {
        b.setEnabled(false); 
      }
    }
  }
  //http://stackoverflow.com/a/12434038/1199911
  @Override
  public void onDestroyView() {
      if (getDialog() != null && getRetainInstance())
          getDialog().setDismissMessage(null);
          super.onDestroyView();
  }
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    int progressStyle = getArguments().getInt(KEY_PROGRESS_STYLE);
    String messageFromArguments = getArguments().getString(KEY_MESSAGE);
    String titleFromArguments = getArguments().getString(KEY_TITLE);
    mDialog = (progressStyle == ScrollableProgressDialog.STYLE_SPINNER) ?
        new ScrollableProgressDialog(getActivity()) :
        new ProgressDialog(getActivity());
    boolean withButton = getArguments().getBoolean(KEY_WITH_BUTTON);
    if (messageFromArguments != null) {
      //message might have been set through setmessage
      if (message == null) {
        message = messageFromArguments  + " …";
        mDialog.setMessage(message);
      }
    } else {
      if (titleFromArguments != null)  {
        if (title == null)
          title = titleFromArguments;
      } else {
        //unless setTitle is called now with non empty argument, calls after dialog is shown are ignored
        if (title == null)
          title = "...";
      }
      mDialog.setTitle(title);
    }
    if (progressStyle != ScrollableProgressDialog.STYLE_SPINNER) {
      mDialog.setProgressStyle(progressStyle);
    } else {
      mDialog.setIndeterminate(true);
      if (max!=0) {
        mDialog.setMax(max);
      }
    }
    if (withButton) {
      mDialog.setButton(DialogInterface.BUTTON_POSITIVE,getString(android.R.string.ok),
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              if (getActivity()==null) {
                return;
              }
              ((MessageDialogListener) getActivity()).onMessageDialogDismissOrCancel();
            }
      });
    }
    return mDialog;
  }
  public void setProgress(int progress) {
    this.progress = progress;
    mDialog.setProgress(progress);
  }
  public void setMax(int max) {
    this.max = max;
    if (mDialog != null) {
      mDialog.setMax(max);
    }
  }
  public void setTitle(String title) {
    this.title = title;
    mDialog.setTitle(title);
  }
  public void appendToMessage(String newMessage) {
    if (TextUtils.isEmpty(this.message)) {
      Log.i("DEBUG", "setting message to " + newMessage);
      this.message = newMessage;
    } else {
      Log.i("DEBUG", "appending message " + newMessage + " to " + this.message);
      this.message += "\n" + newMessage;
    }
    mDialog.setMessage(this.message);
  }
  public void onTaskCompleted() {
    mTaskCompleted = true;
    try {
      mDialog.setIndeterminateDrawable(null);
    } catch (NullPointerException e) {
      //seen on samsung SM-G900F
      AcraHelper.report(e);
    }
    mDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(true);
  }
  @Override
  public void onCancel (DialogInterface dialog) {
    if (getActivity()==null) {
      return;
    }
    ((MessageDialogListener) getActivity()).onMessageDialogDismissOrCancel();
  }
  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(KEY_TASK_COMPLETED, mTaskCompleted);
    outState.putString(KEY_TITLE, title);
    outState.putString(KEY_MESSAGE, message);
    outState.putInt(KEY_PROGRESS, progress);
    outState.putInt(KEY_MAX, max);
  }
}