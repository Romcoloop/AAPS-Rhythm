# AutoISF fork: blended BG, časy jedál, sloty a ladiaca karta

Cadence, zostavenie AndroidAPS (github.com/Romcoloop/Cadence), mení plugin **OpenAPS AutoISF** na štyroch
miestach. Všetko ostatné je upstream AndroidAPS.

| Čo | Kde v appke | Vypnuté znamená |
|---|---|---|
| 1. Blended BG pre bg_ISF | AutoISF settings → Blended BG ISF weight | váha 0,0 = ako upstream |
| 2. Časy jedál (okná slotov jedál) | AutoISF settings | žiadny čas jedla = ako upstream |
| 3. Sloty: vlastné hodnoty pre jedlo, víkend a noc | Slot times + Slot values (sekcie slotov) | prepínač vypnutý = ako upstream |
| 4. AutoISF log analysis (ladiaca karta) | karta na obrazovke pluginu AutoISF | len číta; zapisuje po „Použiť“ |
| 5. Kroky a aktivita | AutoISF settings → Steps and activity | všetkých 5 prepínačov vypnutých = ako upstream |

Všetko je vo vetve `main`. **Inštaluj buildy z `main`.**
Build: GitHub Actions workflow „AutoISF APK“ (APK pre telefón aj hodinky; `runTests = true` najprv
spustí unit testy modulu APS).

> **Pred inštaláciou nového APK:** Maintenance → Export settings.
> Nič tu nemení hodnotu samo. Každú hodnotu nastavuješ ty: v nastaveniach alebo tlačidlom „Použiť“
> v karte analýzy po potvrdení.
> Ide o dávkovanie inzulínu. Meň jednu hodnotu naraz, po malých krokoch, a konzultuj so svojím
> diabetologickým tímom.

**Autorstvo.** Algoritmus AutoISF je od ga-zelle, ako v upstream AAPS. Pravidlá krokov v kapitole 8
sú prevzaté z **OpenAPS AIMI** od MTR93600 (https://github.com/MTR93600/OpenApsAIMI,
`DetermineBasalAIMI2.kt`), s prahmi z AIMI. Nápad blended BG (kapitola 1) je z **Boostu**. Všetko,
vrátane tohto forku, je pod licenciou AGPL-3.0.

## 1. Blended BG

`bg_ISF` sa bežne pozerá len na aktuálnu BG, takže pri rýchlom raste reaguje neskoro. Pri váhe nad 0
sa BG použitá pre `bg_ISF` posunie nahor o časť rastu, ktorý parabola očakáva v najbližších
20 minútach: `BG + váha × (predikovaná BG − BG)`. Nápad je z Boostu.

Pravidlá v kóde (`BlendedBg.kt`):

- len keď je fit paraboly dobrý (korelácia ≥ 0,9, rovnaký limit ako acce_ISF),
- len na cieli alebo nad ním a len keď BG rastie; pri klesaní sa vždy použije skutočná BG,
- posun je obmedzený na 36 mg/dl (2 mmol/l), váha na 0,5,
- mení len `bg_ISF`; `acce_ISF`, `pp_ISF` a `dura_ISF` sa ho nedotýkajú,
- výsledný ISF je stále obmedzený `autoISF_min` / `autoISF_max`.

V logu sú riadky `bgBlend`. Opatrný štart je váha 0,1–0,2.

## 2. Časy jedál

Tri okná v Slot times: Weekday: breakfast / lunch / dinner time, zapísané ako `HH:MM-HH:MM`. Sú to okná
pracovných slotov jedál (kapitola 3). V sobotu, nedeľu a v režime sviatok/dovolenka sa namiesto nich
použijú víkendové časy jedál, ak sú zapnuté víkendové sloty.

Samostatný faktor pre čas mimo jedál už neexistuje (bývalý *Meal factors outside meal times*). Mimo
všetkých okien slotov platia **globálne hodnoty** a pre noc je **nočný slot** s vlastnými 15 hodnotami.
Ak chceš, aby bol čas medzi jedlami miernejší, uprav globálne hodnoty. Pri vypnutých slotoch okná
nerobia nič a loop funguje ako upstream.

