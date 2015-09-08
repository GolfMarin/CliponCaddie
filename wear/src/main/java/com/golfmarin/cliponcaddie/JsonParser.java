package com.golfmarin.cliponcaddie;

/*
        Copyright (C) 2015  Michael Hahn

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import android.content.Context;
import java.util.ArrayList;
import java.io.InputStream;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.HttpURLConnection;


import com.google.android.gms.maps.model.LatLng;

import android.util.Log;


public class JsonParser {
	
	public JSONArray getJSONFromFile(Context ctx, String filename, String objects, String object)
	{
		// This could be a general parser that expects a file containing an objects/object hierarchy
		// This code uses names specific to golfcourses
		InputStream input;
		String jsonString = null;
		JSONObject golfcoursesJson = null;
		JSONObject golfcourseJson = null;
		JSONArray golfcourseJsonArray = null;
		Log.v("mytag", "Started getJSONFromFile:" + filename);
		// Read the file to a string
		try {
			input = ctx.getAssets().open(filename);
			int size = input.available();
			byte[] buffer = new byte[size];
			input.read(buffer);
			input.close();
			jsonString = new String(buffer);
		}
		catch (Exception e) {
    		Log.i("mytag","Couldn't read json file " + e.toString());
		}
		
		// Extract JSONArray from string
		try {
			golfcoursesJson = new JSONObject(jsonString);
			golfcourseJson = golfcoursesJson.getJSONObject(objects);
			golfcourseJsonArray = golfcourseJson.getJSONArray(object);
		}
		catch (JSONException e) {
    		Log.i("mytag","Couldn't parse JSON string." + e);
		}
		return golfcourseJsonArray; 
	}
	/*
		public ArrayList<County> getCountiesFromJSON(JSONArray counties) {
		
		ArrayList<County> countiesList = new ArrayList<County>();

		
		try{
			for(int i=0; i<counties.length(); i++) {
				JSONObject currentObject = counties.getJSONObject(i);
				County county = new County(currentObject.getString("name"));
				county.countyInfo = currentObject.getString("countyInfo");
				county.latitude = currentObject.getDouble("latitude");
				county.longitude = currentObject.getDouble("longitude");
				county.id = currentObject.getString("id");
				county.woeid = currentObject.getString("woeid");
				county.thumbnailURL = currentObject.getString("thumbnailURL");
				
				countiesList.add(county);
				// Log.i("mytag","Added county " + county);
			}
		}
		catch (JSONException e) {
    		//Log.i("mytag","Couldn't parse JSON county object." + e);
		}
		return countiesList;
	}
	*/
	// Fetch golf courses
	
	public ArrayList<Course> getCoursesFromJSON(JSONArray courses, String county) {
		
//		JSONObject holeJson = null;
		JSONArray holeJsonArray = null;
		JSONArray positionJsonArray = null;
		ArrayList<Course> coursesList = new ArrayList<Course>();
		ArrayList<Hole> localHoleList = new ArrayList<Hole>();
		
		try{
			for(int i=0; i<courses.length(); i++) {
				JSONObject currentObject = courses.getJSONObject(i);
				Course course = new Course(currentObject.getString("name"));
				course.address = currentObject.getString("address");
				course.city = currentObject.getString("city");
				course.phone = currentObject.getString("phone");
				course.holes = currentObject.getInt("holes");
				course.slope = currentObject.getString("slope");
				course.isPublic = currentObject.getBoolean("isPublic");
				course.courseInfo = currentObject.getString("info");
				course.directions = currentObject.getString("directions");
				course.latitude = currentObject.getDouble("latitude");
				course.longitude = currentObject.getDouble("longitude");
				course.id = currentObject.getString("id");
				course.woeid = currentObject.getString("woeid");
				course.imageURL = currentObject.getString("imageURL");
				course.thumbnailURL = currentObject.getString("thumbnailURL");
				
				
				course.county = county;

				// Get the holes, if the course has a "caddy" object
				
				if(currentObject.has("caddy")) {
					
					holeJsonArray = currentObject.getJSONArray("caddy");
											
					if(holeJsonArray != null) {
						// Read each hole					
						localHoleList = new ArrayList<Hole>();
						try{
							for(int j=0; j< holeJsonArray.length(); j++) {
								JSONObject currentHoleObject = holeJsonArray.getJSONObject(j);
								Hole currentHole = new Hole(currentHoleObject.getString("hole"));
//								Log.v("myApp", currentHole.holeNum);
								
								// Get the placements, if the course has a "placement" object
								
								if(currentHoleObject.has("placement")) {
									// Read hole placements, front, middle, back
									positionJsonArray = currentHoleObject.getJSONArray("placement");
															
									if(positionJsonArray != null) {
								
										try{
											LatLng frontPosition = new LatLng(positionJsonArray.getJSONObject(0).getDouble("latitude"),
													positionJsonArray.getJSONObject(0).getDouble("longitude"));
											
											LatLng middlePosition = new LatLng(positionJsonArray.getJSONObject(1).getDouble("latitude"),
													positionJsonArray.getJSONObject(1).getDouble("longitude"));	
											
											LatLng backPosition = new LatLng(positionJsonArray.getJSONObject(2).getDouble("latitude"),
													positionJsonArray.getJSONObject(2).getDouble("longitude"));
																					
											currentHole.front = frontPosition;
											currentHole.middle = middlePosition;
											currentHole.back = backPosition;
											
//											Log.v("myApp", "Front: latitude: " + currentHole.front.latitude + ", longitude: " + currentHole.front.longitude);
//											Log.v("myApp", "Back: latitude: " + currentHole.back.latitude + ", longitude: " + currentHole.back.longitude);
										}
										catch (JSONException e) {
								    		Log.v("myApp","Couldn't parse JSON position array." + e);
										}
									}		
								}
								else{
									Log.v("myApp", "oops, no placement in " + course.name + currentHole.holeNum);
								}
								localHoleList.add(currentHole);
							}
						}
						catch (JSONException e) {
				    		Log.v("myApp","Couldn't parse JSON hole array." + e);
						}
					}
					course.holeList = localHoleList;

				}
				else{
					course.holeList = null;
				//	Log.v("myApp", "oops, no caddy in " + course.name);
				}			
				coursesList.add(course);
//				Log.v("myApp", "JSONParser, course: " + course.name);
//				if (course.holeList != null)
//				Log.v("myApp", "First hole front longitude: " + course.holeList.get(0).front.longitude);
						
				 // Log.i("myApp","Added course " + course); 							
			}	
		}		
		catch (JSONException e) {
    		Log.v("myApp","Couldn't parse JSON course object." + e); 
		}
		return coursesList;
	}
}
