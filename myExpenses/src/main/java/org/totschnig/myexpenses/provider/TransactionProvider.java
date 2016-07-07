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

package org.totschnig.myexpenses.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.model.*;
import org.totschnig.myexpenses.model.Account.Grouping;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.util.PlanInfoCursorWrapper;
import org.totschnig.myexpenses.util.Utils;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;

import static org.totschnig.myexpenses.provider.DatabaseConstants.*;

public class TransactionProvider extends ContentProvider {

  protected static TransactionDatabase mOpenHelper;
  public static final String AUTHORITY = BuildConfig.APPLICATION_ID;
  public static final Uri ACCOUNTS_URI =
      Uri.parse("content://" + AUTHORITY + "/accounts");
  //when we need the accounts cursor without the current balance
  //we do not want the cursor to be reloaded when a transaction is added
  //hence we access it through a different URI
  public static final Uri ACCOUNTS_BASE_URI =
      Uri.parse("content://" + AUTHORITY + "/accounts/base");
  public static final Uri ACCOUNTS_AGGREGATE_URI =
      Uri.parse("content://" + AUTHORITY + "/accounts/aggregates");
  public static final Uri TRANSACTIONS_URI =
      Uri.parse("content://" + AUTHORITY + "/transactions");
  public static final Uri UNCOMMITTED_URI =
      Uri.parse("content://" + AUTHORITY + "/transactions/uncommitted");
  public static final Uri TEMPLATES_URI =
      Uri.parse("content://" + AUTHORITY + "/templates");
  public static final Uri CATEGORIES_URI =
      Uri.parse("content://" + AUTHORITY + "/categories");
  public static final Uri AGGREGATES_COUNT_URI =
      Uri.parse("content://" + AUTHORITY + "/accounts/aggregatesCount");
  public static final Uri PAYEES_URI =
      Uri.parse("content://" + AUTHORITY + "/payees");
  public static final Uri MAPPED_PAYEES_URI =
      Uri.parse("content://" + AUTHORITY + "/payees_transactions");
  public static final Uri METHODS_URI =
      Uri.parse("content://" + AUTHORITY + "/methods");
  public static final Uri MAPPED_METHODS_URI =
      Uri.parse("content://" + AUTHORITY + "/methods_transactions");
  public static final Uri ACCOUNTTYPES_METHODS_URI =
      Uri.parse("content://" + AUTHORITY + "/accounttypes_methods");
  public static final Uri SQLITE_SEQUENCE_TRANSACTIONS_URI =
      Uri.parse("content://" + AUTHORITY + "/sqlite_sequence/" + TABLE_TRANSACTIONS);
  public static final Uri PLAN_INSTANCE_STATUS_URI = 
      Uri.parse("content://" + AUTHORITY + "/planinstance_transaction/");
  public static final Uri CURRENCIES_URI =
      Uri.parse("content://" + AUTHORITY + "/currencies");
  public static final Uri TRANSACTIONS_SUM_URI =
      Uri.parse("content://" + AUTHORITY + "/transactions/sumsForAccountsGroupedByType");
  public static final Uri EVENT_CACHE_URI = 
      Uri.parse("content://" + AUTHORITY + "/eventcache");
  public static final Uri DEBUG_SCHEMA_URI =
      Uri.parse("content://" + AUTHORITY + "/debug_schema");
  public static final Uri STALE_IMAGES_URI =
      Uri.parse("content://" + AUTHORITY + "/stale_images");
  public static final Uri MAPPED_TRANSFER_ACCOUNTS_URI =
      Uri.parse("content://" + AUTHORITY + "/transfer_account_transactions");
  /**
   * select info from DB without table, e.g. CategoryList#DATEINFO_CURSOR
   */
  public static final Uri DUAL_URI = 
      Uri.parse("content://" + AUTHORITY + "/dual");
  public static final String URI_SEGMENT_MOVE = "move";
  public static final String URI_SEGMENT_TOGGLE_CRSTATUS = "toggleCrStatus";
  public static final String URI_SEGMENT_UNDELETE = "undelete";
  public static final String URI_SEGMENT_INCREASE_USAGE = "increaseUsage";
  public static final String URI_SEGMENT_GROUPS = "groups";
  public static final String URI_SEGMENT_CHANGE_FRACTION_DIGITS = "changeFractionDigits"; 
  public static final String URI_SEGMENT_TYPE_FILTER = "typeFilter";
  public static final String URI_SEGMENT_LAST_EXCHANGE = "lastExchange";
  public static final String URI_SEGMENT_SWAP_SORT_KEY = "swapSortKey";
  public static final String QUERY_PARAMETER_MERGE_CURRENCY_AGGREGATES = "mergeCurrencyAggregates";
  public static final String QUERY_PARAMETER_IS_FILTERED = "isFiltered";
  public static final String QUERY_PARAMETER_EXTENDED = "extended";
  public static final String QUERY_PARAMETER_DISTINCT = "distinct";
  public static final String QUERY_PARAMETER_MARK_VOID = "markVoid";
  public static final String QUERY_PARAMETER_WITH_PLAN_INFO = "withPlanInfo";

  
  static final String TAG = "TransactionProvider";

  private static final UriMatcher URI_MATCHER;
  //Basic tables
  private static final int TRANSACTIONS = 1;
  private static final int TRANSACTION_ID = 2;
  private static final int CATEGORIES = 3;
  private static final int ACCOUNTS = 4;
  private static final int ACCOUNTS_BASE = 5;
  private static final int ACCOUNT_ID = 6;
  private static final int PAYEES = 7;
  private static final int METHODS = 8;
  private static final int METHOD_ID = 9;
  private static final int ACCOUNTTYPES_METHODS = 10;
  private static final int TEMPLATES = 11;
  private static final int TEMPLATE_ID = 12;
  private static final int CATEGORY_ID = 13;
  private static final int CATEGORY_INCREASE_USAGE = 14;
  private static final int PAYEE_ID = 15;
  private static final int METHODS_FILTERED = 16;
  private static final int TEMPLATES_INCREASE_USAGE = 17;
  private static final int SQLITE_SEQUENCE_TABLE = 19;
  private static final int AGGREGATE_ID = 20;
  private static final int UNCOMMITTED = 21;
  private static final int TRANSACTIONS_GROUPS = 22;
  private static final int ACCOUNT_INCREASE_USAGE = 23;
  private static final int TRANSACTIONS_SUMS = 24;
  private static final int TRANSACTION_MOVE = 25;
  private static final int PLANINSTANCE_TRANSACTION_STATUS = 26;
  private static final int CURRENCIES = 27;
  private static final int AGGREGATES_COUNT = 28;
  private static final int TRANSACTION_TOGGLE_CRSTATUS = 29;
  private static final int MAPPED_PAYEES = 30;
  private static final int MAPPED_METHODS = 31;
  private static final int DUAL = 32;
  private static final int CURRENCIES_CHANGE_FRACTION_DIGITS = 33;
  private static final int EVENT_CACHE = 34;
  private static final int DEBUG_SCHEMA = 35;
  private static final int STALE_IMAGES = 36;
  private static final int STALE_IMAGES_ID = 37;
  private static final int TRANSACTION_UNDELETE = 38;
  private static final int TRANSACTIONS_LASTEXCHANGE = 39;
  private static final int ACCOUNTS_SWAP_SORT_KEY = 40;
  private static final int MAPPED_TRANSFER_ACCOUNTS = 41;
  

  protected static boolean mDirty = false;

  @Override
  public boolean onCreate() {
    mOpenHelper = new TransactionDatabase(getContext());
    return true;
  }

