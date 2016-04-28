Coinomi Wallet
===============

Our goal is to support every cryptocurrency with an active development team. Store all the best cryptocurrency through a single app, without sacrificing security. Private keys are stored on your own device. Instead of having one backup file for every coin, you get a master key that can be memorized or stored on a piece of paper. Restore all wallets from a single recovery phrase.

TODOs:

* Create instrumentation tests to test a signed APK


## Building the app

Install [Android Studio](https://developer.android.com/sdk/installing/studio.html). Once it is
running, import coinomi-android by navigating to where you cloned or downloaded it and selecting
settings.gradle. When it is finished importing, click on the SDK Manager ![SDK Manager](https://developer.android.com/images/tools/sdk-manager-studio.png). You will need to install SDK version 21.

<br/>
Make sure that you have JDK 7 installed before building. You can get it [Here](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html). Once you have that installed, navigate to File > Project Structure > SDK Location and change the path of your current JDK to the path of the new JDK. **The project will not build with JDK 8**. 

<br/>
Once it is finished installing, you will need to enable developer options on your phone. To do so,
go into settings, About Phone, locate your Build Number, and tap it 7 times, or until it says
"You are now a Developer". Then, go back to the main Settings screen and scroll once again to the
bottom. Select Developer options and enable USB Debugging.

<br/>
Then plug your phone into your computer and hit the large green play button at the top of
Android Studio. It will load for a moment before prompting you to select which device to install
it on. Select your device from the list, and hit continue.

**NOTE**
If you are attempting to build on a Lollipop emulator, please ensure that you are using *Android 5.*.* armeabi-v7*. It will not build on an x86/x86_64 emulator.

## Contributions

Your contributions are very welcome, be it translations, extra features or new coins support. Just
fork this repo and create a pull request with your changes.

For new coins support read this [document](https://coinomi.com/AddingSupportForANewCurrency/).
Generally you need:

* Electrum-server support
* Coinomi core support
* A beautiful vector icon
* Entry to the [BIP44 registry](https://github.com/satoshilabs/docs/blob/master/slips/slip-0044.rst) that is maintained by Satoshi labs


## Releasing the app

To release the app follow the steps.

1) Change the following:

* in strings.xml app_name string to "Coinomi" and app_package to com.coinomi.wallet
* in build.gradle the package from "com.coinomi.wallet.dev" to "com.coinomi.wallet"
* in AndroidManifest.xml the android:icon to "ic_launcher" and all "com.coinomi.wallet.dev.*"  to "com.coinomi.wallet.*"
* remove all ic_launcher_dev icons with `rm wallet/src/main/res/drawable*/ic_launcher_dev.png`
* setup ACRA and ShapeShift

2) Then in the Android Studio go to:

* Build -> Clean Project and without waiting...
* Build -> Generate Signed APK and generate a signed APK. ... and now you can grab yourself a nice cup of tea.

3) Test this APK (TODO: with instrumentation tests).

For now test it manually by installing it `adb install -r wallet/wallet-release.apk`

> This one is in the TODOs and must be automated
> because it will be here that you take a break ;)

4) Upload to Play Store and check for any errors and if all OK publish in beta first.

5) Create a GIT release commit:

* Create a commit with the log entry similar to the description in the Play Store
* Create a tag with the version of the released APK with `git tag vX.Y.Z <commit-hash>`


## Version history

New in version 1.6.0-1.6.2
- Overview screen
- Optimized memory usage
- Sweep paper wallets
- “Pull to refresh” functionality
- Synchronized progress bar
- Option to rename accounts
- Option to modify fees in the Settings area
- Transactions now include Date stamps
- Improved handling of addresses of coins with conflicting address versions
- Support for landscape view
- User interface and usability tweaks
- New coins: Auroracoin, Gulden, Potcoin, Bata, Verge, Asiacoin, e-Gulden, OKCash, Clubcoin, Richcoin

New in version 1.5.22
- Improved UI for setting a BIP39 passphrase
- New coins: Clams, GCRcoin, Dogecoindark

New in version 1.5.21
- Fixed memory leak when restoring a wallet
- Fixed crash when adding a coin account with the wrong password in the exchange screen
- Fixed crash on empty password in sign/verify message screen
- Added coin: ParkByte

