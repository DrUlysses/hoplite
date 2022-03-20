package com.sksamuel.hoplite.report

import com.sksamuel.hoplite.ArrayNode
import com.sksamuel.hoplite.BooleanNode
import com.sksamuel.hoplite.DoubleNode
import com.sksamuel.hoplite.LongNode
import com.sksamuel.hoplite.MapNode
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.NullNode
import com.sksamuel.hoplite.Pos
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.Undefined
import com.sksamuel.hoplite.decoder.DotPath

typealias Print = (String) -> Unit

class ReporterBuilder {

  private var print: Print = { println(it) }
  private var obfuscator: Obfuscator = DefaultObfuscator

  fun withPrint(print: Print) = apply {
    this.print = print
  }

  fun withObfuscator(obfuscator: Obfuscator) = apply {
    this.obfuscator = obfuscator
  }

  fun build(): Reporter = Reporter(print, obfuscator)
}

class Reporter(
  private val print: Print,
  private val obfuscator: Obfuscator,
) {

  companion object {
    fun default(): Reporter = ReporterBuilder().build()
  }

  fun printReport(
    sources: List<PropertySource>,
    node: Node,
    used: List<Pair<DotPath, Pos>>,
    usedSecrets: Set<DotPath>,
  ) {

    val r = buildString {
      appendLine()
      appendLine("--Start Hoplite Config Report---")
      appendLine()
      appendLine(report(sources))
      appendLine()

      val usedPaths = used.map { it.first }
      val (usedResources, unusedResources) = node.resources().partition { usedPaths.contains(it.path) }

      if (used.isEmpty()) appendLine("Used keys: none")
      if (used.isNotEmpty()) appendLine(reportResources(usedResources, "Used", usedSecrets))

      appendLine()

      if (unusedResources.isEmpty()) appendLine("Unused keys: none")
      if (unusedResources.isNotEmpty()) appendLine(reportResources(unusedResources, "Unused", usedSecrets))

      appendLine()
      appendLine("--End Hoplite Config Report--")
      appendLine()
    }

    print(r)
  }

  fun report(sources: List<PropertySource>): String {
    return "Property sources (highest to lowest priority):" + System.lineSeparator() +
      sources.joinToString(System.lineSeparator() + "  - ", "  - ") { it.source() }
  }

  fun reportResources(resources: List<ConfigResource>, title: String, usedSecrets: Set<DotPath>): String {

    val obfuscated = resources.map {
      val value = obfuscator.obfuscate(it.path, it.value)
      it.copy(value = value)
    }

    val keyPadded = obfuscated.maxOf { it.path.flatten().length }
    val sourcePadded = obfuscated.maxOf { it.source.length }
    val valuePadded = obfuscated.maxOf { it.value.length }

    val rows = obfuscated.map {
      "| " + it.path.flatten().padEnd(keyPadded, ' ') +
        " | " + it.source.padEnd(sourcePadded, ' ') +
        " | " + it.value.padEnd(valuePadded, ' ') + " |"
    }

    val titleRow = "$title keys ${resources.size}"

    val bar = listOf(
      "".padEnd(keyPadded + 2, '-'),
      "".padEnd(sourcePadded + 2, '-'),
      "".padEnd(valuePadded + 2, '-')
    ).joinToString("+", "+", "+")

    val titles = listOf(
      "Key".padEnd(keyPadded, ' '),
      "Source".padEnd(sourcePadded, ' '),
      "Value".padEnd(valuePadded, ' ')
    ).joinToString(" | ", "| ", " |")

    return (listOf(titleRow, bar, titles, bar) + rows + listOf(bar)).joinToString(System.lineSeparator())
  }
}

fun Node.resources(): List<ConfigResource> {
  return when (this) {
    is ArrayNode -> emptyList()
    is MapNode -> map.entries.map { (_, value) -> value.resources() }.flatten()
    is BooleanNode -> listOf(ConfigResource(path, pos.source() ?: "n/a", value.toString()))
    is NullNode -> listOf(ConfigResource(path, pos.source() ?: "n/a", "<null>"))
    is DoubleNode -> listOf(ConfigResource(path, pos.source() ?: "n/a", value.toString()))
    is LongNode -> listOf(ConfigResource(path, pos.source() ?: "n/a", value.toString()))
    is StringNode -> listOf(ConfigResource(path, pos.source() ?: "n/a", value))
    Undefined -> emptyList()
  }
}

data class ConfigResource(
  val path: DotPath,
  val source: String,
  val value: String,
)
