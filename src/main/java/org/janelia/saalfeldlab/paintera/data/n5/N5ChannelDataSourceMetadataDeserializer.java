package org.janelia.saalfeldlab.paintera.data.n5;

import bdv.util.volatiles.SharedQueue;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class N5ChannelDataSourceMetadataDeserializer implements JsonDeserializer<N5ChannelDataSourceMetadata<?, ?>> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String META_CLASS_KEY = "metaType";

  private static final String META_KEY = "meta";

  private static final String TRANSFORM_KEY = "transform";

  private final SharedQueue queue;

  private final int priority;

  public N5ChannelDataSourceMetadataDeserializer(final SharedQueue queue, final int priority) {

	super();
	this.queue = queue;
	this.priority = priority;
  }

  @Override
  public N5ChannelDataSourceMetadata<?, ?> deserialize(final JsonElement el, final Type type, final JsonDeserializationContext
		  context)
		  throws JsonParseException {

	try {
	  LOG.debug("Deserializing from {}", el);
	  final String clazz = el.getAsJsonObject().get(META_CLASS_KEY).getAsString();
	  final N5Meta meta = (N5Meta)context.deserialize(
			  el.getAsJsonObject().get(META_KEY),
			  Class.forName(clazz));
	  final AffineTransform3D transform = context.deserialize(
			  el.getAsJsonObject().get(TRANSFORM_KEY),
			  AffineTransform3D.class);

	  JsonObject obj = el.getAsJsonObject();
	  final int channelDimension = obj.get(N5ChannelDataSourceSerializer.CHANNEL_DIMENSION_KEY).getAsInt();
	  final long[] channels = Optional.ofNullable(obj.get(N5ChannelDataSourceSerializer.CHANNELS_KEY)).map(e -> (long[])context.deserialize(e, long[].class))
			  .orElse(null);
	  LOG.debug("Deserialized transform: {}", transform);
	  final var metadataState = MetadataUtils.tmpCreateMetadataState(meta);
	  return N5ChannelDataSourceMetadata.valueExtended(metadataState, "", queue, priority, channelDimension, channels, Double.NaN);
	} catch (IOException | ClassNotFoundException | DataTypeNotSupported e) {
	  throw new JsonParseException(e);
	}

  }

  @Plugin(type = StatefulSerializer.DeserializerFactory.class)
  public static class Factory implements StatefulSerializer.DeserializerFactory<N5ChannelDataSourceMetadata<?, ?>,
		  N5ChannelDataSourceMetadataDeserializer> {

	@Override
	public N5ChannelDataSourceMetadataDeserializer createDeserializer(
			final Arguments arguments,
			final Supplier<String> projectDirectory,
			final IntFunction<SourceState<?, ?>> dependencyFromIndex) {

	  return new N5ChannelDataSourceMetadataDeserializer(arguments.viewer.getQueue(), 0);
	}

	@Override
	public Class<N5ChannelDataSourceMetadata<?, ?>> getTargetClass() {

	  return (Class<N5ChannelDataSourceMetadata<?, ?>>)(Class<?>)N5ChannelDataSourceMetadata.class;
	}
  }

}
