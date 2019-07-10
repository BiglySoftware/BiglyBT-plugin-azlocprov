/*
 * Created on Mar 28, 2013
 * Created by Paul Gardner
 * 
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */



package com.vuze.plugins.azlocprov;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.TrackersUtil;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.UnloadablePlugin;

public class 
LocationProviderPlugin 
	implements UnloadablePlugin
{
	private PluginInterface		plugin_interface;
	
	private LocationProviderBase	provider;
	
	@Override
	public void
	initialize(
		PluginInterface _pi )
	
		throws PluginException 
	{
		plugin_interface = _pi;

		applyPatch1();
				
		provider = new LocationProvider2Impl( plugin_interface.getPluginVersion(), new File( plugin_interface.getPluginDirectoryName()));
		
		plugin_interface.getUtilities().addLocationProvider( provider );
	}
	
	
	@Override
	public void
	unload() 
	
		throws PluginException 
	{
		if ( plugin_interface != null ){
			
			provider.destroy();
			
			plugin_interface.getUtilities().removeLocationProvider( provider );
			
			provider			= null;
			plugin_interface 	= null;
		}
	}
	
	private void
	applyPatch1()
	{
		// 1800: MultiTrackerEditor is borked if the user has NO existing tracker templates. Fix is to 	
		// add a default one
		
		try{
			if ( Constants.getCurrentVersion().startsWith( "1.8.0." )){
														
				TrackersUtil tu = TrackersUtil.getInstance();
						
				Map<String,List<List<String>>> mts = tu.getMultiTrackers();
						
				if ( mts.isEmpty()){
							
					tu.addMultiTracker( "Default", new ArrayList<>());
				}
				
				String sel = COConfigurationManager.getStringParameter( "multitrackereditor.last.selection", "" );
				
				if ( sel.isEmpty()){
					
					COConfigurationManager.setParameter( "multitrackereditor.last.selection", "Default" );
				}
			}
		}catch( Throwable e ){
			
		}
	}
}
