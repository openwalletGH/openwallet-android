package com.coinomi.wallet;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.exceptions.AddressMalformedException;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftTxStatus;
import com.coinomi.core.wallet.AbstractAddress;

import java.io.Serializable;
import java.util.List;

import javax.annotation.Nonnull;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 */
public class ExchangeHistoryProvider extends ContentProvider {
    private static final String DATABASE_TABLE = "exchange_history";

    public static final String KEY_ROWID = "_id";
    public static final String KEY_STATUS = "status";
    public static final String KEY_DEPOSIT_TXID = "deposit_txid";
    public static final String KEY_DEPOSIT_ADDRESS = "deposit_address";
    public static final String KEY_DEPOSIT_COIN_ID = "deposit_coin_id";
    public static final String KEY_DEPOSIT_AMOUNT_UNIT = "deposit_amount_unit";
    public static final String KEY_WITHDRAW_TXID = "withdraw_txid";
    public static final String KEY_WITHDRAW_ADDRESS = "withdraw_address";
    public static final String KEY_WITHDRAW_COIN_ID = "withdraw_coin_id";
    public static final String KEY_WITHDRAW_AMOUNT_UNIT = "withdraw_amount_unit";

    private Helper helper;

    public static Uri contentUri(@Nonnull final String packageName, @Nonnull final AbstractAddress deposit) {
        return Uri.parse("content://" + packageName + '.' + DATABASE_TABLE).buildUpon()
                .appendPath(deposit.getType().getId()).appendPath(deposit.toString()).build();
    }

    public static Uri contentUri(@Nonnull final String packageName) {
        return Uri.parse("content://" + packageName + '.' + DATABASE_TABLE);
    }

