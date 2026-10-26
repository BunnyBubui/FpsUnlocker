# FPS Unlocker Tool (Android 11+, Shizuku / SAF)

## Build APK
1. เปิดโฟลเดอร์นี้ใน Android Studio (Ladybug ขึ้นไป) แล้วรอ Gradle sync
2. Build > Build App Bundle(s) / APK(s) > Build APK(s)
3. ไฟล์ออกที่ `app/build/outputs/apk/debug/app-debug.apk`

หรือใช้ command line: `./gradlew assembleDebug`

## วิธีใช้
1. ติดตั้งและเปิด Shizuku (Wireless debugging บน Android 11+) แล้วกด "อนุญาต Shizuku" ในแอป
2. กด "ค้นหาเกม NetEase ในเครื่อง" แล้วแตะแพ็กเกจของเกม (หรือพิมพ์ package name เอง)
3. กด "Scan หาไฟล์/คีย์ FPS" แล้วแตะรายการที่ดูถูกต้อง (หรือกรอก path และ key เอง)
4. เลือก FPS แล้วกด Apply Settings (สร้าง `<ไฟล์>.bak` อัตโนมัติครั้งแรก)
5. ถ้าเกมมีปัญหา กด Restore Backup

## ข้อจำกัด
- แอปไม่รู้ล่วงหน้าว่าเกมเก็บค่า FPS ไว้ที่ไหน ต้องหาด้วย Scan
- ถ้าไฟล์ config เป็น binary/เข้ารหัส หรือเกมบังคับค่าจากเซิร์ฟเวอร์ แอปนี้แก้ไม่ได้
- SAF ใช้กับ Android/data ได้เฉพาะ Android 11–12
- ปิดเกมก่อน Apply เสมอ ไม่งั้นเกมอาจเขียนทับค่าตอนปิด
