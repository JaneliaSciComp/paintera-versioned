package bdv.bigcat.viewer.atlas.mode.paint;

import java.lang.invoke.MethodHandles;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.mode.Mode;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState.LabelSourceState;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.util.IdService;
import bdv.viewer.Source;

public class SelectNextId
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final SourceInfo sourceInfo;

	private final Mode mode;

	public SelectNextId( final SourceInfo sourceInfo, final Mode mode )
	{
		super();
		this.sourceInfo = sourceInfo;
		this.mode = mode;
	}

	public void getNextIds( final int count )
	{
		getNextId( ( selectedIds, idService ) -> selectedIds.activate( idService.next( count ) ) );
	}

	public void getNextId()
	{
		getNextId( ( selectedIds, idService ) -> selectedIds.activate( idService.next() ) );
	}

	private void getNextId( final BiConsumer< SelectedIds, IdService > action )
	{

		final Source< ? > currentSource = sourceInfo.currentSourceProperty().get();

		if ( currentSource == null )
		{
			LOG.warn( "No current source -- cannot create new id." );
			return;
		}

		final AtlasSourceState< ?, ? > state = sourceInfo.getState( currentSource );

		// TODO should we create ids also for invisible sources?
		if ( !state.visibleProperty().get() )
		{
			LOG.warn( "Source {} is not visible -- cannot create new id.", currentSource );
			return;
		}

		if ( !( state instanceof LabelSourceState< ?, ? > ) )
		{
			LOG.warn( "Source {} is not a label source -- cannot create new id.", currentSource );
			return;
		}

		final LabelSourceState< ?, ? > labelState = ( LabelSourceState< ?, ? > ) state;

		final IdService idService = labelState.idServiceProperty().get();
		if ( idService == null )
		{
			LOG.warn( "Source {} does not provide id-service -- cannot create new id.", currentSource );
			return;
		}

		final SelectedIds selectedIds = labelState.selectedIds().get( mode );
		action.accept( selectedIds, idService );
	}

}