package org.totschnig.myexpenses.test.provider;

import org.totschnig.myexpenses.model.Payee;
import org.totschnig.myexpenses.model.Transaction.CrStatus;
import org.totschnig.myexpenses.provider.DatabaseConstants;

import android.content.ContentValues;

// A utility for converting note data to a ContentValues map.
class TransactionInfo {
    String comment;
    long amount;
    String date;
    String payeeName= "N.N";
    long payeeId;
    long accountId;
    /*
     * Constructor for a NoteInfo instance. This class helps create a note and
     * return its values in a ContentValues map expected by data model methods.
     * The note's id is created automatically when it is inserted into the data model.
     */
    public TransactionInfo(String comment, String date, long amount, long accountId) {
      this.comment = comment;
      this.date = date;
      this.amount = amount;
      Long payee = Payee.require(payeeName);
      this.payeeId = payee == null ? 0 : payee;
      this.accountId = accountId;
    }

    /*
     * Returns a ContentValues instance (a map) for this NoteInfo instance. This is useful for
     * inserting a NoteInfo into a database.
     */
    public ContentValues getContentValues() {
        // Gets a new ContentValues object
        ContentValues v = new ContentValues();

        // Adds map entries for the user-controlled fields in the map
        v.put(DatabaseConstants.KEY_COMMENT, comment);
        v.put(DatabaseConstants.KEY_DATE, date);
        v.put(DatabaseConstants.KEY_AMOUNT, amount);
        v.put(DatabaseConstants.KEY_PAYEEID, payeeId);
        v.put(DatabaseConstants.KEY_ACCOUNTID, accountId);
        v.put(DatabaseConstants.KEY_CR_STATUS, CrStatus.UNRECONCILED.name());
        return v;
    }
}