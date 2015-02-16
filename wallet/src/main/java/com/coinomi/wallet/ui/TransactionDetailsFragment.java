package com.coinomi.wallet.ui;



import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.TransactionAmountVisualizer;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import static com.coinomi.core.Preconditions.checkNotNull;


/**
 * Fragment that restores a wallet
 */
public class TransactionDetailsFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(TransactionDetailsFragment.class);

    private static final int UPDATE_VIEW = 0;

    private Sha256Hash txId;
    private WalletPocket pocket;
    private CoinType type;

    private ListView outputRows;
    private TransactionAmountVisualizerAdapter adapter;
    private TextView txStatusView;
    private TextView txIdView;
    private TextView blockExplorerLink;
    private TextView sendDirectionView;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_VIEW:
                    updateView();
            }
        }
    };

    public TransactionDetailsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        sendDirectionView = (TextView) header.findViewById(R.id.send_direction);

        // Footer
        View footer = inflater.inflate(R.layout.fragment_transaction_details_footer, null);
        outputRows.addFooterView(footer, null, false);
        txIdView = (TextView) footer.findViewById(R.id.tx_id);
        blockExplorerLink = (TextView) footer.findViewById(R.id.block_explorer_link);

        type = CoinID.typeFromId(getArguments().getString(Constants.ARG_COIN_ID));
        pocket = checkNotNull(getWalletApplication().getWalletPocket(type));
        pocket.addEventListener(walletListener);

        adapter = new TransactionAmountVisualizerAdapter(inflater.getContext(), pocket);
        outputRows.setAdapter(adapter);

        String txIdString = getArguments().getString(Constants.ARG_TRANSACTION_ID);
        if (txIdString != null) {
            txId = new Sha256Hash(txIdString);
        }

        updateView();

        return view;
    }

    private AdapterView.OnItemClickListener getListener() {
        return new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= outputRows.getHeaderViewsCount()) {
                    Object obj = parent.getItemAtPosition(position);

                    if (obj != null && obj instanceof TransactionOutput) {
                        TransactionOutput txo = (TransactionOutput) obj;
                        actionForOutput(txo);
                    }
                }
            }
        };
    }

    private void actionForOutput(final TransactionOutput txo) {
        if (!(getActivity() instanceof ActionBarActivity)) {
            log.warn("To show action mode, your activity must extend " + ActionBarActivity.class);
            return;
        }

        final String address = txo.getScriptPubKey().getToAddress(type).toString();

        ((ActionBarActivity) getActivity()).startSupportActionMode(new ActionMode.Callback() {

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                final MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.transaction_details_output_options, menu);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                final String label = AddressBookProvider.resolveLabel(getActivity(), type, address);
                mode.setTitle(label != null ? label : GenericUtils.addressSplitToGroups(address));
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.action_edit_label:
                        EditAddressBookEntryFragment.edit(getFragmentManager(), type, address);
                        mode.finish();
                        return true;
                }

                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) { }
        });
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
            Transaction tx = pocket.getTransaction(txId);
            if (tx == null) {
                cannotShowTxDetails();
            } else {
                showTxDetails(pocket, tx);
            }
        }
    }

    private void showTxDetails(WalletPocket pocket, Transaction tx) {
        TransactionConfidence confidence = tx.getConfidence();
        String txStatusText;
        switch (confidence.getConfidenceType()) {
            case BUILDING:
                txStatusText = getResources().getQuantityString(R.plurals.status_building,
                        confidence.getDepthInBlocks(), confidence.getDepthInBlocks());
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
        boolean isSending = tx.getValue(pocket).signum() < 0;
        sendDirectionView.setText(isSending ? R.string.sent : R.string.received);
        adapter.setTransaction(tx);
        txIdView.setText(tx.getHashAsString());
        setupBlockExplorerLink(pocket.getCoinType(), tx.getHashAsString());
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
