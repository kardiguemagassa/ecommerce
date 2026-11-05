package com.kmgtechnologies.ecommerce.media;

import com.kmgtechnologies.ecommerce.exception.ImageTooLargeException;
import com.kmgtechnologies.ecommerce.exception.StorageUnavailableException;
import com.kmgtechnologies.ecommerce.exception.UnsupportedImageException;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaFilesTest {

    private static final byte[] UPLOAD = {1, 2, 3};
    private static final ProcessedImage JPEG = new ProcessedImage(new byte[]{(byte) 0xFF, (byte) 0xD8}, 256, 256, "png");

    @Mock
    private ImageProcessor processor;

    @Mock
    private MediaStorage storage;

    private MediaFiles mediaFiles() {
        return new MediaFiles(processor, storage, ObservationRegistry.NOOP);
    }

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void store_writesTheReEncodedImageUnderANewRandomKey() {
        when(processor.process(ImageKind.AVATAR, UPLOAD)).thenReturn(JPEG);

        String key = mediaFiles().store(ImageKind.AVATAR, file(UPLOAD));
        String other = mediaFiles().store(ImageKind.AVATAR, file(UPLOAD));

        assertThat(key).matches("avatars/[0-9a-f-]{36}\\.jpg").isNotEqualTo(other);
        verify(storage).put(key, JPEG.jpeg(), "image/jpeg");
    }

    @Test
    void store_deletesTheNewObjectWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        when(processor.process(ImageKind.PRODUCT, UPLOAD)).thenReturn(JPEG);

        String key = mediaFiles().store(ImageKind.PRODUCT, file(UPLOAD));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete(key);
    }

    @Test
    void store_keepsTheNewObjectWhenTheTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        when(processor.process(ImageKind.PRODUCT, UPLOAD)).thenReturn(JPEG);

        mediaFiles().store(ImageKind.PRODUCT, file(UPLOAD));
        commit();

        verify(storage, never()).delete(anyString());
    }

    @Test
    void store_refusesAFileAboveTheLimitOfItsKind_withoutReadingIt() {
        MockMultipartFile tooLarge = file(new byte[(int) ImageKind.AVATAR.maxBytes() + 1]);

        assertThatThrownBy(() -> mediaFiles().store(ImageKind.AVATAR, tooLarge))
                .isInstanceOf(ImageTooLargeException.class);
        verifyNoInteractions(processor, storage);
    }

    @Test
    void store_writesNothingWhenTheImageIsRefused() {
        when(processor.process(ImageKind.AVATAR, UPLOAD)).thenThrow(new UnsupportedImageException());

        assertThatThrownBy(() -> mediaFiles().store(ImageKind.AVATAR, file(UPLOAD)))
                .isInstanceOf(UnsupportedImageException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void store_propagatesAStorageOutage() {
        when(processor.process(ImageKind.AVATAR, UPLOAD)).thenReturn(JPEG);
        doThrow(new StorageUnavailableException("down", null)).when(storage).put(anyString(), any(), eq("image/jpeg"));

        assertThatThrownBy(() -> mediaFiles().store(ImageKind.AVATAR, file(UPLOAD)))
                .isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    void deleteAfterCommit_waitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        mediaFiles().deleteAfterCommit("products/old.jpg");
        verify(storage, never()).delete(anyString());

        commit();
        verify(storage).delete("products/old.jpg");
    }

    @Test
    void deleteAfterCommit_keepsTheObjectWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        mediaFiles().deleteAfterCommit("products/old.jpg");
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage, never()).delete(anyString());
    }

    @Test
    void deleteAfterCommit_deletesAtOnceWithoutTransaction_andIgnoresNoImage() {
        mediaFiles().deleteAfterCommit(null);
        verifyNoInteractions(storage);

        mediaFiles().deleteAfterCommit("avatars/old.jpg");
        verify(storage).delete("avatars/old.jpg");
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "photo.png", "image/png", content);
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        complete(TransactionSynchronization.STATUS_COMMITTED);
    }

    private static void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }
}