  private void setDirty() {
    if (!mDirty) {
      mDirty = true;
      MyApplication.markDataDirty();
    }
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection,
      String[] selectionArgs, String sortOrder) {
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    SQLiteDatabase db;
    db = mOpenHelper.getReadableDatabase();

    Cursor c;

    log("Query for URL: " + uri);
    String defaultOrderBy = null;
    String groupBy = null;
    String having = null;
    String limit = null;

    String accountSelectionQuery;
    String accountSelector;
    int uriMatch = URI_MATCHER.match(uri);
    switch (uriMatch) {
    case TRANSACTIONS:
      boolean extended = uri.getQueryParameter(QUERY_PARAMETER_EXTENDED) != null;
      qb.setTables(extended ? VIEW_EXTENDED : VIEW_COMMITTED);
      if (uri.getQueryParameter(QUERY_PARAMETER_DISTINCT) != null) {
        qb.setDistinct(true);
      }
      defaultOrderBy = KEY_DATE + " DESC";
      if (projection == null)
        projection = extended ? Transaction.PROJECTION_EXTENDED : Transaction.PROJECTION_BASE;
      break;
    case UNCOMMITTED:
      qb.setTables(VIEW_UNCOMMITTED);
      defaultOrderBy = KEY_DATE + " DESC";
      if (projection == null)
        projection = Transaction.PROJECTION_BASE;
      break;
    case TRANSACTION_ID:
      qb.setTables(VIEW_ALL);
      qb.appendWhere(KEY_ROWID + "=" + uri.getPathSegments().get(1));
      break;
    case TRANSACTIONS_SUMS:
      accountSelector = uri.getQueryParameter(KEY_ACCOUNTID);
      if (accountSelector == null) {
        accountSelector = uri.getQueryParameter(KEY_CURRENCY);
        accountSelectionQuery = " IN " +
            "(SELECT " + KEY_ROWID + " FROM " + TABLE_ACCOUNTS + " WHERE " + KEY_CURRENCY + " = ? AND " +
          KEY_EXCLUDE_FROM_TOTALS + "=0)";
      } else {
        accountSelectionQuery = " = ?";
      }
      qb.setTables(VIEW_COMMITTED);
      projection = new String[] {"amount>0 as " + KEY_TYPE,"abs(sum(amount)) as  " + KEY_SUM};
      groupBy = KEY_TYPE;
      qb.appendWhere(WHERE_TRANSACTION);
      qb.appendWhere(" AND " + KEY_ACCOUNTID + accountSelectionQuery);
      selectionArgs = new String[]{accountSelector};
      break;
    case TRANSACTIONS_GROUPS:
      String accountSelectionQueryOpeningBalance;
      accountSelector = uri.getQueryParameter(KEY_ACCOUNTID);
      if (accountSelector == null) {
        accountSelector = uri.getQueryParameter(KEY_CURRENCY);
        accountSelectionQuery = accountSelectionQueryOpeningBalance 
            = KEY_CURRENCY + " = ? AND "+ KEY_EXCLUDE_FROM_TOTALS + " = 0"; 
      } else {
        accountSelectionQuery = KEY_ACCOUNTID + " = ?";
        accountSelectionQueryOpeningBalance = KEY_ROWID + " = ?";
      }
      boolean isFiltered = uri.getQueryParameter(QUERY_PARAMETER_IS_FILTERED)!=null;

      Grouping group;
      try {
        group = Grouping.valueOf(uri.getPathSegments().get(2));
      } catch (IllegalArgumentException e) {
        group = Grouping.NONE;
      }
      String yearExpression;
      switch (group) {
        case WEEK:
          yearExpression = getYearOfWeekStart();
          break;
        case MONTH:
          yearExpression = getYearOfMonthStart();
          break;
        default:
          yearExpression = YEAR;
      }
//      String secondColumnAlias = " AS " + KEY_SECOND_GROUP;
//      if (group.equals(Grouping.NONE)) {
//        qb.setTables(VIEW_COMMITTED);
//        selection = accountSelection;
//        //the second accountId is used in openingBalanceSubquery
//        selectionArgs = new String[]{accountSelector,accountSelector};
//        projection = new String[] {
//            "1 AS " + KEY_YEAR,
//            "1"+secondColumnAlias,
//            INCOME_SUM,
//            EXPENSE_SUM,
//            TRANSFER_SUM,
//            MAPPED_CATEGORIES,
//            openingBalanceSubQuery
//                + " + coalesce(sum(CASE WHEN " + WHERE_NOT_SPLIT + " THEN " + KEY_AMOUNT + " ELSE 0 END),0) AS " + KEY_INTERIM_BALANCE
//        };
//      } else {
        String subGroupBy = KEY_YEAR + "," + KEY_SECOND_GROUP;
        String secondDef ="";

        switch(group) {
        case NONE:
          yearExpression = "1";
          secondDef = "1";
          break;
        case DAY:
          secondDef = DAY;
          break;
        case WEEK:
          secondDef = getWeek();
          break;
        case MONTH:
          secondDef = getMonth();
          break;
        case YEAR:
          secondDef = "1";
          subGroupBy = KEY_YEAR;
          break;
        }
        qb.setTables("(SELECT "
            + yearExpression + " AS " + KEY_YEAR + ","
            + secondDef + " AS " + KEY_SECOND_GROUP + ","
            + INCOME_SUM + ","
            + EXPENSE_SUM + ","
            + TRANSFER_SUM + ","
            + MAPPED_CATEGORIES
            + " FROM " + VIEW_EXTENDED
            + " WHERE " + accountSelectionQuery
            + (selection!=null ? " AND " + selection : "")
            + " GROUP BY " + subGroupBy + ") AS t");
      projection = new String[7];
      projection[0] = KEY_YEAR;
      projection[1] = KEY_SECOND_GROUP;
      projection[2] = KEY_SUM_INCOME;
      projection[3] = KEY_SUM_EXPENSES;
      projection[4] = KEY_SUM_TRANSFERS;
      projection[5] = KEY_MAPPED_CATEGORIES;
      String[] accountArgs;
      if (!isFiltered) {
        String openingBalanceSubQuery =
            "(SELECT sum(" + KEY_OPENING_BALANCE + ") FROM " + TABLE_ACCOUNTS + " WHERE " + accountSelectionQueryOpeningBalance + ")";
        String deltaExpr = "(SELECT sum(amount) FROM "
            + VIEW_EXTENDED
            + " WHERE " + accountSelectionQuery + " AND " + WHERE_NOT_SPLIT + " AND " + WHERE_NOT_VOID
            + " AND (" + yearExpression + " < " + KEY_YEAR + " OR "
            + "(" + yearExpression + " = " + KEY_YEAR + " AND "
            + secondDef + " <= " + KEY_SECOND_GROUP + ")))";
        projection[6] = openingBalanceSubQuery + " + " + deltaExpr + " AS " + KEY_INTERIM_BALANCE;
        accountArgs = new String[]{accountSelector,accountSelector,accountSelector};
      } else {
        projection[6] = "0 AS " + KEY_INTERIM_BALANCE;//ignored
        accountArgs = new String[]{accountSelector};
      }
        defaultOrderBy = KEY_YEAR + " DESC," + KEY_SECOND_GROUP + " DESC";
        //CAST(strftime('%Y',date) AS integer)
        //the accountId is used three times , once in the table subquery, twice in the KEY_INTERIM_BALANCE subquery
        //(first in the where clause, second in the subselect for the opening balance),
        log("SelectionArgs before join : " + Arrays.toString(selectionArgs));
        selectionArgs = Utils.joinArrays(
            accountArgs,
            selectionArgs);
        //selection is used in the inner table, needs to be set to null for outer query
        selection=null;
      //}
      break;
    case CATEGORIES:
      qb.setTables(TABLE_CATEGORIES);
      qb.appendWhere(KEY_ROWID+ " != " + SPLIT_CATID);
      if (projection == null) {
        projection = Category.PROJECTION;
      }
      defaultOrderBy = Utils.defaultOrderBy(KEY_LABEL, PrefKey.SORT_ORDER_CATEGORIES);
      break;
    case CATEGORY_ID:
      qb.setTables(TABLE_CATEGORIES);
      qb.appendWhere(KEY_ROWID + "=" + uri.getPathSegments().get(1));
      break;
    case ACCOUNTS:
    case ACCOUNTS_BASE:
      qb.setTables(TABLE_ACCOUNTS);
      boolean mergeCurrencyAggregates = uri.getQueryParameter(QUERY_PARAMETER_MERGE_CURRENCY_AGGREGATES) != null;
      defaultOrderBy =  Utils.defaultOrderBy(KEY_LABEL, PrefKey.SORT_ORDER_ACCOUNTS);
      if (mergeCurrencyAggregates) {
        if (projection != null)
          throw new IllegalArgumentException(
              "When calling accounts cursor with mergeCurrencyAggregates, projection is ignored ");
        @SuppressWarnings("deprecation")
        String accountSubquery = qb.buildQuery(Account.PROJECTION_FULL, selection, null, groupBy,
            null, null, null);
        qb.setTables("(SELECT " +
            KEY_ROWID + "," +
            KEY_CURRENCY + "," +
            KEY_OPENING_BALANCE + "," +
            KEY_OPENING_BALANCE + " + (" + SELECT_AMOUNT_SUM +
              " AND " + WHERE_NOT_SPLIT +
              " AND " + WHERE_IN_PAST + " ) AS " + KEY_CURRENT_BALANCE + ", " +
            KEY_OPENING_BALANCE + " + (" + SELECT_AMOUNT_SUM +
              " AND " + WHERE_NOT_SPLIT + " ) AS " + KEY_TOTAL + ", " +
            "(" + SELECT_AMOUNT_SUM + " AND " + WHERE_EXPENSE + ") AS " + KEY_SUM_EXPENSES + "," +
            "(" + SELECT_AMOUNT_SUM + " AND " + WHERE_INCOME + ") AS " + KEY_SUM_INCOME + ", " +
              HAS_EXPORTED + ", " +
              HAS_FUTURE +
            " FROM " + TABLE_ACCOUNTS + " WHERE " + KEY_EXCLUDE_FROM_TOTALS + " = 0) as t");
        groupBy = "currency";
        having = "count(*) > 1";
        projection = new String[] {
            "0 - (SELECT " + KEY_ROWID + " FROM " + TABLE_CURRENCIES
                + " WHERE code = currency)  AS " + KEY_ROWID,//we use negative ids for aggregate accounts
            KEY_CURRENCY + " AS " + KEY_LABEL,
            "'' AS " + KEY_DESCRIPTION,
            "sum(" + KEY_OPENING_BALANCE + ") AS " + KEY_OPENING_BALANCE,
            KEY_CURRENCY,
            "-1 AS " + KEY_COLOR,
            "'NONE' AS " + KEY_GROUPING,
            "'AGGREGATE' AS " + KEY_TYPE,
            "0 AS " + KEY_SORT_KEY,
            "0 AS " + KEY_EXCLUDE_FROM_TOTALS,
            "max(" + KEY_HAS_EXPORTED + ") AS " + KEY_HAS_EXPORTED,
            "sum(" + KEY_CURRENT_BALANCE + ") AS " + KEY_CURRENT_BALANCE,
            "sum(" + KEY_SUM_INCOME + ") AS " + KEY_SUM_INCOME,
            "sum(" + KEY_SUM_EXPENSES + ") AS " + KEY_SUM_EXPENSES,
            "0 AS " + KEY_SUM_TRANSFERS,
            "sum(" + KEY_TOTAL + ") AS " + KEY_TOTAL,
            "0 AS " + KEY_CLEARED_TOTAL, //we do not calculate cleared and reconciled totals for aggregate accounts
            "0 AS " + KEY_RECONCILED_TOTAL,
            "0 AS " + KEY_USAGES,
            "1 AS " + KEY_IS_AGGREGATE,
            "max(" + KEY_HAS_FUTURE + ") AS " + KEY_HAS_FUTURE,
            "0 AS " + KEY_HAS_CLEARED,
            "0 AS " + KEY_SORT_KEY_TYPE,
            "0 AS " + KEY_LAST_USED}; //ignored
        @SuppressWarnings("deprecation")
        String currencySubquery = qb.buildQuery(projection, null, null, groupBy, having, null, null);
        String grouping="";
        Account.AccountGrouping accountGrouping;
        try {
          accountGrouping = Account.AccountGrouping.valueOf(
              PrefKey.ACCOUNT_GROUPING.getString("TYPE"));
        } catch (IllegalArgumentException e) {
          accountGrouping = Account.AccountGrouping.TYPE;
        }
        switch (accountGrouping) {
        case CURRENCY:
          grouping = KEY_CURRENCY + "," + KEY_IS_AGGREGATE;
          break;
        case TYPE:
          grouping = KEY_IS_AGGREGATE + "," + KEY_SORT_KEY_TYPE;
          break;
        case NONE:
          //real accounts should come first, then aggregate accounts
          grouping = KEY_IS_AGGREGATE;
        }
        sortOrder = grouping + "," + defaultOrderBy;
        String sql = qb.buildUnionQuery(
            new String[] {accountSubquery,currencySubquery},
            sortOrder,
            null);
        c = db.rawQuery(sql, null);
        log("Query : " + sql);

        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
      }
      if (projection == null)
        projection = Account.PROJECTION_BASE;
      break;
    case AGGREGATE_ID:
      String currencyId = uri.getPathSegments().get(2);
      qb.setTables(TABLE_CURRENCIES);
      projection = new String[] {
          "0 - " + KEY_ROWID + "  AS " + KEY_ROWID,//we use negative ids for aggregate accounts
          KEY_CODE + " AS " + KEY_LABEL,
          "'' AS " + KEY_DESCRIPTION,
          "(select sum(" + KEY_OPENING_BALANCE
              + ") from " + TABLE_ACCOUNTS + " where " + KEY_CURRENCY + " = " + KEY_CODE + ") AS " + KEY_OPENING_BALANCE,
          KEY_CODE + " AS " + KEY_CURRENCY,
          "-1 AS " + KEY_COLOR,
          "'NONE' AS " + KEY_GROUPING,
          "'AGGREGATE' AS " + KEY_TYPE,
          "-1 AS " + KEY_SORT_KEY,
          "0 AS " + KEY_EXCLUDE_FROM_TOTALS};
      qb.appendWhere(KEY_ROWID + "=" + currencyId);
      break;
    case ACCOUNT_ID:
      qb.setTables(TABLE_ACCOUNTS);
      qb.appendWhere(KEY_ROWID + "=" + uri.getPathSegments().get(1));
      break;
//    case AGGREGATES:
//      //we calculate the aggregates by taking in account the split parts instead of the split transactions,
//      //thus we can ignore split parts that are transfers
//      qb.setTables("(select currency,opening_balance,"+
//          "(SELECT coalesce(sum(amount),0) FROM "
//              + VIEW_COMMITTED
//              + " WHERE account_id = accounts._id AND " + WHERE_EXPENSE + ") as sum_expenses," +
//          "(SELECT coalesce(sum(amount),0) FROM "
//              + VIEW_COMMITTED
//              + " WHERE account_id = accounts._id AND " + WHERE_INCOME + ") as sum_income," +
//          "opening_balance + (SELECT coalesce(sum(amount),0) FROM "
//              + VIEW_COMMITTED
//              + " WHERE account_id = accounts._id and (cat_id is null OR cat_id != "
//                  + SPLIT_CATID + ")) as current_balance " +
//          "from " + TABLE_ACCOUNTS + ") as t");
//      groupBy = "currency";
//      having = "count(*) > 1";
//      projection = new String[] {"1 as _id","currency",
//          "sum(opening_balance) as opening_balance",
//          "sum(sum_income) as sum_income",
//          "sum(sum_expenses) as sum_expenses",
//          "sum(current_balance) as current_balance"};
//      break;
    case AGGREGATES_COUNT:
      qb.setTables(TABLE_ACCOUNTS);
      groupBy = "currency";
      having = "count(*) > 1";
      projection = new String[] {"count(*)"};
      break;
    case PAYEES:
      qb.setTables(TABLE_PAYEES);
      defaultOrderBy = KEY_PAYEE_NAME;
      if (projection == null)
        projection = Payee.PROJECTION;
      break;
    case MAPPED_PAYEES:
      qb.setTables(TABLE_PAYEES  + " JOIN " + TABLE_TRANSACTIONS+ " ON (" + KEY_PAYEEID + " = " + TABLE_PAYEES + "." + KEY_ROWID + ")");
      projection = new String[] {"DISTINCT " + TABLE_PAYEES + "." + KEY_ROWID,KEY_PAYEE_NAME + " AS " + KEY_LABEL};
      defaultOrderBy = KEY_PAYEE_NAME;
      break;
    case MAPPED_TRANSFER_ACCOUNTS:
      qb.setTables(TABLE_ACCOUNTS  + " JOIN " + TABLE_TRANSACTIONS+ " ON (" + KEY_TRANSFER_ACCOUNT + " = " + TABLE_ACCOUNTS + "." + KEY_ROWID + ")");
      projection = new String[] {"DISTINCT " + TABLE_ACCOUNTS + "." + KEY_ROWID, KEY_LABEL};
      defaultOrderBy = KEY_LABEL;
      break;
    case METHODS:
      qb.setTables(TABLE_METHODS);
      if (projection == null) {
        projection = PaymentMethod.PROJECTION(getContext());
      }
      defaultOrderBy = PaymentMethod.localizedLabelSqlColumn(getContext()) + " COLLATE LOCALIZED";
      break;
    case MAPPED_METHODS:
      String localizedLabel = PaymentMethod.localizedLabelSqlColumn(getContext());
      qb.setTables(TABLE_METHODS  + " JOIN " + TABLE_TRANSACTIONS+ " ON (" + KEY_METHODID + " = " + TABLE_METHODS + "." + KEY_ROWID + ")");
      projection = new String[] {"DISTINCT " + TABLE_METHODS + "." + KEY_ROWID,localizedLabel+ " AS "+KEY_LABEL};
      defaultOrderBy = localizedLabel + " COLLATE LOCALIZED";
      break;
    case METHOD_ID:
      qb.setTables(TABLE_METHODS);
      if (projection == null)
        projection = PaymentMethod.PROJECTION(getContext());
      qb.appendWhere(KEY_ROWID + "=" + uri.getPathSegments().get(1));
      break;
    case METHODS_FILTERED:
      localizedLabel = PaymentMethod.localizedLabelSqlColumn(getContext());
      qb.setTables(TABLE_METHODS + " JOIN " + TABLE_ACCOUNTTYES_METHODS + " ON (" + KEY_ROWID + " = " + KEY_METHODID + ")");
      projection =  new String[] {KEY_ROWID,localizedLabel+ " AS "+KEY_LABEL,KEY_IS_NUMBERED};
      String paymentType = uri.getPathSegments().get(2);
      if (paymentType.equals("1")) {
        selection = TABLE_METHODS + ".type > -1";
      } else if (paymentType.equals("-1")) {
        selection = TABLE_METHODS + ".type < 1";
      } else {
        throw new IllegalArgumentException("Unknown paymentType " + paymentType);
      }
      String accountType = uri.getPathSegments().get(3);
      try {
        Account.Type.valueOf(accountType);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unknown accountType " + accountType);
      }
      selection += " and " + TABLE_ACCOUNTTYES_METHODS + ".type = ?";
      selectionArgs = new String[] {accountType};
      defaultOrderBy = localizedLabel+ " COLLATE LOCALIZED";
      break;
    case ACCOUNTTYPES_METHODS:
      qb.setTables(TABLE_ACCOUNTTYES_METHODS);
      break;
    case TEMPLATES:
      qb.setTables(VIEW_TEMPLATES_EXTENDED);
      defaultOrderBy =  Utils.defaultOrderBy(KEY_TITLE, PrefKey.SORT_ORDER_TEMPLATES);
      if (projection == null)
        projection = Template.PROJECTION_EXTENDED;
      break;
    case TEMPLATE_ID:
      qb.setTables(VIEW_TEMPLATES_EXTENDED);
      qb.appendWhere(KEY_ROWID + "=" + uri.getPathSegments().get(1));
      if (projection == null)
        projection = Template.PROJECTION_EXTENDED;
      break;
    case SQLITE_SEQUENCE_TABLE:
      qb.setTables("SQLITE_SEQUENCE");
      projection = new String[] {"seq"};
      selection = "name = ?";
      selectionArgs = new String[] {uri.getPathSegments().get(1)};
      break;
    case PLANINSTANCE_TRANSACTION_STATUS:
      qb.setTables(TABLE_PLAN_INSTANCE_STATUS);
      break;
    //only called from unit test
    case CURRENCIES:
      qb.setTables(TABLE_CURRENCIES);
      break;
    case DUAL:
      qb.setTables("sqlite_master");
      return qb.query(db, projection, selection, selectionArgs, null,
          null, null,"1");
    case EVENT_CACHE:
      qb.setTables(TABLE_EVENT_CACHE);
      break;
    case DEBUG_SCHEMA:
      qb.setTables("sqlite_master");
      return qb.query(
          db,
          new String[]{"name","sql"},
          "type = 'table'",
          null,null,null,null);
    case STALE_IMAGES:
      qb.setTables(TABLE_STALE_URIS);
      if (projection == null)
        projection = new String[] {"rowid as _id",KEY_PICTURE_URI};
      break;
    case STALE_IMAGES_ID:
      qb.setTables(TABLE_STALE_URIS);
      qb.appendWhere("rowid = " + uri.getPathSegments().get(1));
      projection = new String[] {KEY_PICTURE_URI};
      break;
    case TRANSACTIONS_LASTEXCHANGE:
      String currency1 = uri.getPathSegments().get(2);
      String currency2 = uri.getPathSegments().get(3);
      selection = "(SELECT " + KEY_CURRENCY + " FROM " + TABLE_ACCOUNTS +
          " WHERE " + KEY_ROWID + " = " + KEY_ACCOUNTID + ") = ? AND " +
          "(SELECT " + KEY_CURRENCY + " FROM " + TABLE_ACCOUNTS + " WHERE " + KEY_ROWID + " = " +
              "(SELECT " + KEY_ACCOUNTID + " FROM " + TABLE_TRANSACTIONS + " WHERE " +  KEY_ROWID +
              " = " + VIEW_COMMITTED + "." + KEY_TRANSFER_PEER + ")) = ?";
      selectionArgs = new String[] {currency1, currency2};
      projection = new String[] {
          "'" + currency1 + "'", // we pass the currency codes back so that the receiver
          "'" + currency2 + "'", // can check if the data is still relevant for him
          "abs(" + KEY_AMOUNT + ")",
          "abs((SELECT " + KEY_AMOUNT + " FROM " + TABLE_TRANSACTIONS + " WHERE " + KEY_ROWID +
              " = " + VIEW_COMMITTED + "." + KEY_TRANSFER_PEER + "))"
      };
      sortOrder = KEY_DATE + " DESC";
      limit = "1";
      qb.setTables(VIEW_COMMITTED);
      break;

    default:
      throw new IllegalArgumentException("Unknown URL " + uri);
    }
    String orderBy;
    if (TextUtils.isEmpty(sortOrder)) {
      orderBy = defaultOrderBy;
    } else {
      orderBy = sortOrder;
    }

    if (BuildConfig.DEBUG) {
      @SuppressWarnings("deprecation")
      String qs = qb.buildQuery(projection, selection, null, groupBy,
          null, orderBy, limit);
      log("Query : " + qs);
      log("SelectionArgs : " + Arrays.toString(selectionArgs));
    }
    //long startTime = System.nanoTime();
    c = qb.query(db, projection, selection, selectionArgs, groupBy, having, orderBy, limit);
    //long endTime = System.nanoTime();
    //Log.d("TIMER",uri.toString() + Arrays.toString(selectionArgs) + " : "+(endTime-startTime));

    if (uriMatch == TEMPLATES && uri.getQueryParameter(QUERY_PARAMETER_WITH_PLAN_INFO) != null) {
      c = new PlanInfoCursorWrapper(getContext(), c, defaultOrderBy == null);
    }
    c.setNotificationUri(getContext().getContentResolver(), uri);
    return c;
  }

