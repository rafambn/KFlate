package com.rafambn.kflate.plots

import java.io.File
import kotlinx.serialization.json.*
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.settings.LineType
import org.jetbrains.kotlinx.kandy.letsplot.translator.toLetsPlot
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.letsPlot.core.util.PlotHtmlHelper
import org.jetbrains.letsPlot.core.util.sizing.SizingMode
import org.jetbrains.letsPlot.core.util.sizing.SizingPolicy
import org.jetbrains.letsPlot.intern.toSpec
import org.jetbrains.letsPlot.scale.scaleXContinuous
import org.jetbrains.letsPlot.scale.scaleYContinuous
import org.jetbrains.letsPlot.themes.elementLine
import org.jetbrains.letsPlot.themes.elementRect
import org.jetbrains.letsPlot.themes.elementText
import org.jetbrains.letsPlot.themes.theme
import org.jetbrains.letsPlot.themes.themeMinimal

private val plotLevels = 1..8

fun main(args: Array<String>) {
    val output = File(args.getOrElse(1) { "benchmark-plots/build/site" }).apply { mkdirs() }
    val historyFile = File(args.getOrElse(0) { "performance/history.json" })
    if (!historyFile.exists()) {
        File(output, "index.json").writeText("{\"charts\":[]}")
        return
    }
    val history = Json.parseToJsonElement(historyFile.readText()).jsonObject
    val baselines = history.getValue("baselines").jsonObject
    val runs = history.getValue("runs").jsonArray.map { it.jsonObject }
    val charts = mutableListOf<JsonObject>()
    for ((runIndex, run) in runs.withIndex()) {
        val platform = run.getValue("platform").jsonPrimitive.content
        val baseline = baselines.getValue(platform).jsonObject
        val rows = run.getValue("rows").jsonArray.map { it.jsonObject }
        val baselineRows = baseline.getValue("rows").jsonArray.map { it.jsonObject }
        for (operation in listOf("compression", "decompression")) {
            for ((corpusIndex, corpus) in rows.map { it.getValue("corpus").jsonPrimitive.content }.distinct().withIndex()) {
                fun matching(row: JsonObject) = row.getValue("corpus").jsonPrimitive.content == corpus &&
                    row.getValue("operation").jsonPrimitive.content == operation
                val current = rows.filter(::matching).sortedBy { it.getValue("level").jsonPrimitive.int }
                val reference = baselineRows.filter(::matching).sortedBy { it.getValue("level").jsonPrimitive.int }
                require(current.size == 10 && reference.size == 10) { "Incomplete plot: $platform / $corpus / $operation" }
                val plottedCurrent = current.filter { it.getValue("level").jsonPrimitive.int in plotLevels }
                val plottedReference = reference.filter { it.getValue("level").jsonPrimitive.int in plotLevels }
                val all = runs.filter { it.getValue("platform").jsonPrimitive.content == platform }
                    .flatMap { it.getValue("rows").jsonArray.map { row -> row.jsonObject } }
                    .filter(::matching)
                    .filter { it.getValue("level").jsonPrimitive.int in plotLevels } +
                    reference.filter { it.getValue("level").jsonPrimitive.int in plotLevels }
                val maxTime = all.maxOf { it.getValue("metric").jsonObject.getValue("averageMs").jsonPrimitive.double } * 1.08
                val maxBytes = all.maxOf { it.getValue("compressedSizeBytes").jsonPrimitive.double } * 1.08
                fun chart(bytes: Boolean) = plot {
                    for ((index, series) in listOf(plottedCurrent, plottedReference).withIndex()) {
                        val levels = series.map { it.getValue("level").jsonPrimitive.int }
                        val values = series.map {
                            if (bytes) it.getValue("compressedSizeBytes").jsonPrimitive.double
                            else it.getValue("metric").jsonObject.getValue("averageMs").jsonPrimitive.double
                        }
                        val ink = Color.hex(if (index == 0) "#22d3ee" else "#fbbf24")
                        line {
                            x(levels)
                            y(values)
                            color = ink
                            width = if (bytes) 1.3 else 2.5
                            type = if (bytes) LineType.DASHED else LineType.SOLID
                        }
                        if (!bytes) points {
                            x(levels)
                            y(values)
                            color = ink
                            size = 2.5
                        }
                    }
                }.toLetsPlot() + scaleXContinuous(name = "Compression level", breaks = plotLevels.toList(), limits = 1 to 8) +
                    scaleYContinuous(name = if (bytes) "Compressed bytes" else "Average time (ms)",
                        limits = 0 to if (bytes) maxBytes else maxTime, position = "left")
                val style = themeMinimal() + theme(
                    text = elementText(color = "#e2e8f0", family = "sans-serif", size = 14),
                    plotBackground = elementRect(blank = true),
                    panelBackground = elementRect(blank = true),
                    panelGridMajor = elementLine(color = "#334155", size = 0.4),
                    panelGridMinor = "blank"
                )
                val figures = (if (operation == "compression") listOf(chart(false), chart(true)) else listOf(chart(false)))
                    .mapIndexed { index, figure ->
                        val axes = if (operation == "compression" && index == 0) theme(
                            axisTitleX = "blank") else theme()
                        (figure + style + axes).toSpec()
                    }
                val plotHeight = if (operation == "compression") 680.0 else 360.0
                val specification = mutableMapOf<String, Any>(
                    "kind" to "subplots", "figures" to figures,
                    "layout" to if (operation == "compression") mapOf(
                        "name" to "grid", "ncol" to 1, "nrow" to 2,
                        "sharex" to "all", "sharey" to "none", "vspace" to 12
                    ) else mapOf("name" to "grid", "ncol" to 1, "nrow" to 1),
                    "theme" to mapOf("plot_background" to mapOf("fill" to "#0f172a", "color" to "#0f172a")),
                    "ggsize" to mapOf("width" to 900, "height" to plotHeight)
                )
                val filename = "$runIndex-$corpusIndex-$operation.html"
                val display = PlotHtmlHelper.getDisplayHtmlForRawSpec(specification,
                    SizingPolicy(SizingMode.FIT, SizingMode.FIXED, height = plotHeight),
                    dynamicScriptLoading = false, forceImmediateRender = false, responsive = true,
                    removeComputationMessages = true, logComputationMessages = true)
                val html = """<!doctype html><html lang="en"><head><meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Benchmark chart</title>
                    <style>
                        html,body{margin:0;padding:0;background:#0f172a;color:#e2e8f0;font:14px system-ui}
                        .plt-container .axis-tooltip-text-x,
                        .plt-container .axis-tooltip-text-y{fill:#e2e8f0 !important}
                        .plt-container .tooltip-text,
                        .plt-container .tooltip-title,
                        .plt-container .tooltip-label{fill:#0f172a !important}
                    </style>
                    <script src="https://cdn.jsdelivr.net/gh/JetBrains/lets-plot@v4.10.1/js-package/distr/lets-plot.min.js"></script>
                    </head><body><p id="chart-error" hidden>Chart could not load. Reload the page or use the measurements table.</p>
                    <script>if(!window.LetsPlot)document.getElementById('chart-error').hidden=false;</script>
                    $display</body></html>""".trimIndent()
                File(output, filename).writeText(html)
                charts += buildJsonObject {
                    put("platform", platform)
                    put("operation", operation)
                    put("corpus", corpus)
                    put("runId", run.getValue("runId"))
                    put("startedAt", run["startedAt"] ?: JsonPrimitive(""))
                    put("baselineRunId", baseline.getValue("runId"))
                    put("url", filename)
                    put("rows", JsonArray(current))
                    put("baselineRows", JsonArray(reference))
                }
            }
        }
    }
    File(output, "index.json").writeText(buildJsonObject { put("charts", JsonArray(charts)) }.toString())
    historyFile.copyTo(File(output, "history.json"), overwrite = true)
    println("Generated ${charts.size} Kandy plots")
}
