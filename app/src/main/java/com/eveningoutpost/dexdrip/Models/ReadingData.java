package com.eveningoutpost.dexdrip.Models;

// class from LibreAlarm

import com.eveningoutpost.dexdrip.Models.UserError.Log;

import com.eveningoutpost.dexdrip.utils.LibreTrendPoint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class ReadingData {

    private static final String TAG = "ReadingData";
    public List<GlucoseData> trend; // Per minute data.
    public List<GlucoseData> history;  // Per 15 minutes data.
    public byte[] raw_data;

    static final int ERROR_INFLUENCE = 4; //  The influence of each error

    public ReadingData() {
        this.trend = new ArrayList<GlucoseData>();
        this.history = new ArrayList<GlucoseData>();
        // The two bytes are needed here since some components don't like a null pointer.
        this.raw_data = new byte[2];
    }

    public ReadingData(List<GlucoseData> trend, List<GlucoseData> history) {
        this.trend = trend;
        this.history = history;
    }


    public static class TransferObject {
        public ReadingData data;

        public TransferObject() {}

        public TransferObject(long id, ReadingData data) {
            this.data = data;
        }
    }

    // A function to calculate the smoothing based only on 3 points.
    private void CalculateSmothedData3Points() {
        for (int i=0; i < trend.size() - 2 ; i++) {
            trend.get(i).glucoseLevelRawSmoothed = 
                    (trend.get(i).glucoseLevelRaw + trend.get(i+1).glucoseLevelRaw + trend.get(i+2).glucoseLevelRaw) / 3;
        }
        // Set the last two points. (doing our best - this will only be used if there are no previous readings).
        if(trend.size() >= 2) {
            // We have two points, use their average for both
            int average = (trend.get(trend.size()-2).glucoseLevelRaw + trend.get(trend.size()-1).glucoseLevelRaw ) / 2;
            trend.get(trend.size()-2).glucoseLevelRawSmoothed = average;
            trend.get(trend.size()-1).glucoseLevelRawSmoothed = average;
        } else if(trend.size() == 1){
            // Only one point, use it
            trend.get(trend.size()-1).glucoseLevelRawSmoothed = trend.get(trend.size()-1).glucoseLevelRaw;
        }
        
    }
    
    private void CalculateSmothedData5Points() {
        // In all places in the code, there should be exactly 16 points.
        // Since that might change, and I'm doing an average of 5, then in the case of less then 5 points,
        // I'll only copy the data as is (to make sure there are reasonable values when the function returns).
        if(trend.size() < 5) {
            for (int i=0; i < trend.size() - 4 ; i++) {
                trend.get(i).glucoseLevelRawSmoothed = trend.get(i).glucoseLevelRaw;
            }
            return;
        }
        
        for (int i=0; i < trend.size() - 4 ; i++) {
            trend.get(i).glucoseLevelRawSmoothed = 
                    (trend.get(i).glucoseLevelRaw + 
                     trend.get(i+1).glucoseLevelRaw + 
                     trend.get(i+2).glucoseLevelRaw + 
                     trend.get(i+3).glucoseLevelRaw +
                     trend.get(i+4).glucoseLevelRaw ) / 5;
        }
        // We now have to calculate the last 4 points, will do our best...
        trend.get(trend.size()-4).glucoseLevelRawSmoothed = 
                (trend.get(trend.size()-4).glucoseLevelRaw + 
                 trend.get(trend.size()-3).glucoseLevelRaw +
                 trend.get(trend.size()-2).glucoseLevelRaw +
                 trend.get(trend.size()-1).glucoseLevelRaw ) / 4;
        
        trend.get(trend.size()-3).glucoseLevelRawSmoothed = 
               (trend.get(trend.size()-3).glucoseLevelRaw +
                trend.get(trend.size()-2).glucoseLevelRaw +
                trend.get(trend.size()-1).glucoseLevelRaw ) / 3;

        // Use the last two points for both last points
        trend.get(trend.size()-2).glucoseLevelRawSmoothed = 
                (trend.get(trend.size()-2).glucoseLevelRaw +
                trend.get(trend.size()-1).glucoseLevelRaw ) / 2;
        
        trend.get(trend.size()-1).glucoseLevelRawSmoothed = trend.get(trend.size()-2).glucoseLevelRawSmoothed;
    }
    
    public void CalculateSmothedData() {
        CalculateSmothedData5Points();
        // print the values, remove before release
        for (int i=0; i < trend.size() ; i++) {
            Log.e("xxx","" + i + " raw val " +  trend.get(i).glucoseLevelRaw + " smoothed " +  trend.get(i).glucoseLevelRawSmoothed);
        }
    }

    public void ClearErrors(List<LibreTrendPoint> libreTrendPoints) {
        // For the history data where each reading holds data for 15 minutes we remove only bad points.
        Iterator<GlucoseData> it = history.iterator();
        while (it.hasNext()) {
            GlucoseData glucoseData = it.next();
            if (libreTrendPoints.get(glucoseData.sensorTime).isError()) {
                it.remove();
            }
        }

        // For the per minute data, we are also going to check that the data from the last 4 minutes did not have an error.
        HashSet<Integer> errorHash = calculateErrorSet(libreTrendPoints,  trend);

        it = trend.iterator();
        while (it.hasNext()) {
            GlucoseData glucoseData = it.next();

            if(errorHash.contains(glucoseData.sensorTime ) || libreTrendPoints.get(glucoseData.sensorTime).rawSensorValue == 0) {
                Log.e(TAG, "Removing point glucoseData =  " + glucoseData.toString());
                it.remove();
            }
/*
            for (int i = 0; i < ERROR_INFLUENCE && !errorFound; i++) {
                if (libreTrendPoints.get((int) glucoseData.sensorTime - i ).isError()) {
                    Log.e(TAG, "removnig point glucoseData =  " + glucoseData.toString());
                    it.remove();
                    errorFound = true;
                    continue;
                }
            }

 */
        }
    }

    // A helper function to calculate the errors and their influence on data.
    static private  HashSet<Integer> calculateErrorSet(List<LibreTrendPoint> libreTrendPoints, List<GlucoseData> trend) {
        // Create a set of all the points with errors. (without changing libreTrendPoints). Each point with an error
        // has an influence to the next 4 points.
        HashSet<Integer> errorHash = new HashSet<Integer>();
        Iterator<GlucoseData> it = trend.iterator();

        // Find the minimum values to look for error
        int min = libreTrendPoints.size();
        int max = 0;
        while (it.hasNext()) {
            GlucoseData glucoseData = it.next();
            min = Math.min(min, glucoseData.sensorTime);
            max = Math.max(max,  glucoseData.sensorTime);
        }
        min = Math.max(0, min - ERROR_INFLUENCE);
        max = Math.min(libreTrendPoints.size(), max + ERROR_INFLUENCE);

        for(int i = min; i < max ; i++) {
            System.err.println("Checking glucoseData + for errors" + libreTrendPoints.get(i ));
            if (libreTrendPoints.get(i ).isError()) {
                System.err.println("error found" + libreTrendPoints.get(i ));
                for (int j=0; j < ERROR_INFLUENCE; j++) {

                    errorHash.add( i + j);
                }
            }
        }
        return errorHash;

    }

    public void calculateSmoothDataImproved(List<LibreTrendPoint> libreTrendPoints) {
        // use the data on libreTrendPoints to do the calculations.
        // Try to use the first 5 points to do the average if they exist and if not use up to 7 more points.

        HashSet<Integer> errorHash = calculateErrorSet(libreTrendPoints,  trend);
        Iterator<GlucoseData> it = trend.iterator();
        while (it.hasNext()) {
            GlucoseData glucoseData = it.next();
            boolean remove = calculateSmoothDataPerPoint(glucoseData, libreTrendPoints, errorHash);
            if(remove) {
                it.remove();
            }
        }
    }

    // true means we need to remove this objects.
    private boolean calculateSmoothDataPerPoint(GlucoseData glucoseData, List<LibreTrendPoint> libreTrendPoints, HashSet<Integer> errorHash) {
        if(glucoseData.sensorTime < 7) {
            // First values are not interesting, but would make the algorithm more complex.
            return false;
        }

        int points_used = 0;
        double sum = 0;

        for(int i = 0; i < 7 && points_used < 5; i++) {
            LibreTrendPoint libreTrendPoint = libreTrendPoints.get(glucoseData.sensorTime - i);
            if(errorHash.contains(glucoseData.sensorTime - i) || libreTrendPoint.rawSensorValue == 0) {
                continue;
            }
            sum += libreTrendPoint.rawSensorValue;
            points_used++;
        }
        if(points_used > 0) {
            glucoseData.glucoseLevelRawSmoothed = (int)(sum / points_used);
        } else {
            //glucoseData.glucoseLevelRawSmoothed = 0;
            Log.e(TAG, "Removing object because it does not have any data " + glucoseData);
            return true;
        }
        return false;
    }
}
