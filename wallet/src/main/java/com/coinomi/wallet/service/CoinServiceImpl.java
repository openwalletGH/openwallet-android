package com.coinomi.wallet.service;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.format.DateUtils;

import com.coinomi.core.network.ConnectivityHelper;
import com.coinomi.core.network.ServerClients;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.WalletApplication;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.CheckForNull;


/**
 * @author John L. Jegutanis
 * @author Andreas Schildbach
 */
public class CoinServiceImpl extends Service implements CoinService {
    private WalletApplication application;
    private Configuration config;
    private ConnectivityHelper connHelper;
    private BroadcastReceiver connectivityReceiver;

    @CheckForNull
    private ServerClients clients;

    private String lastAccount;

//    private PowerManager.WakeLock wakeLock;

    private NotificationManager nm;
    private static final int NOTIFICATION_ID_CONNECTED = 0;
    private static final int NOTIFICATION_ID_COINS_RECEIVED = 1;

    private int notificationCount = 0;
    private BigInteger notificationAccumulatedAmount = BigInteger.ZERO;
    private final List<AbstractAddress> notificationAddresses = new LinkedList<>();
    private AtomicInteger transactionsReceived = new AtomicInteger();
    private long serviceCreatedAt;

    private static final int MIN_COLLECT_HISTORY = 2;
    private static final int IDLE_BLOCK_TIMEOUT_MIN = 2;
    private static final int IDLE_TRANSACTION_TIMEOUT_MIN = 9;
    private static final int MAX_HISTORY_SIZE = Math.max(IDLE_TRANSACTION_TIMEOUT_MIN, IDLE_BLOCK_TIMEOUT_MIN);
    private static final long APPWIDGET_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS;

    private static final Logger log = LoggerFactory.getLogger(CoinService.class);

//    private final WalletEventListener walletEventListener = new ThrottlingWalletChangeListener(APPWIDGET_THROTTLE_MS)
//    {
//        @Override
//        public void onThrottledWalletChanged()
//        {
//            notifyWidgets();
//        }
//
//        @Override
//        public void onCoinsReceived(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
//        {
//            transactionsReceived.incrementAndGet();
//
//            final int bestChainHeight = blockChain.getBestChainHeight();
//
//            final Address from = WalletUtils.getFirstFromAddress(tx);
//            final BigInteger amount = tx.getValue(wallet);
//            final TransactionConfidence.ConfidenceType confidenceType = tx.getConfidence().getConfidenceType();
//
//            handler.post(new Runnable()
//            {
//                @Override
//                public void run()
//                {
//                    final boolean isReceived = amount.signum() > 0;
//                    final boolean replaying = bestChainHeight < bestChainHeightEver;
//                    final boolean isReplayedTx = confidenceType == TransactionConfidence.ConfidenceType.BUILDING && replaying;
//
//                    if (isReceived && !isReplayedTx)
//                        notifyCoinsReceived(from, amount);
//                }
//            });
//        }
//
//        @Override
//        public void onCoinsSent(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
//        {
//            transactionsReceived.incrementAndGet();
//        }
//    };

//    private void notifyCoinsReceived(@Nullable final Address from, @Nonnull final BigInteger amount)
//    {
//        if (notificationCount == 1)
//            nm.cancel(NOTIFICATION_ID_COINS_RECEIVED);
//
//        notificationCount++;
//        notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);
//        if (from != null && !notificationAddresses.contains(from))
//            notificationAddresses.add(from);
//
//        final int btcPrecision = config.getBtcPrecision();
//        final int btcShift = config.getBtcShift();
//        final String btcPrefix = config.getBtcPrefix();
//
//        final String packageFlavor = application.applicationPackageFlavor();
//        final String msgSuffix = packageFlavor != null ? " [" + packageFlavor + "]" : "";
//
//        final String tickerMsg = getString(R.string.notification_coins_received_msg,
//                btcPrefix + ' ' + GenericUtils.formatCoinValue(amount, btcPrecision, btcShift))
//                + msgSuffix;
//
//        final String msg = getString(R.string.notification_coins_received_msg,
//                btcPrefix + ' ' + GenericUtils.formatCoinValue(notificationAccumulatedAmount, btcPrecision, btcShift))
//                + msgSuffix;
//
//        final StringBuilder text = new StringBuilder();
//        for (final Address address : notificationAddresses)
//        {
//            if (text.length() > 0)
//                text.append(", ");
//
//            final String addressStr = address.toString();
//            final String label = AddressBookProvider.resolveLabel(getApplicationContext(), addressStr);
//            text.append(label != null ? label : addressStr);
//        }
//
//        final NotificationCompat.Builder notification = new NotificationCompat.Builder(this);
//        notification.setSmallIcon(R.drawable.stat_notify_received);
//        notification.setTicker(tickerMsg);
//        notification.setContentTitle(msg);
//        if (text.length() > 0)
//            notification.setContentText(text);
//        notification.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, WalletActivity.class), 0));
//        notification.setNumber(notificationCount == 1 ? 0 : notificationCount);
//        notification.setWhen(System.currentTimeMillis());
//        notification.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.coins_received));
//        nm.notify(NOTIFICATION_ID_COINS_RECEIVED, notification.getNotification());
//    }
//

