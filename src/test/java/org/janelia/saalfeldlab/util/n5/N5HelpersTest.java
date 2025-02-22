package org.janelia.saalfeldlab.util.n5;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.imglib2.img.cell.CellGrid;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.CompressionAdapter;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5TreeNode;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.janelia.saalfeldlab.util.n5.N5Helpers.listAndSortScaleDatasets;

public class N5HelpersTest {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final Gson gsonWithCompression = new GsonBuilder().registerTypeHierarchyAdapter(Compression.class, CompressionAdapter.getJsonAdapter()).create();

  private static void assertEquals(final DatasetAttributes expected, final DatasetAttributes actual) {

	Assert.assertEquals(expected.getNumDimensions(), actual.getNumDimensions());
	Assert.assertArrayEquals(expected.getDimensions(), actual.getDimensions());
	Assert.assertArrayEquals(expected.getBlockSize(), actual.getBlockSize());
	Assert.assertEquals(expected.getDataType(), actual.getDataType());
	Assert.assertEquals(expected.getCompression().getClass(), actual.getCompression().getClass());
	Assert.assertEquals(expected.getCompression().getType(), actual.getCompression().getType());
	Assert.assertEquals(gsonWithCompression.toJson(expected.getCompression()), gsonWithCompression.toJson(actual.getCompression()));
  }

  @Test
  public void testAsCellGrid() {

	final long[] dims = new long[]{4, 5, 6};
	final int[] blockSize = new int[]{3, 2, 1};
	final DatasetAttributes attributes = new DatasetAttributes(dims, blockSize, DataType.UINT64, new GzipCompression());
	Assert.assertEquals(new CellGrid(dims, blockSize), N5Helpers.asCellGrid(attributes));
  }

  @Test
  public void testVolumetricDataGroup() {

	final String group = "some/group";
	Assert.assertEquals(group, N5Helpers.volumetricDataGroup(group, false));
	Assert.assertEquals(group + "/" + N5Helpers.PAINTERA_DATA_DATASET, N5Helpers.volumetricDataGroup(group, true));
  }

  @Test public void testIsMultiscale() throws IOException {

	final N5Writer writer = N5TestUtil.fileSystemWriterAtTmpDir(!LOG.isDebugEnabled());
	final String group = "group";
	writer.createGroup(group);

	Assert.assertFalse(N5Helpers.isMultiScale(writer, group));

	final DatasetAttributes attrs = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new RawCompression());
	writer.createDataset(group + "/s0", attrs);
	writer.createDataset(group + "/s1", attrs);
	writer.createDataset(group + "/s2", attrs);

	Assert.assertTrue(N5Helpers.isMultiScale(writer, group));

	writer.createGroup(group + "/MAKES_IT_FAIL");

	Assert.assertFalse(N5Helpers.isMultiScale(writer, group));

	writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
	Assert.assertTrue(N5Helpers.isMultiScale(writer, group));

  }

  @Test
  public void testListAndSortScaleDatasets() throws IOException {

	final N5Writer writer = N5TestUtil.fileSystemWriterAtTmpDir(!LOG.isDebugEnabled());
	final String group = "group";
	writer.createGroup(group);
	writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
	final DatasetAttributes attrs = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new RawCompression());
	writer.createDataset(group + "/s0", attrs);
	writer.createDataset(group + "/s1", attrs);
	writer.createDataset(group + "/s2", attrs);

	Assert.assertEquals(Arrays.asList("s0", "s1", "s2"), Arrays.asList(listAndSortScaleDatasets(writer, group)));

	Assert.assertEquals("group/s0", String.join("/", N5Helpers.getFinestLevelJoinWithGroup(writer, group)));
	Assert.assertEquals("group/s2", String.join("/", N5Helpers.getCoarsestLevelJoinWithGroup(writer, group)));
  }

  //  @Test FIXME meta
  public void testDiscoverDatasets() throws IOException {

	final N5Writer writer = N5TestUtil.fileSystemWriterAtTmpDir(!LOG.isDebugEnabled());
	final String group = "group";
	writer.createGroup(group);
	writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
	final DatasetAttributes attrs = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new RawCompression());
	writer.createDataset(group + "/s0", attrs);
	writer.createDataset(group + "/s1", attrs);
	writer.createDataset(group + "/s2", attrs);
	writer.createGroup("some_group");
	writer.createDataset("some_group/two", attrs);
	final var metadataTree = N5Helpers.parseMetadata(writer);
	Assert.assertTrue(metadataTree.isPresent());

	final var groups = metadataTree.stream()
			.flatMap(N5TreeNode::flattenN5Tree)
			.filter(node -> !node.childrenList().isEmpty())
			.map(N5TreeNode::getPath)
			.sorted()
			.collect(Collectors.toCollection(ArrayList::new));
	LOG.debug("Got groups {}", groups);
	Assert.assertEquals(Arrays.asList("/group", "/some_group/two"), groups);

  }

  @Test
  public void testGetDatasetAttributes() throws IOException {

	final N5Writer writer = N5TestUtil.fileSystemWriterAtTmpDir(!LOG.isDebugEnabled());
	final DatasetAttributes attributes = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new GzipCompression());

	// single scale
	final String dataset = "dataset";
	writer.createDataset(dataset, attributes);
	assertEquals(attributes, N5Helpers.getDatasetAttributes(writer, dataset));

	// multi scale
	final String group = "group";
	writer.createGroup(group);
	writer.createDataset("group/s0", attributes);
	assertEquals(attributes, N5Helpers.getDatasetAttributes(writer, group));

	writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
	assertEquals(attributes, N5Helpers.getDatasetAttributes(writer, group));

	// paintera data
	final String painteraData = "paintera-data";
	final String data = painteraData + "/data";
	writer.createDataset(data + "/s0", attributes);
	writer.createGroup(painteraData);
	writer.createGroup(data);
	writer.setAttribute(data, N5Helpers.MULTI_SCALE_KEY, true);

	Assert.assertNull(N5Helpers.getDatasetAttributes(writer, painteraData));
	assertEquals(attributes, N5Helpers.getDatasetAttributes(writer, data));

	writer.setAttribute(painteraData, N5Helpers.PAINTERA_DATA_KEY, Stream.of(1).collect(Collectors.toMap(o -> "type", o -> "raw")));
	assertEquals(attributes, N5Helpers.getDatasetAttributes(writer, painteraData));

  }

}
