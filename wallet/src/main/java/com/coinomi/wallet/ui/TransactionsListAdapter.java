package com.coinomi.wallet.ui;

/*
 * Copyright 2011-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.VpncoinMain;
import com.coinomi.core.messages.MessageFactory;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.families.vpncoin.VpncoinTxMessage;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.widget.CurrencyTextView;
import com.coinomi.wallet.util.Fonts;
import com.coinomi.wallet.util.WalletUtils;

import org.acra.ACRA;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;

/**
 * @author Andreas Schildbach
 */
public class TransactionsListAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final AbstractWallet walletPocket;

    private final List<Transaction> transactions = new ArrayList<Transaction>();
    private final Resources res;
    private int precision = 0;
    private int shift = 0;
    private boolean showEmptyText = false;

    private final int colorSignificant;
    private final int colorLessSignificant;
    private final int colorInsignificant;
    private final int colorError;
    private final int colorCircularBuilding = Color.parseColor("#44ff44");
    private final String minedTitle;
    private final String fontIconMined;
    private final String sentToTitle;
    private final String fontIconSentTo;
    private final String receivedWithTitle;
    private final String fontIconReceivedWith;

    private final Map<String, String> labelCache = new HashMap<String, String>();
    private final static Object CACHE_NULL_MARKER = "";

    private static final String CONFIDENCE_SYMBOL_DEAD = "\u271D"; // latin cross
    private static final String CONFIDENCE_SYMBOL_UNKNOWN = "?";

    private static final int VIEW_TYPE_TRANSACTION = 0;

    public TransactionsListAdapter(final Context context, @Nonnull final AbstractWallet walletPocket) {
        this.context = context;
        inflater = LayoutInflater.from(context);

        this.walletPocket = walletPocket;

        res = context.getResources();
        colorSignificant = res.getColor(R.color.gray_87_text);
        colorLessSignificant = res.getColor(R.color.gray_54_sec_text_icons);
        colorInsignificant = res.getColor(R.color.gray_26_hint_text);
        colorError = res.getColor(R.color.fg_error);
        minedTitle = res.getString(R.string.wallet_transactions_coinbase);
        fontIconMined = res.getString(R.string.font_icon_mining);
        sentToTitle = res.getString(R.string.sent_to);
        fontIconSentTo = res.getString(R.string.font_icon_send_coins);
        receivedWithTitle = res.getString(R.string.received_with);
        fontIconReceivedWith = res.getString(R.string.font_icon_receive_coins);
    }

    public void setPrecision(final int precision, final int shift) {
        this.precision = precision;
        this.shift = shift;

        notifyDataSetChanged();
    }

    public void clear() {
        transactions.clear();

        notifyDataSetChanged();
    }

    public void replace(@Nonnull final Transaction tx) {
        transactions.clear();
        transactions.add(tx);

        notifyDataSetChanged();
    }

    public void replace(@Nonnull final Collection<Transaction> transactions) {
        this.transactions.clear();
        this.transactions.addAll(transactions);

        showEmptyText = true;

        notifyDataSetChanged();
    }

    @Override
    public boolean isEmpty() {
        return showEmptyText && super.isEmpty();
    }

    @Override
    public int getCount() {
        return transactions.size();
    }

    @Override
    public Transaction getItem(final int position) {
        if (position == transactions.size())
            return null;

        return transactions.get(position);
    }

    @Override
    public long getItemId(final int position) {
        if (position == transactions.size())
            return 0;

        return WalletUtils.longHash(transactions.get(position).getHash());
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(final int position, View row, final ViewGroup parent) {
        final int type = getItemViewType(position);

        if (type == VIEW_TYPE_TRANSACTION) {
            if (row == null)
                row = inflater.inflate(R.layout.transaction_row, null);

            final Transaction tx = getItem(position);
            bindView(row, tx);
        } else {
            throw new IllegalStateException("unknown type: " + type);
        }

        return row;
    }

    public void bindView(@Nonnull final View row, @Nonnull final Transaction tx) {
        Resources res = context.getResources();
        final TransactionConfidence confidence = tx.getConfidence();
        final ConfidenceType confidenceType = confidence.getConfidenceType();
        final boolean isOwn = confidence.getSource().equals(TransactionConfidence.Source.SELF);
        final boolean isMined = tx.isCoinBase() || tx.isCoinStake();
//        final boolean isInternal = WalletUtils.isInternal(tx);

        final Coin value = tx.getValue(walletPocket);
        final boolean sent = value.signum() < 0;

        final CoinType type = walletPocket.getCoinType();

        // TODO set colors as theme, not here in code

        final TextView rowDirectionText = (TextView) row.findViewById(R.id.transaction_row_direction_text);
        final TextView rowDirectionFontIcon = (TextView) row.findViewById(R.id.transaction_row_direction_font_icon);
        Fonts.setTypeface(rowDirectionFontIcon, Fonts.Font.COINOMI_FONT_ICONS);
        final TextView rowConfirmationsFontIcon = (TextView) row.findViewById(R.id.transaction_row_confirmations_font_icon);
        Fonts.setTypeface(rowConfirmationsFontIcon, Fonts.Font.COINOMI_FONT_ICONS);
        final TextView rowMessageFontIcon = (TextView) row.findViewById(R.id.transaction_row_message_font_icon);
        Fonts.setTypeface(rowMessageFontIcon, Fonts.Font.COINOMI_FONT_ICONS);
        // TODO implement date
//        final TextView rowDate = (TextView) row.findViewById(R.id.transaction_row_time);
        final TextView rowLabel = (TextView) row.findViewById(R.id.transaction_row_label);
        final TextView rowAddress = (TextView) row.findViewById(R.id.transaction_row_address);
        final CurrencyTextView rowValue = (CurrencyTextView) row.findViewById(R.id.transaction_row_value);

        // confidence
        if (confidenceType == ConfidenceType.PENDING) {
            rowLabel.setTextColor(colorInsignificant);
            rowValue.setTextColor(colorInsignificant);
            rowDirectionText.setTextColor(colorInsignificant);
            rowDirectionFontIcon.setTextColor(colorInsignificant);
            rowDirectionFontIcon.setBackgroundResource(R.drawable.transaction_row_circle_bg_pending);
        } else if (confidenceType == ConfidenceType.BUILDING) {
            rowLabel.setTextColor(colorSignificant);
            rowValue.setTextColor(colorSignificant);
            rowDirectionText.setTextColor(colorLessSignificant);
            rowDirectionFontIcon.setTextColor(colorLessSignificant);
            if (value.isNegative()) {
                rowDirectionFontIcon.setBackgroundResource(R.drawable.transaction_row_circle_bg_send);
                rowValue.setTextColor(res.getColor(R.color.send_color_fg));
            } else {
                rowDirectionFontIcon.setBackgroundResource(R.drawable.transaction_row_circle_bg_receive);
                rowValue.setTextColor(res.getColor(R.color.receive_color_fg));
            }
        } else if (confidenceType == ConfidenceType.DEAD) {
            rowLabel.setTextColor(colorSignificant);
            rowValue.setTextColor(colorSignificant);
            Fonts.strikeThrough(rowLabel);
            Fonts.strikeThrough(rowValue);
        } else {
            rowDirectionText.setTextColor(colorError);
            rowLabel.setTextColor(colorInsignificant);
            rowValue.setTextColor(colorInsignificant);
            rowDirectionFontIcon.setTextColor(colorInsignificant);
            rowDirectionFontIcon.setBackgroundResource(R.drawable.transaction_row_circle_bg_pending);
        }

        // Confirmations
        if (confidence.getDepthInBlocks() < 4) {
            rowConfirmationsFontIcon.setVisibility(View.VISIBLE);
            rowConfirmationsFontIcon.setTextColor(colorLessSignificant);
            switch (confidence.getDepthInBlocks()) {
                case 0: // No confirmations
                    rowConfirmationsFontIcon.setText(res.getString(R.string.font_icon_progress_empty));
                    rowConfirmationsFontIcon.setTextColor(colorInsignificant); // PENDING
                    break;
                case 1: // 1 out of 3 confirmations
                    rowConfirmationsFontIcon.setText(res.getString(R.string.font_icon_progress_one));
                    break;
                case 2: // 2 out of 3 confirmations
                    rowConfirmationsFontIcon.setText(res.getString(R.string.font_icon_progress_two));
                    break;
                case 3: // 3 out of 3 confirmations
                    rowConfirmationsFontIcon.setText(res.getString(R.string.font_icon_progress_full));
                    break;
            }
        } else {
            rowConfirmationsFontIcon.setVisibility(View.GONE);
        }

        // Money direction and icon
        if (isMined) {
            rowDirectionText.setText(minedTitle);
            rowDirectionFontIcon.setText(fontIconMined);
        } else {
            if (value.isNegative()) {
                rowDirectionText.setText(sentToTitle);
                rowDirectionFontIcon.setText(fontIconSentTo);
            } else {
                rowDirectionText.setText(receivedWithTitle);
                rowDirectionFontIcon.setText(fontIconReceivedWith);
            }
        }

        // date
//        final Date time = tx.getUpdateTime();
//        rowDate.setText(time != null ? (DateUtils.getRelativeTimeSpanString(context, time.getTime())) : null);

        // address - label
        final Address address;
        final String label;

        if (sent) {
            // we send payment to those addresses
            List<Address> sentTo = WalletUtils.getSendToAddress(tx, walletPocket);
            // For now show only the first address
            address = sentTo.size() == 0 ? null : sentTo.get(0);
        } else {
            // received with those addresses
            List<Address> receivedWith = WalletUtils.getReceivedWithAddress(tx, walletPocket);
            // Should be one
            address = receivedWith.size() == 0 ? null : receivedWith.get(0);
        }

        if (address != null) {
            label = resolveLabel(address.toString());
        } else {
            if (sent) {
                // If no address found, assume it is an internal transfer
                label = res.getString(R.string.internal_transfer);
            } else {
                label = "?";
            }
        }

        if (label != null) {
            rowLabel.setText(label);
            if (address != null) {
                rowAddress.setText(GenericUtils.addressSplitToGroups(address.toString()));
                rowAddress.setVisibility(View.VISIBLE);
            } else {
                rowAddress.setVisibility(View.GONE);
            }
        } else if (address != null) {
            rowLabel.setText(GenericUtils.addressSplitToGroups(address.toString()));
            rowAddress.setVisibility(View.GONE);
        } else {
            rowLabel.setText("???"); // should not happen
        }
        rowAddress.setVisibility(View.GONE);
        rowLabel.setTypeface(label != null ? Typeface.DEFAULT : Typeface.MONOSPACE);

        // value
        rowValue.setAlwaysSigned(true);
        rowValue.setAmount(Value.valueOf(type, value));

        // Show message label
        if (type.canHandleMessages()) {
            MessageFactory factory = type.getMessagesFactory();
            try {
                // TODO not efficient, should parse the message and save it to a database
                if (factory != null && factory.extractPublicMessage(tx) != null) {
                    rowMessageFontIcon.setVisibility(View.VISIBLE);
                } else {
                    rowMessageFontIcon.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                rowMessageFontIcon.setVisibility(View.GONE);
                ACRA.getErrorReporter().handleSilentException(e);
            }
        } else {
            rowMessageFontIcon.setVisibility(View.GONE);
        }
    }

    private String resolveLabel(@Nonnull final String address) {
        final String cachedLabel = labelCache.get(address);
        if (cachedLabel == null) {
            final String label = AddressBookProvider.resolveLabel(context, walletPocket.getCoinType(), address);
            if (label != null) {
                labelCache.put(address, label);
            } else {
                labelCache.put(address, (String)CACHE_NULL_MARKER);
            }
            return label;
        } else {
            return cachedLabel != CACHE_NULL_MARKER ? cachedLabel : null;
        }
    }

    public void clearLabelCache() {
        labelCache.clear();

        notifyDataSetChanged();
    }
}