    private class MyBroadcastReceiver extends BroadcastReceiver {
        private final ConnectivityManager connectivityManager;
        private boolean hasConnectivity;
        private boolean hasStorage = true;
        private int currentNetworkType = -1;

        public MyBroadcastReceiver(ConnectivityManager connectivityManager) {
            this.connectivityManager = connectivityManager;
            checkNetworkType();
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                hasConnectivity = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

                boolean isNetworkChanged = checkNetworkType();
                log.info("network is " + (hasConnectivity ? "up" : "down"));
                log.info("network type " + (isNetworkChanged ? "changed" : "didn't change"));

                check(isNetworkChanged);
            } else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action)) {
                hasStorage = false;
                log.info("device storage low");

                check(false);
            } else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action)) {
                hasStorage = true;
                log.info("device storage ok");

                check(false);
            }
        }

        private boolean checkNetworkType() {
            boolean isNetworkChanged;
            NetworkInfo activeInfo = connectivityManager.getActiveNetworkInfo();
            if (activeInfo != null && activeInfo.isConnected()) {
                isNetworkChanged = currentNetworkType != activeInfo.getType();
                currentNetworkType = activeInfo.getType();
            } else {
                isNetworkChanged = false;
                currentNetworkType = -1;
            }
            return isNetworkChanged;
        }

        //        @SuppressLint("Wakelock")
        private void check(boolean isNetworkChanged) {
            Wallet wallet = application.getWallet();
            final boolean hasEverything = hasConnectivity && hasStorage && (wallet != null);

            if (hasEverything && clients == null) {
//                log.debug("acquiring wakelock");
//                wakeLock.acquire();

                log.info("Creating coins clients");
                clients = getServerClients(wallet);
//                if (lastAccount != null) clients.startAsync(wallet.getAccount(lastAccount));
            } else if (hasEverything && isNetworkChanged) {
                log.info("Restarting coins clients as network changed");
                clients.resetConnections();
            } else if (!hasEverything && clients != null) {
                log.info("stopping stratum clients");
                disconnectClients();

//                log.debug("releasing wakelock");
//                wakeLock.release();
            }
        }
    };

    private ServerClients getServerClients(Wallet wallet) {
        ServerClients newClients = new ServerClients(Constants.DEFAULT_COINS_SERVERS, connHelper);
        if (application.getTxCachePath() != null) {
            newClients.setCacheDir(application.getTxCachePath(), Constants.TX_CACHE_SIZE);
        }
        return newClients;
    }

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            log.debug("Received a tick {}", intent);

            if (clients != null) {
                clients.ping(application.getVersionString());
            }

            long lastStop = application.getLastStop();
            if (lastStop > 0) {
                long secondsIdle = (SystemClock.elapsedRealtime() - lastStop) / 1000;

                if (secondsIdle > Constants.STOP_SERVICE_AFTER_IDLE_SECS) {
                    log.info("Idling detected, stopping service");
                    stopSelf();
                }
            }
        }
    };

    public class LocalBinder extends Binder {
        public CoinService getService() {
            return CoinServiceImpl.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(final Intent intent) {
        log.debug(".onBind()");

        return mBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        log.debug(".onUnbind()");

        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        serviceCreatedAt = System.currentTimeMillis();
        log.debug(".onCreate()");

        super.onCreate();

        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

//        final String lockName = getPackageName() + " blockchain sync";
//        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
//        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockName);

        application = (WalletApplication) getApplication();
        config = application.getConfiguration();
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        connHelper = getConnectivityHelper(connManager);

        connectivityReceiver = new MyBroadcastReceiver(connManager);
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
        intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
        registerReceiver(connectivityReceiver, intentFilter);
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    private ConnectivityHelper getConnectivityHelper(final ConnectivityManager manager) {
        return new ConnectivityHelper() {
            @Override
            public boolean isConnected() {
                NetworkInfo activeInfo = manager.getActiveNetworkInfo();
                return activeInfo != null && activeInfo.isConnected();
            }
        };
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        log.info("service start command: " + intent
                + (intent.hasExtra(Intent.EXTRA_ALARM_COUNT) ? " (alarm count: " + intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 0) + ")" : ""));

        final String action = intent.getAction();

        if (CoinService.ACTION_CANCEL_COINS_RECEIVED.equals(action)) {
            notificationCount = 0;
            notificationAccumulatedAmount = BigInteger.ZERO;
            notificationAddresses.clear();

            nm.cancel(NOTIFICATION_ID_COINS_RECEIVED);

        } else if (CoinService.ACTION_CLEAR_CONNECTIONS.equals(action)) {
            disconnectClients();
        } else if (CoinService.ACTION_RESET_ACCOUNT.equals(action)) {
            if (application.getWallet() != null) {
                Wallet wallet = application.getWallet();
                if (intent.hasExtra(Constants.ARG_ACCOUNT_ID)) {
                    String accountId = intent.getStringExtra(Constants.ARG_ACCOUNT_ID);
                    WalletAccount account = wallet.getAccount(accountId);
                    if (account != null) {
                        account.refresh();

                        if (clients == null) {
                            if (connHelper.isConnected()) {
                                clients = getServerClients(wallet);
                                clients.startAsync(account);
                            }
                        } else {
                            clients.resetAccount(account);
                        }
                    } else {
                        log.warn("Tried to start a service for account id {} but no account found.",
                                accountId);
                    }

                } else {
                    log.warn("Missing account id argument, not doing anything");
                }
            } else {
                log.warn("Got wallet reset intent, but no wallet is available");
            }
        } else if (CoinService.ACTION_RESET_WALLET.equals(action)) {
            if (application.getWallet() != null) {
                Wallet wallet = application.getWallet();

                if (clients == null) {
                    if (connHelper.isConnected()) {
                        clients = getServerClients(wallet);
                    }
                }

                for (WalletAccount account : wallet.getAllAccounts()) {
                    account.refresh();

                    if (clients != null) {
                        clients.startOrResetAccountAsync(account);
                    }
                }
            } else {
                log.warn("Got wallet reset intent, but no wallet is available");
            }
        } else if (CoinService.ACTION_CONNECT_COIN.equals(action)) {
            if (application.getWallet() != null) {
                Wallet wallet = application.getWallet();
                if (intent.hasExtra(Constants.ARG_ACCOUNT_ID)) {
                    lastAccount = intent.getStringExtra(Constants.ARG_ACCOUNT_ID);
                    WalletAccount account = wallet.getAccount(lastAccount);
                    if (account != null) {
                        if (clients == null && connHelper.isConnected()) {
                            clients = getServerClients(wallet);
                        }

                        if (clients != null) clients.startAsync(account);
                    } else {
                        log.warn("Tried to start a service for account id {} but no account found.",
                                lastAccount);
                    }
                } else {
                    log.warn("Missing account id argument, not doing anything");
                }
            } else {
                log.error("Got connect coin intent, but no wallet is available");
            }
        } else if (CoinService.ACTION_CONNECT_ALL_COIN.equals(action)) {
            if (application.getWallet() != null) {
                Wallet wallet = application.getWallet();
                if (clients == null && connHelper.isConnected()) {
                    clients = getServerClients(wallet);
                }

                if (clients != null) {
                    for (WalletAccount account : wallet.getAllAccounts()) {
                        clients.startAsync(account);
                    }
                }
            } else {
                log.error("Got connect coin intent, but no wallet is available");
            }
        } else if (CoinService.ACTION_BROADCAST_TRANSACTION.equals(action)) {
            final Sha256Hash hash = new Sha256Hash(intent.getByteArrayExtra(CoinService.ACTION_BROADCAST_TRANSACTION_HASH));
            final Transaction tx = null; // FIXME

            if (clients != null) {
                log.info("broadcasting transaction " + tx.getHashAsString());
                broadcastTransaction(tx);
            } else {
                log.info("client not available, not broadcasting transaction " + tx.getHashAsString());
            }
        }

        return START_REDELIVER_INTENT;
    }

    private void broadcastTransaction(Transaction tx) {
        // TODO send broadcast message
    }

    @Override
    public void onDestroy() {
        log.debug(".onDestroy()");

        unregisterReceiver(tickReceiver);
        unregisterReceiver(connectivityReceiver);

        disconnectClients();

        application.saveWalletNow();

//        if (wakeLock.isHeld())
//        {
//            log.debug("wakelock still held, releasing");
//            wakeLock.release();
//        }

        super.onDestroy();

        log.info("service was up for " + ((System.currentTimeMillis() - serviceCreatedAt) / 1000 / 60) + " minutes");
    }

    private void disconnectClients() {
        if (clients != null) {
            clients.stopAllAsync();
            clients = null;
        }
    }

    @Override
    public void onLowMemory() {
        log.warn("low memory detected, stopping service");
        stopSelf();
    }
}
