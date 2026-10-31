# AutoISF sloty: vlastné nastavenia pre jedlá, víkend a noc

Tento fork AndroidAPS pridáva do algoritmu AutoISF **sloty**. Slot je časové okno s vlastnou kópiou
hodnôt AutoISF a SMB delivery. Vo vnútri okna loop používa hodnoty slotu, mimo všetkých okien
globálne hodnoty – presne ako doteraz.

Vetvy: `autoisf-custom` (základ), `autoisf-slots` (fáza 1), `autoisf-slots-2` (víkend a noc),
`autoisf-analysis` (sloty + karta analýzy logov). Build: GitHub Actions workflow „AutoISF APK“.

> **Pred inštaláciou nového APK:** Maintenance → Export settings. Nič v tejto funkcii nemení hodnotu
> samo. Každú hodnotu nastavuješ ty – v nastaveniach alebo tlačidlom „Použiť“ v karte analýzy po
> potvrdení.

## 1. Ako sa vyberá slot

Slotov je sedem:

| Slot | Platí | Okno sa nastavuje v |
|---|---|---|
| Raňajky, Obed, Večera | pondelok – piatok | AutoISF settings → Breakfast / Lunch / Dinner time |
| Raňajky, Obed, Večera (víkend) | sobota, nedeľa, režim sviatok/dovolenka | Meal slot settings → … time (weekend) |
| Noc | každý deň | Meal slot settings → Night time |

Pravidlá:

- Slot sa vyberá **len podľa hodín**, raz na začiatku každého behu loopu. Zadanie sacharidov slot
  nemení.
- Okno sa píše ako `HH:MM-HH:MM`, napríklad `11:00-14:00`. Noc môže ísť cez polnoc, napríklad
  `23:00-05:30`. Koniec sa nepočíta: o `08:00` je raňajkové okno `05:30-08:00` už skončené.
- Prázdne okno = slot sa nepoužíva.
- Kde sa prekrýva okno jedla s nočným, **vyhráva jedlo**. Kde sa prekrývajú dve jedlá, vyhráva prvé v
  poradí raňajky → obed → večera.
- Sobota a nedeľa používajú víkendové sloty. V pracovný deň sa víkendové sloty použijú, kým je zapnutý
  prepínač **Holiday or vacation**.
- Per slot sú len čísla: 9 hodnôt AutoISF, IOB threshold a 5 hodnôt SMB delivery (spolu 15).
  Prepínače AutoISF (SMB, UAM, autosens, …) ostávajú globálne.

Prepínače a čo znamená „vypnuté“:

| Prepínač | Vypnuté |
|---|---|
| Use meal slot settings | Správanie presne ako bez slotov: všade globálne hodnoty. |
| Separate weekend slots | Pracovné sloty a pracovné časy jedál platia každý deň. |
| Holiday or vacation | Víkendové sloty len v sobotu a nedeľu. |

Časy jedál riadia aj starší faktor „Meal factors outside meal times“. Cez víkend (alebo v režime
sviatok/dovolenka pri zapnutých víkendových slotoch) tento faktor používa víkendové časy jedál.

## 2. Kde sú nastavenia

Všetko je v nastaveniach pluginu **OpenAPS AutoISF**.

**AutoISF settings** (bez zmeny): globálne hodnoty, pracovné Breakfast / Lunch / Dinner time a
„Meal factors outside meal times“.

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

## 3. Prvé nastavenie v bezpečnom poradí

Nový slot začína s továrenskými hodnotami (autoISF_max 1,0, všetky váhy 0,0). Taký slot by bežal
takmer bez AutoISF. Preto slot najprv **naplň** a až potom mu daj okno.

1. Use meal slot settings → zapnúť.
2. Ak sú pracovné sloty ešte rovné globálnym hodnotám: **Copy global values to all slots**.
   Ak si pracovné sloty už ladil: **nestláčaj** ho. Použi **Copy weekday slots to weekend** a nočný
   slot naplň ručne 15 globálnymi hodnotami.
3. Nastav pracovné časy jedál (AutoISF settings), ak ešte nie sú.
4. Separate weekend slots → zapnúť, nastaviť víkendové časy, napríklad `07:00-10:00`, `12:00-15:00`,
   `18:00-21:00`.
