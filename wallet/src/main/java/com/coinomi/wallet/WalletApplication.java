package com.coinomi.wallet;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.StrictMode;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.exchange.shapeshift.ShapeShift;
import com.coinomi.core.util.HardwareSoftwareCompliance;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.core.wallet.WalletProtobufSerializer;
import com.coinomi.wallet.service.CoinService;
import com.coinomi.wallet.service.CoinServiceImpl;
import com.coinomi.wallet.util.Fonts;
import com.coinomi.wallet.util.LinuxSecureRandom;
import com.coinomi.wallet.util.NetworkUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.store.UnreadableWalletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 * @author Andreas Schildbach
 */
@ReportsCrashes(
        // Also uncomment ACRA.init(this) in onCreate
        httpMethod = HttpSender.Method.PUT,
        reportType = HttpSender.Type.JSON
)
public class WalletApplication extends Application {
    private static final Logger log = LoggerFactory.getLogger(WalletApplication.class);

    private static HashMap<String, Typeface> typefaces;
    private Configuration config;
    private ActivityManager activityManager;

    private Intent coinServiceIntent;
    private Intent coinServiceConnectIntent;
    private Intent coinServiceCancelCoinsReceivedIntent;

    private File walletFile;
    @Nullable
    private Wallet wallet;
    private PackageInfo packageInfo;
    private String versionString;

    private long lastStop;
    private ConnectivityManager connManager;
    private ShapeShift shapeShift;
    private File txCachePath;

    @Override
    public void onCreate() {
//        ACRA.init(this);

        config = new Configuration(PreferenceManager.getDefaultSharedPreferences(this));

        new LinuxSecureRandom(); // init proper random number generator
        performComplianceTests();

        initLogging();

        // TODO review this
        StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder().detectAll().permitDiskReads().permitDiskWrites().penaltyLog().build());

        super.onCreate();

        packageInfo = packageInfoFromContext(this);
        versionString = packageInfo.versionName.replace(" ", "_") + "__" +
                packageInfo.packageName + "_android";

        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        coinServiceIntent = new Intent(this, CoinServiceImpl.class);
        coinServiceConnectIntent = new Intent(CoinService.ACTION_CONNECT_COIN,
                null, this, CoinServiceImpl.class);
        coinServiceCancelCoinsReceivedIntent = new Intent(CoinService.ACTION_CANCEL_COINS_RECEIVED,
                null, this, CoinServiceImpl.class);

        createTxCache();

        // Set MnemonicCode.INSTANCE if needed
        if (MnemonicCode.INSTANCE == null) {
            try {
                MnemonicCode.INSTANCE = new MnemonicCode();
            } catch (IOException e) {
                throw new RuntimeException("Could not set MnemonicCode.INSTANCE", e);
            }
        }

        config.updateLastVersionCode(packageInfo.versionCode);

        connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        walletFile = getFileStreamPath(Constants.WALLET_FILENAME_PROTOBUF);
        loadWallet();

        afterLoadWallet();

