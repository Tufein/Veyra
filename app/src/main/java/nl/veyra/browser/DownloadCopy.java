package nl.veyra.browser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/** Bounded-memory copying of the original response, with explicit completion checks. */
final class DownloadCopy {
    private DownloadCopy() { }

    static long copy(InputStream input, OutputStream output, long expected,
                     BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long count = 0;
        while (true) {
            checkCancelled(cancelled);
            int read = input.read(buffer);
            if (read == -1) break;
            checkCancelled(cancelled);
            if (read == 0) continue;
            output.write(buffer, 0, read);
            count += read;
            progress.accept(count);
        }
        checkCancelled(cancelled);
        if (expected >= 0 && count != expected) throw new IOException("Onvolledige download");
        output.flush();
        return count;
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("Geannuleerd");
    }
}
