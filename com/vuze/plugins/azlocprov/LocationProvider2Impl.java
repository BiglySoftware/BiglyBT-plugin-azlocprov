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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.*;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;


public class
LocationProvider2Impl 
	extends LocationProviderBase
{
	private static final int[][] FLAG_SIZES = {{18,12},{25,15}};
	
	private final String	plugin_version;
	private final File		plugin_dir;
	
	private final boolean	has_images_dir;
	
	private volatile boolean is_destroyed;
	
	private volatile DatabaseReader 	db_reader;
	
	private Set<String>	failed_dbs = new HashSet<String>();
	
	protected
	LocationProvider2Impl(
		String		_plugin_version,
		File		_plugin_dir )
	{
		plugin_version	= _plugin_version==null?"":_plugin_version;
		plugin_dir 		= _plugin_dir;
		
		has_images_dir = new File( plugin_dir, "images" ).isDirectory();
	}
	
	@Override
	public String
	getProviderName()
	{
		return( Constants.APP_NAME + " Location Provider" );
	}
	
	@Override
	public long
	getCapabilities()
	{
		return( CAP_COUNTY_BY_IP | CAP_FLAG_BY_IP | CAP_ISO3166_BY_IP );
	}
	
	private DatabaseReader
	getDBReader(
		String	database_name )
	{
		if ( failed_dbs.contains( database_name )){
			
			return( null );
		}
		
		if ( is_destroyed ){
			
			return( null );
		}
		
		File	db_file = new File( plugin_dir, database_name );
		
		try{
			DatabaseReader reader = new DatabaseReader.Builder(db_file).build();
			
			if ( reader != null ){
				
				System.out.println( "Loaded " + db_file );
				
				return( reader );
			}
		}catch( Throwable e ){
			
			Debug.out( "Failed to load LookupService DB from " + db_file, e );
		}
		
		failed_dbs.add( database_name );
		
		return( null );
	}
	
	private DatabaseReader
	getDBReader(
		InetAddress		ia )
	{
		DatabaseReader result = db_reader;
			
		if ( result == null ){
							
			if ( plugin_version.length() > 0 && Constants.compareVersions( plugin_version, "0.1.1" ) > 0 ){
				
				result = db_reader = getDBReader( "GeoLite2-Country_" + plugin_version + ".mmdb" );
			}
			
			if ( result == null ){
			
				result = db_reader = getDBReader( "GeoLite2-Country.mmdb" );
			}
		}
		
		return( result );
	}
	
	private CountryResponse 
	getCountry(
		InetAddress	ia )
	{
		if ( ia == null ){
			
			return( null );
		}
		
		DatabaseReader	ls = getDBReader( ia );
		
		if ( ls == null ){
			
			return( null );
		}
		
		CountryResponse result;
		
		try{
			result = ls.country( ia );
			
		}catch ( Throwable  e ){
			
			result = null;
		}
		
		return( result );
	}
	
	@Override
	public String
	getCountryNameForIP(
		InetAddress		address,
		Locale			in_locale )
	{
		CountryResponse response = getCountry( address );
		
		if ( response == null ){
			
			return( null );
		}
		
		Country country = response.getCountry();
		
		if ( country == null ){
			
			return( null );
		}
		
		Locale country_locale = new Locale( "", country.getIsoCode());
		
		try{
			country_locale.getISO3Country();
			
			return( country_locale.getDisplayCountry( in_locale ));
			
		}catch( Throwable e ){
			
			return( country.getName());
		}
	}
	
	@Override
	public String
	getISO3166CodeForIP(
		InetAddress		address )
	{
		CountryResponse response = getCountry( address );
		
		if ( response == null ){
			
			return( null );
		}
		
		Country country = response.getCountry();
		
		String result;
		
		if ( country == null ){
			
			result = null;
			
		}else{
		
			result = country.getIsoCode();
		}
				
		return( result );
	}
		
	@Override
	public int[][]
	getCountryFlagSizes()
	{
		return( FLAG_SIZES );
	}
		
	@Override
	public InputStream
	getCountryFlagForIP(
		InetAddress		address,
		int				size_index )
	{
		String code = getISO3166CodeForIP( address );
		
		if ( code == null ){
			
			return( null );
		}
		
		String flag_file_dir 	= (size_index==0?"18x12":"25x15");
		String flag_file_name 	= code.toLowerCase() + ".png";
		
		if ( has_images_dir ){
			
			File ff = new File( plugin_dir, "images" + File.separator + flag_file_dir + File.separator + flag_file_name );
			
			if ( ff.exists()){
				
				try{
					return( new ByteArrayInputStream( FileUtil.readFileAsByteArray( ff )));
					
				}catch( Throwable e ){
					
					Debug.out( "Failed to load " + ff, e );
				}
			}
		}
		
		return( getClass().getClassLoader().getResourceAsStream( "com/vuze/plugins/azlocprov/images/" + flag_file_dir + "/" + flag_file_name ));
	}
	
	@Override
	public void
	destroy()
	{
		is_destroyed = true;
		
		if ( db_reader != null ){
			
			try{
				db_reader.close();
				
			}catch( Throwable e ){
				
			}
			
			db_reader = null;
		}
	}
	
	@Override
	public boolean 
	isDestroyed() 
	{
		return( is_destroyed );
	}
	
	public static void
	main(
		String[]	args )
	{
		try{
			LocationProvider2Impl prov = new LocationProvider2Impl( "", new File( "C:\\Users\\Paul\\git\\BiglyBT-plugin-azlocprov" ));
			
			System.out.println( prov.getCountry( InetAddress.getByName( "www.vuze.com" )).getCountry().getIsoCode());
			System.out.println( prov.getCountry( InetAddress.getByName( "2001:4860:4001:801::1011" )).getCountry().getIsoCode());
			System.out.println( prov.getCountryNameForIP( InetAddress.getByName( "bbc.co.uk" ), Locale.FRANCE ));
			System.out.println( prov.getCountryFlagForIP( InetAddress.getByName( "bbc.co.uk" ), 0 ));
			System.out.println( prov.getCountryFlagForIP( InetAddress.getByName( "bbc.co.uk" ), 1 ));
			
			System.out.println( prov.getCountry( InetAddress.getByName( "193.37.254.27" )).getCountry().getIsoCode());
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
}
