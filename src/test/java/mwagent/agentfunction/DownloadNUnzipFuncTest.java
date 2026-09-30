package mwagent.agentfunction;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DownloadNUnzipFunc.unzipFile 테스트 - Zip Slip 방어
 */
class DownloadNUnzipFuncTest {

    @TempDir
    Path tempDir;

    private File makeZip(String... entryNames) throws IOException {
        File zip = tempDir.resolve("test.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            for (String name : entryNames) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write(("content of " + name).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zip;
    }

    @Test
    void unzipFile_WithNormalEntries_ShouldExtract() throws IOException {
        File zip = makeZip("a.txt", "sub/b.txt");
        File dest = tempDir.resolve("out").toFile();

        DownloadNUnzipFunc.unzipFile(zip, dest);

        assertThat(new File(dest, "a.txt")).exists();
        assertThat(new File(dest, "sub/b.txt")).exists();
    }

    @Test
    void unzipFile_WithTraversalEntry_ShouldRejectAndNotWriteOutside() throws IOException {
        File zip = makeZip("../evil.txt");
        File dest = tempDir.resolve("out").toFile();

        assertThatThrownBy(() -> DownloadNUnzipFunc.unzipFile(zip, dest))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("outside of the target dir");
        assertThat(tempDir.resolve("evil.txt")).doesNotExist();
    }

    @Test
    void unzipFile_WithDeepTraversalEntry_ShouldReject() throws IOException {
        File zip = makeZip("sub/../../../evil.txt");
        File dest = tempDir.resolve("out").toFile();

        assertThatThrownBy(() -> DownloadNUnzipFunc.unzipFile(zip, dest))
            .isInstanceOf(IOException.class);
        assertThat(Files.exists(tempDir.getParent().resolve("evil.txt"))).isFalse();
    }
}
