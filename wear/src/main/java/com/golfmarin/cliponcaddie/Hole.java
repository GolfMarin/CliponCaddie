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

import com.google.android.gms.maps.model.LatLng;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

/*
* This class describes the hole placements for a golf course green.
*/

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

