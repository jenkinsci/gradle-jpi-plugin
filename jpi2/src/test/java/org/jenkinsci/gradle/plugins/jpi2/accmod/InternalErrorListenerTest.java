package org.jenkinsci.gradle.plugins.jpi2.accmod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.accmod.impl.Location;

class InternalErrorListenerTest {

    private InternalErrorListener listener;

    @BeforeEach
    void setUp() {
        listener = new InternalErrorListener();
    }

    @Test
    void hasErrorsIsFalseUntilAnErrorIsReported() {
        assertThat(listener.hasErrors()).isFalse();

        listener.onError(null, location("com.example.Caller", 10), "restricted api");

        assertThat(listener.hasErrors()).isTrue();
    }

    @Test
    void aggregatesCallSitesByMessageAndDeduplicatesIdenticalSites() {
        listener.onError(null, location("com.example.A", 1), "msg-a");
        listener.onError(null, location("com.example.B", 2), "msg-a");
        listener.onError(null, location("com.example.A", 1), "msg-a");
        listener.onError(null, location("com.example.C", 3), "msg-b");

        String message = listener.errorMessage();

        assertThat(message).contains("msg-a").contains("msg-b");
        assertThat(message).contains("2 lines").contains("1 line");
        assertThat(message).contains("- com.example.A:1").contains("- com.example.B:2");
        assertThat(message.indexOf("msg-a")).isLessThan(message.indexOf("msg-b"));
    }

    @Test
    void sortsCallSitesByClassNameThenLineNumber() {
        listener.onError(null, location("com.example.Z", 1), "msg");
        listener.onError(null, location("com.example.A", 20), "msg");
        listener.onError(null, location("com.example.A", 5), "msg");

        String message = listener.errorMessage();
        int a5 = message.indexOf("- com.example.A:5");
        int a20 = message.indexOf("- com.example.A:20");
        int z1 = message.indexOf("- com.example.Z:1");

        assertThat(a5).isGreaterThan(-1);
        assertThat(a20).isGreaterThan(a5);
        assertThat(z1).isGreaterThan(a20);
    }

    @Test
    void nullMessageAndLocationAreHandledSafely() {
        listener.onError(null, null, null);
        listener.onError(null, location(null, 0), "other");

        String message = listener.errorMessage();

        assertThat(message).contains("<unknown restricted api>");
        assertThat(message).contains("- null:0");
        assertThat(message).contains("- null:null");
    }

    @Test
    void pluralizesLineVersusLines() {
        listener.onError(null, location("com.example.A", 1), "once");
        assertThat(listener.errorMessage()).contains("1 line:");

        listener.onError(null, location("com.example.B", 2), "once");
        assertThat(listener.errorMessage()).contains("2 lines:");
    }

    private static Location location(String className, int lineNumber) {
        Location location = mock(Location.class);
        when(location.getClassName()).thenReturn(className);
        when(location.getLineNumber()).thenReturn(lineNumber);
        return location;
    }
}
