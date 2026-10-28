package nl.veyra.browser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DownloadCopyTest {
    @Test public void copiesOriginalResponseBytesAndReportsActualProgress() throws Exception {
        byte[] source = new byte[5 * 65536 + 13]; new Random(812).nextBytes(source);
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        AtomicLong progress = new AtomicLong();
        assertEquals(source.length, DownloadCopy.copy(new ByteArrayInputStream(source), destination,
                source.length, () -> false, progress::set));
        assertArrayEquals(source, destination.toByteArray()); assertEquals(source.length, progress.get());
    }

    @Test public void truncatedResponsesNeverReportSuccessfulCompletion() throws Exception {
        try {
            DownloadCopy.copy(new ByteArrayInputStream(new byte[12]), new ByteArrayOutputStream(),
                    15, () -> false, ignored -> {}); fail();
        } catch (IOException expected) { assertEquals("Onvolledige download", expected.getMessage()); }
    }

    @Test public void cancellationStopsTheNextChunkAndRetainsTheWrittenByteCount() throws Exception {
        AtomicBoolean cancel = new AtomicBoolean(); AtomicLong progress = new AtomicLong();
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        try {
            DownloadCopy.copy(new ByteArrayInputStream(new byte[200000]), destination, -1,
                    cancel::get, count -> { progress.set(count); cancel.set(true); }); fail();
        } catch (InterruptedIOException expected) {
            assertTrue(destination.size() > 0 && destination.size() < 200000);
            assertEquals(destination.size(), progress.get());
        }
    }

    @Test public void storageFailuresPropagateInsteadOfReportingACompletedDownload() throws Exception {
        OutputStream full = new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("Storage full"); }
        };
        try {
            DownloadCopy.copy(new ByteArrayInputStream(new byte[50]), full, -1, () -> false, ignored -> {}); fail();
        } catch (IOException expected) { assertEquals("Storage full", expected.getMessage()); }
    }

    @Test public void unknownLengthAndEmptyResponsesAreSupported() throws Exception {
        assertEquals(0, DownloadCopy.copy(new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(), -1, () -> false, ignored -> {}));
        assertEquals(7, DownloadCopy.copy(new ByteArrayInputStream(new byte[7]),
                new ByteArrayOutputStream(), -1, () -> false, ignored -> {}));
    }
}
