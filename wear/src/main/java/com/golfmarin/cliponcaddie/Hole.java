package com.golfmarin.cliponcaddie;

import com.google.android.gms.maps.model.LatLng;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import android.util.Log;

public class Hole implements Parcelable {
	
	String holeNum = "None";
	
	LatLng front = null;
	LatLng middle = null;
	LatLng back = null;
	
	Hole(String hole){
		holeNum = hole;
	}
	
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
    	out.writeString(holeNum);
        out.writeParcelable(front, 0);
        out.writeParcelable(middle, 0);
        out.writeParcelable(back, 0);
    }

    public static final Parcelable.Creator<Hole> CREATOR
            = new Parcelable.Creator<Hole>() {
        public Hole createFromParcel(Parcel in) {
            return new Hole(in);
        }

        public Hole[] newArray(int size) {
            return new Hole[size];
        }
    };
    
    private Hole(Parcel in) {
    	holeNum = in.readString();
        front = in.readParcelable(LatLng.class.getClassLoader());
        middle = in.readParcelable(LatLng.class.getClassLoader());
        back = in.readParcelable(LatLng.class.getClassLoader());
//        Log.v("myApp", "Hole " + holeNum + " front: " + front);
    }

    /***************************************************
     * Returns a Location object for the hole position.
     * @param placement front, middle, or back
     * @return placement Location
     */
    public Location getLocation(String placement) {

        Location location = new Location("");
        if (placement.equals("front")) {
            location.setLatitude(front.latitude);
            location.setLongitude(front.longitude);
        }
        else if (placement.equals("middle")) {
            location.setLatitude(middle.latitude);
            location.setLongitude(middle.longitude);
        }
        else if (placement.equals("back")) {
            location.setLatitude(back.latitude);
            location.setLongitude(back.longitude);
        }
        return location;
    }
}

