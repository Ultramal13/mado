# Calibrar el Google Pixel 11 Pro · Calibrating the Google Pixel 11 Pro · Google Pixel 11 Proの校正

[Español](#español) · [English](#english) · [日本語](#日本語)

Mado calcula el recuadro de cada cámara con el campo de visión (FOV) del teléfono. Para que sea exacto, hay que **medir el FOV real de cada lente** del Pixel y compararlo con el teórico. Son unos **15 minutos** y se necesita una cinta métrica. Con tus mediciones se agrega el **perfil «Pixel 11 Pro»** al catálogo, como el del Xperia.

---

## Español

### Qué necesitas
> **Usa la versión 0.2.43-beta o más nueva**: ordena los lentes de ancho a largo y con nombre, y calcula el equivalente en 35 mm por diagonal (12 · 24 · 105 mm), como lo informan los fabricantes.

- El **Pixel 11 Pro** con **Mado** instalado ([última versión](https://github.com/Ultramal13/mado/releases/latest): baja el `.apk` y ábrelo; si Android pregunta por «fuentes desconocidas», permítelo solo para el navegador o el gestor de archivos). Se instala **encima** de la anterior, sin desinstalar.
- Una **cinta métrica** de 1 m (basta) y **cinta adhesiva**.
- Una **pared plana y lisa**, con luz normal (no a contraluz).
- Algo estable donde apoyar el teléfono (libros, un trípode).
- **Cierra la Blackmagic Camera y la cámara de Google** (deslízalas fuera de las apps recientes): si otra app tiene la cámara abierta, Mado no puede usarla.

### Paso 1 · Compartir el diagnóstico (2 minutos)
Esto me deja ver qué datos entrega tu Pixel (lentes, sensores, calibración interna) antes de medir.

1. Abre **Mado** → **☰** (arriba a la derecha) → **diagnóstico de cámaras**.
2. Toca **compartir** y mándalo por **WhatsApp** o correo. No lleva fotos ni ubicación.

### Paso 2 · Las 3 lentes que se miden
En la pantalla de calibración verás botones con las lentes del teléfono, **ordenados de ancho a largo**:

| Botón (aprox.) | Lente |
|---|---|
| `ultra 12mm` (`超広角` en japonés) | ultra gran angular |
| `principal 24mm` | principal |
| `tele 105mm` | teleobjetivo |

Si aparece otro botón, **ignóralo**: probablemente es un sensor de apoyo. En versiones anteriores a la 0.2.43 los botones se llaman `3 · 13mm`, `2 · 25mm` y `4 · 109mm`: son las mismas lentes.

### Paso 3 · Preparar la pared (una sola vez)
1. Pega la **cinta métrica en la pared**, **horizontal**, a la altura de tus ojos. Que se vea de **0 a 50 cm o más** hacia la derecha del punto donde apuntará el centro.
2. Pon el teléfono en **horizontal** (apaisado), apoyado y apuntando **recto** a la pared. No lo inclines.

### Paso 4 · Medir cada lente
1. Abre **Mado** → **☰** → **calibrar lentes (medir FOV)**.
2. Elige la **lente**. Arriba verás un número grande: es el campo teórico.
3. Mira «**distancia lente → pared (cm) · sugerida N**». Pon el teléfono a **esa distancia**, medida **desde la lente hasta la pared** (la lente es el círculo de vidrio de la cámara). Usa la cinta con su 0 en la lente; **después** pégala en la pared. Si no puedes poner exactamente la sugerida, usa otra y **escribe la real** en la casilla de distancia.
4. Toca **iniciar preview**. Verás una **cruz** en el centro y marcas naranjas.
5. Mueve el teléfono para que el **0 de la cinta quede justo en el centro de la cruz**.
6. Mira hacia el **borde derecho** de la imagen y lee **qué número de la cinta queda justo en el borde**. La app te dice cuál debería ser «cerca de N cm».
7. Escribe ese número en «**número que marca la cinta en el borde del visor (cm)**».
8. Verás **fov medido** y **error vs teoría**. Toca **guardar** (debe decir «guardado · 1», «guardado · 2», …).
9. Toca **detener** y repite con la siguiente lente.

> Si el teléfono se calienta, toca **detener** y espera unos minutos. El teleobjetivo puede no enfocar a menos de ~50 cm: usa **1 metro** o más. En el Pixel la cámara puede **recortar un poco** la imagen para corregir la distorsión: por eso conviene medir también la ronda 2.

### Ronda 2 (opcional, si tienes tiempo)
Repite **solo la lente principal** cambiando «**corrección de distorsión**» a **apagada** (si el teléfono lo permite), a la misma distancia. Sirve para saber cuánto cambia el campo con y sin corrección.

### Qué datos anotar y enviar
Para **cada medición** anota (o toma una captura de la pantalla de calibración):

| Lente (botón) | Distancia lente→pared (cm) | Número de la cinta en el borde (cm) | Corrección de distorsión | Orientación |
|---|---|---|---|---|
| `ultra 12mm` | | | por defecto | horizontal |
| `principal 24mm` | | | por defecto | horizontal |
| `tele 105mm` | | | por defecto | horizontal |
| `principal 24mm` (opcional) | | | apagada | horizontal |

Además anota el **modelo** del teléfono (Pixel 11 Pro) y cualquier cosa rara (imagen desenfocada, el teléfono se calentó, no pudiste usar la distancia sugerida).

### Paso 5 · Enviar
1. En la pantalla de calibración toca **compartir**. Se abre el menú de Android: elige **WhatsApp**, correo u otro y mándalo a quien te pidió la prueba. Eso envía **todas las mediciones guardadas**.
2. Manda también **tu hoja o las capturas** de pantalla (con el teléfono: **botón de encendido + bajar volumen**).
3. Si puedes: una **foto de la pared** con la cinta y el teléfono apuntando.

> Sin tocar «compartir» los datos **no salen** del teléfono: solo se guardan dentro de Mado.

### Extra · Probar el sol y la brújula (opcional)
Si hay sol: en el visor toca **☀**, permite la ubicación y saca **2 fotos al sol** (una con el teléfono en horizontal y otra en vertical) en un lugar abierto, lejos de edificios y autos. Mándalas tal cual: los datos de la foto incluyen el campo magnético y si la brújula estaba alterada. Si el círculo del sol cae lejos del sol real, toca **◎** y luego el sol real en la pantalla.

---

## English

### What you need
> **Use version 0.2.43-beta or newer**: it lists the lenses from wide to long with names, and works out the 35 mm equivalent by diagonal (12 · 24 · 105 mm), the way manufacturers report it.

- The **Pixel 11 Pro** with **Mado** installed ([latest version](https://github.com/Ultramal13/mado/releases/latest): download the `.apk` and open it; if Android asks about "unknown sources", allow it only for the browser or file manager). Install it **over** the previous version, without uninstalling.
- A **tape measure** (1 m is enough) and **adhesive tape**.
- A **flat, plain wall** with normal light (not backlit).
- Something steady to rest the phone on (books, a tripod).
- **Close Blackmagic Camera and the Google camera** (swipe them away from recent apps): if another app has the camera open, Mado cannot use it.

### Step 1 · Share the diagnostic (2 minutes)
This lets me see what data your Pixel provides (lenses, sensors, internal calibration) before you measure.

1. Open **Mado** → **☰** (top right) → **camera diagnostics**.
2. Tap **share** and send it by **WhatsApp** or email. It has no photos and no location.

### Step 2 · The 3 lenses to measure
On the calibration screen you will see buttons for the phone's lenses, **ordered from wide to long**:

| Button (approx.) | Lens |
|---|---|
| `ultra 12mm` | ultra wide |
| `main 24mm` | main |
| `tele 105mm` | telephoto |

If another button appears, **ignore it**: it is probably a support sensor. In versions before 0.2.43 the buttons are called `3 · 13mm`, `2 · 25mm` and `4 · 109mm`: they are the same lenses.

### Step 3 · Prepare the wall (once)
1. Stick the **tape measure on the wall**, **horizontal**, at eye level. It should show **0 to 50 cm or more** to the right of the point where the centre will aim.
2. Put the phone in **landscape**, resting and pointing **straight** at the wall. Do not tilt it.

### Step 4 · Measure each lens
1. Open **Mado** → **☰** → **calibrate lenses (measure FOV)**.
2. Pick the **lens**. A big number at the top is the theoretical field.
3. Read "**distance lens → wall (cm) · suggested N**". Put the phone at **that distance**, measured **from the lens to the wall** (the lens is the glass circle of the camera). Use the tape with its 0 at the lens; **then** stick it on the wall. If you cannot use exactly the suggested distance, use another and **type the real one** in the distance box.
4. Tap **start preview**. You will see a **cross** in the centre and orange marks.
5. Move the phone so the **0 of the tape sits exactly on the centre of the cross**.
6. Look to the **right edge** of the image and read **which tape number is right at the edge**. The app tells you which it should be, "about N cm".
7. Type that number in "**number the tape shows at the viewer edge (cm)**".
8. You will see **measured fov** and **error vs theory**. Tap **save** (it should say "saved · 1", "saved · 2", …).
9. Tap **stop** and repeat with the next lens.

> If the phone gets hot, tap **stop** and wait a few minutes. The telephoto may not focus closer than ~50 cm: use **1 metre** or more. On the Pixel the camera may **crop the image a little** to correct distortion: that is why round 2 is worth doing.

### Round 2 (optional, if you have time)
Repeat **only the main lens** with "**distortion correction**" set to **off** (if the phone allows it), at the same distance. It shows how much the field changes with and without correction.

### What to note and send
For **each measurement** note (or take a screenshot of the calibration screen):

| Lens (button) | Lens→wall distance (cm) | Tape number at the edge (cm) | Distortion correction | Orientation |
|---|---|---|---|---|
| `ultra 12mm` | | | default | landscape |
| `main 24mm` | | | default | landscape |
| `tele 105mm` | | | default | landscape |
| `main 24mm` (optional) | | | off | landscape |

Also note the phone **model** (Pixel 11 Pro) and anything odd (blurry image, the phone got hot, you could not use the suggested distance).

### Step 5 · Send
1. On the calibration screen tap **share**. Android's menu opens: pick **WhatsApp**, email or another and send it to whoever asked for the test. That sends **all the saved measurements**.
2. Also send **your sheet or the screenshots** (on the phone: **power button + volume down**).
3. If you can: a **photo of the wall** with the tape and the phone pointing, to see how you set it up.

> Without tapping "share" the data **does not leave** the phone: it is only stored inside Mado.

### Extra · Try the sun and the compass (optional)
If there is sun: in the viewer tap **☀**, allow the location and take **2 photos of the sun** (one with the phone in landscape and one in portrait) in an open place, away from buildings and cars. Send them as they are: the photo's data includes the magnetic field and whether the compass was disturbed. If the sun's circle lands away from the real sun, tap **◎** and then the real sun on the screen.

---

## 日本語

### 必要なもの
> **バージョン 0.2.43-beta 以降を使ってください。** レンズを広角から望遠の順に名前付きで表示し、35mm換算焦点距離をメーカーの表記と同じ対角基準（12・24・105 mm）で計算します。

- **Mado** を入れた **Pixel 11 Pro**（[最新版](https://github.com/Ultramal13/mado/releases/latest)：`.apk` をダウンロードして開きます。Android が「提供元不明のアプリ」について聞いたら、ブラウザまたはファイル管理アプリにだけ許可してください）。前のバージョンの**上から**入れます。アンインストールは不要です。
- **メジャー**（1 m で十分）と**テープ**。
- **平らで無地の壁**と普通の明るさ（逆光でないこと）。
- スマホを安定して置けるもの（本、三脚など）。
- **Blackmagic Camera と Google のカメラを閉じてください**（最近使ったアプリから上にスワイプして終了）。ほかのアプリがカメラを開いていると、Mado は使えません。

### ステップ1・診断を共有する（2分）
測定の前に、Pixel がどんなデータ（レンズ、センサー、内部の校正）を出すかを確認できます。

1. **Mado** を開く → 右上の **☰** → **カメラ診断**。
2. **共有** をタップして **WhatsApp** かメールで送ってください。写真も位置情報も含まれません。

### ステップ2・測定する3つのレンズ
校正画面にスマホのレンズのボタンが、**広角から望遠の順**に並びます。

| ボタン（目安） | レンズ |
|---|---|
| `超広角 12mm` | 超広角 |
| `メイン 24mm` | メイン |
| `望遠 105mm` | 望遠 |

ほかのボタンが出たら**無視**してください。補助センサーの可能性が高いです。0.2.43 より前のバージョンでは、ボタンは `3 · 13mm`、`2 · 25mm`、`4 · 109mm` と表示されます。同じレンズです。

### ステップ3・壁の準備（最初の1回だけ）
1. **メジャーを壁に水平**に、目の高さに貼ります。中心が向く点から右に **0〜50 cm 以上**見えるようにします。
2. スマホを**横向き**にして安定させ、壁に**まっすぐ**向けます。傾けないでください。

### ステップ4・レンズごとに測定する
1. **Mado** を開く → **☰** → **レンズ校正（画角の測定）**。
2. **レンズ**を選びます。上の大きな数字が理論上の画角です。
3. 「**レンズ → 壁の距離（cm）・推奨 N**」を見ます。**レンズから壁まで**（レンズはカメラのガラスの丸い部分）を**その距離**にスマホを置きます。メジャーの 0 をレンズに当てて距離を測り、**そのあと**壁に貼ります。推奨どおりにできないときは別の距離で測り、**実際の距離**を入力欄に書きます。
4. **プレビュー開始**をタップします。中央に**十字**とオレンジの目盛りが出ます。
5. **メジャーの 0 が十字の中心**に合うようにスマホを動かします。
6. 画像の**右端**を見て、**端にちょうど来ているメジャーの数字**を読みます。アプリが「約 N cm のはず」と教えてくれます。
7. その数字を「**ビューアの端でメジャーが示す数字（cm）**」に入力します。
8. **測定画角**と**理論との差**が出ます。**保存**をタップします（「保存済み・1」「保存済み・2」…と出ます）。
9. **停止**をタップして、次のレンズで繰り返します。

> スマホが熱くなったら**停止**して数分待ってください。望遠は約 50 cm より近いとピントが合わないことがあります。**1 m 以上**で測ってください。Pixel は歪み補正のために画像を**少し切り出す**ことがあるので、ラウンド2も測ると役立ちます。

### ラウンド2（任意・時間があれば）
**メインレンズだけ**、「**歪み補正**」を**オフ**にして（スマホが対応していれば）同じ距離で繰り返します。補正あり・なしで画角がどれだけ変わるかが分かります。

### 記録して送るデータ
**測定ごと**に記録します（または校正画面のスクリーンショット）。

| レンズ（ボタン） | レンズ→壁の距離（cm） | 端のメジャーの数字（cm） | 歪み補正 | 向き |
|---|---|---|---|---|
| `超広角 12mm` | | | 標準 | 横 |
| `メイン 24mm` | | | 標準 | 横 |
| `望遠 105mm` | | | 標準 | 横 |
| `メイン 24mm`（任意） | | | オフ | 横 |

スマホの**機種**（Pixel 11 Pro）と、気づいたこと（ピンボケ、発熱、推奨距離にできなかったなど）も書いてください。

### ステップ5・送る
1. 校正画面で**共有**をタップします。Android のメニューが開くので、**WhatsApp**、メールなどを選んで、テストを頼んだ人に送ります。**保存した測定値すべて**が送られます。
2. **記録表かスクリーンショット**も送ってください（スマホでは**電源ボタン＋音量を下げる**）。
3. できれば、メジャーとスマホを向けた様子の**壁の写真**も。

> 「共有」をタップしないかぎり、データはスマホの外に**出ません**。Mado の中に保存されるだけです。

### おまけ・太陽とコンパスを試す（任意）
日が出ていれば、ビューアで **☀** をタップし、位置情報を許可して、建物や車から離れた開けた場所で**太陽の写真を2枚**（スマホを横向きと縦向きで1枚ずつ）撮ってください。そのまま送ってください。写真のデータに磁場とコンパスが乱れていたかが入っています。太陽の円が本物の太陽から離れていたら、**◎** をタップしてから画面の本物の太陽をタップします。
