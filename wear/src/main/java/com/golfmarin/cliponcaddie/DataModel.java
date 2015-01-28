package com.golfmarin.cliponcaddie;

import java.util.ArrayList;
import org.json.JSONArray;
import android.content.Context;
import android.util.Log;


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
		}
	    
		// Method to retrieve courses
	    public ArrayList<Course> getCourses() {
	    	return allCoursesArray;
	    }
}


