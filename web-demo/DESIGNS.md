# Compression demo designs

Build with `./gradlew :web-demo:assembleWebDemo` and serve `web-demo/build/webDemo`. The main page implements the approved dark slate measurement layout, with the exact library logo, larger text, a custom format selector, and inline results. Open `/designs/` for the three earlier exploratory layouts.

Each opening section occupies the viewport using dynamic viewport height. The demo occupies a scrollable area above a persistent benchmark cue. Small screens can scroll the demo internally; the benchmark table starts below the opening section. The cue scrolls to the benchmarks and moves keyboard focus there.

The three designs share the compressor and benchmark renderer:

- Type & space uses a mint typographic poster, an open file picker, and controls along the bottom.
- Floating field places the file picker among curved lines on a dark background, with a separate control dock.
- Experiment log arranges file selection, operation, and execution as numbered entries directly on a blue page.

Each hero identifies KFlate as an experimental compression library written in Kotlin and describes the browser demo without promising size savings or performance.

All three use responsive layouts with controls that wrap or stack on narrow screens.

The pages use new markup and `fresh.css`, without loading the former layout styles. `fresh.js` adds keyboard file selection and benchmark navigation. The main page uses `report.css` and `report.js`. The exploratory layouts remain available for reference.
