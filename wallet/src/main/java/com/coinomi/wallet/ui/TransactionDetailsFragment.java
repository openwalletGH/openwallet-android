package com.coinomi.wallet.ui;



import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.TransactionAmountVisualizer;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Fragment that restores a wallet
 */
public class TransactionDetailsFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(TransactionDetailsFragment.class);
    private TextView txStatus;
    private TextView txId;
    private TransactionAmountVisualizer amountVisualizer;
    private TextView blockExplorerLink;

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
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_transaction_details, container, false);

        txStatus = (TextView) view.findViewById(R.id.tx_status);
        amountVisualizer = (TransactionAmountVisualizer) view.findViewById(R.id.transaction_amount_visualizer);
        txId = (TextView) view.findViewById(R.id.tx_id);
        blockExplorerLink = (TextView) view.findViewById(R.id.block_explorer_link);

        CoinType type = CoinID.typeFromId(getArguments().getString(Constants.ARG_COIN_ID));
        String txId = getArguments().getString(Constants.ARG_TRANSACTION_ID);
        WalletPocket pocket = getWalletApplication().getWalletPocket(type);

        if (pocket == null || txId == null) {
            cannotShowTxDetails();
        } else {
            Transaction tx = pocket.getTransaction(new Sha256Hash(txId));
            if (tx == null) {
                cannotShowTxDetails();
            } else {
                showTxDetails(pocket, tx);
            }
        }
        return view;
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
        txStatus.setText(txStatusText);
        amountVisualizer.setTransaction(pocket, tx);
        txId.setText(tx.getHashAsString());

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
}
