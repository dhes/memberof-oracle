package oracle

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Runs HAPI over published WHO ValueSets read in place from a local package directory. The output carries codes from
 * licensed code systems, so it is written under out/who/, which is gitignored.
 */
private data class Entry(val system: String, val code: String)

private fun JsonObject.expansionEntries(): List<Entry> {
  fun walk(arr: com.google.gson.JsonArray?): List<Entry> =
    arr?.flatMap { e ->
      val o = e.asJsonObject
      listOfNotNull(if (o.has("system") && o.has("code")) Entry(o["system"].asString, o["code"].asString) else null) +
        walk(o.getAsJsonArray("contains"))
    } ?: emptyList()
  return walk(getAsJsonObject("expansion")?.getAsJsonArray("contains"))
}

private fun obsOf(vararg entries: Entry) =
  """{"resourceType":"Observation","status":"final","code":{"coding":[${entries.joinToString(",") { """{"system":"${it.system}","code":"${it.code}"}""" }}]}}"""

fun who(packageDir: String, targetsFile: String, txServer: String?) {
  val targets = File(targetsFile).readLines().map { it.trim() }.filter { it.isNotEmpty() }
  val pkg = File(packageDir)
  val resources = pkg.listFiles { f -> f.name.startsWith("ValueSet-") || f.name.startsWith("CodeSystem-") }!!.sortedBy { it.name }
  val byUrl = resources.filter { it.name.startsWith("ValueSet-") }.associate { f ->
    val o = JsonParser.parseString(f.readText()).asJsonObject
    o["url"].asString to o
  }
  val sets = targets.associateWith { byUrl.getValue(it).expansionEntries() }
  val everything = sets.values.flatten().distinct()

  data class WhoCase(val url: String, val shape: String, val entries: List<Entry>, val expansionSays: Boolean, val expression: String)
  val cases = buildList {
    for ((url, members) in sets) {
      members.forEach { add(WhoCase(url, "Coding", listOf(it), true, "Observation.code.coding.memberOf('$url')")) }
      val outsiders = everything.filter { it !in members }.take(6)
      outsiders.forEach { add(WhoCase(url, "Coding", listOf(it), false, "Observation.code.coding.memberOf('$url')")) }
      add(WhoCase(url, "CodeableConcept", listOf(outsiders.first(), members.first()), true, "Observation.code.memberOf('$url')"))
      add(WhoCase(url, "CodeableConcept", outsiders.take(2), false, "Observation.code.memberOf('$url')"))
      add(WhoCase(url, "code", listOf(members.first()), true, "Observation.code.coding.code.memberOf('$url')"))
      add(WhoCase(url, "code", listOf(outsiders.first()), false, "Observation.code.coding.code.memberOf('$url')"))
    }
  }

  val label = if (txServer == null) "offline" else "tx"
  val engine = HapiR5(txServer)
  val loadFailures = resources.mapNotNull { f -> engine.load(f.readText())?.let { "${f.name}: $it" } }
  val outcomes = cases.map { engine.evaluate(obsOf(*it.entries.toTypedArray()), it.expression) }

  val rows = cases.zip(outcomes).map { (c, o) ->
    linkedMapOf(
      "valueSet" to c.url.substringAfterLast('/'), "shape" to c.shape,
      "codings" to c.entries.map { "${it.system}#${it.code}" },
      "expansionSays" to c.expansionSays, "hapi" to o.value, "detail" to o.detail,
    ).filterValues { it != null }
  }
  File("out/who").mkdirs()
  File("out/who/golden-who-$label.json").writeText(
    GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(
      linkedMapOf(
        "hapiCoreVersion" to System.getProperty("hapiCoreVersion"), "terminologyServer" to (txServer ?: "none (offline)"),
        "package" to pkg.path, "loadFailures" to loadFailures, "cases" to rows,
      )
    ) + "\n"
  )

  println("WHO run [$label]  valueSets=${sets.size}  cases=${cases.size}  loadFailures=${loadFailures.size}")
  loadFailures.take(5).forEach { println("  LOAD FAILURE $it") }
  println("%-16s %-42s %6s %6s %6s %6s".format("shape", "system of first coding", "cases", "agree", "wrong", "error"))
  cases.zip(outcomes).groupBy { (c, _) -> c.shape to c.entries.first().system }.toSortedMap(compareBy({ it.first }, { it.second })).forEach { (k, v) ->
    val agree = v.count { (c, o) -> o.value == c.expansionSays.toString() }
    val err = v.count { (_, o) -> o.value.startsWith("error") }
    println("%-16s %-42s %6d %6d %6d %6d".format(k.first, k.second.removePrefix("http://"), v.size, agree, v.size - agree - err, err))
  }
  val falsePos = cases.zip(outcomes).count { (c, o) -> !c.expansionSays && o.value == "true" }
  val falseNeg = cases.zip(outcomes).count { (c, o) -> c.expansionSays && o.value != "true" }
  println("HAPI says member where the expansion says no: $falsePos;  HAPI does not say member where the expansion says yes: $falseNeg")
}
