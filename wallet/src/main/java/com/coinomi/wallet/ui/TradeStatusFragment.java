package com.coinomi.wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.coinomi.core.exchange.shapeshift.ShapeShift;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftException;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftTxStatus;
import com.coinomi.core.wallet.WalletPocketConnectivity;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.util.Fonts;
import com.coinomi.wallet.util.WeakHandler;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 */
public class TradeStatusFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(TradeStatusFragment.class);

    private static final int UPDATE_STATUS = 0;
    private static final int ERROR_MESSAGE = 1;
    private static final long POLLING_MS = 3000;

    private Listener mListener;
    private TextView depositIcon;
    private ProgressBar depositProgress;
    private TextView depositText;
    private TextView exchangeIcon;
    private ProgressBar exchangeProgress;
    private TextView exchangeText;
    private TextView errorIcon;
    private TextView errorText;
    private Button viewTransaction;
    private Button emailReceipt;

    private Address deposit;
    private ShapeShiftTxStatus status;
    private StatusPollTask pollTask;
    private final Handler handler = new MyHandler(this);
    private Timer timer;
    private WalletApplication application;

    private static class StatusPollTask extends TimerTask {
        private final ShapeShift shapeShift;
        private final Address depositAddress;
        private final Handler handler;

        private StatusPollTask(ShapeShift shapeShift, Address depositAddress, Handler handler) {
            this.shapeShift = shapeShift;
            this.depositAddress = depositAddress;
            this.handler = handler;
        }

        @Override
        public void run() {
            for (int tries = 3; tries > 0; tries--) {
                try {
                    log.info("Polling status for deposit: {}", depositAddress);
                    ShapeShiftTxStatus newStatus = shapeShift.getTxStatus(depositAddress);
                    handler.sendMessage(handler.obtainMessage(UPDATE_STATUS, newStatus));
                    break;
                } catch (ShapeShiftException e) {
                    log.warn("Error occurred while polling", e);
                    handler.sendMessage(handler.obtainMessage(ERROR_MESSAGE, e.getMessage()));
                    break;
                } catch (IOException e) {
                    /* ignore and retry */
                }
            }
        }
    }

    private static class MyHandler extends WeakHandler<TradeStatusFragment> {
        public MyHandler(TradeStatusFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(TradeStatusFragment ref, Message msg) {
            switch (msg.what) {
                case UPDATE_STATUS:
                    ref.status = (ShapeShiftTxStatus) msg.obj;
                    ref.updateView();
                    break;
                case ERROR_MESSAGE:
                    ref.errorIcon.setVisibility(View.VISIBLE);
                    ref.errorText.setVisibility(View.VISIBLE);
                    ref.errorText.setText(ref.getString(R.string.trade_status_failed, msg.obj));
                    ref.stopPolling();
                    break;
            }
        }
    }

    public static TradeStatusFragment newInstance(Address deposit) {
        TradeStatusFragment fragment = new TradeStatusFragment();
        Bundle args = new Bundle();
        args.putSerializable(Constants.ARG_ADDRESS, deposit);
        fragment.setArguments(args);
        return fragment;
    }

    public TradeStatusFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            deposit = (Address) getArguments().getSerializable(Constants.ARG_ADDRESS);
        }
        checkNotNull(deposit);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trade_status, container, false);

        depositIcon = (TextView) view.findViewById(R.id.trade_deposit_status_icon);
        depositProgress = (ProgressBar) view.findViewById(R.id.trade_deposit_status_progress);
        depositText = (TextView) view.findViewById(R.id.trade_deposit_status_text);
        exchangeIcon = (TextView) view.findViewById(R.id.trade_exchange_status_icon);
        exchangeProgress = (ProgressBar) view.findViewById(R.id.trade_exchange_status_progress);
        exchangeText = (TextView) view.findViewById(R.id.trade_exchange_status_text);
        errorIcon = (TextView) view.findViewById(R.id.trade_error_status_icon);
        errorText = (TextView) view.findViewById(R.id.trade_error_status_text);
        Fonts.setTypeface(depositIcon, Fonts.Font.COINOMI_FONT_ICONS);
        Fonts.setTypeface(exchangeIcon, Fonts.Font.COINOMI_FONT_ICONS);
        Fonts.setTypeface(errorIcon, Fonts.Font.COINOMI_FONT_ICONS);

        viewTransaction = (Button) view.findViewById(R.id.trade_view_transaction);
        emailReceipt = (Button) view.findViewById(R.id.trade_email_receipt);

        view.findViewById(R.id.button_finish).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onFinishPressed();
            }
        });

        depositIcon.setVisibility(View.GONE);
        depositProgress.setVisibility(View.VISIBLE);
        depositText.setVisibility(View.VISIBLE);
        depositText.setText(R.string.trade_status_waiting_deposit);
        exchangeIcon.setVisibility(View.GONE);
        exchangeProgress.setVisibility(View.GONE);
        exchangeText.setVisibility(View.GONE);
        errorIcon.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        viewTransaction.setVisibility(View.GONE);
        emailReceipt.setVisibility(View.GONE);

        view.findViewById(R.id.powered_by_shapeshift).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.about_shapeshift_title)
                        .setMessage(R.string.about_shapeshift_message)
                        .setPositiveButton(R.string.button_ok, null)
                        .create().show();
            }
        });

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPolling();
    }

    @Override
    public void onResume() {
        super.onResume();
        startPolling();
    }

    private void startPolling() {
        if (timer == null) {
            ShapeShift shapeShift = application.getShapeShift();
            pollTask = new StatusPollTask(shapeShift, deposit, handler);
            timer = new Timer();
            timer.schedule(pollTask, 0, POLLING_MS);
        }
    }

    private void stopPolling() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
            pollTask.cancel();
            pollTask = null;
        }
    }

    public void onFinishPressed() {
        stopPolling();
        if (mListener != null) {
            mListener.onFinish();
        }
    }

    private void updateView() {
        if (status != null && status.status != null) {
            switch (status.status) {
                case NO_DEPOSITS:
                    depositIcon.setVisibility(View.GONE);
                    depositProgress.setVisibility(View.VISIBLE);
                    depositText.setVisibility(View.VISIBLE);
                    depositText.setText(R.string.trade_status_waiting_deposit);
                    exchangeIcon.setVisibility(View.GONE);
                    exchangeProgress.setVisibility(View.GONE);
                    exchangeText.setVisibility(View.GONE);
                    errorIcon.setVisibility(View.GONE);
                    errorText.setVisibility(View.GONE);
                    viewTransaction.setVisibility(View.GONE);
                    emailReceipt.setVisibility(View.GONE);
                    break;
                case RECEIVED:
                    depositIcon.setVisibility(View.VISIBLE);
                    depositProgress.setVisibility(View.GONE);
                    depositText.setVisibility(View.VISIBLE);
                    depositText.setText(getString(R.string.trade_status_received_deposit,
                            status.incomingValue));
                    exchangeIcon.setVisibility(View.GONE);
                    exchangeProgress.setVisibility(View.VISIBLE);
                    exchangeText.setVisibility(View.VISIBLE);
                    exchangeText.setText(R.string.trade_status_waiting_trade);
                    errorIcon.setVisibility(View.GONE);
                    errorText.setVisibility(View.GONE);
                    viewTransaction.setVisibility(View.GONE);
                    emailReceipt.setVisibility(View.GONE);
                    break;
                case COMPLETE:
                    depositIcon.setVisibility(View.VISIBLE);
                    depositProgress.setVisibility(View.GONE);
                    depositText.setVisibility(View.VISIBLE);
                    depositText.setText(getString(R.string.trade_status_received_deposit,
                            status.incomingValue));
                    exchangeIcon.setVisibility(View.VISIBLE);
                    exchangeProgress.setVisibility(View.GONE);
                    exchangeText.setVisibility(View.VISIBLE);
                    exchangeText.setText(getString(R.string.trade_status_complete,
                            status.outgoingValue));
                    errorIcon.setVisibility(View.GONE);
                    errorText.setVisibility(View.GONE);
                    viewTransaction.setVisibility(View.GONE); // TODO enable
                    emailReceipt.setVisibility(View.GONE);
                    stopPolling();
                    break;
                case FAILED:
                    errorIcon.setVisibility(View.VISIBLE);
                    errorText.setVisibility(View.VISIBLE);
                    errorText.setText(getString(R.string.trade_status_failed, status.errorMessage));
                    stopPolling();
            }
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (Listener) activity;
            application = (WalletApplication) activity.getApplication();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + TradeStatusFragment.Listener.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface Listener {
        public void onFinish();
    }

}
