# Calibrar el Xperia 1 III · Calibrating the Xperia 1 III · Xperia 1 IIIの校正

[Español](#español) · [English](#english) · [日本語](#日本語)

Mado calcula el recuadro de cada cámara con el campo de visión (FOV) del teléfono. Para que sea exacto, hay que **medir el FOV real de cada lente** y compararlo con el teórico. Son unos **15 minutos** y se necesita una cinta métrica.

---

## Español

### Qué necesitas
> **Usa la versión 0.2.13-beta o más nueva.** En las anteriores, al girar el teléfono a horizontal la pantalla de calibración podía verse estirada y sin menú, sobre todo después de cambiar el idioma dentro de Mado.

- El **Xperia 1 III** con **Mado** instalado ([última versión](https://github.com/Ultramal13/mado/releases/latest): baja el `.apk` y ábrelo; si Android pregunta por «fuentes desconocidas», permítelo solo para el navegador o el gestor de archivos).
- Una **cinta métrica** de 1 m (basta) y **cinta adhesiva**.
- Una **pared plana y lisa**, con luz normal (no a contraluz).
- Algo estable donde apoyar el teléfono (libros, un trípode).
- Papel y lápiz (o la hoja de abajo).

### Las 3 lentes que se miden
En la pantalla de calibración verás botones con las lentes del teléfono. **Mide estas tres** (una por una):

| Botón (aprox.) | Lente |
|---|---|
| `4 · 17mm` | ultra gran angular |
| `2 · 25mm` | principal |
| `3 · 75mm` | teleobjetivo |

Si aparece otro botón (por ejemplo `5 · …`), **ignóralo**: probablemente es un sensor de apoyo.

### Preparar la pared (una sola vez)
1. Pega la **cinta métrica en la pared**, **horizontal**, a la altura de tus ojos. Que se vea de **0 a 50 cm o más** hacia la derecha del punto donde apuntará el centro.
2. Pon el teléfono en **horizontal** (apaisado), apoyado y apuntando **recto** a la pared. No lo inclines.

### Pasos para cada lente
1. Abre **Mado** → toca **☰** (arriba a la derecha) → **calibrar lentes (medir FOV)**.
2. Elige la **lente** (por ejemplo `2 · 25mm`). Arriba verás un número grande: es el campo teórico.
3. Mira «**distancia lente → pared (cm) · sugerida N**». Pon el teléfono a **esa distancia**, medida **desde la lente hasta la pared** (la lente es el círculo de vidrio de la cámara). Para medir esa distancia usa la cinta con su 0 en la lente; **después** pégala en la pared. Si no puedes poner exactamente la sugerida, usa otra y **escribe la real** en la casilla de distancia.
4. Toca **iniciar preview**. Verás una **cruz** en el centro y marcas naranjas.
5. Mueve el teléfono para que el **0 de la cinta quede justo en el centro de la cruz**.
6. Mira hacia el **borde derecho** de la imagen y lee **qué número de la cinta queda justo en el borde**. La app te dice cuál debería ser «cerca de N cm».
7. Escribe ese número en «**número que marca la cinta en el borde del visor (cm)**».
8. Verás **fov medido** y **error vs teoría**. Toca **guardar** (debe decir «guardado · 1», «guardado · 2», …).
9. Toca **detener** y repite con la siguiente lente.

> Si el teléfono se calienta, toca **detener** y espera unos minutos. El teleobjetivo puede no enfocar a menos de ~50 cm: usa **1 metro** o más.

### Ronda 2 (opcional, si tienes tiempo)
Repite **solo la lente principal** (`2 · 25mm`) cambiando «**corrección de distorsión**» a **apagada** (si el teléfono lo permite), a la misma distancia. Sirve para saber cuánto cambia el campo con y sin corrección.

### Qué datos anotar y enviar
Para **cada medición** anota (o toma una captura de pantalla de la pantalla de calibración):

| Lente (botón) | Distancia lente→pared (cm) | Número de la cinta en el borde (cm) | Corrección de distorsión | Orientación |
|---|---|---|---|---|
| `4 · 17mm` | | | por defecto | horizontal |
| `2 · 25mm` | | | por defecto | horizontal |
| `3 · 75mm` | | | por defecto | horizontal |
| `2 · 25mm` (opcional) | | | apagada | horizontal |

Además anota:
- El **modelo** del teléfono (Xperia 1 III) y si es de operadora (por ejemplo SO-51B).
- Cualquier cosa rara (la imagen salió desenfocada, el teléfono se calentó, no pudiste usar la distancia sugerida).

### Cómo enviarlo
1. Cuando termines las mediciones, en la pantalla de calibración toca **compartir**. Se abre el menú de Android: elige **WhatsApp**, correo u otro y mándalo a quien te pidió la prueba. Eso envía **todas las mediciones guardadas**.
2. Manda también **tu hoja o las capturas** de pantalla (con el teléfono: **botón de encendido + bajar volumen**).
3. Si puedes: una **foto de la pared** con la cinta y el teléfono apuntando, para ver cómo la montaste.

> Sin mandar «compartir» los datos **no salen** del teléfono: solo se guardan dentro de Mado.

---

## English

### What you need
> **Use version 0.2.13-beta or newer.** In older versions, turning the phone to landscape could leave the calibration screen stretched and without its menu, especially after changing the language inside Mado.

- The **Xperia 1 III** with **Mado** installed ([latest version](https://github.com/Ultramal13/mado/releases/latest): download the `.apk` and open it; if Android asks about "unknown sources", allow it only for the browser or file manager).
- A **tape measure** (1 m is enough) and **adhesive tape**.
- A **flat, plain wall** with normal light (not backlit).
- Something steady to rest the phone on (books, a tripod).
- Pen and paper (or the sheet below).

### The 3 lenses to measure
On the calibration screen you will see buttons for the phone's lenses. **Measure these three**, one at a time:

| Button (approx.) | Lens |
|---|---|
| `4 · 17mm` | ultra-wide |
| `2 · 25mm` | main |
| `3 · 75mm` | telephoto |

If another button shows up (for example `5 · …`), **ignore it**: it is probably a support sensor.

### Set up the wall (once)
1. Stick the **tape measure on the wall**, **horizontal**, at eye level. It must show **0 to 50 cm or more** to the right of the point the centre will aim at.
2. Hold the phone in **landscape**, resting on something and aiming **straight** at the wall. Do not tilt it.

### Steps for each lens
1. Open **Mado** → tap **☰** (top right) → **calibrate lenses (measure FOV)**.
2. Pick the **lens** (for example `2 · 25mm`). A big number at the top is the theoretical field of view.
3. Read "**distance lens → wall (cm) · suggested N**". Place the phone at **that distance**, measured **from the lens to the wall** (the lens is the glass circle of the camera). To measure that distance use the tape with its 0 at the lens, and **then** stick it on the wall. If you cannot use exactly the suggested one, use another and **type the real distance** into the distance box.
4. Tap **start preview**. You will see a **cross** in the centre and orange marks.
5. Move the phone so the tape's **0 sits right at the centre of the cross**.
6. Look at the **right edge** of the image and read **which tape number sits right at the edge**. The app tells you what it should be, "about N cm".
7. Type that number into "**number the tape shows at the viewfinder edge (cm)**".
8. You will see **measured fov** and **error vs theory**. Tap **save** (it should say "saved · 1", "saved · 2", …).
9. Tap **stop** and repeat with the next lens.

> If the phone gets warm, tap **stop** and wait a few minutes. The telephoto may not focus closer than ~50 cm: use **1 metre** or more.

### Round 2 (optional, if you have time)
Repeat **only the main lens** (`2 · 25mm`) with "**lens distortion correction**" set to **off** (if the phone allows it), at the same distance. It shows how much the field changes with and without correction.

### What to write down and send
For **each measurement** write down (or take a screenshot of the calibration screen):

| Lens (button) | Distance lens→wall (cm) | Tape number at the edge (cm) | Distortion correction | Orientation |
|---|---|---|---|---|
| `4 · 17mm` | | | default | landscape |
| `2 · 25mm` | | | default | landscape |
| `3 · 75mm` | | | default | landscape |
| `2 · 25mm` (optional) | | | off | landscape |

Also note:
- The phone **model** (Xperia 1 III) and whether it is a carrier model (for example SO-51B).
- Anything odd (blurry image, the phone got hot, you could not use the suggested distance).

### How to send it
1. When the measurements are done, tap **share** on the calibration screen. Android's share menu opens: pick **WhatsApp**, email or another app and send it to whoever asked for the test. This sends **all saved measurements**.
2. Also send **your sheet or the screenshots** (on the phone: **power button + volume down**).
3. If you can: a **photo of the wall** with the tape and the phone aiming at it, to see how you set it up.

> Unless you tap "share", the data **does not leave** the phone: it is only stored inside Mado.

---

## 日本語
（※翻訳は未確認です。分からない点はスペイン語・英語の手順も参照してください。）

### 用意するもの
> **バージョン 0.2.13-beta 以降を使ってください。** 以前のバージョンでは、端末を横向きにすると校正画面が引き伸ばされ、メニューが消えることがありました（特にMado内で言語を変更した後）。

- **Mado** をインストールした **Xperia 1 III**（[最新版](https://github.com/Ultramal13/mado/releases/latest)から `.apk` をダウンロードして開きます。「提供元不明のアプリ」の確認が出たら、ブラウザまたはファイル管理アプリにだけ許可してください）。
- **メジャー**（1 mで十分）と**テープ**。
- **平らで無地の壁**（逆光でない通常の明るさ）。
- 端末を置ける安定した場所（本や三脚）。
- 紙とペン（または下の表）。

### 測定する3つのレンズ
校正画面にレンズのボタンが並びます。次の**3つを1つずつ**測ります。

| ボタン（目安） | レンズ |
|---|---|
| `4 · 17mm` | 超広角 |
| `2 · 25mm` | メイン |
| `3 · 75mm` | 望遠 |

ほかのボタン（例: `5 · …`）は補助センサーと思われるので**無視**してください。

### 壁の準備（最初に1回）
1. **メジャーを壁に水平に**、目の高さに貼ります。中心が向く位置から右へ **0〜50 cm以上**が見えるようにします。
2. 端末を**横向き**にして安定した場所に置き、壁に**まっすぐ**向けます。傾けないでください。

### 各レンズの手順
1. **Mado** を開き、右上の **☰** →「**レンズ校正（画角の測定）**」。
2. **レンズ**を選びます（例: `2 · 25mm`）。上の大きな数字が理論上の画角です。
3.「**レンズから壁までの距離 (cm) · 推奨 N**」を見て、その**距離**（**レンズから壁まで**。レンズはカメラのガラスの円。距離はメジャーの0をレンズに当てて測り、**その後**で壁に貼ります）に端末を置きます。推奨どおりにできない場合は別の距離で測り、**実際の距離**を入力欄に入れてください。
4.「**プレビュー開始**」をタップします。中心に**十字**とオレンジの目印が出ます。
5. メジャーの**0が十字の中心**に来るように端末を動かします。
6. 画像の**右端**で、**ちょうど端に来ているメジャーの数値**を読みます。期待値（約 N cm）が画面に出ます。
7. その数値を「**ビューファインダーの端でメジャーが示す数値 (cm)**」に入力します。
8. **実測の画角**と**理論との差**が出ます。「**保存**」をタップします（「保存済み · 1」「保存済み · 2」…と出ます）。
9.「**停止**」を押して、次のレンズで繰り返します。

> 端末が熱くなったら「停止」を押して数分待ってください。望遠は約50 cm以内ではピントが合わないことがあります。**1 m**以上で測ってください。

### 第2ラウンド（任意）
**メインレンズ**（`2 · 25mm`）だけ、「**歪み補正**」を**オフ**にして同じ距離でもう一度測ります。

### 記録して送るデータ
**測定ごと**に次を記録するか、校正画面のスクリーンショットを撮ってください。

| レンズ（ボタン） | レンズ→壁の距離 (cm) | 端のメジャーの数値 (cm) | 歪み補正 | 向き |
|---|---|---|---|---|
| `4 · 17mm` | | | デフォルト | 横 |
| `2 · 25mm` | | | デフォルト | 横 |
| `3 · 75mm` | | | デフォルト | 横 |
| `2 · 25mm`（任意） | | | オフ | 横 |

そのほか:
- 端末の**機種**（Xperia 1 III）と、キャリア版かどうか（例: SO-51B）。
- 気づいたこと（ピンボケ、熱くなった、推奨距離で測れなかった など）。

### 送り方
1. 測定が終わったら、校正画面で「**共有**」をタップします。Androidの共有メニューが開くので、**WhatsApp**やメールなどを選び、テストを頼んだ人に送ります。**保存したすべての測定値**が送られます。
2. **記録表またはスクリーンショット**も送ってください（端末の**電源ボタン＋音量ダウン**）。
3. 可能なら、メジャーと端末が写った**壁の写真**。

> 「共有」を押さない限り、データは端末の外に出ません（Mado内に保存されるだけです）。
