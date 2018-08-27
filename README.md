OpenWallet for Android
===============

The goal of this project is to build the best free, libre, and open source light wallet for multiple cryptocurrencies (Bitcoin, Ethereum, Ripple, etc) on Android. Security and usability are, of course, the priorities. For this reason, your private keys never leave the device. Luckily, this wallet is compliant with BIP32, BIP39, and BIP44. A single 24-word mnemonic phrase may be used to recover each and every one of your cryptocurrency wallets.

## Contributions

Contributions aren't just welcome, they're financially encouraged! (Well, they will be soon. Hang tight!) Eventually, we hope to set up a cryptocurrency-based (likely BTC or ETH) rewards system for contributions, be they new features, new translations, or new coins. As always, all you've gotta do is fork and pull!

By the way, if you'd like to add new coins, check out this document provided by Coinomi: [document](https://gist.github.com/CosmoJG/5c75b81b4fdf36398760189908692120).

You should find that a lot of Coinomi's documentation applies to OpenWallet as well. This is because OpenWallet was forked from Coinomi before it ditched the open source model in favor of a more proprietary, "source-available" model. **OpenWallet is, and forever will be, free, libre, and open source!**

Anyway, back to the coins. Generally you'll need:

* Electrum-server support
* OpenWallet core support
* A beautiful vector icon
* BIP32, BIP39, and BIP44 compliance

## How to Go About Building an Independent Fork of This App

First off, ensure that your client device is running Android Lollipop or later. Second, ensure that your client device is running an ARM processor as this project is currently incompatible with x86_64/amd64.

Start up Android Studio and import this repository (openwallet-android) in its entirety (click on settings.gradle). When that's done, install Version 21 of the SDK. Note that this project must be built with JDK 7 as it is currently incompatible with JDK 8.

Once built, enable developer options on your Android smartphone as well as USB debugging. Plug your smartphone into your computer, and install your shiny new app through Android Studio.

## How to Go About Releasing an Independent Fork of This App

1) Change the following:

* in strings.xml app_name string to "OpenWallet" and app_package to com.openwallet.wallet
* in build.gradle the package from "com.openwallet.wallet.dev" to "com.openwallet.wallet"
* in AndroidManifest.xml the android:icon to "ic_launcher" and all "com.openwallet.wallet.dev.*"  to "com.openwallet.wallet.*"
* remove all ic_launcher_dev icons with `rm wallet/src/main/res/drawable*/ic_launcher_dev.png`
* setup ACRA and ShapeShift

2) Then perform the following in Android Studio:

* Build -> Clean Project and without waiting...
* Build -> Generate Signed APK and generate a signed APK. ... and now you can grab yourself a nice cup of tea.

3) Test the APK.

Install the APK with `adb install -r wallet/wallet-release.apk`

4) Upload everything to the Play Store, and continue checking for any errors. If all goes well, you're good to go!

5) Create a git release commit:

* Create a commit with a detailed description
* Create a tag with the version of the released APK using `git tag vX.Y.Z <commit-hash>` or something similar


## Version history

All previous history is available at the following repository, which is an unmodified fork of Coinomi prior to its license change:
https://github.com/CosmoJG/open-coinomi-android
