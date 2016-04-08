package org.totschnig.myexpenses.activity;

import java.io.Serializable;
import java.util.UUID;

import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.googleUtils.IabHelper;
import org.onepf.oms.appstore.googleUtils.IabResult;
import org.onepf.oms.appstore.googleUtils.Purchase;
import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.MyApplication.PrefKey;
import org.totschnig.myexpenses.contrib.Config;
import org.totschnig.myexpenses.dialog.ContribDialogFragment;
import org.totschnig.myexpenses.dialog.ContribInfoDialogFragment;
import org.totschnig.myexpenses.dialog.DonateDialogFragment;
import org.totschnig.myexpenses.dialog.MessageDialogFragment.MessageDialogListener;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.util.Distrib;
import org.totschnig.myexpenses.util.Utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

public class ContribInfoDialogActivity extends ProtectedFragmentActivity
    implements MessageDialogListener {
  protected long sequenceCount;
  public final static String KEY_FEATURE = "feature";
  public static final String KEY_TAG = "tag";
  private OpenIabHelper mHelper;
  private boolean mSetupDone;
  private String mPayload = (Distrib.IS_CHROMIUM || BuildConfig.FLAVOR.equals("amazon"))
      ? null : UUID.randomUUID().toString();
  private String tag = ContribInfoDialogActivity.class.getName();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTheme(MyApplication.getThemeIdTranslucent());

    mHelper = Distrib.getIabHelper(this);
    if (mHelper != null) {
      try {
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
          public void onIabSetupFinished(IabResult result) {
            Log.d(tag, "Setup finished.");

            if (!result.isSuccess()) {
              mSetupDone = false;
              // Oh noes, there was a problem.
              complain("Problem setting up in-app billing: " + result);
              return;
            }
            mSetupDone = true;
            Log.d(tag, "Setup successful.");
          }
        });
      } catch (SecurityException e) {
        Utils.reportToAcra(e);
        mHelper.dispose();
        mHelper = null;
        complain("Problem setting up in-app billing: " + e.getMessage());
      }
    }

    ContribFeature f = (ContribFeature) getIntent().getSerializableExtra(KEY_FEATURE);
    sequenceCount = getIntent().getLongExtra(
        ContribInfoDialogFragment.KEY_SEQUENCE_COUNT, -1);

    if (savedInstanceState == null) {
      if (f == null) {
        ContribInfoDialogFragment.newInstance(sequenceCount)
            .show(getSupportFragmentManager(), "CONTRIB_INFO");
      } else {
        ContribDialogFragment.newInstance(
            f, getIntent().getSerializableExtra(KEY_TAG))
            .show(getSupportFragmentManager(), "CONTRIB");
      }
    }
  }

  @Override
  public boolean dispatchCommand(int command, Object tag) {
    switch (command) {
      case R.id.REMIND_LATER_CONTRIB_COMMAND:
        PrefKey.NEXT_REMINDER_CONTRIB.putLong(
            sequenceCount + MyExpenses.TRESHOLD_REMIND_CONTRIB);
        break;
      case R.id.REMIND_NO_CONTRIB_COMMAND:
        PrefKey.NEXT_REMINDER_CONTRIB.putLong(-1);
    }
    finish();
    return true;
  }

  private void contribBuyBlackBerry() {
    Intent i = new Intent(Intent.ACTION_VIEW);
    i.setData(Uri.parse("appworld://content/57168887"));
    if (Utils.isIntentAvailable(this, i)) {
      startActivity(i);
    } else {
      Toast.makeText(
          this,
          R.string.error_accessing_market,
          Toast.LENGTH_LONG)
          .show();
    }
    finish();
  }

  public void contribBuyDo(boolean extended) {
    if (BuildConfig.FLAVOR.equals("blackberry")) {
      contribBuyBlackBerry();
      return;
    }
    if (mHelper == null) {
      finish();
      return;
    }
    if (!mSetupDone) {
      complain("Billing setup is not completed yet");
      finish();
      return;
    }
    final IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener =
        new IabHelper.OnIabPurchaseFinishedListener() {
          public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(tag,
                "Purchase finished: " + result + ", purchase: " + purchase);
            if (result.isFailure()) {
              Log.w(tag,
                  "Purchase failed: " + result + ", purchase: " + purchase);
              complain(getString(R.string.premium_failed_or_canceled));
            } else if (!verifyDeveloperPayload(purchase)) {
              complain("Error purchasing. Authenticity verification failed.");
            } else {
              Log.d(tag, "Purchase successful.");

              boolean isPremium = purchase.getSku().equals(Config.SKU_PREMIUM);
              if (isPremium ||
                  purchase.getSku().equals(Config.SKU_EXTENDED) ||
                  purchase.getSku().equals(Config.SKU_PREMIUM2EXTENDED)) {
                // bought the premium upgrade!
                Log.d(tag,
                    "Purchase is premium upgrade. Congratulating user.");
                Toast.makeText(
                    ContribInfoDialogActivity.this,
                    Utils.concatResStrings(
                        ContribInfoDialogActivity.this," ",
                        isPremium ? R.string.licence_validation_premium : R.string.licence_validation_extended,
                        R.string.thank_you),
                    Toast.LENGTH_SHORT).show();
                Distrib.registerPurchase(ContribInfoDialogActivity.this, !isPremium);
              }
            }
            finish();
          }

          private boolean verifyDeveloperPayload(Purchase purchase) {
            if (mPayload == null) {
              return true;
            }
            String payload = purchase.getDeveloperPayload();
            if (payload == null) {
              return false;
            }
            return payload.equals(mPayload);
          }
        };
    String sku = extended ?
        (MyApplication.getInstance().isContribEnabled() ?
            Config.SKU_PREMIUM2EXTENDED :
            Config.SKU_EXTENDED) :
        Config.SKU_PREMIUM;
    mHelper.launchPurchaseFlow(
        ContribInfoDialogActivity.this,
        sku,
        ProtectedFragmentActivity.PURCHASE_PREMIUM_REQUEST,
        mPurchaseFinishedListener,
        mPayload
    );
  }

  void complain(String message) {
    Log.e(tag, "**** InAppPurchase Error: " + message);
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onMessageDialogDismissOrCancel() {
    finish();
  }

  @Override
  public void finish() {
    final ContribFeature feature = (ContribFeature) getIntent().getSerializableExtra(KEY_FEATURE);
    if (feature != null) {
      Intent i = new Intent();
      i.putExtra(KEY_FEATURE, feature);
      i.putExtra(KEY_TAG, getIntent().getSerializableExtra(KEY_TAG));
      if (feature.hasAccess() || feature.usagesLeft() > 0) {
        setResult(RESULT_OK, i);
      } else {
        setResult(RESULT_CANCELED, i);
      }
    }
    super.finish();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d(tag,
        "onActivityResult() requestCode: " + requestCode +
            " resultCode: " + resultCode + " data: " + data);

    // Pass on the activity result to the helper for handling
    if (mHelper == null || !mHelper.handleActivityResult(requestCode, resultCode, data)) {
      // not handled, so handle it ourselves (here's where you'd
      // perform any handling of activity results not related to in-app
      // billing...
      finish();
    } else {
      Log.d(tag, "onActivityResult handled by IABUtil.");
    }
  }

  // We're being destroyed. It's important to dispose of the helper here!
  @Override
  public void onDestroy() {
    super.onDestroy();

    // very important:
    Log.d(tag, "Destroying helper.");
    if (mHelper != null) mHelper.dispose();
    mHelper = null;
  }
}
