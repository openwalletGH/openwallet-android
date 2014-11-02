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
* in AndroidManifest.xml the android:icon from "ic_launcher_dev" to "ic_launcher"

2) Then in the Android Studio go to:

* Build -> Clean Project and without waiting...
* Build -> Generate Signed APK and generate a signed APK. ... and now you can grab yourself a nice cup of tea.

3) Test this APK (TODO: with instrumentation tests).

For now test it manually by installing it `adb install wallet/wallet-release.apk`

> This one is in the TODOs and must be automated
> because it will be here that you take a break ;)

4) Upload to Play Store and check for any errors and if all OK publish in beta first.

5) Create a GIT release commit:

* Checkout a throwaway branch
* Create a commit with the log entry similar to the description in the Play Store
* Checkout the release branch and run `git merge <throwaway-branch-name>`
* Create a tag with the version of the released APK

Enjoy!