  @Override
  public String getType(Uri uri) {
    return null;
  }
  @Override
  public Uri insert(Uri uri, ContentValues values) {
    setDirty();
    log(values.toString());
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    long id;
    String newUri;
    int uriMatch = URI_MATCHER.match(uri);
    switch (uriMatch) {
    case TRANSACTIONS:
      id = db.insertOrThrow(TABLE_TRANSACTIONS, null, values);
      newUri = TRANSACTIONS_URI + "/" + id;
      break;
    case ACCOUNTS:
      id = db.insertOrThrow(TABLE_ACCOUNTS, null, values);
      newUri = ACCOUNTS_URI + "/" + id;
      break;
    case METHODS:
      id = db.insertOrThrow(TABLE_METHODS, null, values);
      newUri = METHODS_URI + "/" + id;
      break;
    case ACCOUNTTYPES_METHODS:
      id = db.insertOrThrow(TABLE_ACCOUNTTYES_METHODS,null,values);
      //we are not interested in accessing individual entries in this table, but have to return a uri
      newUri = ACCOUNTTYPES_METHODS_URI + "/" + id;
      break;
    case TEMPLATES:
      id = db.insertOrThrow(TABLE_TEMPLATES, null, values);
      newUri = TEMPLATES_URI + "/" + id;
      break;
    case CATEGORIES:
      //for categories we can not rely on the unique constraint, since it does not work for parent_id is null
      Long parentId = values.getAsLong(KEY_PARENTID);
      String label = values.getAsString(KEY_LABEL);
      String selection;
      String[] selectionArgs;
      if (parentId == null) {
        selection = KEY_PARENTID + " is null";
        selectionArgs = new String[]{label};
      } else {
        selection = KEY_PARENTID + " = ?";
        selectionArgs = new String[]{String.valueOf(parentId),label};
      }
      selection += " and " + KEY_LABEL + " = ?";
      Cursor mCursor = db.query(TABLE_CATEGORIES, new String []{KEY_ROWID}, selection, selectionArgs, null, null, null);
      if (mCursor.getCount() != 0) {
        mCursor.close();
        throw new SQLiteConstraintException();
      }
      mCursor.close();
      id = db.insertOrThrow(TABLE_CATEGORIES, null, values);
      newUri = CATEGORIES_URI + "/" + id;
      break;
    case PAYEES:
      id = db.insertOrThrow(TABLE_PAYEES, null, values);
      newUri = PAYEES_URI + "/" + id;
      break;
    case PLANINSTANCE_TRANSACTION_STATUS:
      id = db.insertOrThrow(TABLE_PLAN_INSTANCE_STATUS, null, values);
      newUri = PLAN_INSTANCE_STATUS_URI + "/" + id;
      break;
    case EVENT_CACHE:
      id = db.insertOrThrow(TABLE_EVENT_CACHE, null, values);
      newUri = EVENT_CACHE_URI + "/" + id;
      break;
    case STALE_IMAGES:
      id = db.insertOrThrow(TABLE_STALE_URIS, null, values);
      newUri = TABLE_STALE_URIS + "/" + id;
      break;
    default:
      throw new IllegalArgumentException("Unknown URI: " + uri);
    }
    getContext().getContentResolver().notifyChange(uri, null);
    //the accounts cursor contains aggregates about transactions
    //we need to notify it when transactions change
    if (uriMatch == TRANSACTIONS) {
      getContext().getContentResolver().notifyChange(ACCOUNTS_URI, null);
      getContext().getContentResolver().notifyChange(UNCOMMITTED_URI, null);
    } else if (uriMatch == ACCOUNTS) {
      getContext().getContentResolver().notifyChange(ACCOUNTS_BASE_URI, null);
    }
    return id >0 ? Uri.parse(newUri) : null;
  }