## 3. Sloty

Slot je časové okno s vlastnou kópiou 15 čísel AutoISF: 9 hodnôt AutoISF, IOB threshold a 5 hodnôt
SMB delivery. Vo vnútri okna loop používa hodnoty slotu, mimo všetkých okien globálne hodnoty.
Prepínače AutoISF (SMB, UAM, autosens, …) ostávajú globálne.

### 3.1 Sedem slotov

| Slot | Platí | Okno sa nastavuje v |
|---|---|---|
| Raňajky, Obed, Večera | pondelok – piatok | Slot times → Weekday: breakfast / lunch / dinner time |
| Raňajky, Obed, Večera (víkend) | sobota, nedeľa, režim sviatok/dovolenka | Slot times → Weekend: breakfast / lunch / dinner time |
| Noc | každý deň | Slot times → Night time (every day) |

- Slot sa vyberá **len podľa hodín**, raz na začiatku každého behu loopu. Zadanie sacharidov slot
  nemení.
- Noc môže ísť cez polnoc, napríklad `23:00-05:30`. Koniec okna sa nepočíta: o `08:00` je raňajkové
  okno `05:30-08:00` už skončené.
- Prázdne okno = slot sa nepoužíva.
- Kde sa prekrýva okno jedla s nočným, **vyhráva jedlo**. Kde sa prekrývajú dve jedlá, vyhráva prvé v
  poradí raňajky → obed → večera.
- Sobota a nedeľa používajú víkendové sloty. V pracovný deň sa víkendové sloty použijú, kým je
  zapnutý **Holiday or vacation**.

| Prepínač | Vypnuté |
|---|---|
| Use meal slot settings | Správanie presne ako bez slotov: všade globálne hodnoty. |
| Separate weekend slots | Pracovné sloty a pracovné časy jedál platia každý deň. |
| Holiday or vacation | Víkendové sloty len v sobotu a nedeľu. |

### 3.2 Kde sú nastavenia

Všetko v nastaveniach pluginu OpenAPS AutoISF, v tomto poradí:

1. **AutoISF settings** a **SMB delivery settings**: globálne hodnoty. Žiadne časy.
2. **Slot times**: jediné miesto pre všetky časy.
   - Use meal slot settings
   - Weekday: breakfast / lunch / dinner time
   - Separate weekend slots
   - Weekend: breakfast / lunch / dinner time – vidno, kým sú víkendové sloty zapnuté
   - Night time (every day)
   - Holiday or vacation a „Holiday or vacation ends on“ (`RRRR-MM-DD`). S dátumom sa prepínač sám
     vypne deň po tom dátume. Bez dátumu ho vypínaš ručne.
   - **Copy global values to the night slot** – zapíše globálne hodnoty len do **nočného slotu**.
     Sloty jedál a čas noci sa nemenia. Dobrý začiatok pre noc: skopíruj, potom hodnoty ručne
     zmiernite (kapitola 5).
   - **Copy global values to all slots** – zapíše globálne hodnoty do **všetkých siedmich** slotov.
     Hodnoty, ktoré už v slotoch boli, sa stratia. Dobré raz, úplne na začiatku.
   - **Copy weekday slots to weekend** – zapíše hodnoty pracovných raňajok / obeda / večere do
     víkendových slotov. Pracovné sloty sa nemenia. Okná sa nikdy nekopírujú.
3. **Slot values**, sedem sekcií v pevnom poradí: *Breakfast (weekday)*, *Lunch (weekday)*,
   *Dinner (weekday)*, *Breakfast (weekend)*, *Lunch (weekend)*, *Dinner (weekend)*, *Night (every
   day)*. Každá má 15 hodnôt svojho slotu. Sekcia je skrytá, kým je jej prepínač vypnutý.

Obrazovka nastavení má len dve úrovne (obrazovka pluginu, sekcia), preto sekcie idú za sebou v rade
a nedajú sa vnoriť.

