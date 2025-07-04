GrabIt Shopping List
A simple, stylish, and efficient Android app to manage your shopping lists effortlessly. GrabIt helps you add, track, and organize items with an intuitive interface, perfect for busy individuals, families, or anyone looking to save time.
Features

Add and edit shopping items with name, quantity, and price.
Check off items as you shop with a single tap.
Delete items easily to keep your list clean.
Local storage for privacy and quick access.
Attractive design with comfortable borders and themes.
Integrated AdMob for personalized ads (optional revenue support).

Installation
Prerequisites

Android device running Android 5.0 (API 21) or higher.
Android Studio (for developers) or Google Play Store (for users).

Steps to Download and Use

Via Google Play Store (Recommended for Users):

Open the Google Play Store on your Android device.
Search for "GrabIt Shopping List".
Tap "Install" and wait for the app to download.
Open the app and start creating your shopping list!


Via GitHub (For Developers or Manual Installation):

Clone or download this repository:git clone [https://github.com/yourusername/GrabIt.git](https://github.com/varundarling/GrabIt.git)

or download the ZIP file from the repository page and extract it.
Open the project in Android Studio.
Sync the project with Gradle (File > Sync Project with Gradle Files).
Build the app (Build > Build Bundle(s) / APK(s) > Build APK).
Locate the generated APK in app/build/outputs/apk/debug/.
Transfer the APK to your Android device and install it (enable "Install from Unknown Sources" if needed).
Open the app and grant necessary permissions (e.g., storage for AdMob).



Configuration (For Developers)

Add your AdMob App ID and Ad Unit ID in AndroidManifest.xml and ad initialization code.
Ensure the com.google.android.gms.permission.AD_ID permission is included for Android 13+ support.
Update res/values/strings.xml with your app details if needed.

Usage

Tap the "+" button to add a new item (enter name, quantity, price).
Check the box to mark items as purchased.
Swipe left or tap "Delete" to remove items.
View your total spending at the bottom of the list.
Enjoy a seamless shopping experience with a modern UI!

Contributing
Contributions are welcome! Please fork the repository, make changes, and submit a pull request. Follow the code style in the project and test your changes thoroughly.
License
This project is licensed under the MIT License - see the LICENSE file for details.
Privacy
GrabIt uses AdMob for ads, which may collect device data (e.g., Advertising ID). No personal data is stored by the app. See our Privacy Policy for more information.
