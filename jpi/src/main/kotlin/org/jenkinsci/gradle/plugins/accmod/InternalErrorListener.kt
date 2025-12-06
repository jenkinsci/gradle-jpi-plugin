package org.jenkinsci.gradle.plugins.accmod

import org.kohsuke.accmod.impl.ErrorListener
import org.kohsuke.accmod.impl.Location
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InternalErrorListener(private val errors: MutableMap<String, MutableSet<CallSite>> = mutableMapOf()) : ErrorListener {
    private companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(InternalErrorListener::class.java)
    }

    data class CallSite(val className: String?, val line: Int?)

    fun hasErrors(): Boolean = errors.isNotEmpty()

    fun errorMessage(): String {
        val sorted = errors.entries.sortedByDescending { it.value.size }
        val lines = mutableListOf<String>("")
        for ((restricted, callSites) in sorted) {
            lines.add("")
            lines.add(restricted)
            lines.add("\tbut was used on ${pluralizeLines(callSites.size)}:")

            callSites.sortedWith(compareBy({ it.className }, { it.line }))
                    .map { "\t\t- ${it.className}:${it.line}" }
                    .forEach { lines.add(it) }
        }
        return lines.joinToString("%n".format())
    }

    private fun pluralizeLines(count: Int): String {
        val suffix = if (count == 1) "line" else "lines"
        return "$count $suffix"
    }

    /**
     * Accesses of Restricted APIs invoke this method.
     *
     * Rather than log invalid accesses right away, we aggregate them in order
     * to do some processing and present in a more consumable way.
     *
     * @param t throwable - always seems to be null
     * @param loc callsite
     * @param msg restricted class and error text
     */
    override fun onError(t: Throwable?, loc: Location?, msg: String) {
        val e = errors.computeIfAbsent(msg) { mutableSetOf() }
        e.add(CallSite(loc?.className, loc?.lineNumber))
        errors[msg] = e
    }

    /**
     * This never seems to be called.
     *
     * If this changes in the future, it's better to propagate this forward
     * to Gradle's logger rather than drop these messages.
     *
     * @param t throwable
     * @param loc callsite
     * @param msg warning message
     */
    override fun onWarning(t: Throwable?, loc: Location?, msg: String?) {
        LOGGER.warn("{} {}", loc?.toString(), msg, t)
    }
}
