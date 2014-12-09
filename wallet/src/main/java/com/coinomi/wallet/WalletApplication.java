package com.coinomi.wallet;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.StrictMode;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.WalletProtobufSerializer;
import com.coinomi.wallet.service.CoinService;
import com.coinomi.wallet.service.CoinServiceImpl;
import com.coinomi.wallet.util.CrashReporter;
import com.coinomi.wallet.util.Fonts;
import com.coinomi.wallet.util.LinuxSecureRandom;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.utils.Threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

import javax.annotation.Nullable;

//import ch.qos.logback.classic.Level;
//import ch.qos.logback.classic.LoggerContext;
//import ch.qos.logback.classic.android.LogcatAppender;
//import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
//import ch.qos.logback.classic.spi.ILoggingEvent;
//import ch.qos.logback.core.rolling.RollingFileAppender;
//import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

/**
 * @author Giannis Dzegoutanis
 * @author Andreas Schildbach
 */
public class WalletApplication extends Application {
    private static HashMap<String, Typeface> typefaces;
    private Configuration config;
    private ActivityManager activityManager;

    private Intent coinServiceIntent;
    private Intent coinServiceCancelCoinsReceivedIntent;
    private Intent coinServiceResetWalletIntent;

    private File walletFile;
    @Nullable private Wallet wallet;
    private PackageInfo packageInfo;

    private long lastStop;

    private static final Logger log = LoggerFactory.getLogger(WalletApplication.class);

    @Override
    public void onCreate() {
        new LinuxSecureRandom(); // init proper random number generator

        initLogging();

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().permitDiskReads().permitDiskWrites().penaltyLog().build());

        super.onCreate();

        packageInfo = packageInfoFromContext(this);

        CrashReporter.init(getCacheDir());

        // TODO does it work?
        Threading.uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(final Thread thread, final Throwable throwable)
            {
                log.info("coinomi uncaught exception", throwable);
                CrashReporter.saveBackgroundTrace(throwable, packageInfo);
            }
        };

        config = new Configuration(PreferenceManager.getDefaultSharedPreferences(this));
        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        coinServiceIntent = new Intent(this, CoinServiceImpl.class);
        coinServiceCancelCoinsReceivedIntent = new Intent(CoinService.ACTION_CANCEL_COINS_RECEIVED,
                null, this, CoinServiceImpl.class);
        coinServiceResetWalletIntent = new Intent(CoinService.ACTION_RESET_WALLET,
                null, this, CoinServiceImpl.class);

        // Set MnemonicCode.INSTANCE if needed
        if (MnemonicCode.INSTANCE == null) {
            try {
                MnemonicCode.INSTANCE = new MnemonicCode();
            } catch (Exception e) {
                log.error("Could not set MnemonicCode.INSTANCE", e);
            }
        }

        walletFile = getFileStreamPath(Constants.WALLET_FILENAME_PROTOBUF);

        loadWallet();

        config.updateLastVersionCode(packageInfo.versionCode);

        afterLoadWallet();

        Fonts.initFonts(this.getAssets());
    }

    private void afterLoadWallet()
    {
//        wallet.autosaveToFile(walletFile, 1, TimeUnit.SECONDS, new WalletAutosaveEventListener());
//
        // clean up spam
//        wallet.cleanup();
//
//        ensureKey();
//
//        migrateBackup();
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


    public Configuration getConfiguration()
    {
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
    public WalletPocket getWalletPocket(CoinType type) {
        if (wallet != null && wallet.isPocketExists(type)) {
            return wallet.getPocket(type);
        }
        else { return null; }
    }

    /**
     * Check if pocket exists
     */
    public boolean isPocketExists(CoinType type) {
        if (wallet != null) {
            return wallet.isPocketExists(type);
        } else {
            return false;
        }
    }

    public void setWallet(Wallet wallet) {
        // Disable auto-save of the previous wallet if exists, so it doesn't override the new one
        if (this.wallet != null) {
            this.wallet.shutdownAutosaveAndWait();
        }

        this.wallet = wallet;
        this.wallet.autosaveToFile(walletFile,
                Constants.WALLET_WRITE_DELAY, Constants.WALLET_WRITE_DELAY_UNIT, null);
    }

    private void loadWallet() {
        if (walletFile.exists())
        {
            final long start = System.currentTimeMillis();

            FileInputStream walletStream = null;

            try
            {
                walletStream = new FileInputStream(walletFile);

                setWallet(WalletProtobufSerializer.readWallet(walletStream));

                log.info("wallet loaded from: '" + walletFile + "', took " + (System.currentTimeMillis() - start) + "ms");
            }
            catch (final FileNotFoundException x)
            {
                log.error("problem loading wallet", x);
                Toast.makeText(WalletApplication.this, R.string.error_could_not_read_wallet, Toast.LENGTH_LONG).show();
            }
            catch (final UnreadableWalletException x)
            {
                log.error("problem loading wallet", x);

                Toast.makeText(WalletApplication.this, R.string.error_could_not_read_wallet, Toast.LENGTH_LONG).show();
            }
            finally
            {
                if (walletStream != null)
                {
                    try
                    {
                        walletStream.close();
                    }
                    catch (final IOException x)
                    {
                        // swallow
                    }
                }
            }

//            if (!wallet.isConsistent())
//            {
//                Toast.makeText(this, "inconsistent wallet: " + walletFile, Toast.LENGTH_LONG).show();
//
//                wallet = restoreWalletFromBackup();
//            }
//
//            if (!wallet.getParams().equals(Constants.NETWORK_PARAMETERS))
//                throw new Error("bad wallet network parameters: " + wallet.getParams().getId());
        }
        // ELSE create a wallet later
//        else
//        {
//            // TODO handle exceptions
//            try {
//                log.info("Creating a new wallet from mnemonic");
//                wallet = new Wallet(Constants.TEST_MNEMONIC);
//                log.info("Adding coin pockets for some coins");
//                wallet.createCoinPockets(Constants.COINS_TEST);
//            } catch (MnemonicException e) {
//                e.printStackTrace();
//            }
//
//            log.info("new wallet created");
//        }

        // TODO check if needed
        // this check is needed so encrypted wallets won't get their private keys removed accidently
//        for (final ECKey key : wallet.getKeys())
//            if (key.getPrivKeyBytes() == null)
//                throw new Error("found read-only key, but wallet is likely an encrypted wallet from the future");
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
            case RESET_WALLET:
                startService(coinServiceResetWalletIntent);
                break;
            case NORMAL:
            default:
                startService(coinServiceIntent);
                break;
        }
    }

    public void stopBlockchainService()
    {
        stopService(coinServiceIntent);
    }


    public static PackageInfo packageInfoFromContext(final Context context)
    {
        try
        {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        }
        catch (final PackageManager.NameNotFoundException x)
        {
            throw new RuntimeException(x);
        }
    }

    public PackageInfo packageInfo()
    {
        return packageInfo;
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
}