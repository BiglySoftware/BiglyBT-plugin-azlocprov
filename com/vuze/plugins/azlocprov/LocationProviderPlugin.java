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
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagFeatureProperties;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TrackersUtil;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.UnloadablePlugin;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.utils.LocaleUtilities;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.TableStructureEventDispatcher;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.views.tableitems.mytorrents.TagColorsItem;

public class 
LocationProviderPlugin 
	implements UnloadablePlugin
{
	private PluginInterface		plugin_interface;
	private LocaleUtilities		loc_utils;
	private MenuManager			menu_manager;
	
	private TagManager			tag_manager;
	
	private LocationProviderBase	provider;
	
	private MenuItem	tags_menu;
	
	@Override
	public void
	initialize(
		PluginInterface _pi )
	
		throws PluginException 
	{
		plugin_interface = _pi;

		applyPatch1();
				
		provider = new LocationProviderImpl( plugin_interface.getPluginVersion(), new File( plugin_interface.getPluginDirectoryName()));
		
		plugin_interface.getUtilities().addLocationProvider( provider );
		
		loc_utils = plugin_interface.getUtilities().getLocaleUtilities();
		
		UIManager uiManager = plugin_interface.getUIManager();
		
		menu_manager = uiManager.getMenuManager();
	
		tag_manager = TagManagerFactory.getTagManager();
		
		if ( Constants.compareVersions( Constants.getCurrentVersion(), "2.2.0.3" ) >= 0 ){
			
			tags_menu = menu_manager.addMenuItem( MenuManager.MENU_MENUBAR_TOOLS, "azlocprov.tags.ex");
			
			tags_menu.setStyle( MenuItem.STYLE_MENU );		
				
			addConstraint( 	
					tags_menu,
					"azlocprov.tags.ex.added.recently", 
					"age  < weeksToSeconds( 1 )" );
				
			addConstraint( 	
					tags_menu,
					"azlocprov.tags.ex.complete.recently", 
					"isComplete() && completed_age  < weeksToSeconds( 1 )" );
	
			addConstraint( 	
					tags_menu,
					"azlocprov.tags.ex.no.recent.upload", 
					"(!(isStopped() || isPaused() || isError())) && isComplete() && up_idle  >= daysToSeconds( 3 )" );
	
			addConstraint( 	
					tags_menu,
					"azlocprov.tags.ex.bad.ratio", 
					"isComplete() && share_ratio < getConfig( \"queue.seeding.ignore.share.ratio\" ) " );		
				
			addConstraint( 	
				tags_menu,
				"azlocprov.tags.ex.seeding.week", 
				"seeding_for >= weeksToSeconds( 1 )" );
			
			MenuItem menu_row_colours = menu_manager.addMenuItem( tags_menu, "azlocprov.tags.ex.row.colors" );
			
			menu_row_colours.setStyle( MenuItem.STYLE_MENU );
			
			addColour( 	menu_row_colours, 
						"azlocprov.tags.ex.row.colors.stopped", 
						"isStopped() && setColors( -1, #E6C7F4 )" );
			
			addColour( 	menu_row_colours, 
						"azlocprov.tags.ex.row.colors.forced", 
						"isForceStart() && setColors( #FFFFFF, #BE330E )" );
		}
	}
	
	private MenuItem
	addMenuItem(
		Tag[]			existing_tag,
		MenuItem		menu,
		String			resource,
		String			constraint_string )
	{
		MenuItem mi = menu_manager.addMenuItem( menu, resource );

		mi.setStyle( MenuItem.STYLE_CHECK );
		
		mi.addFillListener((m,s)->{
			
			String group_name = loc_utils.getLocalisedMessageText( "azlocprov.tags.ex" );
						
			existing_tag[0] = null;
			
			for ( Tag tag: tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTags()){
			
				String g = tag.getGroup();
				
				if ( g != null && g.equals( group_name )){
					
					TagProperty constraint = ((TagFeatureProperties)tag).getProperty( TagFeatureProperties.PR_CONSTRAINT );
					
					String[] c = constraint.getStringList();
					
					if ( c != null && c.length > 0 ){
						
						if ( c[0].equals( constraint_string )){
							
							existing_tag[0] = tag;
							
							break;
						}
					}
					
				}
			}
			
			mi.setData( existing_tag[0] != null );
		});
		
		return( mi );
	}
	
	private void
	addConstraint(
		MenuItem		menu,
		String			resource,
		String			constraint_string )
	{
		Tag[] existing_tag = { null };
		
		MenuItem mi = addMenuItem( existing_tag, menu, resource, constraint_string );
			
		String tag_name = loc_utils.getLocalisedMessageText( resource );
		
		mi.addListener((m,t)->{
			
			try{
				if ( existing_tag[0] == null ){
					
					Tag tag = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL ).createTag(	tag_name, true );
					
					tag.setPublic( false );
					
					tag.setGroup(  loc_utils.getLocalisedMessageText( "azlocprov.tags.ex" ));
					
					TagProperty constraint = ((TagFeatureProperties)tag).getProperty( TagFeatureProperties.PR_CONSTRAINT );
	
					constraint.setStringList(
						new String[]{
								constraint_string
						});
		
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(	MultipleDocumentInterface.SIDEBAR_SECTION_TAGS );
	
					tag.setTransientProperty( Tag.TP_SETTINGS_REQUESTED, true );
					
				}else{
					
					existing_tag[0].removeTag();
				}
			}catch( Throwable e ){
				
				Debug.out( e );;
			}
		});
	}
	
	private void
	addColour(
		MenuItem		menu,
		String			resource,
		String			constraint_string )
	{
		Tag[] existing_tag = { null };
		
		MenuItem mi = addMenuItem( existing_tag, menu, resource, constraint_string );

		String tag_name = loc_utils.getLocalisedMessageText( resource );

		mi.addListener((m,t)->{
			
			try{
				if ( existing_tag[0] == null ){
					
					Tag tag = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL ).createTag(	tag_name, true );
					
					tag.setPublic( false );
					
					tag.setGroup(  loc_utils.getLocalisedMessageText( "azlocprov.tags.ex" ));
	
					TagProperty constraint = ((TagFeatureProperties)tag).getProperty( TagFeatureProperties.PR_CONSTRAINT );
	
					constraint.setStringList(
						new String[]{
								constraint_string
						});
					
					TableColumnManager tcm = TableColumnManager.getInstance();
					
					for ( String table_id: TableManager.TABLE_MYTORRENTS_ALL ){
					
						TableColumn tc = tcm.getTableColumnCore( table_id, TagColorsItem.COLUMN_ID );
						
						if ( tc != null ){
						
							tc.setVisible( true );
							
							tcm.saveTableColumns( tc.getForDataSourceType(), table_id);
							
							TableStructureEventDispatcher tsed = TableStructureEventDispatcher.getInstance( table_id );
	
							tsed.tableStructureChanged(true, null );
						}
					}
		
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(	MultipleDocumentInterface.SIDEBAR_SECTION_TAGS );
	
					tag.setTransientProperty( Tag.TP_SETTINGS_REQUESTED, true );

				}else{
					
					existing_tag[0].removeTag();
				}
			}catch( Throwable e ){
				
				Debug.out( e );;
			}
		});
	}
	
	@Override
	public void
	unload() 
	
		throws PluginException 
	{
		if ( plugin_interface != null ){
			
			provider.destroy();
			
			plugin_interface.getUtilities().removeLocationProvider( provider );
			
			if ( tags_menu != null ){
				
				tags_menu.remove();
				
				tags_menu = null;
			}
			
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
