<div dir="rtl">

# TeleStream TV – אפליקציה לטלוויזיה

אפליקציית אנדרואיד TV נפרדת שמתחברת לשרת TeleStream שבמחשב (http://192.168.7.7:8765).
היא לא משנה שום דבר בשרת: היא משתמשת באותן כתובות API שתוסף הקודי משתמש בהן.

## מה יש בה
- מסך בית: המשך צפייה, הרשימה שלי, סרטים וסדרות חדשים, הדירוג הגבוה.
- סרטים וסדרות לפי ז'אנר ודירוג, חיפוש, וקבצים שלא זוהו.
- דף סרט ודף סדרה עם עונות ופרקים, כולל המשך מהנקודה שעצרת.
- נגן ExoPlayer: דילוג על פתיח/תקציר, הצעה לפרק הבא, דיווח התקדמות לשרת.
- הטלוויזיה נרשמת בשרת בשם שבחרת, כך שאפשר לשלוח אליה סרטים מהספרייה בדפדפן (כמו בקודי) כשהאפליקציה פתוחה.

## התקנה
באפליקציה Downloader בטלוויזיה מקלידים את הכתובת:

<div dir="ltr">

```
https://raw.githubusercontent.com/TheYGN/telestream-tv/main/release/TeleStreamTV.apk
```

</div>

## עדכונים
בכל פתיחה האפליקציה קוראת את `release/latest.json` מהמאגר הזה, ואם יש גרסה חדשה היא מציעה להתקין אותה (אפשר גם ידנית: הגדרות ← בדוק עדכונים).
כדי לפרסם גרסה: מעלים את `versionCode` ו-`versionName` ב-`app/build.gradle.kts`, בונים, מחליפים את `release/TeleStreamTV.apk` ומעדכנים את `version` ו-`notes` ב-`release/latest.json`.

מפתח החתימה (`app/telestream.keystore`) לא נמצא במאגר. הוא שמור בתיקיית הפרויקט, ובלעדיו גרסה חדשה לא תותקן מעל הישנה.

## בנייה
צריך JDK 17+ ו-Android SDK (platform 35):

```
gradlew assembleRelease
```

ה-APK יוצא ב-`app/build/outputs/apk/release/app-release.apk`.

</div>