New in version 1.5.20
- Fixed crashes on some devices
- Added coins: Novacoin, Canada eCoin and ShadowCash
- Experimental req-addressrequest support

New in version 1.5.19
- Possibility to sign and verify messages
- Account details screen to view the public key
- Transaction messages in Vpncoin
- Russian translation
- Some UI optimizations
- Bug fixes
- Increase the default size of the recovery phrase

New in version 1.5.18
- Can set an amount in receive screen
- Added Chinese and Japanese translations
- Updated the recovery phrase creation procedure
- Added coins: Namecoin, Vpncoin, Vertcoin, Jumbucks, Neoscoin

New in version 1.5.17
- Added Greek translation
- Fixed Peercoin and Digitalcoin rare invalid transaction creation
- New block explorer for Blackcoin
- Added Neoscoin
- Small UI fixes

New in version 1.5.16
- Changed the way balance is calculated and added the possibility to spend unconfirmed transactions
- Small optimizations when handling the QR code and transactions

New in version 1.5.15
- Support payment URIs requests from browsers and other apps
- Changed NuBits and NuShares URIs to "nu"
- Added Monacoin and Digibyte
- Added ability to spend own unconfirmed change funds
- Usability tweaks and bug fixes

New in version 1.5.14
- Added exchange history log
- Can send alt-coins from bitcoin wallet and vise-versa
- Make exchange rates appear faster in the UI
- Fix rare crash when viewing the exchange status of Peercoin or NuBits

New in version 1.5.13
- Integrated exchange (beta)
- Rebranding of Darkcoin to Dash
- UI tweaks

New in version 1.5.12
- Click on any addresses to edit the label or copy it
- Dedicated copy address button in the receive screen
- New user interface for Android Lollipop devices
- Improved icons
- Bug and crash fixes

New in version 1.5.11
- New settings screen
- Ability to view recovery phrase in settings
- Manual receiving address management (enable in settings)
- Testnet for Bitcoin, Litecoin and Dogecoin
- Usability tweaks
- Bug and crash fixes

New in version 1.5.10
- Balance screen shows the amount with 4 decimal places (click to view the full amount)
- Basic address book

New in version 1.5.9
- Ask confirmation before creating a new address

New in version 1.5.8
- Added the ability to create new addresses and view the previous ones
- When creating a new wallet, it is now possible to select the passphrase and copy it
- Usability fix when setting a password
- Better bug/crash reporting

New in version 1.5.6 and 1.5.7
- Cannacoin, Feathercoin, Digitalcoin and Rubycoin support

New in version 1.5.5
- Revert BTC fees to previous values, as transactions are not included fast enough in the blocks
- Added local currency values in the sign transaction screen

New in version 1.5.4
- Improved transaction broadcasting and send validation logic
- Fix issue where the balance was incorrectly calculated in some cases
- Updated Darkcoin p2sh address versions

New in version 1.5.3
- Changed Blackcoin code from BC to BLK

New in version 1.5.2
- Added exchange rates for various national currencies
- Blackcoin support
- Implemented multiple coin selection on creation or restoring wallet
- Improved automatic connectivity management with faster reconnects and detection of network change. Added feature to disconnect when the app is in background and idle for 30 minutes.
- Fix issue when restoring a wallet the previous wallet could reappear
- General usability and bug fixes

New in version 1.5.1
- Added beta support for Blackcoin

New in version 1.5.0
- The supported coins are now Bitcoin, Litecoin, Dogecoin, Peercoin, Darkcoin, Reddcoin, NuBits and NuShares. All will work without changing your seed!
- Cleaner interface and bug fixes.

New in version 1.4.1
- Re-design of the create wallet tutorial and set more sane defaults
- Fix crash when refreshing a non connected wallet
- Small UI tweaks

New in version 1.4.0
- New balance screen design
- Optimization for old 2.3.x Androids with very small screens
- Fix crash when emptying an already empty wallet
- When refreshing, do it only for the current coin
- General UI and usability tweaks
- Optimize layouts for small screens
- Fixed crash in older Androids due to a missing API
- Fixed camera crash in low resolution screens
- Able to install app in external storage
- ... and many fixes and optimizations