  @Override
  public int delete(Uri uri, String where, String[] whereArgs) {
    setDirty();
    log("Delete for URL: " + uri);
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    int count;
    String whereString;
    String segment;
    int uriMatch = URI_MATCHER.match(uri);
    switch (uriMatch) {
    case TRANSACTIONS:
      count = db.delete(TABLE_TRANSACTIONS, where, whereArgs);
      break;
    case TRANSACTION_ID:
      //maybe TODO ?: where and whereArgs are ignored
      segment = uri.getPathSegments().get(1);
      //when we are deleting a transfer whose peer is part of a split, we cannot the delete the peer,
      //because the split would be left in an invalid state, hence we transform the peer to a normal split part
      //first we find out the account label
      db.beginTransaction();
      try {
        Cursor c = db.query(
            TABLE_ACCOUNTS,
            new String []{KEY_LABEL},
            KEY_ROWID + " = (SELECT " + KEY_ACCOUNTID + " FROM " + TABLE_TRANSACTIONS + " WHERE " + KEY_ROWID + " = ?)",
            new String[] {segment},
            null, null, null);
        c.moveToFirst();
        //cursor should not be empty, but has been observed to be (bug report 67a7942fe8b6c9c96859b226767a9000)
        String accountLabel = c.moveToFirst() ? c.getString(0) : "UNKNOWN";
        c.close();
        ContentValues args = new ContentValues();
        args.put(KEY_COMMENT, getContext().getString(R.string.peer_transaction_deleted,accountLabel));
        args.putNull(KEY_TRANSFER_ACCOUNT);
        args.putNull(KEY_TRANSFER_PEER);
        db.update(TABLE_TRANSACTIONS,
            args,
            KEY_TRANSFER_PEER + " = ? AND " + KEY_PARENTID + " IS NOT null",
            new String[] {segment});
        //we delete the transaction, its children (in case of delete they are also handled by
        //ON DELETE CASCADE, but not in case of mark void and its transfer peer, and transfer peers of its children
        whereArgs = new String[] {segment,segment, segment, segment};
        if (uri.getQueryParameter(QUERY_PARAMETER_MARK_VOID) == null) {
          count = db.delete(TABLE_TRANSACTIONS, WHERE_DEPENDENT, whereArgs);
        } else {
          ContentValues v = new ContentValues();
          v.put(KEY_CR_STATUS, Transaction.CrStatus.VOID.name());
          count = db.update(TABLE_TRANSACTIONS,v,WHERE_DEPENDENT,whereArgs);
        }
        db.setTransactionSuccessful();
      }    finally {
        db.endTransaction();
      }
      break;
    case TEMPLATES:
      count = db.delete(TABLE_TEMPLATES, where, whereArgs);
      break;
    case TEMPLATE_ID:
      segment = uri.getPathSegments().get(1);
      if (!TextUtils.isEmpty(where)) {
        whereString = " AND (" + where + ')';
      } else {
        whereString = "";
      }
      count = db.delete(TABLE_TEMPLATES, "_id=" + segment + whereString,
          whereArgs);
      break;
    case ACCOUNTTYPES_METHODS:
      count = db.delete(TABLE_ACCOUNTTYES_METHODS, where, whereArgs);
      break;
    case ACCOUNTS:
      count = db.delete(TABLE_ACCOUNTS, where, whereArgs);
      break;
    case ACCOUNT_ID:
      segment = uri.getPathSegments().get(1);
      if (!TextUtils.isEmpty(where)) {
        whereString = " AND (" + where + ')';
      } else {
        whereString = "";
      }
      count = db.delete(TABLE_ACCOUNTS, "_id=" + segment + whereString,
          whereArgs);
      //update aggregate cursor
      //getContext().getContentResolver().notifyChange(AGGREGATES_URI, null);
      break;
    case CATEGORIES:
      count = db.delete(TABLE_CATEGORIES, where, whereArgs);
      break;
    case CATEGORY_ID:
      segment = uri.getPathSegments().get(1);
      if (!TextUtils.isEmpty(where)) {
        whereString = " AND (" + where + ')';
      } else {
        whereString = "";
      }
      count = db.delete(TABLE_CATEGORIES, "_id=" + segment + whereString,
          whereArgs);
      break;
    case PAYEE_ID:
      segment = uri.getPathSegments().get(1);
      if (!TextUtils.isEmpty(where)) {
        whereString = " AND (" + where + ')';
      } else {
        whereString = "";
      }
      count = db.delete(TABLE_PAYEES, "_id=" + segment + whereString,
          whereArgs);
      break;
    case METHOD_ID:
      segment = uri.getPathSegments().get(1);
      if (!TextUtils.isEmpty(where)) {
        whereString = " AND (" + where + ')';
      } else {
        whereString = "";
      }
      count = db.delete(TABLE_METHODS, "_id=" + segment + whereString,
          whereArgs);
      break;
    case PLANINSTANCE_TRANSACTION_STATUS:
      count = db.delete(TABLE_PLAN_INSTANCE_STATUS, where, whereArgs);
      break;
    case EVENT_CACHE:
      count = db.delete(TABLE_EVENT_CACHE, where, whereArgs);
      break;
    case STALE_IMAGES_ID:
      segment = uri.getPathSegments().get(1);
      count = db.delete(TABLE_STALE_URIS, "rowid=" + segment,null);
      break;
    case STALE_IMAGES:
      count = db.delete(TABLE_STALE_URIS, where, whereArgs);
      break;
    default:
      throw new IllegalArgumentException("Unknown URL " + uri);
    }
    if (uriMatch == TRANSACTIONS || uriMatch == TRANSACTION_ID) {
      getContext().getContentResolver().notifyChange(TRANSACTIONS_URI, null);
      getContext().getContentResolver().notifyChange(ACCOUNTS_URI, null);
      getContext().getContentResolver().notifyChange(UNCOMMITTED_URI, null);
    } else {
      if (uriMatch == ACCOUNTS) {
        getContext().getContentResolver().notifyChange(ACCOUNTS_BASE_URI, null);
      }
      getContext().getContentResolver().notifyChange(uri, null);
    }
    return count;
  }