        Fonts.initFonts(this.getAssets());
    }

    private void createTxCache() {
        txCachePath = new File(this.getCacheDir(), Constants.TX_CACHE_NAME);
        if (!txCachePath.exists()) {
            if (!txCachePath.mkdirs()) {
                txCachePath = null;
                log.error("Error creating transaction cache folder");
                return;
            }
        }

        // Make cache dirs for all coins
        for (CoinType type : Constants.SUPPORTED_COINS) {
            File coinCachePath = new File(txCachePath, type.getId());
            if (!coinCachePath.exists()) {
                if (!coinCachePath.mkdirs()) {
                    txCachePath = null;
                    log.error("Error creating transaction cache folder");
                    return;
                }
            }
        }
    }

    public boolean isConnected() {
        NetworkInfo activeInfo = connManager.getActiveNetworkInfo();
        return activeInfo != null && activeInfo.isConnected();
    }

    public ShapeShift getShapeShift() {
        if (shapeShift == null) {
            shapeShift = new ShapeShift(NetworkUtils.getHttpClient(getApplicationContext()));
        }
        return shapeShift;
    }

    public File getTxCachePath() {
        return txCachePath;
    }

    /**
     * Some devices have software bugs that causes the EC crypto to malfunction.
     */
    private void performComplianceTests() {
        if (!config.isDeviceCompatible()) {
            if (!HardwareSoftwareCompliance.isEllipticCurveCryptographyCompliant()) {
                config.setDeviceCompatible(false);
                ACRA.getErrorReporter().handleSilentException(
                        new Exception("Device failed EllipticCurveCryptographyCompliant test"));
            } else {
                config.setDeviceCompatible(true);
            }
        }
    }

    private void afterLoadWallet() {
        setupFeeProvider();
//        wallet.autosaveToFile(walletFile, 1, TimeUnit.SECONDS, new WalletAutosaveEventListener());
//
        // clean up spam
//        wallet.cleanup();
//
//        ensureKey();
//
//        migrateBackup();
    }

    private void setupFeeProvider() {
        CoinType.setFeeProvider(new CoinType.FeeProvider() {
            @Override
            public Value getFeeValue(CoinType type) {
                return config.getFeeValue(type);
            }
        });
    }

    private void initLogging() {
//        final File logDir = getDir("log", Constants.TEST ? Context.MODE_WORLD_READABLE : MODE_PRIVATE);
//        final File logFile = new File(logDir, "wallet.log");
//
//        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
//
//        final PatternLayoutEncoder filePattern = new PatternLayoutEncoder();
//        filePattern.setContext(context);
//        filePattern.setPattern("%d{HH:mm:ss.SSS} [%thread] %logger{0} - %msg%n");
//        filePattern.start();
//
//        final RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<ILoggingEvent>();
//        fileAppender.setContext(context);
//        fileAppender.setFile(logFile.getAbsolutePath());
//
//        final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<ILoggingEvent>();
//        rollingPolicy.setContext(context);
//        rollingPolicy.setParent(fileAppender);
//        rollingPolicy.setFileNamePattern(logDir.getAbsolutePath() + "/wallet.%d.log.gz");
//        rollingPolicy.setMaxHistory(7);
//        rollingPolicy.start();
//
//        fileAppender.setEncoder(filePattern);
//        fileAppender.setRollingPolicy(rollingPolicy);
//        fileAppender.start();
//
//        final PatternLayoutEncoder logcatTagPattern = new PatternLayoutEncoder();
//        logcatTagPattern.setContext(context);
//        logcatTagPattern.setPattern("%logger{0}");
//        logcatTagPattern.start();
//
//        final PatternLayoutEncoder logcatPattern = new PatternLayoutEncoder();
//        logcatPattern.setContext(context);
//        logcatPattern.setPattern("[%thread] %msg%n");
//        logcatPattern.start();
//
//        final LogcatAppender logcatAppender = new LogcatAppender();
//        logcatAppender.setContext(context);
//        logcatAppender.setTagEncoder(logcatTagPattern);
//        logcatAppender.setEncoder(logcatPattern);
//        logcatAppender.start();
//
//        final ch.qos.logback.classic.Logger log = context.getLogger(Logger.ROOT_LOGGER_NAME);
//        log.addAppender(fileAppender);
//        log.addAppender(logcatAppender);
//        log.setLevel(Level.INFO);
    }


    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the current wallet.
     */
    @Nullable
    public Wallet getWallet() {
        return wallet;
    }

    @Nullable
    public WalletAccount getAccount(@Nullable String accountId) {
        if (wallet != null) {
            return wallet.getAccount(accountId);
        } else {
            return null;
        }
    }

    public List<WalletAccount> getAccounts(CoinType type) {
        if (wallet != null) {
            return wallet.getAccounts(type);
        } else {
            return ImmutableList.of();
        }
    }


    public List<WalletAccount> getAccounts(List<CoinType> types) {
        if (wallet != null) {
            return wallet.getAccounts(types);
        } else {
            return ImmutableList.of();
        }
    }

    public List<WalletAccount> getAccounts(AbstractAddress address) {
        if (wallet != null) {
            return wallet.getAccounts(address);
        } else {
            return ImmutableList.of();
        }
    }

    public List<WalletAccount> getAllAccounts() {
        if (wallet != null) {
            return wallet.getAllAccounts();
        } else {
            return ImmutableList.of();
        }
    }

    /**
     * Check if account exists
     */
    public boolean isAccountExists(String accountId) {
        if (wallet != null) {
            return wallet.isAccountExists(accountId);
        } else {
            return false;
        }
    }

    /**
     * Check if accounts exists for the spesific coin type
     */
    public boolean isAccountExists(CoinType type) {
        return wallet != null && wallet.isAccountExists(type);
    }

    public void setEmptyWallet() {
        setWallet(null);
    }

    public void setWallet(@Nullable Wallet wallet) {
        // Disable auto-save of the previous wallet if exists, so it doesn't override the new one
        if (this.wallet != null) {
            this.wallet.shutdownAutosaveAndWait();
        }

        this.wallet = wallet;
        if (this.wallet != null) {
            this.wallet.autosaveToFile(walletFile, Constants.WALLET_WRITE_DELAY,
                    Constants.WALLET_WRITE_DELAY_UNIT, null);
        }
    }

    private void loadWallet() {
        if (walletFile.exists()) {
            final long start = System.currentTimeMillis();

            FileInputStream walletStream = null;

            try {
                walletStream = new FileInputStream(walletFile);

                setWallet(WalletProtobufSerializer.readWallet(walletStream));

                log.info("wallet loaded from: '" + walletFile + "', took " + (System.currentTimeMillis() - start) + "ms");
            } catch (final FileNotFoundException e) {
                ACRA.getErrorReporter().handleException(e);
                Toast.makeText(WalletApplication.this, R.string.error_could_not_read_wallet, Toast.LENGTH_LONG).show();
            } catch (final UnreadableWalletException e) {
                Toast.makeText(WalletApplication.this, R.string.error_could_not_read_wallet, Toast.LENGTH_LONG).show();
                ACRA.getErrorReporter().handleException(e);
            } finally {
                if (walletStream != null) {
                    try {
                        walletStream.close();
                    } catch (final IOException x) { /* ignore */ }
                }
            }
        }
    }


    public void saveWalletNow() {
        if (wallet != null) {
            wallet.saveNow();
        }
    }

    public void saveWalletLater() {
        if (wallet != null) {
            wallet.saveLater();
        }
    }

    public void startBlockchainService(CoinService.ServiceMode mode) {
        switch (mode) {
            case CANCEL_COINS_RECEIVED:
                startService(coinServiceCancelCoinsReceivedIntent);
                break;
            case NORMAL:
            default:
                startService(coinServiceIntent);
                break;
        }
    }

    public void stopBlockchainService() {
        stopService(coinServiceIntent);
    }


    public static PackageInfo packageInfoFromContext(final Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (final PackageManager.NameNotFoundException x) {
            throw new RuntimeException(x);
        }
    }

    public PackageInfo packageInfo() {
        return packageInfo;
    }

    public String getVersionString() {
        return versionString;
    }

    public void touchLastResume() {
        lastStop = -1;
    }

    public void touchLastStop() {
        lastStop = SystemClock.elapsedRealtime();
    }

    public long getLastStop() {
        return lastStop;
    }

    public void maybeConnectAccount(WalletAccount account) {
        if (!account.isConnected()) {
            coinServiceConnectIntent.putExtra(Constants.ARG_ACCOUNT_ID, account.getId());
            startService(coinServiceConnectIntent);
        }
    }
}