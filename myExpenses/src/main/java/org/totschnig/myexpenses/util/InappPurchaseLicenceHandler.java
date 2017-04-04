package org.totschnig.myexpenses.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings.Secure;

import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.PreferenceObfuscator;

import org.onepf.oms.Appstore;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.AmazonAppstore;
import org.totschnig.myexpenses.contrib.Config;
import org.totschnig.myexpenses.preference.PrefKey;

import java.util.ArrayList;

import timber.log.Timber;

public class InappPurchaseLicenceHandler extends LicenceHandler {

  private String contribStatus = InappPurchaseLicenceHandler.STATUS_DISABLED;
  public static boolean IS_CHROMIUM = Build.BRAND.equals("chromium");

  private static final long REFUND_WINDOW = 172800000L;
  public static final String STATUS_DISABLED = "0";
  
  /**
   * this status was used before and including the APP_GRATIS campaign
   */
  public static final String STATUS_ENABLED_LEGACY_FIRST = "1";
  /**
   * this status was used after the APP_GRATIS campaign in order to distinguish
   * between free riders and buyers
   */
  public static final String STATUS_ENABLED_LEGACY_SECOND = "2";

  /**
   * user has recently purchased, and is inside a two days window
   */
  public static final String STATUS_ENABLED_TEMPORARY = "3";

  /**
   * user has recently purchased, and is inside a two days window
   */
  public static final String STATUS_ENABLED_VERIFICATION_NEEDED = "4";
  
  /**
   * recheck passed
   */
  public static final String STATUS_ENABLED_PERMANENT = "5";

  public static final String STATUS_EXTENDED_TEMPORARY = "6";

  public static final String STATUS_EXTENDED_PERMANENT = "7";
  private PreferenceObfuscator licenseStatusPrefs;

  public InappPurchaseLicenceHandler(Context context) {
    super(context);
  }

  @Override
  public void init() {
    requireLicenseStatusPrefs();
    super.init();
  }

  private void requireLicenseStatusPrefs() {
    if (licenseStatusPrefs == null) {
      String PREFS_FILE = "license_status";
      String deviceId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
      //TODO move to content provider, eventually https://github.com/grandcentrix/tray
      SharedPreferences sp = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
      byte[] SALT = new byte[]{
          -1, -124, -4, -59, -52, 1, -97, -32, 38, 59, 64, 13, 45, -104, -3, -92, -56, -49, 65, -25
      };
      licenseStatusPrefs = new PreferenceObfuscator(
          sp, new AESObfuscator(SALT, context.getPackageName(), deviceId));
    }
  }


  /**
   * this is used from in-app billing
   * @param extended if true user has purchase extended licence
   */
  public void registerPurchase(boolean extended) {
    requireLicenseStatusPrefs();
    String status = extended ? STATUS_EXTENDED_TEMPORARY : STATUS_ENABLED_TEMPORARY;
    long timestamp = Long.parseLong(licenseStatusPrefs.getString(
        PrefKey.LICENSE_INITIAL_TIMESTAMP.getKey(),"0"));
    long now = System.currentTimeMillis();
    if (timestamp == 0L) {
      licenseStatusPrefs.putString(PrefKey.LICENSE_INITIAL_TIMESTAMP.getKey(),
          String.valueOf(now));
    } else {
      long timeSincePurchase = now - timestamp;
      Timber.d("time since initial check : %d", timeSincePurchase);
        //give user 2 days to request refund
      if (timeSincePurchase> REFUND_WINDOW) {
        status = extended ? STATUS_EXTENDED_PERMANENT : STATUS_ENABLED_PERMANENT;
      }
    }
    licenseStatusPrefs.putString(PrefKey.LICENSE_STATUS.getKey(), status);
    licenseStatusPrefs.commit();
    refresh(true);
  }

  public void registerUnlockLegacy() {
    requireLicenseStatusPrefs();
    licenseStatusPrefs.putString(PrefKey.LICENSE_STATUS.getKey(), String.valueOf(InappPurchaseLicenceHandler.STATUS_ENABLED_LEGACY_SECOND));
    licenseStatusPrefs.commit();
    refresh(true);
  }

  public static OpenIabHelper getIabHelper(Context ctx) {
    if (DistribHelper.isBlackberry()) {
      return null;
    }
    OpenIabHelper.Options.Builder builder =
        new OpenIabHelper.Options.Builder()
            .setVerifyMode(OpenIabHelper.Options.VERIFY_EVERYTHING)
            .addStoreKeys(Config.STORE_KEYS_MAP);


    builder.setStoreSearchStrategy(OpenIabHelper.Options.SEARCH_STRATEGY_INSTALLER_THEN_BEST_FIT);
    if (!IS_CHROMIUM) {
      if (DistribHelper.isPlay()) {
        builder.addAvailableStoreNames("com.google.play");
      } else if (DistribHelper.isAmazon()) {
        ArrayList<Appstore> stores = new ArrayList<Appstore>();
        stores.add(new AmazonAppstore(ctx) {
          public boolean isBillingAvailable(String packageName) {
            return true;
          }
        });
        builder.addAvailableStores(stores);
      }
    }
    return new OpenIabHelper(ctx, builder.build());
  }

  /**
   * After 2 days, if purchase cannot be verified, we set back
   */
  public void maybeCancel() {
    requireLicenseStatusPrefs();
    long timestamp = Long.parseLong(licenseStatusPrefs.getString(
        PrefKey.LICENSE_INITIAL_TIMESTAMP.getKey(), "0"));
    long now = System.currentTimeMillis();
    long timeSincePurchase = now - timestamp;
    if (timeSincePurchase> REFUND_WINDOW) {
       licenseStatusPrefs.putString(PrefKey.LICENSE_STATUS.getKey(), STATUS_DISABLED);
      licenseStatusPrefs.commit();
      refresh(true);
    }
  }

  @Override
  public void refreshDo() {
    requireLicenseStatusPrefs();
    String contribStatus = licenseStatusPrefs.getString(PrefKey.LICENSE_STATUS.getKey(), STATUS_DISABLED);
    Timber.d("contrib status is now %s", contribStatus);
    setContribStatus(contribStatus);
  }

  @Override
  public boolean isContribEnabled() {
    return ! contribStatus.equals(InappPurchaseLicenceHandler.STATUS_DISABLED);
  }

  @Override
  public boolean isExtendedEnabled() {
    if (!HAS_EXTENDED) {
      return isContribEnabled();
    }
    return contribStatus.equals(InappPurchaseLicenceHandler.STATUS_EXTENDED_PERMANENT) ||
        contribStatus.equals(InappPurchaseLicenceHandler.STATUS_EXTENDED_TEMPORARY);
  }

  @Override
  protected void setLockStateDo(boolean locked) {
    setContribStatus(locked ? STATUS_DISABLED : STATUS_ENABLED_PERMANENT);
    invalidate();
  }

  private void setContribStatus(String contribStatus) {
    this.contribStatus = contribStatus;
  }

  public String getContribStatus() {
    return contribStatus;
  }

}