<div align="center">

<img src="docs/icon.svg" width="96" alt="ไอคอน FPS Unlocker" />

# FPS Unlocker

**ปรับเฟรมเรตของเกมบน Android 11 ขึ้นไป โดยไม่ต้อง root**

แอปจะหาไฟล์ตั้งค่าของเกม แก้ค่า FPS อย่างปลอดภัยพร้อมสำรองไฟล์เดิมให้อัตโนมัติ และมีตัววัด FPS ลอยบนจอไว้ตรวจว่าผลเปลี่ยนจริงหรือไม่

[![Build APK](https://github.com/BunnyBubui/FpsUnlocker/actions/workflows/build-apk.yml/badge.svg)](https://github.com/BunnyBubui/FpsUnlocker/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/BunnyBubui/FpsUnlocker?label=download&sort=date)](https://github.com/BunnyBubui/FpsUnlocker/releases/latest)
![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

[English](README.md) · **ภาษาไทย**

</div>

---

## ดาวน์โหลด

**[⬇ ดาวน์โหลด APK ตัวล่าสุด](https://github.com/BunnyBubui/FpsUnlocker/releases/latest/download/FpsUnlocker.apk)** (ลิงก์นี้ชี้ไปที่ build ใหม่ที่สุดเสมอ) หรือดูทุกเวอร์ชันได้ที่หน้า **[Releases](https://github.com/BunnyBubui/FpsUnlocker/releases)** เวอร์ชันใหม่ติดตั้งทับเวอร์ชันเก่าได้ ไม่ต้องถอนก่อน

แอปมีทั้ง**ภาษาอังกฤษ**และ**ภาษาไทย** และจะแสดงตามภาษาที่ตั้งไว้ในเครื่อง

## แอปทำอะไรได้บ้าง

| | ฟีเจอร์ | รายละเอียด |
|---|---|---|
| 🎮 | **รายชื่อเกม** | แสดงเกมทุกเกมในเครื่อง พร้อมไอคอนจริง ชื่อค่าย ชื่อ package และ engine ที่ใช้ เช่น Unity, Unreal, Cocos, Godot ค้นหาและกรองได้ และบอกว่าเกมไหนมีไฟล์ให้สแกน |
| 🔍 | **สแกนไฟล์ตั้งค่าแบบละเอียด** | ไล่ทีละโฟลเดอร์ย่อยใน `Android/data` ของเกม อ่านไฟล์ขนาดเล็กทุกนามสกุล แล้วแสดงคีย์ที่น่าจะเกี่ยวกับ FPS ถ้าเปิด Shizuku แบบ root แอปจะสแกน `/data/data` ด้วย ซึ่งเป็นที่ที่เกมส่วนใหญ่เก็บค่าตั้งค่าไว้ |
| ✍️ | **แก้ไฟล์อย่างปลอดภัย** | แก้เฉพาะค่าในไฟล์ INI, JSON และ XML ของ Android ก่อนเขียนจะแสดงบรรทัดก่อนและหลังแก้ให้ยืนยัน สำรองไฟล์ต้นฉบับในการแก้ครั้งแรก และอ่านไฟล์กลับมาตรวจหลังเขียน |
| 📈 | **ตัววัด FPS ลอยบนจอ** | ตัวเลขลอยที่ลากย้ายได้ วัดจำนวนเฟรมที่เกมส่งขึ้นจอจริง พร้อมค่าเฉลี่ยและค่าต่ำสุด |
| 🔒 | **บังคับ FPS ระดับระบบ** | ล็อกจอไว้ที่ Hz สูงสุด หรือใช้ Game Mode ของ Android 13 ขึ้นไปล็อก FPS ของเกมให้นิ่ง ใช้ได้แม้เกมจะแก้ไฟล์ไม่ได้ |
| ♻️ | **Backup และตรวจค่าที่ถูกเขียนทับ** | รวม backup ของทุกเกมไว้ในที่เดียว กดคืนค่าได้ทันที และแจ้งเตือนเมื่อเกมเขียนทับค่าที่ตั้งไว้ พร้อมปุ่มตั้งซ้ำ |
| 📖 | **คู่มือในแอป** | สอนจับคู่ Shizuku ทีละขั้น และแสดงวิธีแก้ปัญหาที่ตรงกับยี่ห้อมือถือ (ColorOS, HyperOS/MIUI, One UI, Flyme) |

## สิ่งที่ควรรู้ก่อนใช้

แอปนี้แก้ได้เฉพาะไฟล์ตั้งค่าที่เป็น**ข้อความธรรมดา** และมีข้อจำกัดที่ควรรู้ไว้ดังนี้

- **เกมจำนวนมากเข้ารหัสไฟล์ตั้งค่า หรือเก็บไว้ใน `/data/data`** ถ้าไม่ root ระบบ Android ไม่ให้แอปใดเข้าโฟลเดอร์ `/data/data` เลย ส่วนเกมที่เข้ารหัสไฟล์ แอปจะแจ้งให้รู้ และ**จะไม่พยายามเจาะการเข้ารหัส**
- **ไม่มีแอปไหนดัน FPS ของเกมให้สูงกว่าเพดานที่เกมตั้งเองได้ ด้วยการตั้งค่าระบบอย่างเดียว** Game Mode ของ Android ทำได้แค่ลด FPS หรือทำให้ FPS นิ่ง ส่วนการล็อกจอทำได้แค่กันไม่ให้จอลด Hz ลงเอง
- **ตั้ง FPS สูงกว่า refresh rate ของจอ ภาพจะไม่ลื่นขึ้น** มีแต่ทำให้เครื่องร้อนและแบตหมดเร็วขึ้น แอปจะเตือนเมื่อเลือกค่าที่สูงกว่าที่จอรองรับ
- **เกมออนไลน์อาจห้ามแก้ไฟล์ในข้อตกลงการใช้งาน** และระบบป้องกันการโกงอาจตรวจพบได้ ควรลองกับบัญชีสำรองก่อน ผู้ใช้รับผิดชอบการใช้งานเอง

วิธีตรวจผลที่เชื่อถือได้ที่สุดคือตัววัด FPS ให้ดูตัวเลขก่อนและหลังแก้ทุกครั้ง

## สิ่งที่ต้องมี

- Android **11 ขึ้นไป**
- แอป **[Shizuku](https://shizuku.rikka.app/)** เปิดผ่าน Wireless debugging (ไม่ต้อง root) หรือเปิดด้วย root ถ้าเปิดด้วย root แอปจะสแกน `/data/data` ได้ด้วย
- บน Android 11–12 ใช้ตัวเลือกโฟลเดอร์ของระบบ (SAF) กับเกมทีละเกมแทน Shizuku ได้

## เริ่มใช้งาน

1. ติดตั้ง **Shizuku** และ **FPS Unlocker**
2. เปิด FPS Unlocker แล้วไปที่แท็บ **ตั้งค่า & คู่มือ** ทำตามขั้นตอนทั้ง 6 ขั้น ถ้า Shizuku ขึ้นว่า *"การอนุญาตของ adb นั้นจำกัด"* ให้ดูวิธีแก้ในการ์ดที่ไฮไลต์ตามยี่ห้อมือถือ
3. ไปที่แท็บ **เกม** แตะเกมที่ต้องการ แอปจะพาไปแท็บ **ปรับ FPS** แล้วกด **Scan**
4. แตะคีย์ที่เจอ กด **ดูเนื้อหาไฟล์** เพื่อตรวจไฟล์ เลือก FPS ที่ต้องการ แล้วกด **Apply** ตรวจบรรทัดที่เปลี่ยนแล้วกดยืนยัน
5. เปิด **ตัววัด FPS** แล้วเข้าเกม เพื่อดูว่าตัวเลขเปลี่ยนจริงหรือไม่

## หลักการทำงาน

```text
Compose UI ──► ViewModel ──► FileAccess ──► Shizuku UserService (สิทธิ์ shell/root)
                   │                        └─► SAF DocumentFile (Android 11–12)
                   ├──► ConfigEditor     แทนค่าด้วย regex คงรูปแบบไฟล์เดิม
                   ├──► GameRepository   PackageManager + ตรวจ engine จาก native lib ใน APK
                   └──► FpsOverlayService ──► FpsMeter (dumpsys SurfaceFlinger)
```

- **การเข้าถึงไฟล์**: Shizuku เปิด `FileService` ในโปรเซสแยกด้วยสิทธิ์ shell (uid 2000) หรือ root (uid 0) การคัดลอกไฟล์จะเขียนทับลงไฟล์เดิม ไม่ลบแล้วสร้างใหม่ เจ้าของไฟล์และสิทธิ์จึงไม่เปลี่ยน ข้อนี้สำคัญมากกับไฟล์ใน `/data/data`
- **การตรวจ engine**: หาไฟล์ library อย่าง `libunity.so`, `libUE4.so` หรือ `libcocos*.so` ใน APK และ split APK
- **ตัววัด FPS**: คำสั่ง `dumpsys SurfaceFlinger --latency <layer>` ให้เวลาที่แต่ละเฟรมของเกมขึ้นจอ แอปคำนวณ FPS = (จำนวนเฟรมใน 1 วินาทีล่าสุด − 1) ÷ ช่วงเวลา ถ้าใช้คำสั่งนี้ไม่ได้ จะใช้ตัวนับเฟรมของทั้งจอ (`service call SurfaceFlinger 1013`) แทน

## Build เอง

ไม่ต้องมีคอมพิวเตอร์ ทุกครั้งที่ push ขึ้น `main` ระบบ GitHub Actions จะ build APK และลงไว้ที่หน้า Releases ให้อัตโนมัติ

ถ้าจะ build ในเครื่อง ให้ติดตั้ง Android Studio (JDK 17) เปิดโฟลเดอร์นี้ แล้วรันคำสั่ง

```bash
./gradlew assembleDebug
# ไฟล์อยู่ที่ app/build/outputs/apk/debug/app-debug.apk
```

ทุก build เซ็นด้วยกุญแจ debug `app/debug.keystore` (รหัสผ่าน `android`) ที่เก็บไว้ใน repo เวอร์ชันใหม่จึงติดตั้งทับเวอร์ชันเก่าได้ **ห้ามใช้กุญแจนี้ลง Play Store** ให้สร้างกุญแจสำหรับอัปโหลดของตัวเองและเก็บเป็นความลับ

## โครงสร้างโปรเจกต์

```text
app/src/main/
├─ aidl/…/IFileService.aidl        อินเทอร์เฟซ Binder ของ file service ใน Shizuku
├─ java/com/fpsunlocker/tool/
│  ├─ App.kt, MainActivity.kt
│  ├─ shizuku/   ShizukuManager, FileService (UserService)
│  ├─ data/      FileAccess, ConfigEditor, GameRepository, Publishers, DisplayInfo
│  ├─ overlay/   FpsOverlayService, FpsMeter
│  └─ ui/        MainScreen, GamesTab, TuneTab, TuneExtras, SetupTab, Theme, Components
└─ res/values/strings.xml          ภาษาอังกฤษ (ค่าเริ่มต้น)
   res/values-th/strings.xml       ภาษาไทย
```

## เพิ่มภาษา

ข้อความทั้งหมดอยู่ใน `res/values/strings.xml` ถ้าจะเพิ่มภาษา ให้คัดลอกไฟล์นี้ไปไว้ที่ `res/values-<รหัสภาษา>/strings.xml` เช่น `values-vi` แปลข้อความ โดยคง `%1$s` และ `%1$d` ไว้ครบทุกตัว แล้วส่ง pull request มาได้เลย

## ร่วมพัฒนา

ยินดีรับรายงานปัญหาและ pull request ถ้าแจ้งปัญหากับเกมใด ช่วยใส่รุ่นมือถือ เวอร์ชัน Android วิธีเปิด Shizuku (adb หรือ root) ชื่อ package ของเกม และบรรทัดสรุปผล Scan จาก Log มาด้วย

## สัญญาอนุญาต

[MIT](LICENSE) © 2026 BunnyBubui

โปรเจกต์นี้ไม่มีส่วนเกี่ยวข้องกับ Shizuku, Google หรือผู้พัฒนาเกมใดๆ ชื่อเกมและเครื่องหมายการค้าเป็นของเจ้าของแต่ละราย
