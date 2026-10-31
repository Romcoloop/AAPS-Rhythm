# This fork: AutoISF with slots and a tuning card

This is a fork of AndroidAPS with four changes to the **OpenAPS AutoISF** plugin: blended BG for
bg_ISF, meal times with a factor outside meal times, **slots** (own AutoISF and SMB values for each
meal, for the weekend and for the night) and an **AutoISF log analysis** card that suggests settings
from the phone's logs. Nothing changes a value on its own.

* **Manual:** [docs/autoisf-fork.md](https://github.com/Romcoloop/AndroidAPS/blob/autoisf-analysis/docs/autoisf-fork.md)
  (English) · [docs/autoisf-fork.sk.md](https://github.com/Romcoloop/AndroidAPS/blob/autoisf-analysis/docs/autoisf-fork.sk.md) (slovensky)
* **Branch to build and install:** `autoisf-analysis` (workflow "AutoISF APK" under Actions).
  `autoisf-custom` is the base, `autoisf-slots` and `autoisf-slots-2` are the steps in between.
  `autoisf-steps` adds step counting with the phone and three step rules from OpenAPS AIMI (sport
  guard, SMB spacing after activity, effort protection), all off by default - see chapter 8 of the manual.
* Everything else is upstream AndroidAPS, see below. Use it at your own risk, as with any AAPS build.

---

# AAPS
* Check the wiki: https://wiki.aaps.app
* Want to contribute? Please read [CONTRIBUTING.md](CONTRIBUTING.md) first — note that AI-generated pull requests are not welcome.
*  Everyone who’s been looping with AAPS needs to fill out the form after 3 days of looping  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/4fQUWHZ4Mw)

[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/master.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/master)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/androidaps/localized.svg)](https://translations.aaps.app/project/androidaps)
[![Documentation Status](https://readthedocs.org/projects/androidaps/badge/?version=latest)](https://wiki.aaps.app/en/latest/?badge=latest)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/master/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS)

DEV: 
[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/dev/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS/tree/dev)

<img src="https://cdn.iconscout.com/icon/free/png-256/bitcoin-384-920569.png" srcset="https://cdn.iconscout.com/icon/free/png-512/bitcoin-384-920569.png 2x" alt="Bitcoin Icon" width="100">

3KawK8aQe48478s6fxJ8Ms6VTWkwjgr9f2
