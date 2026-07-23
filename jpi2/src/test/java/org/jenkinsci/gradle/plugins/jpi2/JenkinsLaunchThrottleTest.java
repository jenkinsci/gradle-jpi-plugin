package org.jenkinsci.gradle.plugins.jpi2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.jenkinsci.gradle.plugins.jpi2.JenkinsLaunchThrottle.resolveMaxParallelLaunches;

class JenkinsLaunchThrottleTest {

    @Test
    void unsetValueDefaultsToProcessorsDividedByThree() {
        assertThat(resolveMaxParallelLaunches(null, 10)).isEqualTo(3);
        assertThat(resolveMaxParallelLaunches("", 10)).isEqualTo(3);
        assertThat(resolveMaxParallelLaunches("   ", 12)).isEqualTo(4);
    }

    @Test
    void absoluteValueIsUsedAsIs() {
        assertThat(resolveMaxParallelLaunches("1", 10)).isEqualTo(1);
        assertThat(resolveMaxParallelLaunches("4", 10)).isEqualTo(4);
        // An absolute cap is independent of the core count.
        assertThat(resolveMaxParallelLaunches("3", 64)).isEqualTo(3);
    }

    @Test
    void processorRelativeValueScalesWithCoreCount() {
        assertThat(resolveMaxParallelLaunches("C", 10)).isEqualTo(10);
        assertThat(resolveMaxParallelLaunches("C/2", 10)).isEqualTo(5);
        assertThat(resolveMaxParallelLaunches("C/3", 10)).isEqualTo(3);
        assertThat(resolveMaxParallelLaunches("C/4", 10)).isEqualTo(2);
        // The same C/3 setting yields a different, machine-appropriate cap elsewhere.
        assertThat(resolveMaxParallelLaunches("C/3", 24)).isEqualTo(8);
    }

    @Test
    void processorRelativeValueIsCaseAndWhitespaceInsensitive() {
        assertThat(resolveMaxParallelLaunches("c/2", 10)).isEqualTo(5);
        assertThat(resolveMaxParallelLaunches(" C / 2 ", 10)).isEqualTo(5);
    }

    @Test
    void resultIsAlwaysAtLeastOne() {
        // Integer division would give 0 on a small machine; the cap must stay runnable.
        assertThat(resolveMaxParallelLaunches("C/4", 2)).isEqualTo(1);
        assertThat(resolveMaxParallelLaunches(null, 2)).isEqualTo(1);
        assertThat(resolveMaxParallelLaunches("0", 10)).isEqualTo(1);
    }

    @Test
    void invalidValuesAreRejectedWithAnActionableMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolveMaxParallelLaunches("C/0", 10))
                .withMessageContaining("testServer.maxParallelLaunches")
                .withMessageContaining("C/D");
        assertThatIllegalArgumentException().isThrownBy(() -> resolveMaxParallelLaunches("-2", 10));
        assertThatIllegalArgumentException().isThrownBy(() -> resolveMaxParallelLaunches("half", 10));
        assertThatIllegalArgumentException().isThrownBy(() -> resolveMaxParallelLaunches("C/", 10));
        assertThatIllegalArgumentException().isThrownBy(() -> resolveMaxParallelLaunches("3.5", 10));
    }
}
