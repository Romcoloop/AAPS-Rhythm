# AutoISF fork: blended BG, časy jedál, sloty a ladiaca karta

Tento fork AndroidAPS (github.com/Romcoloop/AndroidAPS) mení plugin **OpenAPS AutoISF** na štyroch
miestach. Všetko ostatné je upstream AndroidAPS.

| Čo | Kde v appke | Vypnuté znamená |
|---|---|---|
| 1. Blended BG pre bg_ISF | AutoISF settings → Blended BG ISF weight | váha 0,0 = ako upstream |
| 2. Časy jedál a „faktory mimo jedla“ | AutoISF settings | žiadny čas jedla = ako upstream |
| 3. Sloty: vlastné hodnoty pre jedlo, víkend a noc | Meal slot settings + sekcie slotov | prepínač vypnutý = ako upstream |
| 4. AutoISF log analysis (ladiaca karta) | karta na obrazovke pluginu AutoISF | len číta; zapisuje po „Použiť“ |
| 5. Kroky a aktivita (vetva `autoisf-steps`) | AutoISF settings → Steps and activity | všetky 4 prepínače vypnuté = ako upstream |

Vetvy: `autoisf-custom` (1, 2, 4) → `autoisf-slots` (3, fáza 1) → `autoisf-slots-2` (víkend, noc)
→ `autoisf-analysis` (všetko, plus 4 per slot) → `autoisf-steps` (5, kroky). **Inštaluj buildy
z `autoisf-analysis`**, alebo z `autoisf-steps`, ak chceš kroky.
Build: GitHub Actions workflow „AutoISF APK“ (APK pre telefón aj hodinky; `runTests = true` najprv
spustí unit testy modulu APS).

> **Pred inštaláciou nového APK:** Maintenance → Export settings.
> Nič tu nemení hodnotu samo. Každú hodnotu nastavuješ ty: v nastaveniach alebo tlačidlom „Použiť“
> v karte analýzy po potvrdení.
> Ide o dávkovanie inzulínu. Meň jednu hodnotu naraz, po malých krokoch, a konzultuj so svojím
> diabetologickým tímom.

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

## 2. Časy jedál a faktor mimo jedla

Tri okná v AutoISF settings: Breakfast / Lunch / Dinner time, zapísané ako `HH:MM-HH:MM`. Vo vnútri
okna pracujú faktory jedla (`bgBlend`, `pp_ISF` a `acce_ISF` nad cieľom) s plnou váhou. Mimo všetkých
okien sa násobia hodnotou **Meal factors outside meal times** (1,0 = bez zmeny, 0,5 = polovica,
0,0 = vypnuté). Brzdenie pri klesaní BG sa nikdy neznižuje. Bez nastaveného okna sa nič nemení.

Tie isté okná sú oknami pracovných slotov jedál (kapitola 3). V sobotu, nedeľu a v režime
sviatok/dovolenka sa namiesto nich použijú víkendové časy jedál, ak sú zapnuté víkendové sloty.

## 3. Sloty

Slot je časové okno s vlastnou kópiou 15 čísel AutoISF: 9 hodnôt AutoISF, IOB threshold a 5 hodnôt
SMB delivery. Vo vnútri okna loop používa hodnoty slotu, mimo všetkých okien globálne hodnoty.
Prepínače AutoISF (SMB, UAM, autosens, …) ostávajú globálne.

### 3.1 Sedem slotov

| Slot | Platí | Okno sa nastavuje v |
|---|---|---|
| Raňajky, Obed, Večera | pondelok – piatok | AutoISF settings → Breakfast / Lunch / Dinner time |
| Raňajky, Obed, Večera (víkend) | sobota, nedeľa, režim sviatok/dovolenka | Meal slot settings → … time (weekend) |
| Noc | každý deň | Meal slot settings → Night time |

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

Všetko v nastaveniach pluginu OpenAPS AutoISF.

**Meal slot settings**:

- Use meal slot settings
- Night time
- Separate weekend slots
- Breakfast / Lunch / Dinner time (weekend) – vidno, kým sú víkendové sloty zapnuté
- Holiday or vacation a „Holiday or vacation ends on“ (`RRRR-MM-DD`). S dátumom sa prepínač sám vypne
  deň po tom dátume. Bez dátumu ho vypínaš ručne.
- **Copy global values to all slots** – zapíše globálne hodnoty do **všetkých siedmich** slotov.
  Hodnoty, ktoré už v slotoch boli, sa stratia. Dobré raz, úplne na začiatku.
- **Copy weekday slots to weekend** – zapíše hodnoty pracovných raňajok / obeda / večere do
  víkendových slotov. Pracovné sloty sa nemenia. Okná sa nikdy nekopírujú.

**Breakfast slot, Lunch slot, Dinner slot, Night slot, … slot (weekend)**: 15 hodnôt každého slotu.
Sekcia je skrytá, kým je jej prepínač vypnutý.

