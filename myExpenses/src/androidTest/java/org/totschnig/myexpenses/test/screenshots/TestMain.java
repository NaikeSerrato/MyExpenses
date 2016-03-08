package org.totschnig.myexpenses.test.screenshots;

import java.util.Currency;
import java.util.Locale;

import junit.framework.Assert;

import android.content.Context;
import android.content.res.Configuration;
import android.provider.Settings.Secure;
import android.test.ActivityInstrumentationTestCase2;

import org.junit.Ignore;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.activity.CommonCommands;
import org.totschnig.myexpenses.preference.SharedPreferencesCompat;
import org.totschnig.myexpenses.test.util.Fixture;
import org.totschnig.myexpenses.util.Distrib;
import org.totschnig.myexpenses.activity.MyExpenses;

/**
 * These tests are meant to be run with script/testLangs.sh
 * since they depend on the reinitialisation of the db
 * and they prepare the db for script/monkey.py which
 * runs through the app and creates screenshots
 * @author Michael Totschnig
 *
 */
@Ignore
public class TestMain extends ActivityInstrumentationTestCase2<MyExpenses> {
	private MyApplication app;
	private Context instCtx;
	private Locale locale;
	private Currency defaultCurrency;
	
	public TestMain() {
		super(MyExpenses.class);
	}
	
	@Override
	protected void setUp() throws Exception {
    instCtx = getInstrumentation().getContext();
	  app = (MyApplication) getInstrumentation().getTargetContext().getApplicationContext(); 
		super.setUp();
	}
	public void testLang_en() {
    defaultCurrency = Currency.getInstance("USD");
	  helperTestLang("en", "US");
	}
  public void testLang_fr() {
    defaultCurrency = Currency.getInstance("EUR");
    helperTestLang("fr","FR");
  }
  public void testLang_de() {
    defaultCurrency = Currency.getInstance("EUR");
    helperTestLang("de","DE");
  }
  public void testLang_it() {
    defaultCurrency = Currency.getInstance("EUR");
    helperTestLang("it","IT");
  }
  public void testLang_es() {
    defaultCurrency = Currency.getInstance("EUR");
    helperTestLang("es","ES");
  }
  public void testLang_tr() {
    defaultCurrency = Currency.getInstance("TRY");
    helperTestLang("tr","TR");
  }
  public void testLang_vi() {
    //Currency.getInstance(new Locale("vi","VI") => USD on Nexus S
    defaultCurrency = Currency.getInstance("VND");
    helperTestLang("vi","VI");
  }
  public void testLang_ar() {
    defaultCurrency = Currency.getInstance("SAR");
    helperTestLang("ar","SA");
  }
  public void testLang_hu() {
    defaultCurrency = Currency.getInstance("HUF");
    helperTestLang("hu","HU");
  }
  public void testLang_ca() {
    defaultCurrency = Currency.getInstance("EUR");
    helperTestLang("ca","ES");
  }
  public void testLang_km() {
    defaultCurrency = Currency.getInstance("KHR");
    helperTestLang("km","KH");
  }
  public void testLang_zh() {
    defaultCurrency = Currency.getInstance("TWD");
    helperTestLang("zh","TW");
  }
  public void testLang_pt() {
    defaultCurrency = Currency.getInstance("BRL");
    helperTestLang("pt","BR");
  }
  public void testLang_pl() {
    defaultCurrency = Currency.getInstance("PLN");
    helperTestLang("pl","PL");
  }
  public void testLang_cs() {
    defaultCurrency = Currency.getInstance("CZK");
    helperTestLang("cs","CZ");
  }
  public void testLang_ru() {
    defaultCurrency = Currency.getInstance("RUB");
    helperTestLang("ru","RU");
  }
  public void testLang_hr() {
    defaultCurrency = Currency.getInstance("HRK");
    helperTestLang("hr","HR");
  }
  public void testLang_ja() {
    defaultCurrency = Currency.getInstance("JPY");
    helperTestLang("ja","JA");
  }
  public void testLang_ms() {
    defaultCurrency = Currency.getInstance("MYR");
    helperTestLang("ms","MY");
  }
  public void testLang_ro() {
    defaultCurrency = Currency.getInstance("RON");
    helperTestLang("ro","RO");
  }
  public void testLang_si() {
    defaultCurrency = Currency.getInstance("LKR");
    helperTestLang("si","SI");
  }
  public void testLang_eu() {
    defaultCurrency = Currency.getInstance("EUR");
    helperTestLang("eu","ES");
  }
  public void testLang_da() {
    defaultCurrency = Currency.getInstance("DKK");
    helperTestLang("da","DK");
  }
  public void testLang_bg() {
    defaultCurrency = Currency.getInstance("BGN");
    helperTestLang("bg","BG");
  }
	private void helperTestLang(String lang, String country) {
	  this.locale = new Locale(lang,country);
	  Locale.setDefault(locale); 
	  Configuration config = new Configuration(); 
	  config.locale = locale;
	  app.getResources().updateConfiguration(config,  
	      app.getResources().getDisplayMetrics());
    instCtx.getResources().updateConfiguration(config,  
        instCtx.getResources().getDisplayMetrics());
    //set language and contrib key as preference,
//    String s = Secure.getString(MyApplication.getInstance().getContentResolver(),Secure.ANDROID_ID) + 
//        MyApplication.CONTRIB_SECRET;
//    Long l = (s.hashCode() & 0x00000000ffffffffL);
    android.content.SharedPreferences pref = app.getSettings();
    if (pref==null)
      Assert.fail("Could not find prefs");
    SharedPreferencesCompat.apply(pref.edit()
        .putString(MyApplication.PrefKey.UI_LANGUAGE.getKey(), lang + "-"+country)
        //.putString(MyApplication.PrefKey.ENTER_LICENCE.getKey(), l.toString())
    );
    app.setContribStatus(Distrib.STATUS_ENABLED_PERMANENT);

    getActivity();
	  Fixture.setup(getInstrumentation(), locale, defaultCurrency);
    int current_version = CommonCommands.getVersionNumber(getActivity());
    SharedPreferencesCompat.apply(pref.edit()
        .putLong(MyApplication.PrefKey.CURRENT_ACCOUNT.getKey(), Fixture.getAccount3().getId())
        .putInt(MyApplication.PrefKey.CURRENT_VERSION.getKey(), current_version)
        .putInt(MyApplication.PrefKey.FIRST_INSTALL_VERSION.getKey(), current_version));
	}
}