### 3.3 Prvé nastavenie v bezpečnom poradí

Nový slot začína s továrenskými hodnotami (`autoISF_max` 1,0, všetky váhy 0,0). Taký slot by bežal
takmer bez AutoISF. Preto slot najprv **naplň** a až potom mu daj okno.

1. Use meal slot settings → zapnúť.
2. Ak sú pracovné sloty ešte rovné globálnym hodnotám: **Copy global values to all slots**.
   Ak si pracovné sloty už ladil: **nestláčaj** ho. Použi **Copy weekday slots to weekend** a pre noc
   **Copy global values to the night slot** (potom noc zmiernite, hodnoty sú v kapitole 5).
3. Nastav pracovné časy jedál (Slot times), ak ešte nie sú.
4. Separate weekend slots → zapnúť, nastaviť víkendové časy, napríklad `07:00-10:00`, `12:00-15:00`,
   `18:00-21:00`.
5. Night time, napríklad `23:00-05:30` – až keď má nočný slot hodnoty.
6. Pár dní hodnoty nemeň. Ide o to, aby sa najprv v logu objavili riadky s názvami slotov, aby ich
   analýza vedela porovnať.

### 3.4 Kontrola, čo loop urobil

Každý beh zapíše jeden riadok do debug výpisu AutoISF (tab OpenAPS AutoISF a log súbory):

```
AutoISF slot: Dinner-weekend (18:00-21:00, weekend)
AutoISF slot: Night (23:00-05:30, weekday)
AutoISF slot: none, global values (holiday)
```

Prvé slovo za `slot:` je slot (`Breakfast`, `Lunch`, `Dinner`, `Breakfast-weekend`, …, `Night`
alebo `none`). V zátvorke: okno a druh dňa (`weekday`, `weekend` alebo `holiday`; `holiday`
znamená, že bol zapnutý „Holiday or vacation“).

## 4. Ladiaca karta: AutoISF log analysis

Karta na obrazovke pluginu AutoISF číta log súbory v telefóne (`AndroidAPS.log` a staršie
zazipované) a ukazuje, čo AutoISF robil v **posledných hodinách**, nech sa v nich stalo čokoľvek.
Sama nikdy žiadnu hodnotu nemení.

### 4.1 Ovládanie

- **Hodiny:** posuvník od 1 do 24 hodín a dve rýchle tlačidlá **3 h** a **6 h**. Nič nepíšeš.
  Popisok ukazuje zvolené hodiny. Zmena zmaže výsledok na obrazovke.
- **Analyze** to spustí.
- Skúmaný čas sú zvolené hodiny pred teraz. Žiadne časy jedál, výber slotu ani hľadanie jedla:
  jednoducho posledné hodiny so všetkým, čo v nich je.
- **Slot:** ak všetky behy týchto hodín bežali s hodnotami jedného slotu (riadky logu hovoria
  `AutoISF slot: Lunch`, ...), karta ten slot pozná a **Použiť** do neho zapisuje. Ak loop používal
  viac než jednu sadu hodnôt (dva sloty, slot a globálne hodnoty), **návrh nie je**, lebo niet jedného
  nastavenia na porovnanie. Rovnako keď behy nemajú riadok slotu (sloty boli vypnuté alebo staršia
  verzia) a keď je slot teraz vypnutý.
- Výsledok ostáva na obrazovke, kým nezmeníš hodiny alebo znova nestlačíš Analyze.

### 4.2 Čo ukazuje

Nadpis povie, o aký slot ide (alebo *Last 6 h*, keď nie je jeden slot) a od kedy do kedy.

**Fakty:** vrchol (BG, čas, o koľko nad cieľom), minúty nad cieľom +2 a +4 mmol/l, minúty pod 3,9,
BG nad cieľom na konci, počet a jednotky SMB, ako často `autoISF_max` orezal faktor, ako často bol
SMB taký veľký, ako dovolil maxBolus, faktor, ktorý najčastejšie riadil ISF.

