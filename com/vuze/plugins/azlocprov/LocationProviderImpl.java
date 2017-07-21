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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.*;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.utils.LocationProvider;

import com.maxmind.geoip.Country;
import com.maxmind.geoip.LookupService;

public class
LocationProviderImpl 
	extends LocationProvider
{
	private static final int[][] FLAG_SIZES = {{18,12},{25,15}};
	
	private final String	plugin_version;
	private final File		plugin_dir;
	
	private final boolean	has_images_dir;
	
	private volatile boolean is_destroyed;
	
	private volatile LookupService	ls_ipv4;
	private volatile LookupService	ls_ipv6;
	
	private Set<String>	failed_dbs = new HashSet<String>();
	
	protected
	LocationProviderImpl(
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
	
	private LookupService
	getLookupService(
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
			LookupService ls = new LookupService( db_file );
			
			if ( ls != null ){
				
				System.out.println( "Loaded " + db_file );
				
				return( ls );
			}
		}catch( Throwable e ){
			
			Debug.out( "Failed to load LookupService DB from " + db_file, e );
		}
		
		failed_dbs.add( database_name );
		
		return( null );
	}
	
	private LookupService
	getLookupService(
		InetAddress		ia )
	{
		LookupService result;
		
		if ( ia instanceof Inet4Address ){
			
			result = ls_ipv4;
			
			if ( result == null ){
								
				if ( plugin_version.length() > 0 && Constants.compareVersions( plugin_version, "0.1.1" ) > 0 ){
					
					result = ls_ipv4 = getLookupService( "GeoIP_" + plugin_version + ".dat" );
				}
				
				if ( result == null ){
				
					result = ls_ipv4 = getLookupService( "GeoIP.dat" );
				}
			}
		}else{
			
			result = ls_ipv6;
			
			if ( result == null ){
				
				if ( plugin_version.length() > 0 && Constants.compareVersions( plugin_version, "0.1.1" ) > 0 ){
					
					result = ls_ipv6 = getLookupService( "GeoIPv6_" + plugin_version + ".dat" );
				}
				
				if ( result == null ){
				
					result = ls_ipv6 = getLookupService( "GeoIPv6.dat" );
				}
			}
		}
		
		return( result );
	}
	
	private Country 
	getCountry(
		InetAddress	ia )
	{
		if ( ia == null ){
			
			return( null );
		}
		
		LookupService	ls = getLookupService( ia );
		
		if ( ls == null ){
			
			return( null );
		}
		
		Country result;
		
		try{
			result = ls.getCountry( ia );
			
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
		Country country = getCountry( address );
		
		if ( country == null ){
			
			return( null );
		}
		
		Locale country_locale = new Locale( "", country.getCode());
		
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
		Country country = getCountry( address );
		
		String result;
		
		if ( country == null ){
			
			result = null;
			
		}else{
		
			result = country.getCode();
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
	
	
	protected void
	destroy()
	{
		is_destroyed = true;
		
		if ( ls_ipv4 != null ){
			
			ls_ipv4.close();
			
			ls_ipv4 = null;
		}
		
		if ( ls_ipv6 != null ){
			
			ls_ipv6.close();
			
			ls_ipv6 = null;
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
			LocationProviderImpl prov = new LocationProviderImpl( "", new File( "C:\\Projects\\development\\azlocprov" ));
			
			System.out.println( prov.getCountry( InetAddress.getByName( "www.vuze.com" )).getCode());
			System.out.println( prov.getCountry( InetAddress.getByName( "2001:4860:4001:801::1011" )).getCode());
			System.out.println( prov.getCountryNameForIP( InetAddress.getByName( "bbc.co.uk" ), Locale.FRANCE ));
			System.out.println( prov.getCountryFlagForIP( InetAddress.getByName( "bbc.co.uk" ), 0 ));
			System.out.println( prov.getCountryFlagForIP( InetAddress.getByName( "bbc.co.uk" ), 1 ));
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
}
