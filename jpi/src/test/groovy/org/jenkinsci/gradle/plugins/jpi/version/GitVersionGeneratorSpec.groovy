package org.jenkinsci.gradle.plugins.jpi.version

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import spock.lang.Ignore
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

import static org.jenkinsci.gradle.plugins.jpi.version.Util.isGitHash

class GitVersionGeneratorSpec extends Specification {

    def 'generate version from git abbreviated hash'() {
        given:
        def gitRoot = generateGitRepo()

        when:
        def version = new GitVersionGenerator(gitRoot, 12, '', '%d.%s', false, false).generate()

        then:
        version.abbreviatedHash ==~ /3\.\w{12}/
        isGitHash(version.fullHash)
    }

    def 'generate version from git abbreviated hash with a custom format'() {
        given:
        def gitRoot = generateGitRepo()

        when:
        def version = new GitVersionGenerator(gitRoot, 12, '', 'rc.%d-%s', false, false).generate()

        then:
        version.abbreviatedHash ==~ /rc\.3-\w{12}/
        isGitHash(version.fullHash)
    }

    @Ignore('TODO: generate git repo with collision')
    def 'generate version despite ambiguous commit'() {
        given:
        def gitRoot = generateGitRepo()

        when:
        def version = new GitVersionGenerator(gitRoot, 2, '', '%d.%s', false, false).generate()

        then:
        version.abbreviatedHash ==~ /3\.\w{2}/
    }

    def 'cannot generate version from git because of untracked files'() {
        given:
        def gitRoot = generateGitRepo()
        Files.createFile(gitRoot.resolve('untracked')).text = 'bar'

        when:
        new GitVersionGenerator(gitRoot, 12, '', '%d.%s', false, false).generate()

        then:
        def exception = thrown(RuntimeException)
        exception.message == "Repository '${gitRoot}' has some pending changes:\n- untracked files: [untracked]"
    }

    def 'cannot generate version from git because of uncommitted changes'() {
        given:
        def gitRoot = generateGitRepo()
        gitRoot.resolve('somefile').text = 'foo!'

        when:
        new GitVersionGenerator(gitRoot, 12, '', '%d.%s', false, false).generate()

        then:
        def exception = thrown(RuntimeException)
        exception.message == "Repository '${gitRoot}' has some pending changes:\n- uncommitted changes: [somefile]"
    }

    def 'cannot generate version from git because of untracked files and uncommitted changes'() {
        given:
        def gitRoot = generateGitRepo()
        Files.createFile(gitRoot.resolve('untracked')).text = 'bar'
        gitRoot.resolve('somefile').text = 'foo!'

        when:
        new GitVersionGenerator(gitRoot, 12, '', '%d.%s', false, false).generate()

        then:
        def exception = thrown(RuntimeException)
        exception.message == """\
            Repository '${gitRoot}' has some pending changes:
            - untracked files: [untracked]
            - uncommitted changes: [somefile]""".stripIndent()
    }

    def 'can generate version from git with pending changes'() {
        given:
        def gitRoot = generateGitRepo()
        Files.createFile(gitRoot.resolve('untracked')).text = 'bar'

        when:
        def version = new GitVersionGenerator(gitRoot, 12, '', '%d.%s', true, false).generate()

        then:
        version.abbreviatedHash ==~ /3\.\w{12}/
        isGitHash(version.fullHash)
    }

    def 'can sanitize version from git'() {
        given:
        def gitRoot = generateGitRepo()

        when:
        def version = new GitVersionGenerator(gitRoot, 12, '', 'ab-%d.%s', true, true).generate()

        then:
        !(version ==~ /ab-([ab][^_])/)
    }

    def 'sanitize'() {
        when:
        def sanitized = GitVersionGenerator.sanitize('9a80981b849e')

        then:
        sanitized == '9a_80981b_849e'
    }

    Path generateGitRepo() {
        def gitRoot = Files.createTempDirectory('git-repo')
        def gitCmd = Git.init()
        gitCmd.directory = gitRoot.toFile()
        def git = gitCmd.call()
        Files.createFile(gitRoot.resolve('somefile')).text = 'foo'
        git.add().addFilepattern('somefile').call()
        def ident = new PersonIdent('Anne', 'Onyme')
        commit(git, ident, 'First commit')
        commit(git, ident, 'Second commit')
        commit(git, ident, 'Third commit')
        gitRoot
    }

    def commit(Git git, PersonIdent ident, String msg) {
        def commit = git.commit()
        commit.committer = ident
        commit.sign = false
        commit.message = msg
        commit.call()
    }

}