**Hodnoty, ktoré sa teraz používajú:** všetkých 15 čísel AutoISF a SMB delivery, ktoré loop teraz
používa (hodnoty slotu posledného behu, alebo globálne). Tam, kde pravidlá navrhujú zmenu, ju riadok
ukazuje: `1.60 → 1.50`.

**Udalosti:** každý SMB z týchto hodín, po ktorom do 3 hodín prišla hypo (pod 3,9) alebo
predikovaná hypo, ktorá zastavila SMB (`minGuardBG`), s faktorom, ktorý ten SMB riadil.

Ak je vo zvolených hodinách menej než 6 behov loopu, karta to povie. Ak log nepokrýva celý čas (loop
stál, log začal neskoro) alebo bol aktívny temp target, fakty sa ukážu s poznámkou, ale **bez
návrhu**.

### 4.3 Ako vzniká návrh

Pravidlá, žiadny model, a vidia len zvolené hodiny. Z menej než 2 hodín behov s rovnakými
nastaveniami sa nenavrhne nič a zmena nastavenia v tom čase začína nové obdobie. Každý návrh má
vlastné tlačidlo **Použiť**. Vychádzajú z tých istých pár udalostí, preto používaj **jeden po
druhom** a sleduj.

*Priveľa inzulínu* – keď boli aspoň 2 hypo po SMB, alebo viac než 4 % času pod 3,9, alebo dosť
predikovaných hypo: pozrú sa SMB pred hypo. Ak aspoň polovica z nich …

| … bola | návrh |
|---|---|
| orezaná cez `autoISF_max` | `autoISF_max` − 0,1 |
| riadená acce_ISF | `bgAccel_ISF_weight` × 0,8 |
| riadená bg_ISF | `higher_ISFrange_weight` × 0,8 |
| na maxime SMB delivery ratio | `smb_delivery_ratio_max` − 0,1 |
| taká veľká, ako dovolil maxBolus | `smb_max_range_extension` − 1 |

*Primálo inzulínu* – len keď nebola vôbec žiadna hypo ani udalosť, viac než 25 % času nad 10 mmol/l
a aspoň 24 behov vysoko s predikciou stále nad cieľom:

| … | návrh |
|---|---|
| `autoISF_max` orezal faktor v ≥ 30 % týchto behov | `autoISF_max` + 0,1 |
| inak ich v ≥ 30 % riadil bg_ISF | `higher_ISFrange_weight` × 1,1 |

Každý návrh uvádza počet udalostí, hodnotu pred a po a jeden dôvod. **Použiť** sa najprv spýta
(dialóg povie, že návrh je z pár hodín), zapíše hodnotu a odmietne, ak sa hodnota medzitým zmenila.
Hodnoty sú orezané na limity nastavenia.

### 4.4 Ako s tým ladiť

1. Pár hodín je vzorka jedna. Jedlo môže dopadnúť zle kvôli sacharidom, prechádzke, neskorému
   bolusu alebo senzoru. Nemeň hodnotu kvôli jednému zlému času. Pozri sa na tú istú časť dňa aj
   v ďalšie dni a zmeň niečo, len keď sa to isté opakuje.
2. Hodiny zvoľ tak, aby sa zmestili do jedného slotu: pri obedňajšom slote 11:30–14:00 pozri 3
   hodiny okolo 14:30. Dlhší čas mieša sloty a vtedy návrh nie je.
3. Použi jeden návrh a sleduj ďalšie dni.
4. Hypo vždy vyhrávajú nad hyper: slot s hypo dostane najprv menej inzulínu, aj keď má aj vysoké.
5. Nie sú tu štatistiky z viacerých dní, pohľad na celý deň ani história zmien. Zapisuj si, čo si
   zmenil (log má riadok aj pri každom Použiť).

## 5. Štartovacie hodnoty

Tabuľka ukazuje globálne hodnoty autorovho vlastného nastavenia, prečítané z jeho logu z 27.10.2026
(14:09), vedľa predvolených hodnôt AutoISF. Sú to **hodnoty jedného človeka**, nie odporúčanie pre
kohokoľvek iného; ide o to, aby sloty štartovali z rovnakých čísel a líšili sa len tam, kde to povie
analýza.

