package org.jenkinsci.gradle.plugins.jpi2.accmod;

import org.kohsuke.accmod.impl.ErrorListener;
import org.kohsuke.accmod.impl.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class InternalErrorListener implements ErrorListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalErrorListener.class);

    private final Map<String, Set<CallSite>> errors = new HashMap<>();

    boolean hasErrors() {
        return !errors.isEmpty();
    }

    String errorMessage() {
        List<Map.Entry<String, Set<CallSite>>> sorted = new ArrayList<>(errors.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

        List<String> lines = new ArrayList<>();
        lines.add("");
        for (Map.Entry<String, Set<CallSite>> entry : sorted) {
            lines.add("");
            lines.add(entry.getKey());
            lines.add("\tbut was used on " + pluralizeLines(entry.getValue().size()) + ":");
            entry.getValue().stream()
                    .sorted(Comparator.comparing(CallSite::className, Comparator.nullsLast(String::compareTo))
                            .thenComparing(CallSite::line, Comparator.nullsLast(Integer::compareTo)))
                    .map(callSite -> "\t\t- " + callSite.className() + ":" + callSite.line())
                    .forEach(lines::add);
        }
        return String.join(System.lineSeparator(), lines);
    }

    private static String pluralizeLines(int count) {
        return count + " " + (count == 1 ? "line" : "lines");
    }

    @Override
    public void onError(Throwable t, Location loc, String msg) {
        String message = msg == null ? "<unknown restricted api>" : msg;
        errors.computeIfAbsent(message, ignored -> new HashSet<>())
                .add(new CallSite(loc == null ? null : loc.getClassName(), loc == null ? null : loc.getLineNumber()));
    }

    @Override
    public void onWarning(Throwable t, Location loc, String msg) {
        LOGGER.warn("{} {}", loc, msg, t);
    }

    private record CallSite(String className, Integer line) {
    }
}
