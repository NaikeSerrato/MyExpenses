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

package org.totschnig.myexpenses.model;

import org.totschnig.myexpenses.provider.TransactionProvider;

import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import static org.totschnig.myexpenses.provider.DatabaseConstants.*;

/**
 * a transfer consists of a pair of transactions, one for each account
 * this class handles creation and update
 * @author Michael Totschnig
 *
 */
public class Transfer extends Transaction {
  
  public Transfer(long accountId, Long amount) {
    super(accountId,amount);
    this.transferAmount = new Money(this.amount.getCurrency(),this.amount.getAmountMajor().negate());
  }

  public Transfer(long accountId, Money amount) {
    super(accountId,amount);
    this.transferAmount = new Money(amount.getCurrency(),amount.getAmountMajor().negate());
  }

  public Transfer(Account account, long amount, Account transferAccount) {
    super(account,amount);
    this.transfer_account = transferAccount.getId();
    this.transferAmount = new Money(transferAccount.currency,this.amount.getAmountMajor().negate());
  }

  @Override
  public void setAmount(Money amount) {
    if (!amount.getCurrency().getCurrencyCode().equals(transferAmount.getCurrency().getCurrencyCode())) {
      throw new UnsupportedOperationException("for foreign exchange transfers, use setAmountAndTransferAmount");
    }
    super.setAmount(amount);
    this.transferAmount = new Money(amount.getCurrency(),amount.getAmountMajor().negate());
  }

  public void setAmountAndTransferAmount(Money amount, Money transferAmount) {
    this.amount = amount;
    this.transferAmount = transferAmount;
  }

  /**
   * @param accountId if account no longer exists {@link Account#getInstanceFromDb(long) is called with 0}
   * @param transferAccountId
   * @return
   */
  public static Transfer getNewInstance(long accountId, Long transferAccountId) {
    Account account = Account.getInstanceFromDbWithFallback(accountId);
    if (account == null) {
      return null;
    }
    Account transferAccount = Account.getInstanceFromDbWithFallback(transferAccountId);
    if (transferAccount == null) {
      return null;
    }
    return new Transfer(account,0L,transferAccount);
  }
  /* (non-Javadoc)
   * @see org.totschnig.myexpenses.Transaction#save()
   */
  public Uri save() {
    Uri uri;
    long amount = this.amount.getAmountMinor();
    long transferAmount = this.transferAmount.getAmountMinor();
    //the id of the peer_account is stored in KEY_TRANSFER_ACCOUNT,
    //the id of the peer transaction is stored in KEY_TRANSFER_PEER
    ContentValues initialValues = new ContentValues();
    initialValues.put(KEY_COMMENT, comment);
    initialValues.put(KEY_DATE, date.getTime()/1000);
    initialValues.put(KEY_AMOUNT, amount);
    initialValues.put(KEY_TRANSFER_ACCOUNT, transfer_account);
    initialValues.put(KEY_CR_STATUS,crStatus.name());
    initialValues.put(KEY_ACCOUNTID, accountId);
    savePicture(initialValues);
    if (getId() == 0) {
      initialValues.put(KEY_PARENTID, parentId);
      initialValues.put(KEY_STATUS, status);
      uri = cr().insert(CONTENT_URI, initialValues);
      setId(ContentUris.parseId(uri));
      //if the transfer is part of a split, the transfer peer needs to have a null parent
      initialValues.remove(KEY_PARENTID);
      initialValues.put(KEY_AMOUNT, transferAmount);
      initialValues.put(KEY_TRANSFER_ACCOUNT, accountId);
      initialValues.put(KEY_ACCOUNTID, transfer_account);
      initialValues.put(KEY_TRANSFER_PEER,getId());
      Uri transferUri = cr().insert(CONTENT_URI, initialValues);
      transfer_peer = ContentUris.parseId(transferUri);
      //we have to set the transfer_peer for the first transaction
      ContentValues args = new ContentValues();
      args.put(KEY_TRANSFER_PEER,transfer_peer);
      cr().update(Uri.parse(CONTENT_URI+ "/" + getId()), args, null, null);
      cr().update(
          TransactionProvider.ACCOUNTS_URI.buildUpon()
            .appendPath(String.valueOf(accountId))
            .appendPath(TransactionProvider.URI_SEGMENT_INCREASE_USAGE)
            .build(),
          null, null, null);
      cr().update(
          TransactionProvider.ACCOUNTS_URI.buildUpon()
            .appendPath(String.valueOf(transfer_account))
            .appendPath(TransactionProvider.URI_SEGMENT_INCREASE_USAGE)
            .build(),
          null, null, null);
    } else {
      uri = Uri.parse(CONTENT_URI + "/" + getId());
      cr().update(uri,initialValues,null,null);
      initialValues.put(KEY_AMOUNT, transferAmount);
      //if the user has changed the account to which we should transfer,
      //in the peer transaction we need to update the account_id
      initialValues.put(KEY_ACCOUNTID, transfer_account);
      //the account from which is transfered could also have been altered
      initialValues.put(KEY_TRANSFER_ACCOUNT,accountId);
      cr().update(Uri.parse(CONTENT_URI + "/" + transfer_peer),initialValues,null,null);
    }
    return uri;
  }

  public boolean isSameCurrency() {
    return amount.getCurrency().equals(transferAmount.getCurrency());
  }
}
