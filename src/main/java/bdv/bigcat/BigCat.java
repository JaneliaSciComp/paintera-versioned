package bdv.bigcat;

import static bdv.bigcat.CombinedImgLoader.SetupIdAndLoader.setupIdAndLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.ws.http.HTTPException;

import org.scijava.ui.behaviour.io.InputTriggerConfig;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.BigDataViewer;
import bdv.ViewerSetupImgLoader;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.composite.CompositeProjector;
import bdv.bigcat.control.MergeController;
import bdv.bigcat.ui.ARGBConvertedLabelsSource;
import bdv.bigcat.ui.AbstractARGBConvertedLabelsSource;
import bdv.bigcat.ui.RandomSaturatedARGBStream;
import bdv.img.SetCache;
import bdv.img.cache.Cache;
import bdv.img.dvid.LabelblkMultisetSetupImageLoader;
import bdv.img.dvid.Uint8blkImageLoader;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.util.dvid.DatasetKeyValue;
import bdv.util.dvid.Repository;
import bdv.util.dvid.Server;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.TriggerBehaviourBindings;
import bdv.viewer.ViewerOptions;
import bdv.viewer.render.AccumulateProjectorFactory;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.generic.sequence.ImgLoaderHints;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import net.imglib2.display.ScaledARGBConverter;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.view.Views;

public class BigCat
{
	public static < A extends ViewerSetupImgLoader< ? extends NumericType< ? >, ? > & SetCache > BigDataViewer createViewer(
			final String windowTitle,
			final A[] rawDataLoaders,
			final AbstractARGBConvertedLabelsSource[] labelSources,
			final SetCache[] labelLoaders,
			final List< Composite< ARGBType, ARGBType > > composites )
	{
		/* raw pixels */
		final CombinedImgLoader.SetupIdAndLoader[] loaders = new CombinedImgLoader.SetupIdAndLoader[ rawDataLoaders.length ];
		int setupId = 0;
		for ( int i = 0; i < rawDataLoaders.length; ++i )
			loaders[ i ] = setupIdAndLoader( setupId++, rawDataLoaders[ i ] );

		final CombinedImgLoader imgLoader = new CombinedImgLoader( loaders );
		for ( int i = 0; i < rawDataLoaders.length; ++i )
			rawDataLoaders[ i ].setCache( imgLoader.cache );

		final ArrayList< TimePoint > timePointsList = new ArrayList< >();
		final Map< Integer, BasicViewSetup > setups = new HashMap< >();
		final ArrayList< ViewRegistration > viewRegistrationsList = new ArrayList< >();
		for ( final CombinedImgLoader.SetupIdAndLoader loader : loaders )
		{
			timePointsList.add( new TimePoint( 0 ) );
			setups.put( loader.setupId, new BasicViewSetup( loader.setupId, null, null, null ) );
			viewRegistrationsList.add( new ViewRegistration( 0, loader.setupId ) );
		}

		final TimePoints timepoints = new TimePoints( timePointsList );
		final ViewRegistrations reg = new ViewRegistrations( viewRegistrationsList );
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( timepoints, setups, imgLoader, null );
		final SpimDataMinimal spimData = new SpimDataMinimal( null, seq, reg );

		final ArrayList< ConverterSetup > converterSetups = new ArrayList< ConverterSetup >();
		final ArrayList< SourceAndConverter< ? > > sources = new ArrayList< SourceAndConverter< ? > >();

		BigDataViewer.initSetups( spimData, converterSetups, sources );

		/* labels */
		for ( final SetCache setCache : labelLoaders )
			setCache.setCache( imgLoader.cache );
	
		for ( final AbstractARGBConvertedLabelsSource source : labelSources )
		{
			final ScaledARGBConverter.ARGB converter = new ScaledARGBConverter.ARGB( 0, 255 );
			final ScaledARGBConverter.VolatileARGB vconverter = new ScaledARGBConverter.VolatileARGB( 0, 255 );

			final SourceAndConverter< VolatileARGBType > vsoc = new SourceAndConverter< VolatileARGBType >( source, vconverter );
			final SourceAndConverter< ARGBType > soc = new SourceAndConverter< ARGBType >( source.nonVolatile(), converter, vsoc );
			sources.add( soc );

			final RealARGBColorConverterSetup fragmentsConverterSetup = new RealARGBColorConverterSetup( 2, converter, vconverter );
			converterSetups.add( fragmentsConverterSetup );
		}

		/* composites */
		final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositesMap = new HashMap< Source< ? >, Composite< ARGBType, ARGBType > >();
		for ( int i = 0; i < composites.size(); ++i )
			sourceCompositesMap.put( sources.get( i ).getSpimSource(), composites.get( i ) );

		final AccumulateProjectorFactory< ARGBType > projectorFactory = new CompositeProjector.CompositeProjectorFactory< ARGBType >( sourceCompositesMap );

		final BigDataViewer bdv = new BigDataViewer( converterSetups, sources, null, timepoints.size(), imgLoader.getCache(), windowTitle, null, ViewerOptions.options().accumulateProjectorFactory( projectorFactory ).numRenderingThreads( 16 ).targetRenderNanos(10000000) );

		final AffineTransform3D transform = new AffineTransform3D();
//		transform.set(
//			4.3135842398185575, -1.0275561336713027E-16, 1.1102230246251565E-16, -14207.918453952327,
//			-1.141729037412541E-17, 4.313584239818558, 1.0275561336713028E-16, -9482.518144778587,
//			1.1102230246251565E-16, -1.141729037412541E-17, 4.313584239818559, -17181.48737890195 );
//		transform.set( 0, 0, 1, 0, 0, 1, 0, 0, -1, 0, 0, 0 );
		bdv.getViewer().setCurrentViewerTransform( transform );
		bdv.getViewer().setDisplayMode( DisplayMode.FUSED );

		/* separate source min max */
		for ( final ConverterSetup converterSetup : converterSetups )
		{
			bdv.getSetupAssignments().removeSetupFromGroup( converterSetup, bdv.getSetupAssignments().getMinMaxGroups().get( 0 ) );
			converterSetup.setDisplayRange( 0, 255 );
		}

		return bdv;
	}

