Coinomi Wallet
===============

Welcome to the Coinomi Wallet app for Android!

TODOs:

* Create instrumentation tests to test a signed APK


## Building the app

Install [Android Studio](https://developer.android.com/sdk/installing/studio.html). Once it is
running, import coinomi-android by navigating to where you cloned or downloaded it and selecting
settings.gradle. When it is finished importing, click on the SDK Manager ![SDK Manager](https://developer.android.com/images/tools/sdk-manager-studio.png).
<br/>

You will need to install SDK version 19.

<br/>
Once it is finished installing, you will need to enable developer options on your phone. To do so,
go into settings, About Phone, locate your Build Number, and tap it 7 times, or until it says
"You are now a Developer". Then, go back to the main Settings screen and scroll once again to the
bottom. Select Developer options and enable USB Debugging.

<br/>
Then plug in your phone into your computer and hit the large green play button at the top of
Android Studio. It will load for a moment before prompting you to select which device to install
it on. Select your device from the list, and hit continue.


## Releasing the app

To release the app follow the steps.

1) Change the following:

* in strings.xml app_name string from "Coinomi (dev)" to "Coinomi"
* in build.gradle the package from "com.coinomi.wallet.dev" to "com.coinomi.wallet"
* in AndroidManifest.xml the android:icon from "ic_launcher_dev" to "ic_launcher" and com.coinomi.wallet.dev.exchange_rates to com.coinomi.wallet.exchange_rates
* remove all ic_launcher_dev icons with `rm wallet/src/main/res/drawable*/ic_launcher_dev.png`

2) Then in the Android Studio go to:

* Build -> Clean Project and without waiting...
* Build -> Generate Signed APK and generate a signed APK. ... and now you can grab yourself a nice cup of tea.

3) Test this APK (TODO: with instrumentation tests).

For now test it manually by installing it `adb install -r wallet/wallet-release.apk`

> This one is in the TODOs and must be automated
> because it will be here that you take a break ;)

4) Upload to Play Store and check for any errors and if all OK publish in beta first.

5) Create a GIT release commit:

* Checkout a throwaway branch
* Create a commit with the log entry similar to the description in the Play Store
* Checkout the release branch and run `git merge <throwaway-branch-name>`
* Create a tag with the version of the released APK


## Version history

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