### 3.3 Prvé nastavenie v bezpečnom poradí

Nový slot začína s továrenskými hodnotami (`autoISF_max` 1,0, všetky váhy 0,0). Taký slot by bežal
takmer bez AutoISF. Preto slot najprv **naplň** a až potom mu daj okno.

1. Use meal slot settings → zapnúť.
2. Ak sú pracovné sloty ešte rovné globálnym hodnotám: **Copy global values to all slots**.
   Ak si pracovné sloty už ladil: **nestláčaj** ho. Použi **Copy weekday slots to weekend** a nočný
   slot naplň ručne (hodnoty sú v kapitole 5).
3. Nastav pracovné časy jedál (AutoISF settings), ak ešte nie sú.
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
zazipované) a ukazuje, čo AutoISF robil. Sama nikdy žiadnu hodnotu nemení.

### 4.1 Ovládanie

- **Obdobie**: 1 / 3 / 7 dní. Víkendový slot ponúka namiesto toho 14 / 28 dní, lebo vidí len dve
  jedlá za týždeň.
- **Slot**: *Všetko* alebo jeden zo siedmich slotov.
  - *Všetko* = celý deň, globálne hodnoty. „Použiť“ zapisuje do globálnej hodnoty.
  - *Jeden slot* = len jedlá toho slotu: behy jeho okna plus 3 hodiny po ňom, jedno jedlo za deň.
    Jedlo sa počíta do slotu, len keď ho riadky logu z toho dňa menujú – raňajkové okno v sobotu sa
    teda nepomieša do pracovných raňajok. „Použiť“ zapisuje do hodnoty toho slotu. Vypnutý slot
    návrhy nedostane.
- **Analyze** to spustí. Výsledok ostáva na obrazovke, kým nezmeníš slot.

### 4.2 Čo ukazuje

**Súhrn** (celé obdobie alebo jedlá slotu): pokryté hodiny, počet behov, čas pod 3,0 / pod 3,9 /
v rozsahu 3,9–10 / nad 10 mmol/l, počet a jednotky SMB, ako často `autoISF_max` orezal faktor a ako
často bol SMB delivery ratio na maxime, ktorý faktor najčastejšie riadil ISF.

**Jedlá** (len pri jednom slote): za jedlo BG na začiatku, vrchol nad cieľom a minúty do vrcholu,
minúty nad cieľom +2 a +4 mmol/l, minúty pod 3,9, BG na konci, počet a jednotky SMB, ako často
`autoISF_max` orezal faktor, ako často bol SMB taký veľký, ako dovolil maxBolus, faktor, ktorý
najčastejšie riadil ISF. Jedlo s temp targetom alebo také, ktoré log nepokrýva celé, sa zobrazí, ale
do štatistiky sa nepoužije. Riadok štatistiky ukazuje **n = použité jedlá**.

**Udalosti**: každý SMB, po ktorom do 3 hodín prišla hypo (pod 3,9) alebo predikovaná hypo, ktorá
zastavila SMB (`minGuardBG`), s faktorom, ktorý ten SMB riadil.

### 4.3 Ako vzniká návrh

Pravidlá, žiadny model. Z menej než 12 hodín behov s rovnakými nastaveniami (6 hodín pri jednom
slote) sa nenavrhne nič a zmena nastavenia začína nové obdobie.

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

Každý návrh uvádza počet udalostí, hodnotu pred a po a jeden dôvod. **Použiť** zapíše po potvrdení
a odmietne, ak sa hodnota medzitým zmenila. Hodnoty sú orezané na limity nastavenia.

### 4.4 Ako s tým ladiť

1. Začni s *Všetko* za 7 dní: ukáže, či sú globálne hodnoty bezpečné (hypo), skôr než čokoľvek per
   slot.
2. Potom slot po slote, za 7 dní (víkend 14–28). Sleduj **n**: menej než asi 5 jedál nie je vzor.
   Použi najviac jeden návrh na slot a počkaj na ďalšie jedlá.
3. Hypo vždy vyhrávajú nad hyper: slot s hypo dostane najprv menej inzulínu, aj keď má aj vysoké.
4. Nočný slot je v zozname, ale zatiaľ sa hodnotí ako jedlo. Vlastné nočné metriky (minimum BG,
   priebeh 23:00 → 03:00 → ráno, bazál v profile vs. hodnota AutoISF) sú ďalší krok.

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
| Noc | globálne hodnoty, ale miernejšie: `autoISF_max` 1,4, `bgAccel_ISF_weight` 0,15, `smb_delivery_ratio` 0,4 / min 0,5 / max 0,7, `smb_max_range_extension` 3. Spíš, takže loop má mať menej priestoru, nie viac. |

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