5. Night time, napríklad `23:00-05:30` – až keď má nočný slot hodnoty.
6. Pár dní hodnoty nemeň. Ide o to, aby sa najprv v logu objavili riadky s názvami slotov, aby ich
   analýza vedela porovnať.

## 4. Kontrola, čo loop urobil

Každý beh zapíše jeden riadok do debug výpisu AutoISF (tab OpenAPS AutoISF a log súbory):

```
AutoISF slot: Dinner-weekend (18:00-21:00, weekend)
AutoISF slot: Night (23:00-05:30, weekday)
AutoISF slot: none, global values (holiday)
```

Prvé slovo za `slot:` je slot (`Breakfast`, `Lunch`, `Dinner`, `Breakfast-weekend`, …, `Night`
alebo `none`). V zátvorke: okno a druh dňa (`weekday`, `weekend` alebo `holiday`). `holiday`
znamená, že bol zapnutý „Holiday or vacation“.

## 5. Karta analýzy logov

Karta **AutoISF log analysis** na obrazovke pluginu číta log súbory v telefóne a ukazuje, čo AutoISF
robil.

- **Obdobie**: 1 / 3 / 7 dní. Víkendový slot ponúka namiesto toho 14 / 28 dní, lebo vidí len dve
  jedlá za týždeň.
- **Slot**: Všetko alebo jeden zo siedmich slotov.
  - *Všetko* = celý deň, globálne hodnoty, ako doteraz. „Použiť“ zapisuje do globálnej hodnoty.
  - *Jeden slot* = len jedlá toho slotu: behy jeho okna plus asi 3 hodiny po ňom, jedno jedlo za deň.
    Jedlo sa počíta do slotu, len keď ho riadky logu z toho dňa menujú – raňajkové okno v sobotu sa
    teda nepomieša do pracovných raňajok. „Použiť“ zapisuje do hodnoty toho slotu.
- Za jedlo: BG na začiatku, vrchol nad cieľom a minúty do vrcholu, minúty nad cieľom +2 a +4 mmol/l,
  minúty pod 3,9, BG na konci, počet a jednotky SMB, ako často autoISF_max orezal faktor, ako často
  bol SMB taký veľký, ako dovolil maxBolus, ktorý faktor najčastejšie riadil ISF.
- Jedlo s temp targetom alebo také, ktoré log nepokrýva celé, sa zobrazí, ale nepoužije.
- Návrhy sú malé kroky a nikdy sa nezapíšu samy. Vypnutý slot (vypnuté meal slot settings, alebo
  víkendový slot pri vypnutých víkendových slotoch) návrhy nedostane, lebo loop by zmenu nepoužil.
- Nočný slot je v zozname, ale zatiaľ sa hodnotí ako jedlo (vrchol, čas do vrcholu, …). Vlastné nočné
  metriky sú ďalší krok.

## 6. Čo je v pláne

1. Nočné metriky v analýze: minimum BG, priebeh 23:00 → 03:00 → ráno a jasná hranica medzi „zlý bazál
   v profile“ a „treba zmeniť hodnotu AutoISF“.
2. Návrhy per slot z malej tabuľky pravidiel, s históriou a „Vrátiť“.
3. Automatické malé zmeny s tvrdými stropmi v kóde, po dostatočnom počte jedál, s globálnym vypínačom.
4. Voliteľné „Vysvetli cez Claude“: appka pošle len zhrnutie a návrh, nikdy surové logy, a model nikdy
   nerozhoduje o dávke.

## 7. Pre vývojárov

- `plugins/aps/.../openAPSAutoISF/AutoIsfSlots.kt` – sloty, výber, režim sviatok/dovolenka a
  kopírovacie tlačidlá. `AutoIsfSlotsTest` pokrýva pravidlá výberu.
- `MealWindows.kt` – parsovanie textu okna a „cez polnoc“.
- Kľúče: `autoISF_slot_<slot>_<hodnota>` v `DoubleKey` / `IntKey`, okná `autoISF_meal_window_*` v
  `StringKey`, prepínače v `BooleanKey`. Víkendové kľúče majú `_we_`, nočné `_night_`. Tri pracovné
  sloty majú kľúče z fázy 1, takže update nepotrebuje migráciu.
- `logAnalysis/AutoIsfLogAnalysis.kt` – analýza per slot, `AutoIsfMealAnalyzer.kt` – jedno jedlo.
- Unit testy modulu APS bežia vo workflow „AutoISF APK“ s `runTests = true`.
