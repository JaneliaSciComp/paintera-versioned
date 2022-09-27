package org.janelia.saalfeldlab.util.n5;

import com.pivovarit.function.ThrowingRunnable;
import org.apache.commons.io.FileUtils;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.util.n5.universe.N5Factory;
import org.janelia.scicomp.v5.fs.V5FSWriter;
import org.janelia.scicomp.v5.lib.uri.V5FSURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class N5TestUtil {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static N5FSWriter fileSystemWriterAtTmpDir() throws IOException {
		return fileSystemWriterAtTmpDir(true);
	}

	public static N5FSWriter fileSystemWriterAtTmpDir(final boolean deleteOnExit) throws IOException {

	  final Path tmp = Files.createTempDirectory(null);

	  LOG.debug("Creating temporary N5Writer at {} (delete on exit? {})", tmp, deleteOnExit);

	  final File dir = tmp.toFile();
	  if (deleteOnExit) {
		dir.deleteOnExit();
		Runtime.getRuntime().addShutdownHook(new Thread(ThrowingRunnable.unchecked(() -> FileUtils.deleteDirectory(dir))));
	  }
	  return new N5Factory().openFSWriter(tmp.toAbsolutePath().toString());
	}

	public static V5FSWriter fileSystemVersionWriterAtTmpDir(final boolean deleteOnExit) throws IOException {

		final Path tmp = Files.createTempDirectory(null);


		LOG.debug("Creating temporary V5FSWriter at {} (delete on exit? {})", tmp, deleteOnExit);


		Path v5_indexes = Paths.get(tmp.toAbsolutePath().toString(), "v5_indexes");
		Path v5_kv = Paths.get(tmp.toAbsolutePath().toString() ,"v5_kv");
		V5FSURL v5FSURL = new V5FSURL(v5_indexes.toAbsolutePath().toString(),v5_kv.toAbsolutePath().toString());
		final File dir = tmp.toFile();
		if (deleteOnExit) {
			dir.deleteOnExit();
			Runtime.getRuntime().addShutdownHook(new Thread(ThrowingRunnable.unchecked(() -> FileUtils.deleteDirectory(dir))));
		}
		return new N5Factory().openVersionedWriter(v5FSURL.getURL());
	}

	static DatasetAttributes defaultAttributes()
	{
		return defaultAttributes(DataType.UINT8);
	}

	static DatasetAttributes defaultAttributes(DataType t)
	{
		return new DatasetAttributes(new long[] {1}, new int[] {1}, t, new RawCompression());
	}
}