    public static ExchangeEntry getExchangeEntry(@Nonnull final Cursor cursor) {

        final int status = getStatus(cursor);

        CoinType depositType = CoinID.typeFromId(cursor.getString(cursor.getColumnIndexOrThrow(KEY_DEPOSIT_COIN_ID)));
        AbstractAddress depositAddress;
        try {
            depositAddress = depositType.newAddress(cursor.getString(cursor.getColumnIndexOrThrow(KEY_DEPOSIT_ADDRESS)));
        } catch (AddressMalformedException e) {
            // Should never happen
            throw new RuntimeException(e);
        }
        Value depositAmount = depositType.value(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DEPOSIT_AMOUNT_UNIT)));
        String depositTxId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DEPOSIT_TXID));

        AbstractAddress withdrawAddress;
        Value withdrawAmount;
        String withdrawTxId;

        try {
            CoinType withdrawType = CoinID.typeFromId(cursor.getString(cursor.getColumnIndexOrThrow(KEY_WITHDRAW_COIN_ID)));
            withdrawAddress = withdrawType.newAddress(cursor.getString(cursor.getColumnIndexOrThrow(KEY_WITHDRAW_ADDRESS)));
            withdrawAmount = withdrawType.value(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_WITHDRAW_AMOUNT_UNIT)));
            withdrawTxId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_WITHDRAW_TXID));
        } catch (Exception e) {
            withdrawAddress = null;
            withdrawAmount = null;
            withdrawTxId = null;
        }

        return new ExchangeEntry(status, depositAddress, depositAmount, depositTxId,
                withdrawAddress, withdrawAmount, withdrawTxId);
    }


    public static int getStatus(@Nonnull final Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(KEY_STATUS));
    }

    @Override
    public boolean onCreate() {
        helper = new Helper(getContext());
        return true;
    }

    @Override
    public String getType(final Uri uri) {
        throw new UnsupportedOperationException();
    }

    private AbstractAddress getDepositAddress(Uri uri) {
        AbstractAddress address;
        final List<String> pathSegments = getPathSegments(uri);
        try {
            address = CoinID.typeFromId(pathSegments.get(0)).newAddress(pathSegments.get(1));
        } catch (AddressMalformedException e) {
            throw new IllegalArgumentException(e);
        }
        return address;
    }

    private List<String> getPathSegments(Uri uri) {
        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() != 2)
            throw new IllegalArgumentException(uri.toString());
        return pathSegments;
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        final AbstractAddress address = getDepositAddress(uri);

        values.put(KEY_DEPOSIT_COIN_ID, address.getType().getId());
        values.put(KEY_DEPOSIT_ADDRESS, address.toString());

        long rowId = helper.getWritableDatabase().insertOrThrow(DATABASE_TABLE, null, values);

        final Uri rowUri = contentUri(getContext().getPackageName(), address).buildUpon()
                .appendPath(Long.toString(rowId)).build();

        getContext().getContentResolver().notifyChange(rowUri, null);

        return rowUri;
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        final AbstractAddress address = getDepositAddress(uri);

        values.put(KEY_DEPOSIT_COIN_ID, address.getType().getId());
        values.put(KEY_DEPOSIT_ADDRESS, address.toString());

        final int count = helper.getWritableDatabase().update(DATABASE_TABLE, values,
                KEY_DEPOSIT_COIN_ID + "=? AND " + KEY_DEPOSIT_ADDRESS + "=?",
                new String[]{address.getType().getId(), address.toString()});

        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        final AbstractAddress address = getDepositAddress(uri);

        final int count = helper.getWritableDatabase().delete(DATABASE_TABLE,
                KEY_DEPOSIT_COIN_ID + "=? AND " + KEY_DEPOSIT_ADDRESS + "=?",
                new String[]{address.getType().getId(), address.toString()});

        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection,
                        final String[] selectionArgs, final String sortOrder) {
        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(DATABASE_TABLE);

        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() > 2)
            throw new IllegalArgumentException(uri.toString());

        if (pathSegments.size() == 2) {
            final AbstractAddress address = getDepositAddress(uri);
            qb.appendWhere(KEY_DEPOSIT_COIN_ID + "=");
            qb.appendWhereEscapeString(address.getType().getId());
            qb.appendWhere(" AND " + KEY_DEPOSIT_ADDRESS + "=");
            qb.appendWhereEscapeString(address.toString());
        }

        final Cursor cursor = qb.query(helper.getReadableDatabase(), projection,
                selection, selectionArgs, null, null, sortOrder);

        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    private static class Helper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "exchange_history";
        private static final int DATABASE_VERSION = 2;

        private static final String DATABASE_CREATE = "CREATE TABLE " + DATABASE_TABLE + " ("
                + KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + KEY_STATUS + " INTEGER NOT NULL, "
                + KEY_DEPOSIT_ADDRESS + " TEXT NOT NULL, "
                + KEY_DEPOSIT_COIN_ID + " TEXT NOT NULL, "
                + KEY_DEPOSIT_AMOUNT_UNIT + " INTEGER NOT NULL, "
                + KEY_DEPOSIT_TXID + " TEXT NOT NULL, "
                + KEY_WITHDRAW_ADDRESS + " TEXT NULL, "
                + KEY_WITHDRAW_COIN_ID + " TEXT NULL, "
                + KEY_WITHDRAW_AMOUNT_UNIT + " INTEGER NULL, "
                + KEY_WITHDRAW_TXID + " TEXT NULL);";

        public Helper(final Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            db.beginTransaction();
            try {
                for (int v = oldVersion; v < newVersion; v++)
                    upgrade(db, v);

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        private void upgrade(final SQLiteDatabase db, final int oldVersion) {
            if (oldVersion == 1) {
                db.execSQL(renameCoinId(KEY_DEPOSIT_COIN_ID, "darkcoin.main", "dash.main"));
                db.execSQL(renameCoinId(KEY_WITHDRAW_COIN_ID, "darkcoin.main", "dash.main"));
            } else {
                throw new UnsupportedOperationException("old=" + oldVersion);
            }
        }

        private String renameCoinId(String fieldName, String from, String to) {
            return "UPDATE " + DATABASE_TABLE + " SET " + fieldName +
                    " = replace(" + fieldName + ", \"" + from + "\", \"" + to + "\") " +
                    "WHERE " + fieldName + " == \"" + from + "\"";
        }
    }

    public static class ExchangeEntry implements Serializable {
        public static final int STATUS_INITIAL = 0;
        public static final int STATUS_PROCESSING = 1;
        public static final int STATUS_COMPLETE = 2;
        public static final int STATUS_FAILED = -1;
        public static final int STATUS_UNKNOWN = -2;

        public final int status;
        public final AbstractAddress depositAddress;
        public final Value depositAmount;
        public final String depositTransactionId;
        public final AbstractAddress withdrawAddress;
        public final Value withdrawAmount;
        public final String withdrawTransactionId;

        public ExchangeEntry(int status, @Nonnull AbstractAddress depositAddress,
                             @Nonnull Value depositAmount, @Nonnull String depositTransactionId,
                             AbstractAddress withdrawAddress, Value withdrawAmount,
                             String withdrawTransactionId) {
            this.status = status;
            this.depositAddress = checkNotNull(depositAddress);
            this.depositAmount = checkNotNull(depositAmount);
            this.depositTransactionId = checkNotNull(depositTransactionId);
            this.withdrawAddress = withdrawAddress;
            this.withdrawAmount = withdrawAmount;
            this.withdrawTransactionId = withdrawTransactionId;
        }

        public ExchangeEntry(AbstractAddress depositAddress, Value depositAmount, String depositTxId) {
            this(STATUS_INITIAL, depositAddress, depositAmount, depositTxId, null, null, null);
        }

        public ExchangeEntry(ExchangeEntry initialEntry, ShapeShiftTxStatus txStatus) {
            this.status = convertStatus(txStatus.status);
            this.depositAddress = checkNotNull(txStatus.address == null ?
                    initialEntry.depositAddress : txStatus.address);
            this.depositAmount = checkNotNull(txStatus.incomingValue == null ?
                    initialEntry.depositAmount : txStatus.incomingValue);
            this.depositTransactionId = checkNotNull(initialEntry.depositTransactionId);
            this.withdrawAddress = txStatus.withdraw;
            this.withdrawAmount = txStatus.outgoingValue;
            this.withdrawTransactionId = txStatus.transactionId;
        }

        public ContentValues getContentValues() {
            ContentValues values = new ContentValues();
            values.put(KEY_STATUS, status);
            values.put(KEY_DEPOSIT_ADDRESS, depositAddress.toString());
            values.put(KEY_DEPOSIT_COIN_ID, depositAddress.getType().getId());
            values.put(KEY_DEPOSIT_AMOUNT_UNIT, depositAmount.value);
            values.put(KEY_DEPOSIT_TXID, depositTransactionId);
            if (withdrawAddress != null) values.put(KEY_WITHDRAW_ADDRESS, withdrawAddress.toString());
            if (withdrawAddress != null) values.put(KEY_WITHDRAW_COIN_ID, withdrawAddress.getType().getId());
            if (withdrawAmount != null) values.put(KEY_WITHDRAW_AMOUNT_UNIT, withdrawAmount.value);
            if (withdrawTransactionId != null) values.put(KEY_WITHDRAW_TXID, withdrawTransactionId);
            return values;
        }

        public ShapeShiftTxStatus getShapeShiftTxStatus() {
            ShapeShiftTxStatus.Status shapeShiftStatus;
            switch (status) {
                case STATUS_INITIAL:
                    shapeShiftStatus = ShapeShiftTxStatus.Status.NO_DEPOSITS;
                    break;
                case STATUS_PROCESSING:
                    shapeShiftStatus = ShapeShiftTxStatus.Status.RECEIVED;
                    break;
                case STATUS_COMPLETE:
                    shapeShiftStatus = ShapeShiftTxStatus.Status.COMPLETE;
                    break;
                case STATUS_FAILED:
                    shapeShiftStatus = ShapeShiftTxStatus.Status.FAILED;
                    break;
                case STATUS_UNKNOWN:
                default:
                    shapeShiftStatus = ShapeShiftTxStatus.Status.UNKNOWN;
            }

            return new ShapeShiftTxStatus(shapeShiftStatus, depositAddress, withdrawAddress,
                    depositAmount, withdrawAmount, withdrawTransactionId);
        }

        public static int convertStatus(ShapeShiftTxStatus.Status shapeShiftStatus) {
            switch (shapeShiftStatus) {
                case NO_DEPOSITS:
                    return STATUS_INITIAL;
                case RECEIVED:
                    return STATUS_PROCESSING;
                case COMPLETE:
                    return STATUS_COMPLETE;
                case FAILED:
                    return STATUS_FAILED;
                case UNKNOWN:
                default:
                    return STATUS_UNKNOWN;
            }
        }
    }
}
