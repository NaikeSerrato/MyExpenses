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
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ContribInfoDialogActivity;
import org.totschnig.myexpenses.dialog.MessageDialogFragment.MessageDialogListener;
import org.totschnig.myexpenses.util.InappPurchaseLicenceHandler;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.util.LicenceHandlerIFace;
import org.totschnig.myexpenses.util.Utils;

import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;

public class ContribInfoDialogFragment  extends CommitSafeDialogFragment implements OnClickListener {

  public static final String KEY_SEQUENCE_COUNT = "sequenceCount";
  /**
   * @param sequenceCount yes if we are called from a reminder
   * @return
   */
  public static final ContribInfoDialogFragment newInstance(long sequenceCount) {
    ContribInfoDialogFragment dialogFragment = new ContribInfoDialogFragment();
    Bundle bundle = new Bundle();
    bundle.putLong(KEY_SEQUENCE_COUNT, sequenceCount);
    if (sequenceCount!=-1) {
      dialogFragment.setCancelable(false);
    }
    dialogFragment.setArguments(bundle);
    return dialogFragment;
  }
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    boolean isContrib = MyApplication.getInstance().getLicenceHandler().isContribEnabled();
    String pro = getString(R.string.dialog_contrib_extended_gain_access);
    CharSequence linefeed = Html.fromHtml("<br><br>"),
      message = TextUtils.concat(
          Utils.IS_FLAVOURED ? "" : getText(R.string.dialog_contrib_text_1),
          Html.fromHtml(
              getString(
                  R.string.dialog_contrib_text_2,
                  "<i>" + ContribFeature.buildKeyFullName(getActivity(),isContrib) + "</i>")),
          " ",
          getString(R.string.dialog_contrib_reminder_gain_access),
          linefeed,
          Utils.getContribFeatureLabelsAsFormattedList(getActivity(),null,
              isContrib ? LicenceHandlerIFace.LicenceStatus.EXTENDED : LicenceHandlerIFace.LicenceStatus.CONTRIB));
    if (!isContrib && LicenceHandlerIFace.HAS_EXTENDED) {
      CharSequence extendedList = Utils.getContribFeatureLabelsAsFormattedList(
          getActivity(),null, LicenceHandlerIFace.LicenceStatus.EXTENDED);
      message = TextUtils.concat(
          message,
          linefeed,
          pro,
          linefeed,
          extendedList);
    }
    message = TextUtils.concat(
        message,
        linefeed,
        getString(R.string.thank_you));
    //tv.setMovementMethod(LinkMovementMethod.getInstance());
    AlertDialog.Builder builder =  new AlertDialog.Builder(getActivity())
      .setTitle(R.string.menu_contrib);
      builder.setMessage(message);
      if (getArguments().getLong(KEY_SEQUENCE_COUNT)!=-1) {
        builder.setNeutralButton(R.string.dialog_remind_later, this)
            .setNegativeButton(R.string.dialog_remind_no, this)
            .setPositiveButton(R.string.dialog_contrib_buy_premium, this);
      } else {
        if (!isContrib) {
          builder.setNeutralButton(R.string.dialog_contrib_buy_premium, this);
          if (LicenceHandlerIFace.HAS_EXTENDED) {
            builder.setPositiveButton(R.string.dialog_contrib_buy_extended, this);
          }
        } else {
          builder.setPositiveButton(R.string.dialog_contrib_upgrade_extended, this);
        }
        builder.setNegativeButton(R.string.dialog_contrib_no, new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            onCancel(dialog);
          }
        });
      }
    return builder.create();
  }
  @Override
  public void onCancel (DialogInterface dialog) {
    if (getActivity()==null) {
      return;
    }
    ((MessageDialogListener) getActivity()).onMessageDialogDismissOrCancel();
  }
  @Override
  public void onClick(DialogInterface dialog, int which) {
    if (getActivity()==null) {
      return;
    }
    ContribInfoDialogActivity ctx = (ContribInfoDialogActivity) getActivity();
    if (getArguments().getLong(KEY_SEQUENCE_COUNT)!=-1) {
      if (which == AlertDialog.BUTTON_POSITIVE) {
        ctx.contribBuyDo(false);
      } else if (which == AlertDialog.BUTTON_NEUTRAL) {
        ctx.dispatchCommand(R.id.REMIND_LATER_CONTRIB_COMMAND, null);
      } else {//negative
        ctx.dispatchCommand(R.id.REMIND_NO_CONTRIB_COMMAND, null);
      }
    } else {
      if (which == AlertDialog.BUTTON_POSITIVE) {
        ctx.contribBuyDo(true);
      } else {//neutral
        ctx.contribBuyDo(false);
      }
    }
  }
}