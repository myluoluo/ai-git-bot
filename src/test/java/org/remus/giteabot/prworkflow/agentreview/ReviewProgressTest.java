package org.remus.giteabot.prworkflow.agentreview;

import org.junit.jupiter.api.Test;
import org.remus.giteabot.repository.RepositoryApiClient;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewProgressTest {

    @Test
    void reusesOneCommentThrottlesActivityAndAlwaysWritesTerminalState() {
        RepositoryApiClient client = mock(RepositoryApiClient.class);
        when(client.createPullRequestComment(eq("owner"), eq("repo"), eq(1L), anyString())).thenReturn(42L);
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-13T00:00:00Z"));
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        ReviewProgress progress = new ReviewProgress(client, "owner", "repo", 1L, clock);

        progress.start();
        progress.accept("Reviewing round 1 (waiting for model)");
        now.set(now.get().plusSeconds(14));
        progress.accept("Inspecting repository: pr-diff");
        now.set(now.get().plusSeconds(1));
        progress.accept("Reviewing round 2 (waiting for model)");
        progress.finish(true);

        var bodies = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client).createPullRequestComment(eq("owner"), eq("repo"), eq(1L), anyString());
        verify(client, times(3)).updatePullRequestComment(eq("owner"), eq("repo"), eq(1L), eq(42L), bodies.capture());
        assertThat(bodies.getAllValues()).satisfiesExactly(
                body -> assertThat(body).contains("round 1", "Elapsed: 0s"),
                body -> assertThat(body).contains("round 2", "Elapsed: 15s"),
                body -> assertThat(body).contains("Review completed", "Elapsed: 15s"));
    }

    @Test
    void failedCommentWriteIsExposed() {
        RepositoryApiClient client = mock(RepositoryApiClient.class);
        when(client.createPullRequestComment(eq("owner"), eq("repo"), eq(1L), anyString())).thenReturn(42L);
        ReviewProgress progress = new ReviewProgress(client, "owner", "repo", 1L, Clock.systemUTC());
        progress.start();
        doThrow(new IllegalStateException("Comment update denied")).when(client)
                .updatePullRequestComment(eq("owner"), eq("repo"), eq(1L), eq(42L), anyString());

        assertThatThrownBy(() -> progress.accept("Reviewing round 1"))
                .isInstanceOf(IllegalStateException.class).hasMessage("Comment update denied");
    }
}