| Nastavenie | Autor (globál) | Default AutoISF | Poznámka |
|---|---|---|---|
| autoISF_max | 1,6 | 1,0 (vypnuté) | 1,6 je už silné: ISF môže byť o 60 % silnejší. |
| autoISF_min | 0,6 | 1,0 (vypnuté) | ISF sa pri klesaní môže oslabiť na 60 %. |
| bgAccel_ISF_weight | 0,3 | 0,0 | Reaguje na rýchly rast. 27.10.2026 acce_ISF narazil na strop 1,6. |
| bgBrake_ISF_weight | 0,08 | 0,0 | Brzda pri rýchlom poklese. |
| pp_ISF_weight | 0,09 | 0,0 | Postprandiálny rast. |
| lower_ISFrange_weight | 0,3 | 0,0 | Pod cieľom: slabší ISF. |
| higher_ISFrange_weight | 0,6 | 0,0 | Nad cieľom: silnejší ISF. |
| dura_ISF_weight | 0,03 | 0,0 | Dlhé plató nad cieľom. |
| Blended BG ISF weight | nastav ručne | 0,0 | Len vo forku (kapitola 1). Opatrný štart 0,1–0,2. |
| iob_threshold_percent | 90 % | 100 % | Nad týmto podielom max IOB žiadny SMB. |
| smb_delivery_ratio | 0,5 | 0,5 | |
| smb_delivery_ratio_min | 0,6 | 0,5 | |
| smb_delivery_ratio_max | 0,8 | 0,5 | 0,8 = až 80 % potrebného inzulínu v jednom SMB. |
| smb_delivery_ratio_bg_range | 0,25 | 0,0 | V mmol/l, ak je pod 10. |
| smb_max_range_extension | 5,0 | 1,0 | S párnym cieľom dovolí SMB 5 × 30 min bazálu (asi 1 U pri 0,39 U/h). Toto urobilo SMB 0,95 U 27.10.2026. |
| SMB pri párnom cieli | zapnuté | vypnuté | Spolu s riadkom vyššie. |

Z toho istého logu pre kontext: max IOB 6 U, max bazál 1,9 U/h (≈ 5 × bazál 0,39), autosens max 1,6,
maxSMBBasalMinutes 30, UAM 30, cieľ 6,2 mmol/l, ISF 2,27 mmol/l/U, CR 12 g/U.

Štartovacie hodnoty slotov, kým analýza nepovie inak:

| Slot | Začni s |
|---|---|
| Raňajky, Obed, Večera (pracovný deň) | hodnoty, ktoré si naladil; ostávajú ako sú |
| … (víkend) | Copy weekday slots to weekend |
| Noc | Copy global values to the night slot, potom miernejšie: `autoISF_max` 1,4, `bgAccel_ISF_weight` 0,15, `smb_delivery_ratio` 0,4 / min 0,5 / max 0,7, `smb_max_range_extension` 3. Spíš, takže loop má mať menej priestoru, nie viac. |

## 6. Čo je v pláne

1. Nočné metriky v analýze: minimum BG, priebeh 23:00 → 03:00 → ráno a jasná hranica medzi „zlý bazál
   v profile“ a „treba zmeniť hodnotu AutoISF“.
2. Návrhy per slot z malej tabuľky pravidiel (vrchol, čas do vrcholu, hypo skoro / neskoro),
   s históriou a „Vrátiť“.
3. Automatické malé zmeny s tvrdými stropmi v kóde, po dostatočnom počte jedál (3 z posledných 5),
   s globálnym vypínačom. Víkendové sloty až od 10 jedál.
4. Voliteľné „Vysvetli cez Claude“: appka pošle len zhrnutie a návrh, nikdy surové logy, a model nikdy
   nerozhoduje o dávke.
5. Tep z hodiniek ako druhý signál aktivity (kroky už sú v kapitole 8), ručný režim
   stres / choroba s vlastnými 15 hodnotami.

## 7. Pre vývojárov

