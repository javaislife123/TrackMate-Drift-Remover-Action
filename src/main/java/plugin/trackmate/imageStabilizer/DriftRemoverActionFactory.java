package plugin.trackmate.imageStabilizer;

import javax.swing.ImageIcon;

import org.scijava.plugin.Plugin;

import fiji.plugin.trackmate.action.TrackMateAction;
import fiji.plugin.trackmate.action.TrackMateActionFactory;
import ij.ImagePlus;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.view.Views;


@Plugin( type = TrackMateActionFactory.class, enabled = true, visible = true )
public class DriftRemoverActionFactory implements TrackMateActionFactory
{

	private static final String INFO_TEXT = "<html>This action will correct drift.</html>";

	private static final String KEY = "DRIFT_CORRECTOR";

	private static final String NAME = "Drift Corrector";

	@Override
	public String getInfoText()
	{
		return INFO_TEXT;
		

	}

	@Override
	public ImageIcon getIcon()
	{
		return null;
	}

	@Override
	public String getKey()
	{
		return KEY;
	}

	@Override
	public String getName()
	{
		return NAME;
	}

	@Override
	public TrackMateAction create()
	{
		return new DriftRemoverAction();
	}
}