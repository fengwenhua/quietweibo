package com.quietweibo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** 兼容版本清单与运行门禁。 */
public class VersionGateTest {

    @Test
    public void testedVersionsAreNewestFirst() {
        List<VersionGate.TestedVersion> versions = VersionGate.testedVersions();
        assertTrue(versions.size() >= 2);
        assertEquals("16.11.0", versions.get(0).name);
        assertEquals("16.10.3", versions.get(1).name);
        assertEquals("16.11.0", VersionGate.latestTestedVersion());
        assertEquals(8250L, versions.get(0).versionCode);
        assertEquals(8234L, versions.get(1).versionCode);
    }

    @Test
    public void testedVersionsAreRecognised() {
        assertTrue(VersionGate.isTested("16.11.0"));
        assertTrue(VersionGate.isTested("16.10.3"));
        assertFalse(VersionGate.isTested("16.10.2"));
        assertFalse(VersionGate.isTested(null));
    }

    @Test
    public void unknownSixteenVersionsRemainFailOpen() {
        assertTrue(VersionGate.isSupported("16.12.0"));
        assertTrue(VersionGate.isSupported("17.0.0"));
        assertFalse(VersionGate.isSupported("15.9.0"));
        assertFalse(VersionGate.isTested("16.12.0"));
    }
}
