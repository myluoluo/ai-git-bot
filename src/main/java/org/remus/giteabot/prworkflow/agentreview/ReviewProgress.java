package org.remus.giteabot.prworkflow.agentreview;

import org.remus.giteabot.repository.RepositoryApiClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/** Owns one run's progress comment, including its update cadence and terminal state. */
final class ReviewProgress implements Consumer<String> {

    /** Minimum interval between activity updates; the first and terminal updates are immediate. */
    private static final Duration UPDATE_INTERVAL = Duration.ofSeconds(15);

    private final RepositoryApiClient repositoryClient;
    private final String owner;
    private final String repo;
    private final Long prNumber;
    private final Clock clock;
    private final Instant startedAt;
    private Long commentId;
    private Instant lastActivityAt;

    ReviewProgress(RepositoryApiClient repositoryClient, String owner, String repo, Long prNumber, Clock clock) {
        this.repositoryClient = repositoryClient;
        this.owner = owner;
        this.repo = repo;
        this.prNumber = prNumber;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    void start() {
        commentId = repositoryClient.createPullRequestComment(owner, repo, prNumber,
                render("Preparing repository", clock.instant()));
    }

    @Override
    public void accept(String activity) {
        Instant now = clock.instant();
        if (lastActivityAt != null && Duration.between(lastActivityAt, now).compareTo(UPDATE_INTERVAL) < 0) {
            return;
        }
        repositoryClient.updatePullRequestComment(owner, repo, prNumber, commentId, render(activity, now));
        lastActivityAt = now;
    }

    void finish(boolean success) {
        if (commentId != null) {
            repositoryClient.updatePullRequestComment(owner, repo, prNumber, commentId,
                    render(success ? "Review completed" : "Review failed", clock.instant()));
        }
    }

    private String render(String activity, Instant now) {
        return "🤖 **Agentic review progress**\n\nLatest activity: " + activity
                + "\n\nElapsed: " + Duration.between(startedAt, now).toSeconds() + "s"
                + " · Last update: " + now;
    }
}
