---
name: KFlate
description: Dark slate and cyan controls for an experimental Kotlin compression library.
colors:
  bg: "#0c1b2b"
  card: "#182838"
  inset: "#0f1f2f"
  border: "#37485a"
  accent: "#00b4db"
  accent-hover: "#38cdeb"
  text: "#f1f2f6"
  muted: "#b7c2d1"
  success: "#00e676"
  error: "#ffaaa8"
  action-text: "#041723"
typography:
  display:
    fontFamily: "Gothic A1, Inter, sans-serif"
    fontSize: "37px"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-.02em"
  title:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 500
    lineHeight: 1.25
    letterSpacing: "-.02em"
  body:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "20px"
  label:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "1.2rem"
    lineHeight: 1.25
  measurement:
    fontFamily: "JetBrains Mono, monospace"
    fontSize: ".8rem"
    fontWeight: 400
rounded:
  field: "4px"
  control: "6px"
  panel: "12px"
spacing:
  compact: "8px"
  control: "12px"
  grid: "16px"
  section: "24px"
  page: "44px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.action-text}"
    rounded: "{rounded.control}"
    padding: "12px 24px"
  button-primary-hover:
    backgroundColor: "{colors.accent-hover}"
  button-download:
    textColor: "{colors.accent}"
    rounded: "{rounded.control}"
    padding: "12px 20px"
  panel:
    backgroundColor: "{colors.card}"
    rounded: "{rounded.panel}"
  select:
    backgroundColor: "{colors.bg}"
    textColor: "{colors.text}"
    rounded: "{rounded.field}"
    padding: ".5rem"
---

# Design System: KFlate

## Overview

The approved visual direction is a measurement report. Dark slate panels hold readable controls and measured results, with cyan identifying actions and selected values. The interface addresses developers trying an experimental Kotlin library. Copy stays literal and makes no performance promises.

Preserve the exact repository SVG logo, including its original colors and geometry. The name uses Gothic A1; interface text uses Inter. This document records the implemented system in web-demo/src/wasmJsMain/resources/report.css and benchmarks.css. The approved composition lives in .impeccable/report-contract.md.

## Colors

Cyan is the primary action color. Accent-hover brightens interactive feedback and keyboard focus. Use the dark action-text color on filled cyan buttons.

The neutral palette separates the page background, raised panel tone, and inset control areas. Border draws separators without shadows. Text supports headings and values; muted supports explanations and unselected controls.

Success green and error pink identify runtime states. Always retain accompanying status text so color is not the sole signal. The multicolor logo remains an identity asset; its additional hues do not establish extra action colors.

## Typography

Use the frontmatter roles for the brand, section titles, operation labels, body text, and benchmark measurements. Fonts ship locally with swap loading. Preserve readable text: the root is 20px, changing to 18px at widths of 480px and below.

The brand reduces to 30px at 900px and 27px at 480px. Interface buttons use medium weight. Result values use tabular numbers; benchmark cells use JetBrains Mono. Filenames wrap anywhere instead of widening their container.

## Layout

The current demo has two equal columns separated by the grid spacing token. Desktop margins use the page token. At 1200px they reduce to 28px. At 900px the panels stack inside a container capped at 680px, and a Result link helps reach the second panel. At 480px panel padding reduces to 16px.

At widths of 1800px and above the main grid is 1496px wide. The opening occupies the dynamic viewport, with an internally scrollable demo and a separate benchmark link row. Short desktop windows, up to 980px high and at least 901px wide, reduce vertical spacing and control heights. Keep the benchmark cue visible while controls remain reachable by scrolling. Benchmark tables scroll horizontally within their own region.

## Elevation & Depth

The implementation uses flat tonal layers and thin borders. There are no box shadows. Dashed outlines mark file and result wells. Focus uses a 3px accent-hover outline with a 4px offset; segmented controls move the outline inside their bounds.

## Shapes

Panels and inset wells use the panel radius. Segmented groups and buttons use the control radius. Benchmark selects use the smaller field radius. Status dots and slider thumbs are circular. Utility icons are thin outline SVGs with rounded caps and joins; preserve the original logo separately.

## Components

Filled cyan buttons identify file selection and processing. The processing button spans its panel. Disabled processing uses a darker cyan fill and a disabled cursor. Hover brightens enabled actions. Button background and border changes take 160ms with ease-out timing.

Operation buttons split a bordered inset group evenly. The active operation has cyan text and a cyan bottom border. Format choices are real radio inputs with cyan selected text and border, an inset background, and no checkmark. Both groups retain visible keyboard focus.

The file well centers an outline icon, file chooser, and concise helper text. Drag-over changes its border to cyan and its background to a darker blue. Selected filenames replace the prompt and wrap.

The level slider has a cyan filled track and circular thumb. Its numeric output sits in a bordered inset box with tabular figures. Keep a visible label and endpoint values.

Result panels pair a status well with divided measurement rows. Success changes the icon to green; errors change the message to pink. Processing pulses icon opacity over one second. Reduced-motion preferences disable animations and transitions. The outlined Download link becomes cyan when available and muted when disabled.

Header navigation pairs GitHub with a WASM status dot and text. Benchmark selectors use native controls. Tables right-align measurements, left-align row labels, and retain a caption explaining units and comparison limits.

## Do's and Don'ts

- Do preserve the exact KFlate SVG and experimental-library description.
- Do use cyan for actions, selected controls, and visible keyboard focus.
- Do keep labels, numbers, and runtime status readable at narrow and short viewport sizes.
- Do retain native input semantics and textual feedback alongside state colors.
- Don't replace the logo with a recreation or add promotional performance claims.
- Don't shrink all text to fit the desktop composition on a phone; use the implemented stacked layout and scrolling behavior.