- `plugins/aps/.../openAPSAutoISF/BlendedBg.kt` – kapitola 1. `MealWindows.kt` – okná, „cez polnoc“.
- `AutoIsfSlots.kt` – sloty, výber, režim sviatok/dovolenka a kopírovacie tlačidlá. `AutoIsfSlotsTest`
  pokrýva pravidlá výberu.
- Kľúče: `autoISF_slot_<slot>_<hodnota>` v `DoubleKey` / `IntKey`, okná `autoISF_meal_window_*` v
  `StringKey`, prepínače v `BooleanKey`. Víkendové kľúče majú `_we_`, nočné `_night_`. Tri pracovné
  sloty majú kľúče z fázy 1, takže update nepotrebuje migráciu.
- `logAnalysis/AutoIsfLogParser.kt` – jeden beh na riadok `Result: RT(...)`, nastavenia z riadku
  `OapsProfileAutoIsf(...)`. `AutoIsfLogAnalyzer.kt` – štatistiky, udalosti a tabuľka pravidiel
  z 4.3. `AutoIsfRecentAnalyzer.kt` – čísla posledných hodín. `AutoIsfLogAnalysis.kt` – analýza
  a „Použiť“.
- Unit testy modulu APS bežia vo workflow „AutoISF APK“ s `runTests = true`. Snímka schémy
  preferencií (`app/src/testFull/resources/prefs-schema.txt`) sa udržiava ručne, zoradená.

## 8. Kroky a aktivita

Päť prepínačov v AutoISF settings → **Steps and activity**. Všetky sú vypnuté; vypnuté = ako
upstream. Pravidlá sú prenesené z OpenAPS AIMI (MTR93600/OpenApsAIMI, AGPL-3.0) a **len uberajú**:
nikdy nepridajú SMB ani bazál. Bez údajov o krokoch nič nezasiahne.

| Prepínač | Čo robí |
|---|---|
| Use the phone as step source | Vyberie **jeden** zdroj pre všetky pravidlá krokov, nikdy zmes. **Zapnuté:** len krokomer telefónu. Telefón ukladá kroky do databázy v rovnakej forme ako hodinky (riadky 5/10/15/30/60/180 min, každú minútu), takže funguje aj upstream automatizácia „Steps count“ bez hodiniek. Potrebuje povolenie **Fyzická aktivita** – appka si ho vypýta v hárku povolení. **Vypnuté:** len kroky, ktoré posielajú hodinky. Ak vybraný zdroj nemá čerstvé údaje (mladšie ako 15 minút), pravidlá krokov nerobia nič; ticho sa neprepne na druhý zdroj. Hodinky posielajú 10- a 15-minútovú hodnotu ako samostatné 5-minútové bloky; appka bloky sčíta, takže oba zdroje sa čítajú rovnako. Hodinky počítajú dokončené 5-minútové bloky, preto ich najnovšia hodnota môže byť až 5 minút stará. |
| Sport guard | Tri úrovne podľa krokov (mierna, guard, dlhá). Počas pohybu: temp bazál **0 %**, SMB 0 (mierna: SMB × 0,8). Po zastavení: krátke pridržanie, potom sa SMB a temp bazál pomaly vracajú na 100 % (30 až 120 minút). Hranice krokov pre úrovne sú päť nastavení pod prepínačom (pozri 8.1). |
| Longer SMB interval after activity | Po > 1500 krokoch za 3 h a BG < 120 mg/dl (6,7) aspoň 10 min medzi SMB; kým sa ešte hýbeš (> 100/5 min a > 500/30 min, posledný SMB starší než 20 min) aspoň 15 min. |
| Morning: SMB only after waking | Medzi začiatkom a koncom rannej fázy (default 03:00–10:00) **žiadne SMB, kým sa nenapočíta N krokov za hodinu** (default 150, nastaviteľné). Kým spíš, dawn rise koriguje len temp bazál (ako pri vypnutom SMB). Zobudenie je latch: raz otvorená brána ostane otvorená do konca fázy, aj keď sedíš pri raňajkách. Mimo fázy sa latch resetuje. Bez údajov o krokoch neblokuje nič. Kroky nerozlíšia dawn od jedla – rozlíšia spánok od bdenia; po zobudení rozhoduje tvar rastu (acce_ISF) a deklarácia jedla. |
| Effort protection | Od 25 krokov/min je SMB menšie, pri svižnej chôdzi (40/min a viac) až ×0,45. Po skončení chôdze ochrana doznieva 30 min (krátka prechádzka) až 120 min (30+ min chôdze). Krátky „výbeh“ (len 5-min okno) zmenší SMB teraz, ale nespustí dlhú ochranu – na to treba 15-min okno. |