- `plugins/aps/.../openAPSAutoISF/BlendedBg.kt` – kapitola 1. `MealWindows.kt` – okná, „cez polnoc“,
  faktor mimo jedla.
- `AutoIsfSlots.kt` – sloty, výber, režim sviatok/dovolenka a kopírovacie tlačidlá. `AutoIsfSlotsTest`
  pokrýva pravidlá výberu.
- Kľúče: `autoISF_slot_<slot>_<hodnota>` v `DoubleKey` / `IntKey`, okná `autoISF_meal_window_*` v
  `StringKey`, prepínače v `BooleanKey`. Víkendové kľúče majú `_we_`, nočné `_night_`. Tri pracovné
  sloty majú kľúče z fázy 1, takže update nepotrebuje migráciu.
- `logAnalysis/AutoIsfLogParser.kt` – jeden beh na riadok `Result: RT(...)`, nastavenia z riadku
  `OapsProfileAutoIsf(...)`. `AutoIsfLogAnalyzer.kt` – štatistiky, udalosti a tabuľka pravidiel
  z 4.3. `AutoIsfMealAnalyzer.kt` – jedno jedlo. `AutoIsfLogAnalysis.kt` – analýza per slot
  a „Použiť“.
- Unit testy modulu APS bežia vo workflow „AutoISF APK“ s `runTests = true`. Snímka schémy
  preferencií (`app/src/testFull/resources/prefs-schema.txt`) sa udržiava ručne, zoradená.

## 8. Kroky a aktivita (vetva `autoisf-steps`)

Štyri prepínače v AutoISF settings → **Steps and activity**. Všetky sú vypnuté; vypnuté = ako
upstream. Pravidlá sú prenesené z OpenAPS AIMI (MTR93600/OpenApsAIMI, AGPL-3.0) a **len uberajú**:
nikdy nepridajú SMB ani bazál. Bez údajov o krokoch nič nezasiahne.

| Prepínač | Čo robí |
|---|---|
| Count steps with the phone | Číta krokomer telefónu a ukladá kroky do databázy v rovnakej forme ako hodinky (riadky 5/10/15/30/60/180 min, každú minútu). Potrebuje povolenie **Fyzická aktivita** – appka si ho vypýta v hárku povolení. Vďaka tomu funguje aj upstream automatizácia „Steps count“ bez hodiniek. Kroky z pripojených hodiniek sa použijú tiež (telefón má prednosť, ak beží). |
| Sport guard | Pri dávke krokov (≥ 400 za 5 min **a** ≥ 800 za 10 min) alebo trvalej aktivite (≥ 1200/30 min, ≥ 3000/60 min alebo ≥ 4500/180 min) **žiadne SMB** a temp bazál najviac na úrovni profilu. Uvoľní sa hneď, keď je 5 minút 0 krokov – napríklad po prechádzke pri rastúcom jedle. |
| Longer SMB interval after activity | Po > 1500 krokoch za 3 h a BG < 120 mg/dl (6,7) aspoň 10 min medzi SMB; kým sa ešte hýbeš (> 100/5 min a > 500/30 min, posledný SMB starší než 20 min) aspoň 15 min. |
| Effort protection | Od 25 krokov/min je SMB menšie, pri svižnej chôdzi (40/min a viac) až ×0,45. Po skončení chôdze ochrana doznieva 30 min (krátka prechádzka) až 120 min (30+ min chôdze). Krátky „výbeh“ (len 5-min okno) zmenší SMB teraz, ale nespustí dlhú ochranu – na to treba 15-min okno. |

V logu loopu je riadok `steps: 5m=… 10m=… 30m=… 60m=… 180m=…; …` a v dôvode (reason) sú texty
`sport guard: no SMB`, `effort x0.62: SMB 0.80 -> 0.40U` alebo `Waiting …`.

Bezpečný štart: najprv len **Count steps with the phone**, pozri sa deň-dva na graf krokov a na
riadok `steps:` v logu. Potom **Effort protection** (najmiernejšie, plynulé), potom **Sport guard**.
Upstream automatizácia s triggerom „Steps count“ + akciou „Start temp target“ nahrádza AIMI pravidlo
„cieľ 130 pri aktivite“, preto také pravidlo tu nie je.

Pre vývojárov: `openAPSAutoISF/steps/` – `StepBuckets.kt` (minútové koše, sumy okien),
`PhoneStepCounter.kt` (rozhranie; `AndroidPhoneStepCounter` v androidMain, iOS a desktop vracajú
„nedostupné“), `AutoIsfSteps.kt` (ukladanie do DB, čítanie, pamäť), `StepsActivityRules.kt`
a `EffortActivityBelief.kt` (čisté pravidlá, testy v `commonTest`). `determine_basal` dostáva
`steps_activity: StepsActivity`. Kľúče `autoISF_steps_*` v `BooleanKey`.