	public static void main( final String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		final String url = "http://vm570.int.janelia.org:8080";
		final String labelsBase = "multisets-labels-downscaled-zero-extended-2"; // "multisets-labels-downscaled";
		final String uuid = "4668221206e047648f622dc4690ff7dc";

		final Server server = new Server( url );
		final Repository repo = new Repository( server, uuid );

//		final DatasetKeyValue[] stores = new DatasetKeyValue[ 4 ];
		final DatasetKeyValue[] stores = new DatasetKeyValue[ 0 ];

		for ( int i = 0; i < stores.length; ++i )
		{
			stores[ i ] = new DatasetKeyValue( repo.getRootNode(), labelsBase + "-" + ( 1 << ( i + 1 ) ) );

			try
			{
				repo.getRootNode().createDataset( stores[ i ].getName(), DatasetKeyValue.TYPE );
			}
			catch ( final HTTPException e )
			{
				e.printStackTrace( System.err );
			}
		}

//		final double[][] resolutions = new double[][]{
//				{ 1, 1, 1 },
//				{ 2, 2, 2 },
//				{ 4, 4, 4 },
//				{ 8, 8, 8 },
//				{16, 16, 16 } };

		final double[][] resolutions = new double[][] { { 1, 1, 1 } };

		try
		{
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );

			/* data sources */
//			final DvidGrayscale8ImageLoader dvidGrayscale8ImageLoader = new DvidGrayscale8ImageLoader(
//					"http://emrecon100.janelia.priv/api",
//					"2a3fd320aef011e4b0ce18037320227c",
//					"grayscale" );
//			final DvidLabels64MultisetSetupImageLoader dvidLabelsMultisetImageLoader = new DvidLabels64MultisetSetupImageLoader(
//					1,
//					"http://emrecon100.janelia.priv/api",
//					"2a3fd320aef011e4b0ce18037320227c",
//					"bodies",
//					resolutions,
//					stores );

			/* data sources */
//			final Uint8blkImageLoader dvidGrayscale8ImageLoader = new Uint8blkImageLoader( "http://emdata2.int.janelia.org:7000/api", "e402c09ddd0f45e980d9be6e9fcb9bd0", "grayscale" );
//			final LabelblkMultisetSetupImageLoader dvidLabelsMultisetImageLoader = new LabelblkMultisetSetupImageLoader( 1, "http://emdata2.int.janelia.org:7000/api", "e402c09ddd0f45e980d9be6e9fcb9bd0", "labels1104", resolutions, stores );
			final Uint8blkImageLoader dvidGrayscale8ImageLoader = new Uint8blkImageLoader(
					"http://104.197.250.147:8000/api",
					"de02b03701e84eb3830e944919fbec5a",
					"grayscale" );
			final LabelblkMultisetSetupImageLoader dvidLabelsMultisetImageLoader = new LabelblkMultisetSetupImageLoader(
					1,
					"http://104.197.250.147:8000/api",
					"de02b03701e84eb3830e944919fbec5a",
					"7colseg1",
					resolutions,
					stores );
//			final ARGBConvertedLabelsSetupImageLoader dvidLabelsARGBImageLoader = new ARGBConvertedLabelsSetupImageLoader(
//					2,
//					dvidLabelsMultisetImageLoader );

			final FragmentSegmentAssignment assignment = new FragmentSegmentAssignment();

//			final GoldenAngleSaturatedARGBStream colorStream = new GoldenAngleSaturatedARGBStream();
			final RandomSaturatedARGBStream colorStream = new RandomSaturatedARGBStream( assignment );
			colorStream.setAlpha( 0x30 );
			final ARGBConvertedLabelsSource convertedLabels = new ARGBConvertedLabelsSource( 2, dvidLabelsMultisetImageLoader, colorStream );

			final CombinedImgLoader imgLoader = new CombinedImgLoader( setupIdAndLoader( 0, dvidGrayscale8ImageLoader ) );
			dvidGrayscale8ImageLoader.setCache( imgLoader.cache );
			dvidLabelsMultisetImageLoader.setCache( imgLoader.cache );

			final TimePoints timepoints = new TimePoints( Arrays.asList( new TimePoint( 0 ) ) );
			final Map< Integer, BasicViewSetup > setups = new HashMap< Integer, BasicViewSetup >();
			setups.put( 0, new BasicViewSetup( 0, null, null, null ) );
			final ViewRegistrations reg = new ViewRegistrations( Arrays.asList( new ViewRegistration( 0, 0 ) ) );

			final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( timepoints, setups, imgLoader, null );
			final SpimDataMinimal spimData = new SpimDataMinimal( null, seq, reg );

			final ArrayList< ConverterSetup > converterSetups = new ArrayList< ConverterSetup >();
			final ArrayList< SourceAndConverter< ? > > sources = new ArrayList< SourceAndConverter< ? > >();

			BigDataViewer.initSetups( spimData, converterSetups, sources );

			final ScaledARGBConverter.ARGB converter = new ScaledARGBConverter.ARGB( 0, 255 );
			final ScaledARGBConverter.VolatileARGB vconverter = new ScaledARGBConverter.VolatileARGB( 0, 255 );

			final SourceAndConverter< VolatileARGBType > vsoc = new SourceAndConverter< VolatileARGBType >( convertedLabels, vconverter );
			final SourceAndConverter< ARGBType > soc = new SourceAndConverter< ARGBType >( convertedLabels.nonVolatile(), converter, vsoc );
			sources.add( soc );
			converterSetups.add( new RealARGBColorConverterSetup( 2, converter, vconverter ) );

			/* composites */
			final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList< Composite< ARGBType, ARGBType > >();
			composites.add( new CompositeCopy< ARGBType >() );
			composites.add( new ARGBCompositeAlphaYCbCr() );
//			composites.add( new ARGBCompositeAlpha() );
			final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositesMap = new HashMap< Source< ? >, Composite< ARGBType, ARGBType > >();
			sourceCompositesMap.put( sources.get( 0 ).getSpimSource(), composites.get( 0 ) );
			sourceCompositesMap.put( sources.get( 1 ).getSpimSource(), composites.get( 1 ) );
			final AccumulateProjectorFactory< ARGBType > projectorFactory = new CompositeProjector.CompositeProjectorFactory< ARGBType >( sourceCompositesMap );

			final Cache cache = imgLoader.getCache();
			final String windowTitle = "bigcat";
			final BigDataViewer bdv = new BigDataViewer( converterSetups, sources, null, timepoints.size(), cache, windowTitle, null, ViewerOptions.options().accumulateProjectorFactory( projectorFactory ).numRenderingThreads( 16 ) );

			final AffineTransform3D transform = new AffineTransform3D();
			transform.set( 30.367584357121462, -7.233983582120427E-16, 7.815957561302E-16, -103163.46077512865, -8.037759535689243E-17, 30.367584357121462, 7.233983582120427E-16, -68518.45769918368, 7.815957561302E-16, -8.037759535689243E-17, 30.36758435712147, -120957.47720498207 );
			bdv.getViewer().setCurrentViewerTransform( transform );
			bdv.getViewer().setDisplayMode( DisplayMode.FUSED );

			bdv.getViewerFrame().setSize( 1280 - 32, 720 - 28 - 32 );

			bdv.getViewerFrame().setVisible( true );

			final MergeController mergeController = new MergeController(
					bdv.getViewer(),
					RealViews.affineReal(
							Views.interpolate(
									Views.extendValue(
											dvidLabelsMultisetImageLoader.getVolatileImage( 0, 0, ImgLoaderHints.LOAD_COMPLETELY ),
											new VolatileLabelMultisetType() ),
									new NearestNeighborInterpolatorFactory< VolatileLabelMultisetType >() ),
							dvidGrayscale8ImageLoader.getMipmapTransforms()[ 0 ] ),
					colorStream,
					assignment,
					new InputTriggerConfig(),
					bdv.getViewerFrame().getKeybindings(),
					new InputTriggerConfig() );

			final TriggerBehaviourBindings bindings = bdv.getViewerFrame().getTriggerbindings();
			bindings.addBehaviourMap( "bigcat", mergeController.getBehaviourMap() );
			bindings.addInputTriggerMap( "bigcat", mergeController.getInputTriggerMap() );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}
}
