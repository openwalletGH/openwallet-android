package com.coinomi.wallet.ui;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.AbstractTransaction.AbstractOutput;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
import com.coinomi.wallet.util.TimeUtils;
import com.coinomi.wallet.util.UiUtils;
import com.coinomi.wallet.util.WeakHandler;

import org.acra.ACRA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Fragment that restores a wallet
 */
public class TransactionDetailsFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(TransactionDetailsFragment.class);

    private static final int UPDATE_VIEW = 0;

    private String txId;
    private String accountId;
    private AbstractWallet pocket;
    private CoinType type;

    private ListView outputRows;
    private TransactionAmountVisualizerAdapter adapter;
    private TextView txStatusView;
    private TextView txDateLabel;
    private TextView txDate;
    private TextView txIdView;
    private TextView txMessageLabel;
    private TextView txMessage;
    private TextView blockExplorerLink;

    private final Handler handler = new MyHandler(this);

    private static class MyHandler extends WeakHandler<TransactionDetailsFragment> {
        public MyHandler(TransactionDetailsFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(TransactionDetailsFragment ref, Message msg) {
            switch (msg.what) {
                case UPDATE_VIEW:
                    ref.updateView();
            }
        }
    }

    public TransactionDetailsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            accountId = getArguments().getString(Constants.ARG_ACCOUNT_ID);
        }
        pocket = (AbstractWallet) getWalletApplication().getAccount(accountId);
        if (pocket == null) {
            Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
            // TODO report anomaly
            return;
        }
        type = pocket.getCoinType();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transaction_details, container, false);

        outputRows = (ListView) view.findViewById(R.id.output_rows);
        outputRows.setOnItemClickListener(getListener());

        // Header
        View header = inflater.inflate(R.layout.fragment_transaction_details_header, null);
        outputRows.addHeaderView(header, null, false);
        txStatusView = (TextView) header.findViewById(R.id.tx_status);
        txDateLabel = (TextView) header.findViewById(R.id.tx_date_label);
        txDate = (TextView) header.findViewById(R.id.tx_date);

        // Footer
        View footer = inflater.inflate(R.layout.fragment_transaction_details_footer, null);
        outputRows.addFooterView(footer, null, false);
        txIdView = (TextView) footer.findViewById(R.id.tx_id);
        txMessageLabel = (TextView) footer.findViewById(R.id.tx_message_label);
        txMessage = (TextView) footer.findViewById(R.id.tx_message);
        blockExplorerLink = (TextView) footer.findViewById(R.id.block_explorer_link);

        pocket.addEventListener(walletListener);

        adapter = new TransactionAmountVisualizerAdapter(inflater.getContext(), pocket);
        outputRows.setAdapter(adapter);

        txId = getArguments().getString(Constants.ARG_TRANSACTION_ID);

        updateView();

        return view;
    }

    private AdapterView.OnItemClickListener getListener() {
        return new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= outputRows.getHeaderViewsCount()) {
                    Object obj = parent.getItemAtPosition(position);

                    if (obj != null && obj instanceof AbstractOutput) {
                        UiUtils.startAddressActionMode(((AbstractOutput) obj).getAddress(),
                                getActivity(), getFragmentManager());
                    }
                }
            }
        };
    }

    @Override
    public void onDestroyView() {
        pocket.removeEventListener(walletListener);
        walletListener.removeCallbacks();
        super.onDestroyView();
    }

    private void updateView() {
        if (isRemoving() || isDetached()) return;
        if (txId == null) {
            cannotShowTxDetails();
        } else {
            AbstractTransaction tx = pocket.getTransaction(txId);
            if (tx == null) {
                cannotShowTxDetails();
            } else {
                showTxDetails(pocket, tx);
            }
        }
    }

    private void showTxDetails(AbstractWallet pocket, AbstractTransaction tx) {
        String txStatusText;
        switch (tx.getConfidenceType()) {
            case BUILDING:
                txStatusText = getResources().getQuantityString(R.plurals.status_building,
                        tx.getDepthInBlocks(), tx.getDepthInBlocks());
                break;
            case PENDING:
                txStatusText = getString(R.string.status_pending);
                break;
            default:
            case DEAD:
            case UNKNOWN:
                txStatusText = getString(R.string.status_unknown);
        }
        txStatusView.setText(txStatusText);
        long timestamp = tx.getTimestamp();
        if (timestamp > 0) {
            txDate.setText(TimeUtils.toTimeString(getWalletApplication(), timestamp));
            txDateLabel.setVisibility(View.VISIBLE);
            txDate.setVisibility(View.VISIBLE);
        } else {
            txDateLabel.setVisibility(View.GONE);
            txDate.setVisibility(View.GONE);
        }
        adapter.setTransaction(tx);
        txIdView.setText(tx.getHashAsString());
        setupBlockExplorerLink(pocket.getCoinType(), tx.getHashAsString());

        // Show message
        if (type.canHandleMessages() && tx.getMessage() != null) {
            try {
                // TODO not efficient, should parse the message and save it to a database
                TxMessage message = tx.getMessage();
                if (message != null) {
                    // TODO in the future other coin types could support private encrypted messages
                    txMessageLabel.setText(getString(R.string.tx_message_public));
                    txMessageLabel.setVisibility(View.VISIBLE);
                    txMessage.setText(message.toString());
                    txMessage.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                ACRA.getErrorReporter().handleSilentException(e);
            }
        }
    }

    private void setupBlockExplorerLink(CoinType type, String txHash) {
        if (Constants.COINS_BLOCK_EXPLORERS.containsKey(type)) {
            final String url = String.format(Constants.COINS_BLOCK_EXPLORERS.get(type), txHash);
            blockExplorerLink.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                }
            });
        } else {
            blockExplorerLink.setVisibility(View.GONE);
        }
    }

    private void cannotShowTxDetails() {
        Toast.makeText(getActivity(), getString(R.string.get_tx_info_error), Toast.LENGTH_LONG).show();
        getActivity().finish();
    }

    WalletApplication getWalletApplication() {
        return (WalletApplication) getActivity().getApplication();
    }

    private final ThrottlingWalletChangeListener walletListener = new ThrottlingWalletChangeListener() {
        @Override
        public void onThrottledWalletChanged() {
            handler.sendMessage(handler.obtainMessage(UPDATE_VIEW));
        }
    };
}
