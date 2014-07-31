package com.ldbc.driver.runtime.coordination;

import com.ldbc.driver.runtime.ConcurrentErrorReporter;
import com.ldbc.driver.temporal.Duration;
import com.ldbc.driver.temporal.SystemTimeSource;
import com.ldbc.driver.temporal.Time;
import com.ldbc.driver.temporal.TimeSource;
import org.junit.Test;

import java.util.HashSet;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class CompletionTimeServiceAssistantTest {
    @Test
    public void shouldWaitUntilGctAdvancesAndReturnCorrectSuccessValue() throws CompletionTimeException {
        TimeSource timeSource = new SystemTimeSource();
        ConcurrentErrorReporter errorReporter = new ConcurrentErrorReporter();
        CompletionTimeServiceAssistant assistant = new CompletionTimeServiceAssistant();
        ConcurrentCompletionTimeService completionTimeService =
                assistant.newSynchronizedConcurrentCompletionTimeServiceFromPeerIds(new HashSet<String>());

        // initial gct should be null
        assertThat(completionTimeService.globalCompletionTime(), is(nullValue()));

        // there are no writers, gct will never advance
        boolean gctAdvancedSuccessfully =
                assistant.waitForGlobalCompletionTime(timeSource, Time.fromMilli(0), Duration.fromSeconds(1), completionTimeService, errorReporter);
        assertThat(gctAdvancedSuccessfully, is(false));
        assertThat(completionTimeService.globalCompletionTime(), is(nullValue()));

        LocalCompletionTimeWriter writer1 = completionTimeService.newLocalCompletionTimeWriter();
        LocalCompletionTimeWriter writer2 = completionTimeService.newLocalCompletionTimeWriter();

        // no initiated/completed times have been submitted, gct will never advance
        gctAdvancedSuccessfully =
                assistant.waitForGlobalCompletionTime(timeSource, Time.fromMilli(0), Duration.fromSeconds(1), completionTimeService, errorReporter);
        assertThat(gctAdvancedSuccessfully, is(false));
        assertThat(completionTimeService.globalCompletionTime(), is(nullValue()));

        assistant.writeInitiatedAndCompletedTimesToAllWriters(completionTimeService, Time.fromMilli(0));

        // gct can not be known at this stage, because more 0 times/values may arrive later
        // IT[ ] CT[0] --> GCT = ?
        gctAdvancedSuccessfully =
                assistant.waitForGlobalCompletionTime(timeSource, Time.fromMilli(0), Duration.fromSeconds(1), completionTimeService, errorReporter);
        assertThat(gctAdvancedSuccessfully, is(false));
        assertThat(completionTimeService.globalCompletionTime(), is(nullValue()));

        assistant.writeInitiatedAndCompletedTimesToAllWriters(completionTimeService, Time.fromMilli(1));

        // gct should now be 0, because no more 0 values/times can come after 1 values/times have been written to all writers
        // IT[ , ] CT[0,1] --> GCT = 0
        gctAdvancedSuccessfully =
                assistant.waitForGlobalCompletionTime(timeSource, Time.fromMilli(0), Duration.fromSeconds(1), completionTimeService, errorReporter);
        // TODO remove
        System.out.println(completionTimeService.globalCompletionTime());
        assertThat(gctAdvancedSuccessfully, is(true));
        assertThat(completionTimeService.globalCompletionTime(), is(Time.fromMilli(0)));
    }
}