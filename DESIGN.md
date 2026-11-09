---
name: Digix
description: "Halo + Quiet: a black Android app where a fading field of dots frames each screen's one big thing, lists stay plain and readable, and red marks only what is live."
colors:
  background: "#000000"
  foreground: "#F2F2F2"
  secondary: "#A1A1A1"
  quiet: "#8C8C8C"
  muted: "#111111"
  raised: "#1C1C1C"
  border: "#262626"
  divider: "#1E1E1E"
  halo: "#4A4A4A"
  primary: "#F2F2F2"
  onPrimary: "#000000"
  live: "#FF3B30"
  warning: "#FFB000"
  destructive: "#FF6961"
  switchOff: "#2A2A2A"
typography:
  state:
    fontFamily: "Doto"
    fontSize: "92sp"
    fontWeight: 900
  codeLarge:
    fontFamily: "Doto"
    fontSize: "64sp"
    fontWeight: 900
  codeList:
    fontFamily: "JetBrains Mono"
    fontSize: "18sp"
    fontWeight: 700
  code:
    fontFamily: "JetBrains Mono"
    fontSize: "15sp"
    fontWeight: 700
  wordmark:
    fontFamily: "Space Grotesk"
    fontSize: "30sp"
    fontWeight: 700
  wordmarkStacked:
    fontFamily: "Sora"
    fontSize: "26sp"
    fontWeight: 600
  largeTitle:
    fontFamily: "Inter"
    fontSize: "34sp"
    fontWeight: 600
  title:
    fontFamily: "Inter"
    fontSize: "28sp"
    fontWeight: 600
  headline:
    fontFamily: "Inter"
    fontSize: "17sp"
    fontWeight: 600
  body:
    fontFamily: "Inter"
    fontSize: "16sp"
    fontWeight: 400
  subhead:
    fontFamily: "Inter"
    fontSize: "14sp"
    fontWeight: 400
rounded:
  radius: "20dp"
  radiusSmall: "12dp"
  pill: "50%"
---

# Design System: Digix

## Overview

Digix is opened rarely, so its screens read at a glance and then get out of the way. The look, chosen by the owner from a set of explorations, is **Halo + Quiet**: dot matrix as a visual identity, kept to the big moments, with everything a person has to read set plainly on solid black.

- **The halo:** a field of dots that fades out from the middle, drawn behind a screen's one big thing. Its centre stays clear, so text on it is always read against black.
- **Quiet lists:** rows on a solid `#111` surface, in Inter, with codes in a bold monospace so every digit is distinct.
- **Red is the live key:** it appears only where something is listening or happening.

The app is dark in every system mode. The halo and the red key only work on black, and a single ground keeps the identity the same everywhere.

## Colors

