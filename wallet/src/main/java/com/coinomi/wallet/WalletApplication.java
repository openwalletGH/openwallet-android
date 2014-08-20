package com.coinomi.wallet;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.WalletProtobufSerializer;
import com.coinomi.wallet.service.CoinService;
import com.coinomi.wallet.service.CoinServiceImpl;
import com.coinomi.wallet.util.CrashReporter;
import com.coinomi.wallet.util.Io;
import com.coinomi.wallet.util.LinuxSecureRandom;
import com.google.bitcoin.crypto.MnemonicException;
import com.google.bitcoin.store.UnreadableWalletException;
import com.google.bitcoin.utils.Threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.annotation.Nonnull;

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
    private Configuration config;
    private ActivityManager activityManager;

    private Intent coinServiceIntent;
    private Intent coinServiceCancelCoinsReceivedIntent;
    private Intent coinServiceResetBlockchainIntent;

    private File walletFile;
    private Wallet wallet;
    private PackageInfo packageInfo;

    private static final Logger log = LoggerFactory.getLogger(WalletApplication.class);

    @Override
    public void onCreate() {
        new LinuxSecureRandom(); // init proper random number generator

        initLogging();

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().permitDiskReads().permitDiskWrites().penaltyLog().build());

        log.info("configuration: " + (Constants.TEST ? "test" : "prod"));

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
        coinServiceResetBlockchainIntent = new Intent(CoinService.ACTION_RESET_BLOCKCHAIN,
                null, this, CoinServiceImpl.class);


        walletFile = getFileStreamPath(Constants.WALLET_FILENAME_PROTOBUF);

        loadWalletFromProtobuf();

        config.updateLastVersionCode(packageInfo.versionCode);

        afterLoadWallet();

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

    public Wallet getWallet()
    {
        return wallet;
    }

    private void loadWalletFromProtobuf() {
        if (walletFile.exists())
        {
            final long start = System.currentTimeMillis();

            FileInputStream walletStream = null;

            try
            {
                walletStream = new FileInputStream(walletFile);

                wallet = new WalletProtobufSerializer().readWallet(walletStream);

//                if (!wallet.getParams().equals(Constants.NETWORK_PARAMETERS))
//                    throw new UnreadableWalletException("bad wallet network parameters: " + wallet.getParams().getId());

                log.info("wallet loaded from: '" + walletFile + "', took " + (System.currentTimeMillis() - start) + "ms");
            }
            catch (final FileNotFoundException x)
            {
                log.error("problem loading wallet", x);

                Toast.makeText(WalletApplication.this, x.getClass().getName(), Toast.LENGTH_LONG).show();

                //TODO show wallet restoration activity
//                wallet = restoreWalletFromBackup();
            }
            catch (final UnreadableWalletException x)
            {
                log.error("problem loading wallet", x);

                Toast.makeText(WalletApplication.this, x.getClass().getName(), Toast.LENGTH_LONG).show();

                //TODO show wallet restoration activity
//                wallet = restoreWalletFromBackup();
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
        else
        {
            // TODO handle exceptions
            try {
                log.info("Creating a new wallet from mnemonic");
                wallet = new Wallet(Constants.TEST_MNEMONIC);
                log.info("Adding coin pockets for some coins");
                wallet.createCoinPockets(Constants.COINS_TEST);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (MnemonicException e) {
                e.printStackTrace();
            }

            log.info("new wallet created");
        }

        // TODO check if needed
        // this check is needed so encrypted wallets won't get their private keys removed accidently
//        for (final ECKey key : wallet.getKeys())
//            if (key.getPrivKeyBytes() == null)
//                throw new Error("found read-only key, but wallet is likely an encrypted wallet from the future");
    }

    public void saveWallet()
    {
        try
        {
            protobufSerializeWallet(wallet);
        }
        catch (final IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    private void protobufSerializeWallet(@Nonnull final Wallet wallet) throws IOException
    {
        final long start = System.currentTimeMillis();

        wallet.saveToFile(walletFile);

        // make wallets world accessible in test mode
        if (Constants.TEST)
            Io.chmod(walletFile, 0777);

        log.debug("wallet saved to: '" + walletFile + "', took " + (System.currentTimeMillis() - start) + "ms");
    }

    public void startBlockchainService(final boolean cancelCoinsReceived)
    {
        if (cancelCoinsReceived)
            startService(coinServiceCancelCoinsReceivedIntent);
        else
            startService(coinServiceIntent);
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

    public WalletPocket getWalletPocket(CoinType type) {
        return wallet.getPocket(type);
    }
}