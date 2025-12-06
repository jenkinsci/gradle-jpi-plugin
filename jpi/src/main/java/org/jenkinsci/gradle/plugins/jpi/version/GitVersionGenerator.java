package org.jenkinsci.gradle.plugins.jpi.version;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.StreamSupport;

/**
 * Generates version strings based on Git repository information.
 * <p>
 * This class uses JGit to extract information from a Git repository and
 * generate version strings based on commit history, tags, and other repository
 * information.
 */
public class GitVersionGenerator {
    private final int abbrevLength;
    private final String versionPrefix;
    private final String versionFormat;
    private final boolean sanitize;
    private final boolean allowDirty;
    private final Path gitRoot;

    /**
     * Constructs a new Git version generator with the specified parameters.
     *
     * @param gitRoot The root directory of the Git repository
     * @param abbrevLength The length to abbreviate commit hashes to
     * @param versionPrefix The prefix to use for the version string
     * @param versionFormat The format string for the version
     * @param allowDirty Whether to allow dirty working directory
     * @param sanitize Whether to sanitize the version string
     */
    public GitVersionGenerator(Path gitRoot, int abbrevLength, String versionPrefix, String versionFormat, boolean allowDirty, boolean sanitize) {
        this.gitRoot = gitRoot;
        // TODO abbrevLength should be 2 minimum
        this.abbrevLength = abbrevLength;
        this.versionPrefix = versionPrefix;
        this.versionFormat = versionFormat;
        this.sanitize = sanitize;
        this.allowDirty = allowDirty;
    }

    /**
     * Generates a version string based on the Git repository information.
     *
     * @return A GitVersion object containing the full and abbreviated version strings
     * @throws RuntimeException if there is an error accessing the Git repository
     */
    public GitVersion generate() {
        try (Git git = Git.open(gitRoot.toFile())) {
            Repository repo = git.getRepository();
            Status status = git.status().call();
            checkGitStatus(status);
            ObjectId head = repo.resolve("HEAD");
            if (head == null) {
                throw new RuntimeException("Cannot resolve HEAD for repository '" + gitRoot + "'");
            }
            try (ObjectReader reader = repo.newObjectReader()) {
                String abbrevHash = reader.abbreviate(head, abbrevLength).name();
                long headDepth = commitDepth(repo, head);
                if (sanitize) {
                    abbrevHash = sanitize(abbrevHash);
                }
                return new GitVersion(head.getName(), versionPrefix + String.format(versionFormat, headDepth, abbrevHash));
            }
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sanitizes a Git hash by replacing certain characters.
     * <p>
     * This method replaces 'a' and 'b' characters with 'a_' and 'b_' respectively,
     * which can be useful for certain version string formats.
     *
     * @param hash The Git hash to sanitize
     * @return The sanitized hash
     */
    static String sanitize(String hash) {
        return hash.replaceAll("[ab]", "$0_");
    }

    /**
     * Checks if the Git repository has pending changes.
     * <p>
     * If the repository has pending changes and allowDirty is false,
     * this method throws a RuntimeException.
     *
     * @param status The Git status to check
     * @throws RuntimeException if the repository has pending changes and allowDirty is false
     */
    private void checkGitStatus(Status status) {
        if (!status.isClean() && !allowDirty) {
            StringBuilder str = new StringBuilder("Repository '" + gitRoot + "' has some pending changes:");
            if (!status.getUntracked().isEmpty()) {
                str.append("\n- untracked files: ").append(status.getUntracked());
            }
            if (!status.getUncommittedChanges().isEmpty()) {
                str.append("\n- uncommitted changes: ").append(status.getUncommittedChanges());
            }
            throw new RuntimeException(str.toString());
        }
    }

    /**
     * Calculates the depth of a commit in the Git history.
     * <p>
     * This method counts the number of commits from the beginning of the repository
     * to the specified commit.
     *
     * @param repository The Git repository
     * @param objectId The commit ID to calculate the depth for
     * @return The depth of the commit in the history
     * @throws IOException if there is an error accessing the repository
     * @throws GitAPIException if there is an error executing Git commands
     */
    private long commitDepth(Repository repository, ObjectId objectId) throws IOException, GitAPIException {
        try (RevWalk walk = new RevWalk(repository)) {
            walk.setRetainBody(false);
            walk.markStart(walk.parseCommit(objectId));
            return StreamSupport.stream(walk.spliterator(), false).count();
        }
    }

    /**
     * Represents a Git version with full and abbreviated hash information.
     */
    static class GitVersion {
        private final String fullHash;
        private final String abbreviatedHash;

        /**
         * Constructs a new Git version with the specified hashes.
         *
         * @param fullHash The full Git hash
         * @param abbreviatedHash The abbreviated Git hash
         */
        public GitVersion(String fullHash, String abbreviatedHash) {
            this.fullHash = fullHash;
            this.abbreviatedHash = abbreviatedHash;
        }

        @Override
        public String toString() {
            return String.format("%s%n%s", abbreviatedHash, fullHash);
        }

        /**
         * Gets the abbreviated hash.
         *
         * @return The abbreviated Git hash
         */
        public String getAbbreviatedHash() {
            return abbreviatedHash;
        }

        /**
         * Gets the full hash.
         *
         * @return The full Git hash
         */
        public String getFullHash() {
            return fullHash;
        }
    }

}
