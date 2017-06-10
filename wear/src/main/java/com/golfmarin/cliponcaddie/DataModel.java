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

import java.util.ArrayList;
import org.json.JSONArray;
import android.content.Context;
import android.util.Log;

/*
* This class creates a data model that contains golf courses. The open source
* version has selected courses near San Francisco, CA
*/
public class DataModel {
	
	// Define a class to handle the custom data
	// Initially, just reads json-formatted files and returns an array of objects
   
    	ArrayList<Course> allCoursesArray = new ArrayList<Course>();	
        ArrayList<Course> coursesArray = new ArrayList<Course>();
		
        // Initializer to read a text file into an array of golfcourse objects    
		public DataModel(Context ctx) {
	        JsonParser jp = new JsonParser();
	        
	    	// Read Sonoma courses info from file
	    	JSONArray ja = jp.getJSONFromFile(ctx, "sonoma-json.txt", "golfCourses", "golfCourse");
	    	coursesArray = jp.getCoursesFromJSON(ja, "Sonoma");
	    	allCoursesArray.addAll(coursesArray);
	    	
	    	// Read Marin courses info from file
	    	ja = jp.getJSONFromFile(ctx, "marin-json.txt", "golfCourses", "golfCourse");
	    	coursesArray = jp.getCoursesFromJSON(ja, "Marin");
	    	allCoursesArray.addAll(coursesArray);
	    	
	    	// Read San Francisco courses info from file
	    	ja = jp.getJSONFromFile(ctx, "SanFrancisco-json.txt", "golfCourses", "golfCourse");
	    	coursesArray = jp.getCoursesFromJSON(ja, "San Francisco");
	    	allCoursesArray.addAll(coursesArray);
	    	
	    	// Read San Mateo courses info from file
	    	ja = jp.getJSONFromFile(ctx, "SanMateo-json.txt", "golfCourses", "golfCourse");
	    	coursesArray = jp.getCoursesFromJSON(ja, "San Mateo");
	    	allCoursesArray.addAll(coursesArray);	
	    	
	    	// Read Napa courses info from file
	    	ja = jp.getJSONFromFile(ctx, "napa-json.txt", "golfCourses", "golfCourse");
	    	coursesArray = jp.getCoursesFromJSON(ja, "Napa");
	    	allCoursesArray.addAll(coursesArray);
	    	
	    	// Read Contra Costa courses info from file
	    	ja = jp.getJSONFromFile(ctx, "ContraCosta-json.txt", "golfCourses", "golfCourse");
	    	coursesArray = jp.getCoursesFromJSON(ja, "Contra Costa");
	    	allCoursesArray.addAll(coursesArray);
	    	
	    	// Read Solano courses info from file
	    	ja = jp.getJSONFromFile(ctx, "solano-json.txt", "golfCourses", "golfCourse");
	    	coursesArray = jp.getCoursesFromJSON(ja, "Solano");
	    	allCoursesArray.addAll(coursesArray);

			// Read Santa Clara courses info from file
			ja = jp.getJSONFromFile(ctx, "SantaClara-json.txt", "golfCourses", "golfCourse");
			coursesArray = jp.getCoursesFromJSON(ja, "Santa Clara");
			allCoursesArray.addAll(coursesArray);

			// Read Plumas courses info from file
			ja = jp.getJSONFromFile(ctx, "Plumas-json.txt", "golfCourses", "golfCourse");
			coursesArray = jp.getCoursesFromJSON(ja, "Plumas");
			allCoursesArray.addAll(coursesArray);
		}
	    
		// Method to retrieve courses
	    public ArrayList<Course> getCourses() {
	    	return allCoursesArray;
	    }
}


