package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Runs only for the releaseTest build type. */
@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class ParallelInstallIdentityTest {
    @Test
    public void testReleaseHasIndependentApplicationIdentity() {
        assertEquals("com.limelight.noir.anylabtest", BuildConfig.APPLICATION_ID);
        assertNotEquals("com.limelight.noir", BuildConfig.APPLICATION_ID);
        assertNotEquals("com.limelight.noirdebug", BuildConfig.APPLICATION_ID);
        assertEquals(BuildConfig.APPLICATION_ID,
                ApplicationProvider.getApplicationContext().getPackageName());
    }

    @Test
    public void testReleaseIsLabeledClearly() {
        assertEquals("Artemis AnyLab Test",
                ApplicationProvider.getApplicationContext().getString(R.string.app_label));
    }
}