V logu loopu je riadok `steps: steps (watch) 5m=… 10m=… 30m=… 60m=… 180m=…; …` (`phone`, ak je zdrojom telefón; všetky okná sú súčty) a v dôvode (reason) sú texty
`afterglow …` a `sport guard: …` (pozri 8.1), `effort x0.62: SMB 0.80 -> 0.40U` alebo `Waiting …`.

Bezpečný štart: najprv len zdroj krokov (prepínač **Use the phone as step source**), pozri sa deň-dva na graf krokov a na
riadok `steps:` v logu. Potom **Effort protection** (najmiernejšie, plynulé), potom **Sport guard**.
Upstream automatizácia s triggerom „Steps count“ + akciou „Start temp target“ nahrádza AIMI pravidlo
„cieľ 130 pri aktivite“, preto také pravidlo tu nie je.

Pre vývojárov: `openAPSAutoISF/steps/` – `StepBuckets.kt` (minútové koše, sumy okien),
`PhoneStepCounter.kt` (rozhranie; `AndroidPhoneStepCounter` v androidMain, iOS a desktop vracajú
„nedostupné“), `AutoIsfSteps.kt` (ukladanie do DB, čítanie, pamäť), `StepsActivityRules.kt`,
`EffortActivityBelief.kt` a `WakeGate.kt` (čisté pravidlá, testy v `commonTest`). `determine_basal` dostáva
`steps_activity: StepsActivity`. Kľúče `autoISF_steps_*` v `BooleanKey` a `IntKey`. `AutoIsfSteps.windows` vyberá zdroj (`StepSource`) a mení bloky z hodiniek na súčty (`StepWindows.fromWatch`). Sport guard je `ActivityAfterglow.kt`
(čistý, pamäť drží `AutoIsfSteps`); limit temp bazálu beží po výpočte dávky v
`DetermineBasalAutoISF.limitBasalBySteps`.

### 8.1 Sport guard podrobne

Starý guard sa uvoľnil hneď, keď bolo 5 minút 0 krokov, a dávkovanie skočilo späť na 100 %. Teraz
má tri úrovne a pomalý návrat.

Hranice krokov sú nastavenia pod **Sport guard** (čísla nižšie sú predvolené). Každé má povolený rozsah,
takže preklep guard nevypne. Vyššia hranica spustí guard neskôr, teda je menej ochrany. Úrovne sa
kontrolujú od najvyššej; hranica nastavená pod nižšou úrovňou spôsobí len to, že sa nižšia úroveň
preskočí. Rýchly nástup (400 za 5 min a 800 za 10 min) a test „hýbe sa teraz“ (100 krokov za 5 min,
25 krokov/min za 15 min) sú pevné.

| Nastavenie | Predvolené | Rozsah |
|---|---|---|
| Mild: kroky za 60 min | 2500 | 1000 – 5000 |
| Strong: kroky za 30 min | 2000 | 500 – 4000 |
| Strong: kroky za 60 min | 4000 | 1000 – 8000 |
| Long: kroky za 60 min | 6000 | 2000 – 12000 |
| Long: kroky za 180 min | 7000 | 2000 – 20000 |

