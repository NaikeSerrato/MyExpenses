package org.totschnig.myexpenses.test.model;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.util.InappPurchaseLicenceHandler;
import org.totschnig.myexpenses.util.Utils;

import junit.framework.Assert;

public class ContribFeatureTest extends ModelTest  {
  
  public void testFormattedList() {
    Assert.assertNotNull(Utils.getContribFeatureLabelsAsFormattedList(getContext(), null));
  }
  public void testRecordUsage() {
    ContribFeature feature = ContribFeature.ATTACH_PICTURE;
    MyApplication app = (MyApplication) getContext().getApplicationContext();
    Assert.assertEquals(5,feature.usagesLeft());
    app.setContribStatus(InappPurchaseLicenceHandler.STATUS_DISABLED);
    feature.recordUsage();
    Assert.assertEquals(4,feature.usagesLeft());
    app.setContribStatus(InappPurchaseLicenceHandler.STATUS_ENABLED_PERMANENT);
    feature.recordUsage();
    Assert.assertEquals(4,feature.usagesLeft());
    app.setContribStatus(InappPurchaseLicenceHandler.STATUS_DISABLED);
    feature.recordUsage();
    Assert.assertEquals(3,feature.usagesLeft());
  }
}