  @Override
  public int update(Uri uri, ContentValues values, String where,
      String[] whereArgs) {
    setDirty();
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    String segment; // contains rowId
    int count;
    String whereString;
    int uriMatch = URI_MATCHER.match(uri);
    Cursor c;
    switch (uriMatch) {
    case TRANSACTIONS:
      count = db.update(TABLE_TRANSACTIONS, values, where, whereArgs);
      break;
    case TRANSACTION_ID:
      segment = uri.getPathSegments().get(1); 
      if (!TextUtils.isEmpty(where)) {
        whereString = " AND (" + where + ')';
      } else {
        whereString = "";
      }
      count = db.update(TABLE_TRANSACTIONS, values, "_id=" + segment + whereString,
          whereArgs);
      break;
    case TRANSACTION_UNDELETE:
      segment = uri.getPathSegments().get(1);
      whereArgs = new String[] {segment,segment, segment, segment};
      ContentValues v = new ContentValues();
      v.put(KEY_CR_STATUS, Transaction.CrStatus.UNRECONCILED.name());
      count = db.update(TABLE_TRANSACTIONS,v,WHERE_DEPENDENT,whereArgs);
      break;
    case ACCOUNTS:
      count = db.update(TABLE_ACCOUNTS, values, where, whereArgs);
      break;
    case ACCOUNT_ID:
      segment = uri.getPathSegments().get(1); 
      if (!TextUtils.isEmpty(where)) {
        whereString = " AND (" + where + ')';
      } else {
        whereString = "";
      }
      count = db.update(TABLE_ACCOUNTS, values, "_id=" + segment + whereString,
          whereArgs);
      break;
    case TEMPLATES:
      //TODO should not support bulk update of categories
      count = db.update(TABLE_TEMPLATES, values, where, whereArgs);
      break;
    case TEMPLATE_ID:
      segment = uri.getPathSegments().get(1); 
      if (!TextUtils.isEmpty(where)) {
        whereString = " AND (" + where + ')';
      } else {
        whereString = "";
      }
      count = db.update(TABLE_TEMPLATES, values, "_id=" + segment + whereString,
            whereArgs);
      break;
    case PAYEE_ID:
      segment = uri.getPathSegments().get(1);
      if (!TextUtils.isEmpty(where)) {
        whereString = " AND (" + where + ')';
      } else {
        whereString = "";
      }
      count = db.update(TABLE_PAYEES, values, "_id=" + segment + whereString,
            whereArgs);
      getContext().getContentResolver().notifyChange(TRANSACTIONS_URI, null);
      break;
    case CATEGORIES:
      throw new UnsupportedOperationException("Bulk update of categories is not supported");
    case CATEGORY_ID:
      if (values.containsKey(KEY_LABEL) && values.containsKey(KEY_PARENTID))
        throw new UnsupportedOperationException("Simultaneous update of label and parent is not supported");
      segment = uri.getPathSegments().get(1);
      //for categories we can not rely on the unique constraint, since it does not work for parent_id is null
      String label = values.getAsString(KEY_LABEL);
      if (label != null) {
        String selection;
        String[] selectionArgs;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
          selection = "label = ? and ((parent_id is null and (select parent_id from categories where _id = ?) is null) or parent_id = (select parent_id from categories where _id = ?))";
          selectionArgs = new String[]{label, segment, segment};
        } else {
          //this syntax crashes on 2.1, maybe 2.2
          selection = "label = ? and parent_id is (select parent_id from categories where _id = ?)";
          selectionArgs = new String[]{label, segment};
        }
        c = db.query(TABLE_CATEGORIES, new String[]{KEY_ROWID}, selection, selectionArgs, null, null, null);
        if (c.getCount() != 0) {
          c.moveToFirst();
          if (c.getLong(0) == Long.valueOf(segment)) {
            //silently do nothing if we try to update with the same value
            c.close();
            return 0;
          }
          c.close();
          throw new SQLiteConstraintException();
        }
        c.close();
        if (!TextUtils.isEmpty(where)) {
          whereString = " AND (" + where + ')';
        } else {
          whereString = "";
        }
        count = db.update(TABLE_CATEGORIES, values, "_id = " + segment + whereString,
            whereArgs);
        break;
      }
      if (values.containsKey(KEY_PARENTID)) {
        Long newParent = values.getAsLong(KEY_PARENTID);
        String selection;
        String[] selectionArgs;
        selection = "label = (SELECT label FROM categories WHERE _id =?) and parent_id is " + newParent;
        selectionArgs = new String[]{segment};
        c = db.query(TABLE_CATEGORIES, new String[]{KEY_ROWID}, selection, selectionArgs, null, null, null);
        if (c.getCount() != 0) {
          c.moveToFirst();
          if (c.getLong(0) == Long.valueOf(segment)) {
            //silently do nothing if we try to update with the same value
            c.close();
            return 0;
          }
          c.close();
          throw new SQLiteConstraintException();
        }
        c.close();
        if (!TextUtils.isEmpty(where)) {
          whereString = " AND (" + where + ')';
        } else {
          whereString = "";
        }
        count = db.update(TABLE_CATEGORIES, values, "_id = " + segment + whereString,
            whereArgs);
        break;
      }
      return 0;//nothing to do
    case METHOD_ID:
      segment = uri.getPathSegments().get(1);
      if (!TextUtils.isEmpty(where)) {
        whereString = " AND (" + where + ')';
      } else {
        whereString = "";
      }
      count = db.update(TABLE_METHODS, values, "_id=" + segment + whereString,
          whereArgs);
      break;
    case CATEGORY_INCREASE_USAGE:
      segment = uri.getPathSegments().get(1);
      db.execSQL("UPDATE " + DatabaseConstants.TABLE_CATEGORIES + " SET " + KEY_USAGES + " = " +
          KEY_USAGES + " + 1, " + KEY_LAST_USED + " = strftime('%s', 'now')  WHERE " + KEY_ROWID +
          " IN (" + segment + " , (SELECT " + KEY_PARENTID +
          " FROM " + TABLE_CATEGORIES + " WHERE " + KEY_ROWID + " = " + segment + "))");
      count = 1;
      break;
    case TEMPLATES_INCREASE_USAGE:
      segment = uri.getPathSegments().get(1);
      db.execSQL("UPDATE " + TABLE_TEMPLATES + " SET " + KEY_USAGES + " = " + KEY_USAGES + " + 1, " +
          KEY_LAST_USED + " = strftime('%s', 'now') WHERE " + KEY_ROWID + " = " + segment);
      count = 1;
      break;
    case ACCOUNT_INCREASE_USAGE:
      segment = uri.getPathSegments().get(1);
      db.execSQL("UPDATE " + TABLE_ACCOUNTS + " SET " + KEY_USAGES + " = " + KEY_USAGES + " + 1, " +
          KEY_LAST_USED + " = strftime('%s', 'now')   WHERE " + KEY_ROWID + " = " + segment);
      count = 1;
      break;
    //   when we move a transaction to a new target we apply two checks
    //1) we do not move a transfer to its own transfer_account
    //2) we check if the transactions method_id is also available in the target account, if not we set it to null
    case TRANSACTION_MOVE:
      segment = uri.getPathSegments().get(1);
      String target = uri.getPathSegments().get(3);
      db.execSQL("UPDATE " + TABLE_TRANSACTIONS +
          " SET " +
            KEY_ACCOUNTID + " = ?, " +
            KEY_METHODID + " = " +
                " CASE " +
                    " WHEN exists " +
                        " (SELECT 1 FROM " + TABLE_ACCOUNTTYES_METHODS +
                            " WHERE " + KEY_TYPE + " = " +
                                " (SELECT " + KEY_TYPE + " FROM " + TABLE_ACCOUNTS +
                                    " WHERE " + DatabaseConstants.KEY_ROWID + " = ?) " +
                                    " AND " + KEY_METHODID + " = " + TABLE_TRANSACTIONS + "." + KEY_METHODID + ")" +
                    " THEN " + KEY_METHODID +
                    " ELSE null " +
                " END " +
            " WHERE " + DatabaseConstants.KEY_ROWID + " = ? " +
            " AND ( " + KEY_TRANSFER_ACCOUNT + " IS NULL OR " + KEY_TRANSFER_ACCOUNT + "  != ? )",
          new String[]{target,target,segment,target});
      count=1;
      break;
    case PLANINSTANCE_TRANSACTION_STATUS:
      count = db.update(TABLE_PLAN_INSTANCE_STATUS, values, where, whereArgs);
      break;
    case TRANSACTION_TOGGLE_CRSTATUS:
      segment = uri.getPathSegments().get(1);
      db.execSQL("UPDATE " + TABLE_TRANSACTIONS +
          " SET " + KEY_CR_STATUS +
          " = CASE " + KEY_CR_STATUS +
              " WHEN '" + "CLEARED" + "'" +
              " THEN '" + "UNRECONCILED" + "'" +
              " WHEN '" + "UNRECONCILED" + "'" +
              " THEN '" + "CLEARED" + "'" +
              " ELSE "  + KEY_CR_STATUS +
            " END" +
          " WHERE " + DatabaseConstants.KEY_ROWID + " = ? ",
          new String[]{segment});
      count = 1;
      break;
    case CURRENCIES_CHANGE_FRACTION_DIGITS:
      synchronized (MyApplication.getInstance()) {
        db.beginTransaction();
        try {
          List<String> segments = uri.getPathSegments();
          segment = segments.get(2);
          String[] bindArgs = new String[] {segment};
          int oldValue = Money.getFractionDigits(Currency.getInstance(segment));
          int newValue = Integer.parseInt(segments.get(3));
          if (oldValue==newValue) {
            return 0;
          }
          c = db.query(
              TABLE_ACCOUNTS,
              new String[]{"count(*)"},
              KEY_CURRENCY +"=?",
              bindArgs, null, null, null);
          count = 0;
          if (c.getCount() != 0) {
            c.moveToFirst();
            count=c.getInt(0);
          }
          c.close();
          if (count!=0) {
            String operation = oldValue<newValue?"*":"/";
            int factor = (int) Math.pow(10,Math.abs(oldValue-newValue));
            db.execSQL("UPDATE " + TABLE_ACCOUNTS + " SET " + KEY_OPENING_BALANCE + "="
                + KEY_OPENING_BALANCE+operation+factor+ " WHERE " + KEY_CURRENCY + "=?",
                bindArgs);
      
            db.execSQL("UPDATE " + TABLE_TRANSACTIONS + " SET " + KEY_AMOUNT + "="
                + KEY_AMOUNT+operation+factor+ " WHERE " + KEY_ACCOUNTID
                + " IN (SELECT " + DatabaseConstants.KEY_ROWID + " FROM " + TABLE_ACCOUNTS + " WHERE " + KEY_CURRENCY + "=?)",
                bindArgs);
      
            db.execSQL("UPDATE " + TABLE_TEMPLATES + " SET " + KEY_AMOUNT + "="
                + KEY_AMOUNT+operation+factor+ " WHERE " + KEY_ACCOUNTID
                + " IN (SELECT " + DatabaseConstants.KEY_ROWID + " FROM " + TABLE_ACCOUNTS + " WHERE " + KEY_CURRENCY + "=?)",
                bindArgs);
          }
          Money.storeCustomFractionDigits(segment, newValue);
          db.setTransactionSuccessful();
          //force accounts to be refetched,since amountMinor of their opening balance has changed
          Account.clear();
        } finally {
          db.endTransaction();
        }
      }
      break;
    case ACCOUNTS_SWAP_SORT_KEY:
      String sortKey1 = uri.getPathSegments().get(2);
      String sortKey2 = uri.getPathSegments().get(3);
      db.execSQL("UPDATE " + TABLE_ACCOUNTS + " SET " + KEY_SORT_KEY + " = CASE " + KEY_SORT_KEY +
          " WHEN ? THEN ? WHEN ? THEN ? END WHERE " + KEY_SORT_KEY + " in (?,?);",
          new String[] {sortKey1, sortKey2, sortKey2, sortKey1, sortKey1, sortKey2 });
      count = 2;
      break;
    default:
      throw new IllegalArgumentException("Unknown URI " + uri);
    }
    if (uriMatch == TRANSACTIONS || uriMatch == TRANSACTION_ID ||
        uriMatch == CURRENCIES_CHANGE_FRACTION_DIGITS || uriMatch == TRANSACTION_UNDELETE ||
        uriMatch == TRANSACTION_MOVE || uriMatch == TRANSACTION_TOGGLE_CRSTATUS) {
      getContext().getContentResolver().notifyChange(TRANSACTIONS_URI, null);
      getContext().getContentResolver().notifyChange(ACCOUNTS_URI, null);
      getContext().getContentResolver().notifyChange(UNCOMMITTED_URI, null);
      getContext().getContentResolver().notifyChange(CATEGORIES_URI, null);
    } else if (
        //we do not need to refresh cursors on the usage counters
        uriMatch != TEMPLATES_INCREASE_USAGE &&
        uriMatch != CATEGORY_INCREASE_USAGE &&
        uriMatch != ACCOUNT_INCREASE_USAGE) {
      getContext().getContentResolver().notifyChange(uri, null);
    }
    if (uriMatch == CURRENCIES_CHANGE_FRACTION_DIGITS || uriMatch == TEMPLATES_INCREASE_USAGE) {
      getContext().getContentResolver().notifyChange(TEMPLATES_URI,null);
    }
    if (uriMatch == CATEGORY_INCREASE_USAGE) {
      getContext().getContentResolver().notifyChange(CATEGORIES_URI,null);
    }
    if (uriMatch == ACCOUNT_INCREASE_USAGE) {
      getContext().getContentResolver().notifyChange(ACCOUNTS_URI,null);
    }
    return count;
  }
  /**
  * Apply the given set of {@link ContentProviderOperation}, executing inside
  * a {@link SQLiteDatabase} transaction. All changes will be rolled back if
  * any single one fails.
  */
  @Override
  public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
      throws OperationApplicationException {
    final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      final int numOperations = operations.size();
      final ContentProviderResult[] results = new ContentProviderResult[numOperations];
      for (int i = 0; i < numOperations; i++) {
        results[i] = operations.get(i).apply(this, results, i);
      }
      db.setTransactionSuccessful();
      return results;
    } finally {
      db.endTransaction();
    }
  }

  static {
    URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    URI_MATCHER.addURI(AUTHORITY, "transactions", TRANSACTIONS);
    URI_MATCHER.addURI(AUTHORITY, "transactions/uncommitted", UNCOMMITTED);
    URI_MATCHER.addURI(AUTHORITY, "transactions/" + URI_SEGMENT_GROUPS + "/*", TRANSACTIONS_GROUPS);
    URI_MATCHER.addURI(AUTHORITY, "transactions/sumsForAccountsGroupedByType", TRANSACTIONS_SUMS);
    URI_MATCHER.addURI(AUTHORITY, "transactions/" + URI_SEGMENT_LAST_EXCHANGE + "/*/*", TRANSACTIONS_LASTEXCHANGE);
    URI_MATCHER.addURI(AUTHORITY, "transactions/#", TRANSACTION_ID);
    URI_MATCHER.addURI(AUTHORITY, "transactions/#/" + URI_SEGMENT_MOVE + "/#", TRANSACTION_MOVE);
    URI_MATCHER.addURI(AUTHORITY, "transactions/#/" + URI_SEGMENT_TOGGLE_CRSTATUS, TRANSACTION_TOGGLE_CRSTATUS);
    URI_MATCHER.addURI(AUTHORITY, "transactions/#/" + URI_SEGMENT_UNDELETE, TRANSACTION_UNDELETE);
    URI_MATCHER.addURI(AUTHORITY, "categories", CATEGORIES);
    URI_MATCHER.addURI(AUTHORITY, "categories/#", CATEGORY_ID);
    URI_MATCHER.addURI(AUTHORITY, "categories/#/" + URI_SEGMENT_INCREASE_USAGE, CATEGORY_INCREASE_USAGE);
    URI_MATCHER.addURI(AUTHORITY, "accounts", ACCOUNTS);
    URI_MATCHER.addURI(AUTHORITY, "accounts/base", ACCOUNTS_BASE);
    URI_MATCHER.addURI(AUTHORITY, "accounts/#", ACCOUNT_ID);
    URI_MATCHER.addURI(AUTHORITY, "accounts/#/" + URI_SEGMENT_INCREASE_USAGE, ACCOUNT_INCREASE_USAGE);
    URI_MATCHER.addURI(AUTHORITY, "payees", PAYEES);
    URI_MATCHER.addURI(AUTHORITY, "payees/#", PAYEE_ID);
    URI_MATCHER.addURI(AUTHORITY, "methods", METHODS);
    URI_MATCHER.addURI(AUTHORITY, "methods/#", METHOD_ID);
    //methods/typeFilter/{TransactionType}/{AccountType}
    //TransactionType: 1 Income, -1 Expense
    //AccountType: CASH BANK CCARD ASSET LIABILITY
    URI_MATCHER.addURI(AUTHORITY, "methods/" + URI_SEGMENT_TYPE_FILTER + "/*/*", METHODS_FILTERED);
    URI_MATCHER.addURI(AUTHORITY, "accounts/aggregatesCount", AGGREGATES_COUNT);
    URI_MATCHER.addURI(AUTHORITY, "accounttypes_methods", ACCOUNTTYPES_METHODS);
    URI_MATCHER.addURI(AUTHORITY, "templates", TEMPLATES);
    URI_MATCHER.addURI(AUTHORITY, "templates/#", TEMPLATE_ID);
    URI_MATCHER.addURI(AUTHORITY, "templates/#/" + URI_SEGMENT_INCREASE_USAGE, TEMPLATES_INCREASE_USAGE);
    URI_MATCHER.addURI(AUTHORITY, "sqlite_sequence/*", SQLITE_SEQUENCE_TABLE);
    URI_MATCHER.addURI(AUTHORITY, "planinstance_transaction", PLANINSTANCE_TRANSACTION_STATUS);
    URI_MATCHER.addURI(AUTHORITY, "currencies", CURRENCIES);
    URI_MATCHER.addURI(AUTHORITY, "currencies/" + URI_SEGMENT_CHANGE_FRACTION_DIGITS + "/*/#", CURRENCIES_CHANGE_FRACTION_DIGITS);
    URI_MATCHER.addURI(AUTHORITY, "accounts/aggregates/#",AGGREGATE_ID);
    URI_MATCHER.addURI(AUTHORITY, "payees_transactions", MAPPED_PAYEES);
    URI_MATCHER.addURI(AUTHORITY, "methods_transactions", MAPPED_METHODS);
    URI_MATCHER.addURI(AUTHORITY, "dual", DUAL);
    URI_MATCHER.addURI(AUTHORITY, "eventcache", EVENT_CACHE);
    URI_MATCHER.addURI(AUTHORITY, "debug_schema", DEBUG_SCHEMA);
    URI_MATCHER.addURI(AUTHORITY, "stale_images", STALE_IMAGES);
    URI_MATCHER.addURI(AUTHORITY, "stale_images/#", STALE_IMAGES_ID);
    URI_MATCHER.addURI(AUTHORITY, "accounts/"+ URI_SEGMENT_SWAP_SORT_KEY + "/#/#", ACCOUNTS_SWAP_SORT_KEY);
    URI_MATCHER.addURI(AUTHORITY, "transfer_account_transactions", MAPPED_TRANSFER_ACCOUNTS);
  }
  public void resetDatabase() {
    mOpenHelper.close();
    mOpenHelper = new TransactionDatabase(getContext());
}
  /**
   * A test package can call this to get a handle to the database underlying TransactionProvider,
   * so it can insert test data into the database. The test case class is responsible for
   * instantiating the provider in a test context; {@link android.test.ProviderTestCase2} does
   * this during the call to setUp()
   *
   * @return a handle to the database helper object for the provider's data.
   */
  @VisibleForTesting
  public TransactionDatabase getOpenHelperForTest() {
      return mOpenHelper;
  }

  private void log(String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG,message);
    }
  }
  
}