| Úroveň | Začína pri (sumy krokov) | Počas pohybu | Po zastavení |
|---|---|---|---|
| Mierna | ≥ 2500 krokov za 60 min | bazál 0 %, SMB × 0,8 | bez pridržania; SMB a bazál z 80 % na 100 % za 30 min |
| Guard | ≥ 2000 za 30 min, ≥ 4000 za 60 min, alebo ≥ 400 za 5 min **a** ≥ 800 za 10 min | bazál 0 %, SMB 0 | 10 min pridržanie (bazál 0 %, SMB 0), potom 50 % na 100 % za 60 min |
| Dlhá | ≥ 6000 za 60 min alebo ≥ 7000 za 180 min | bazál 0 %, SMB 0 | 10 min pridržanie, potom 50 % na 100 % za 120 min |

„Bazál 0 %“ je nulový temp bazál. Pri návrate „bazál 50 %“ znamená, že temp bazál nesmie byť nad
50 % bazálu z profilu. Nižší temp ostane, ako je. Limit bazál len zmenšuje.

Príklad, 40-minútová prechádzka (úroveň guard), posledný krok o 12:00:
12:00–12:10 pridržanie, bez bazálu a bez SMB · 12:10 SMB a bazál na 50 % · 12:40 na 75 % · 13:10 späť
na 100 %.

Dobré vedieť:
- **Ako sa vyberie úroveň:** úroveň sa určí v čase pohybu a drží sa v pamäti. Po prechádzke ostanú
  60- a 180-minútové sumy vysoké celé hodiny, preto sa na návrat nepoužívajú. Nová úroveň počas
  jedného návratu môže byť len vyššia.
- **Pohyb alebo nie:** „hýbeš sa teraz“ vyžaduje aspoň 100 krokov za posledných 5 minút **a** reálne
  tempo 25 krokov/min za 15 minút. Platí to rovnako pri novom štarte aj pri chôdzi, ktorá pokračuje
  alebo sa obnoví počas návratu. Nový štart navyše potrebuje úroveň zo súm. Pár krokov doma (kuchyňa,
  WC) pridržanie nikdy nespustí, ani keď sú 60- a 180-minútové sumy ešte vysoké po dávnej prechádzke.
- **Vysoká a stúpajúca glykémia:** nad 10 mmol/l (180 mg/dl) a stúpa → návrat je dvakrát kratší,
  aby sa jedlo neblokovalo hodinu.
- **Nízka alebo klesajúca glykémia:** pod 4,5 mmol/l (81 mg/dl) alebo pokles 5 mg/dl za 5 minút
  a viac → SMB ostáva počas návratu na 0. Bazál ide ďalej podľa rampy.
- **S Effort protection:** platí prísnejší faktor SMB z oboch. Nenásobia sa.
- **Zlé dáta z CGM:** ak beh vzdá (staré dáta, šum, rovná čiara), guard nenastaví nulový temp.
  Loop pri zlých dátach nikdy nezastaví inzulín.
- **Pamäť:** je len v appke. Po reštarte appky nie je žiadny návrat, guard sa spustí znova pri
  ďalšej chôdzi.
- **Bez údajov o krokoch:** guard nerobí nič a dávkovanie je na 100 %, aj v strede chôdze, keď
  senzor v telefóne prestane počítať. Pamäť sa počas medzery zachová.
- **Bez úniku pri vysokej glykémii počas pohybu:** počas pohybu a pridržania ostáva temp bazál na 0 %
  a SMB na 0, aj keď je glykémia vysoká. Pravidlo „vysoká a stúpajúca“ platí až pri návrate. Pri
  dlhej chôdzi alebo behu s vysokým štartom to sleduj.
- Čísla sú v kóde (`ActivityAfterglow.kt`), nie v nastaveniach. Ak bude po pár dňoch logu niektoré
  príliš silné alebo slabé, dáme to upraviť.

V logu (reason loopu):
`afterglow GUARD: moving, basal 0%, SMB 0` · `afterglow GUARD: hold 4/10m, basal 0%, SMB 0` ·
`afterglow GUARD: taper 20/60m, x0.67` (+ `high and rising, half length` alebo `low or falling, SMB 0`) ·
`sport guard: temp basal 1.2 -> 0U/hr` (limit bazálu zmenil temp) · `sport guard: no SMB`.
