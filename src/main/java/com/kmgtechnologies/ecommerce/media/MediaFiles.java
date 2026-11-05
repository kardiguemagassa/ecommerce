package com.kmgtechnologies.ecommerce.media;

import com.kmgtechnologies.ecommerce.exception.ImageTooLargeException;
import com.kmgtechnologies.ecommerce.exception.InvalidImageException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * Images of the shop, kept consistent with the database that references them. Called inside the transaction that
 * changes the reference:
 * <ul>
 *   <li>{@link #store}: the new object is written first; if the transaction then rolls back (concurrent change,
 *       constraint), the object is deleted, so no orphan is left behind;</li>
 *   <li>{@link #deleteAfterCommit}: the replaced or removed object is deleted only once the database no longer
 *       references it, so a rollback never leaves a broken image.</li>
 * </ul>
 */
@Component
public class MediaFiles {

    static final String UPLOAD_OBSERVATION = "ecommerce.media.upload";

    private final ImageProcessor processor;
    private final MediaStorage storage;
    private final ObservationRegistry observationRegistry;

    public MediaFiles(ImageProcessor processor, MediaStorage storage, ObservationRegistry observationRegistry) {
        this.processor = processor;
        this.storage = storage;
        this.observationRegistry = observationRegistry;
    }

    /** Checks, re-encodes and stores the upload under a new random key, returned. */
    public String store(ImageKind kind, MultipartFile file) {
        Observation observation = Observation.createNotStarted(UPLOAD_OBSERVATION, observationRegistry)
                .contextualName("media upload")
                .lowCardinalityKeyValue("kind", kind.name().toLowerCase(Locale.ROOT))
                .highCardinalityKeyValue("upload.bytes", String.valueOf(file.getSize()))
                .start();
        String outcome = "success";
        try (Observation.Scope ignored = observation.openScope()) {
            if (file.getSize() > kind.maxBytes()) {
                // Checked before reading: the multipart limit is the one of the largest kind.
                throw new ImageTooLargeException(kind.maxBytes());
            }
            ProcessedImage image = processor.process(kind, read(file));
            observation.lowCardinalityKeyValue("format", image.sourceFormat());
            String key = kind.folder() + "/" + UUID.randomUUID() + ".jpg";
            storage.put(key, image.jpeg(), ProcessedImage.CONTENT_TYPE);
            onRollback(() -> storage.delete(key));
            return key;
        } catch (RuntimeException e) {
            outcome = "rejected";
            observation.error(e);
            throw e;
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
        }
    }

    /** Deletes the object once the current transaction commits (at once without transaction); null is ignored. */
    public void deleteAfterCommit(String key) {
        if (key == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.delete(key);
                }
            });
        } else {
            storage.delete(key);
        }
    }

    private static void onRollback(Runnable cleanup) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        cleanup.run();
                    }
                }
            });
        }
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("The file cannot be read");
        }
    }
}
