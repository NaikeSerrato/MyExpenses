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

package org.totschnig.myexpenses.fragment;

import static org.totschnig.myexpenses.provider.DatabaseConstants.*;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ManageTemplates;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment;
import org.totschnig.myexpenses.dialog.MessageDialogFragment;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.Category;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.ui.SimpleCursorAdapter;
import org.totschnig.myexpenses.util.Utils;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class TemplatesList extends SortableListFragment  {

  private ListView mListView;

  protected int getMenuResource() {
    return R.menu.templateslist_context;
  }

  Cursor mTemplatesCursor;
  private SimpleCursorAdapter mAdapter;
  //private SimpleCursorAdapter mAdapter;
  //private StickyListHeadersListView mListView;
  int mGroupIdColumnIndex;
  private LoaderManager mManager;

  private int columnIndexAmount, columnIndexLabelSub, columnIndexComment,
      columnIndexPayee, columnIndexColor, columnIndexTransferPeer,
      columnIndexCurrency, columnIndexTransferAccount;
  boolean indexesCalculated = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.templates_list, null, false);
    mListView = (ListView) v.findViewById(R.id.list);

    mManager = getLoaderManager();
    mManager.initLoader(SORTABLE_CURSOR, null, this);
    // Create an array to specify the fields we want to display in the list
    String[] from = new String[]{KEY_TITLE, KEY_LABEL_MAIN, KEY_AMOUNT};
    // and an array of the fields we want to bind those fields to
    int[] to = new int[]{R.id.title, R.id.category, R.id.amount};
    mAdapter = new MyAdapter(
        getActivity(),
        R.layout.template_row,
        null,
        from,
        to,
        0);
    mListView.setAdapter(mAdapter);
    mListView.setEmptyView(v.findViewById(R.id.empty));
    mListView.setOnItemClickListener(new OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (isForeignExchangeTransfer(position)) {
          ((ManageTemplates) getActivity()).dispatchCommand(R.id.CREATE_INSTANCE_EDIT_COMMAND,
              id);
        } else if (MyApplication.PrefKey.TEMPLATE_CLICK_HINT_SHOWN.getBoolean(false)) {
          if (MyApplication.PrefKey.TEMPLATE_CLICK_DEFAULT.getString("SAVE").equals("SAVE")) {
            ((ManageTemplates) getActivity()).dispatchCommand(R.id.CREATE_INSTANCE_SAVE_COMMAND,
                new Long[]{id});
          } else {
            ((ManageTemplates) getActivity()).dispatchCommand(R.id.CREATE_INSTANCE_EDIT_COMMAND,
                id);
          }
        } else {
          Bundle b = new Bundle();
          b.putLong(KEY_ROWID, id);
          b.putInt(ConfirmationDialogFragment.KEY_TITLE, R.string.dialog_title_information);
          b.putString(ConfirmationDialogFragment.KEY_MESSAGE, getString(R.string
              .hint_template_click));
          b.putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE, R.id
              .CREATE_INSTANCE_SAVE_COMMAND);
          b.putInt(ConfirmationDialogFragment.KEY_COMMAND_NEGATIVE, R.id
              .CREATE_INSTANCE_EDIT_COMMAND);
          b.putString(ConfirmationDialogFragment.KEY_PREFKEY, MyApplication.PrefKey
              .TEMPLATE_CLICK_HINT_SHOWN.getKey());
          b.putInt(ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_LABEL, R.string
              .menu_create_instance_save);
          b.putInt(ConfirmationDialogFragment.KEY_NEGATIVE_BUTTON_LABEL, R.string
              .menu_create_instance_edit);
          ConfirmationDialogFragment.newInstance(b).show(getFragmentManager(),
              "TEMPLATE_CLICK_HINT");
        }
      }
    });
    registerForContextualActionBar(mListView);
    return v;
  }

  @Override
  public boolean dispatchCommandMultiple(int command,
                                         SparseBooleanArray positions, Long[] itemIds) {
    switch (command) {
      case R.id.DELETE_COMMAND:
        MessageDialogFragment.newInstance(
            R.string.dialog_title_warning_delete_template,
            getResources().getQuantityString(R.plurals.warning_delete_template, itemIds.length, itemIds.length),
            new MessageDialogFragment.Button(
                R.string.menu_delete,
                R.id.DELETE_COMMAND_DO,
                itemIds),
            null,
            new MessageDialogFragment.Button(android.R.string.no, R.id.CANCEL_CALLBACK_COMMAND, null))
            .show(getActivity().getSupportFragmentManager(), "DELETE_TEMPLATE");
        return true;
      case R.id.CREATE_INSTANCE_SAVE_COMMAND:
        return ((ManageTemplates) getActivity()).dispatchCommand(command, itemIds);
    }
    return super.dispatchCommandMultiple(command, positions, itemIds);
  }

  @Override
  public boolean dispatchCommandSingle(int command, ContextMenu.ContextMenuInfo info) {
    AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) info;
    switch (command) {
      case R.id.CREATE_INSTANCE_EDIT_COMMAND:
      case R.id.EDIT_COMMAND:
        return ((ManageTemplates) getActivity()).dispatchCommand(command, menuInfo.id);
    }
    return super.dispatchCommandSingle(command, info);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
    switch (id) {
      case SORTABLE_CURSOR:
        return new CursorLoader(getActivity(),
            TransactionProvider.TEMPLATES_URI,
            null,
            KEY_PLANID + " is null",
            null,
            null);
    }
    return null;
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
    switch (loader.getId()) {
      case SORTABLE_CURSOR:
        mTemplatesCursor = c;
        if (c != null && !indexesCalculated) {
          columnIndexAmount = c.getColumnIndex(KEY_AMOUNT);
          columnIndexLabelSub = c.getColumnIndex(KEY_LABEL_SUB);
          columnIndexComment = c.getColumnIndex(KEY_COMMENT);
          columnIndexPayee = c.getColumnIndex(KEY_PAYEE_NAME);
          columnIndexColor = c.getColumnIndex(KEY_COLOR);
          columnIndexTransferPeer = c.getColumnIndex(KEY_TRANSFER_PEER);
          columnIndexCurrency = c.getColumnIndex(KEY_CURRENCY);
          columnIndexTransferAccount = c.getColumnIndex(KEY_TRANSFER_ACCOUNT);
          indexesCalculated = true;
        }
        mAdapter.swapCursor(mTemplatesCursor);
        invalidateCAB();
        break;
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    mAdapter.swapCursor(null);
  }

  @Override
  protected MyApplication.PrefKey getSortOrderPrefKey() {
    return MyApplication.PrefKey.SORT_ORDER_TEMPLATES;
  }

  public class MyAdapter extends SimpleCursorAdapter {
    private int colorExpense;
    private int colorIncome;
    String categorySeparator = " : ",
        commentSeparator = " / ";

    public MyAdapter(Context context, int layout, Cursor c, String[] from,
                     int[] to, int flags) {
      super(context, layout, c, from, to, flags);
      colorIncome = ((ProtectedFragmentActivity) context).getColorIncome();
      colorExpense = ((ProtectedFragmentActivity) context).getColorExpense();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      convertView = super.getView(position, convertView, parent);
      Cursor c = getCursor();
      c.moveToPosition(position);
      TextView tv1 = (TextView) convertView.findViewById(R.id.amount);
      long amount = c.getLong(columnIndexAmount);
      tv1.setTextColor(amount < 0 ? colorExpense : colorIncome);
      tv1.setText(Utils.convAmount(amount, Utils.getSaveInstance(c.getString(columnIndexCurrency))));
      int color = c.getInt(columnIndexColor);
      convertView.findViewById(R.id.colorAccount).setBackgroundColor(color);
      TextView tv2 = (TextView) convertView.findViewById(R.id.category);
      CharSequence catText = tv2.getText();
      if (c.getInt(columnIndexTransferPeer) > 0) {
        catText = ((amount < 0) ? "=> " : "<= ") + catText;
      } else {
        Long catId = DbUtils.getLongOrNull(c, KEY_CATID);
        if (catId == null) {
          catText = Category.NO_CATEGORY_ASSIGNED_LABEL;
        } else {
          String label_sub = c.getString(columnIndexLabelSub);
          if (label_sub != null && label_sub.length() > 0) {
            catText = catText + categorySeparator + label_sub;
          }
        }
      }
      //TODO: simplify confer TemplateWidget
      SpannableStringBuilder ssb;
      String comment = c.getString(columnIndexComment);
      if (comment != null && comment.length() > 0) {
        ssb = new SpannableStringBuilder(comment);
        ssb.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, comment.length(), 0);
        catText = TextUtils.concat(catText, commentSeparator, ssb);
      }
      String payee = c.getString(columnIndexPayee);
      if (payee != null && payee.length() > 0) {
        ssb = new SpannableStringBuilder(payee);
        ssb.setSpan(new UnderlineSpan(), 0, payee.length(), 0);
        catText = TextUtils.concat(catText, commentSeparator, ssb);
      }
      tv2.setText(catText);
      return convertView;
    }
  }

  @Override
  protected void configureMenuLegacy(Menu menu, ContextMenu.ContextMenuInfo menuInfo) {
    super.configureMenuLegacy(menu, menuInfo);
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
    configureMenuInternal(menu, isForeignExchangeTransfer(info.position));
  }

  @Override
  protected void configureMenu11(Menu menu, int count) {
    super.configureMenu11(menu, count);
    SparseBooleanArray checkedItemPositions = mListView.getCheckedItemPositions();
    boolean hasForeignExchangeTransfer = false;
    for (int i = 0; i < checkedItemPositions.size(); i++) {
      if (checkedItemPositions.valueAt(i) && isForeignExchangeTransfer(checkedItemPositions.keyAt
          (i))) {
        hasForeignExchangeTransfer = true;
        break;
      }
    }
    configureMenuInternal(menu, hasForeignExchangeTransfer);
  }

  private void configureMenuInternal(Menu menu, boolean foreignExchangeTransfer) {
    menu.findItem(R.id.CREATE_INSTANCE_SAVE_COMMAND).setVisible(!foreignExchangeTransfer);
  }

  private boolean isForeignExchangeTransfer(int position) {
    if (mTemplatesCursor != null && mTemplatesCursor.moveToPosition(position)) {
      if (mTemplatesCursor.getInt(columnIndexTransferPeer) != 0) {
        Account transferAccount = Account.getInstanceFromDb(
            mTemplatesCursor.getLong(columnIndexTransferAccount));
        return !mTemplatesCursor.getString(columnIndexCurrency).equals(
            transferAccount.currency.getCurrencyCode());
      }
    }
    return false;
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.sort, menu);
    menu.findItem(R.id.SORT_COMMAND).getSubMenu().findItem(R.id.SORT_AMOUNT_COMMAND).setVisible(true);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    return handleSortOption(item);
  }

}
