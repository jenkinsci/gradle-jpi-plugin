package org.jenkinsci.gradle.plugins.accmod

import groovy.transform.Immutable
import org.kohsuke.accmod.impl.Location
import spock.lang.Specification

@SuppressWarnings('ConsecutiveBlankLines')
class InternalErrorListenerSpec extends Specification {
    def 'should not have errors if empty'() {
        expect:
        !new InternalErrorListener().hasErrors()
    }

    def 'should show one error'() {
        given:
        def listener = new InternalErrorListener()
        listener.onError(null, new TestLocation('org.example.Hello', 11), 'hudson/plugins/mercurial/MercurialChangeSet.setMsg(Ljava/lang/String;)V must not be used')

        when:
        def actual = listener.errorMessage()

        then:
        listener.hasErrors()
        actual == '''\


            hudson/plugins/mercurial/MercurialChangeSet.setMsg(Ljava/lang/String;)V must not be used
            \tbut was used on 1 line:
            \t\t- org.example.Hello:11'''.stripIndent().denormalize()
    }

    def 'should aggregate multiple errors'() {
        given:
        def listener = new InternalErrorListener()
        with(listener) {
            onError(null, new TestLocation('org.example.Hello', 11), 'hudson/plugins/mercurial/MercurialChangeSet.setMsg(Ljava/lang/String;)V must not be used')
            onError(null, new TestLocation('org.example.Hello', 23), 'hudson/plugins/mercurial/MercurialChangeSet.setMsg(Ljava/lang/String;)V must not be used')
            onError(null, new TestLocation('org.example.Goodbye', 38), 'hudson/plugins/mercurial/MercurialChangeSet.setMsg(Ljava/lang/String;)V must not be used')

            onError(null, new TestLocation('org.example.Goodbye', 100), 'hudson/plugins/mercurial/MercurialChangeSet.setUser(Ljava/lang/String;)V must not be used')

            onError(null, new TestLocation('org.example.Something', 51), 'hudson/plugins/mercurial/MercurialChangeSet.setId(Ljava/lang/String;)V must not be used')
            onError(null, new TestLocation('org.example.Something', 15), 'hudson/plugins/mercurial/MercurialChangeSet.setId(Ljava/lang/String;)V must not be used')
        }

        when:
        def actual = listener.errorMessage()

        then:
        listener.hasErrors()
        actual == '''\


            hudson/plugins/mercurial/MercurialChangeSet.setMsg(Ljava/lang/String;)V must not be used
            \tbut was used on 3 lines:
            \t\t- org.example.Goodbye:38
            \t\t- org.example.Hello:11
            \t\t- org.example.Hello:23

            hudson/plugins/mercurial/MercurialChangeSet.setId(Ljava/lang/String;)V must not be used
            \tbut was used on 2 lines:
            \t\t- org.example.Something:15
            \t\t- org.example.Something:51

            hudson/plugins/mercurial/MercurialChangeSet.setUser(Ljava/lang/String;)V must not be used
            \tbut was used on 1 line:
            \t\t- org.example.Goodbye:100'''.stripIndent().denormalize()
    }

    @Immutable
    @SuppressWarnings('GetterMethodCouldBeProperty')
    static class TestLocation implements Location {
        String className
        int lineNumber

        @Override
        String getMethodName() {
            null
        }

        @Override
        String getMethodDescriptor() {
            null
        }

        @Override
        ClassLoader getDependencyClassLoader() {
            null
        }

        @Override
        String getProperty(String key) {
            ''
        }
    }
}
