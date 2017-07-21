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

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Constants;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.UnloadablePlugin;

public class 
LocationProviderPlugin 
	implements UnloadablePlugin
{
	private PluginInterface		plugin_interface;
	
	private LocationProviderImpl	provider;
	
	@Override
	public void
	initialize(
		PluginInterface _pi )
	
		throws PluginException 
	{
		applyPatch1();
		applyPatch2();
		
		plugin_interface = _pi;
		
		provider = new LocationProviderImpl( plugin_interface.getPluginVersion(), new File( plugin_interface.getPluginDirectoryName()));
		
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
		// 5100 bug with simple torrent being saved to a FILE named 'Vuze Downloads'
		// installer has been fixed to create the folder to prevent the issue with new users
		// for existing users look to see if the problem has been encountered and recover things a bit
		
		try{
			if ( Constants.getCurrentVersion().startsWith( "5.1.0." )){
				
				if ( COConfigurationManager.getIntParameter( "azlocprov.patch5100.1.applied", 0 ) == 0 ){
					
					int	patch_result = 99;
					
					try{
						File save_path = new File( COConfigurationManager.getStringParameter( "Default save path" ));
					
						if ( !save_path.exists()){
							
							save_path.mkdirs();
							
							patch_result = 1;
							
						}else if ( save_path.isFile()){
							
							String str = save_path.getAbsolutePath();
							
							if ( !str.endsWith( "2" )){
								
								save_path = new File( str + "2" );
								
								if ( save_path.mkdirs()){
									
									COConfigurationManager.setParameter( "Default save path", save_path.getAbsolutePath());
									
									patch_result = 2;
									
								}else{
									
									patch_result = 3;
								}
							}else{
								
								patch_result = 4;
							}
						}else{
							
							patch_result = 5;
						}
					}finally{
						
						COConfigurationManager.setParameter( "azlocprov.patch5100.1.applied", patch_result );
						
						COConfigurationManager.save();
					}
				}
			}
		}catch( Throwable e ){
			
		}
	}
	
	private void
	applyPatch2()
	{
		// OSX Mavericks Nap prevention
		
		try{
			if ( Constants.getCurrentVersion().startsWith( "5.1.0." ) && Constants.isOSX ){
				
				if ( COConfigurationManager.getIntParameter( "azlocprov.patch5100.2.applied", 0 ) == 0 ){
					
					int	patch_result = 99;
					
					try{
						
				      	if ( new File( "/usr/bin/defaults" ).exists()){
			        		
				        	String[] command = {
				        		"/usr/bin/defaults",
				        		"write",
				        		"com.azureus.vuze",
				        		"NSAppSleepDisabled",
				        		"-bool",
				        		"YES"
				        	};
				        	
				        	Runtime.getRuntime().exec( command );
				        	
				        	patch_result = 1;
				        	
			        	}else{
			        		
			        		patch_result = 2;
			        	}
					}catch( Throwable e ){
						
						patch_result = 3;
						
					}finally{
						
						COConfigurationManager.setParameter( "azlocprov.patch5100.2.applied", patch_result );
						
						COConfigurationManager.save();
					}
				}
			}
		}catch( Throwable e ){			
		}
	}
}