- **Ground** (#000000): every screen, the status bar scrim and the launch screen.
- **Text** (#F2F2F2), **Secondary** (#A1A1A1) and **Quiet** (#8C8C8C, for things that are off or paused). All pass 4.5:1 on black and on the `#111` surface.
- **Surface** (#111111): Groups and lists. **Raised** (#1C1C1C): icon tiles, code pills and the Undo Bar inside or over a surface.
- **Border** (#262626) outlines pills and outline buttons. **Divider** (#1E1E1E) separates rows inside a Group.
- **Halo dots** (#4A4A4A), drawn with alpha falling off towards the edge and the centre.
- **Primary** (#F2F2F2 fill, #000000 label): one main button per view, the add button, the "on" switch track.
- **Live Red** (#FF3B30): the live dot, the master switch thumb when on, the centre key of the logo, the code cursor, and the watch's success ring and check.
- **Warning Amber** (#FFB000) for "Needs setup", **Destructive** (#FF6961) for errors and delete.

### Named Rules
**The Live Key Rule.** Red means listening or happening. No other switch, button or decoration may use it. When Digix is off, every red mark on Home (live dot, switch thumb, logo key) goes Quiet.

## Typography

Inter carries all reading text, at two optical sizes. Four display faces each have one job, and each ships cut down to only the characters it draws (`res/font`, licences in `assets/licenses`):

- **Doto** (900): ON and OFF on Home, a code being set in the editor, and codes on the onboarding watch. Only at 38sp and above, where the dots stay legible. Other languages' On/Off words fall back to the Large Title style.
- **JetBrains Mono** (700): codes in lists and badges.
- **Space Grotesk** (700): the lowercase "digix" wordmark beside the logo on Home and in Settings.
- **Sora** (600): the stacked wordmark on the first onboarding screen.

### Named Rules
**The Readable Dots Rule.** Dot type is for display only. Nothing a person must read in a sentence is ever set in Doto, and no text sits on halo dots without the halo's clear centre behind it.

## Layout

One phone column, edge to edge. App screens use the Pinned Bar, Large Title and a 16dp gutter. Home is the exception: a fixed bar with the wordmark, then the state hero, then "Your codes".

The hero is a 200dp halo with ON or OFF centred, and under it one pill holding the live dot, a one-line explanation and the master switch. The pill is the whole touch target.

Onboarding keeps its 24dp gutter: the stacked wordmark on the first step (step dots after that), the watch inside a halo, then text and actions.

## Shapes

Groups and cards use 20dp. Icon tiles use 12dp. Buttons, pills, code badges and the switch are fully round. The logo is a 40-unit square at an 11-unit radius.

## Components

### Halo
`Halo(strength, clearCentre)` draws dots on a 12dp grid inside its box, fading out by elliptical distance and clearing a centre so the content stays on black. `strength` dims the whole field: Home uses 1 when on and 0.45 when off, on the `smooth` spring. Use one halo per screen: Home's hero, the editor's code, onboarding's watch, empty Activity, and the Settings footer.

### Logo and Wordmarks
The mark is drawn in code: a dark rounded square with eight white dots and a larger red centre key, matching the launcher icon. `Wordmark` puts it beside "digix" in Space Grotesk. `StackedWordmark` puts it above "digix" in Sora. The Home wordmark's key follows the master switch.

### Code Rows (Home)
Code first: the code in bold monospace (62dp column, always left to right), a small Secondary line icon, the title with a status line under it, and a chevron. Rows sit in one Group with dividers. When a code really runs, the row takes an 8% Text wash for 1.6 seconds and the chevron becomes a check, with the `codeCaught` haptic. When Digix is off, codes turn Quiet and titles Secondary.

### No Codes Yet
A dot "+" in Quiet, the empty title and body, then "Start with" shortcuts (Flashlight, Find my phone, Phone battery) that open the editor for that action, and a full-width "Add a code".

### Buttons
Pill shaped, at least 52dp tall, with a semibold label. Primary is Text-filled with a black label. Outline has a Border hairline. Ghost has neither. Destructive is outline with a red label. Pressed buttons scale to 0.97 on `quick` with the `press` haptic.

### Switch
A 52 by 32dp track. On: Text track with a black thumb, or a red thumb for the master switch. Off: `#2A2A2A` track with a Quiet thumb. The thumb stretches to 32dp under the finger.

### Groups, Rows, Icons
A Group is a solid Surface at 20dp with no outline. Rows are at least 58dp tall, with 16dp padding. Action Icons are 38dp Raised tiles. Icon Actions (back, activity, settings) are 44dp Surface circles inside a 48dp touch area.

### Code Field (editor)
The code in Doto Code Large, centred over a halo, with a 132dp underline that is Border at rest, Text while editing and Destructive on error. The cursor is red.

### Motion and Haptics
Unchanged: `quick`, `smooth` and `lively` springs, tweens only for opacity and the two ambient loops, and the haptic vocabulary `press`, `toggle`, `confirm`, `reject`, `codeCaught`. Removed animations drop press scales, pulses and the waiting arc.

### Launcher Icon
An adaptive icon on #09090B: the three by three dial pad of dots with the larger centre key in Live Red, plus a monochrome layer.

## Do's and Don'ts

### Do:
- **Do** give each screen at most one halo, behind its one big thing.
- **Do** keep reading text in Inter on black or on the Surface.
- **Do** show codes in the monospace in lists and in Doto only at display size, always left to right.
- **Do** pair state changes with a theme spring and a haptic.

### Don't:
- **Don't** use red for anything that isn't live.
- **Don't** set sentences, labels or small text in Doto, or place text over halo dots.
- **Don't** add a light theme, shadows or gradients.
- **Don't** use Material chrome. Draw controls from these tokens.
